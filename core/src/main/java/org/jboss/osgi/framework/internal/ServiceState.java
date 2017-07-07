/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.osgi.framework.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.metadata.CaseInsensitiveDictionary;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * The service implementation.
 *
 * @author thomas.diesler@jboss.com
 * @author David Bosschaert
 * @since 29-Jun-2010
 */
@SuppressWarnings("rawtypes")
final class ServiceState implements ServiceRegistration, ServiceReference {
    // Provide logging
    private static final Logger log = Logger.getLogger(ServiceState.class);

    private final ServiceManagerPlugin serviceManager;
    private final AbstractBundleState ownerBundle;
    private final Set<ServiceName> serviceNames;
    private final long serviceId;
    private final ValueProvider valueProvider;
    private final ServiceReference reference;
    private ServiceRegistration registration;
    private final Set<AbstractBundleState> usingBundles = Collections.newSetFromMap(new ConcurrentHashMap());
    private Map<Long, ServiceFactoryHolder> factoryValues;

    // The properties
    private CaseInsensitiveDictionary prevProperties;
    private CaseInsensitiveDictionary currProperties;

    @SuppressWarnings("unchecked")
    ServiceState(ServiceManagerPlugin serviceManager, AbstractBundleState owner, long serviceId, String[] classNames, ValueProvider valueProvider, Dictionary properties) {
        if (serviceManager == null)
            throw new IllegalArgumentException("Null serviceManager");
        if (owner == null)
            throw new IllegalArgumentException("Null owner");
        if (classNames == null || classNames.length == 0)
            throw new IllegalArgumentException("Null clazzes");
        if (valueProvider == null)
            throw new IllegalArgumentException("Null valueProvider");

        this.serviceManager = serviceManager;
        this.ownerBundle = owner;
        this.serviceId = serviceId;
        this.valueProvider = valueProvider;

        if (checkValidClassNames(owner, classNames, valueProvider.getValue()) == false)
            throw new IllegalArgumentException("Invalid object class in: " + Arrays.asList(classNames));

        // Generate the service names
        serviceNames = new HashSet<ServiceName>(classNames.length);
        for (int i = 0; i < classNames.length; i++) {
            if (classNames[i] == null)
                throw new IllegalArgumentException("Null service class at index: " + i);

            ServiceName serviceName = ServiceState.createServiceName(classNames[i]);
            serviceNames.add(serviceName);
        }

        if (properties == null)
            properties = new Hashtable();

        properties.put(Constants.SERVICE_ID, serviceId);
        properties.put(Constants.OBJECTCLASS, classNames);
        this.currProperties = new CaseInsensitiveDictionary(properties);

        // Create the {@link ServiceRegistration} and {@link ServiceReference}
        this.registration = new ServiceRegistrationWrapper(this);
        this.reference = new ServiceReferenceWrapper(this);
    }

    /**
     * Assert that the given reference is an instance of ServiceState
     *
     * @throws IllegalArgumentException if the given reference is not an instance of ServiceState
     */
    static ServiceState assertServiceState(ServiceReference sref) {
        if (sref == null)
            throw new IllegalArgumentException("Null sref");

        if (sref instanceof ServiceReferenceWrapper)
            sref = ((ServiceReferenceWrapper) sref).getServiceState();

        return (ServiceState) sref;
    }

    long getServiceId() {
        return serviceId;
    }

    static ServiceName createXServiceName(String clazz) {
        return Services.JBOSGI_XSERVICE_BASE_NAME.append(clazz);
    }

    static ServiceName createServiceName(String clazz) {
        return Services.JBOSGI_SERVICE_BASE_NAME.append(clazz);
    }

    Object getRawValue() {
        return valueProvider.getValue();
    }

    Object getScopedValue(AbstractBundleState bundleState) {

        // For non-factory services, return the value
        Object value = valueProvider.getValue();
        if (value instanceof ServiceFactory == false)
            return value;

        // Get the ServiceFactory value
        Object result = null;
        try {
            if (factoryValues == null)
                factoryValues = new HashMap<Long, ServiceFactoryHolder>();

            ServiceFactoryHolder factoryHolder = getFactoryHolder(bundleState);
            if (factoryHolder == null) {
                ServiceFactory factory = (ServiceFactory) value;
                factoryHolder = new ServiceFactoryHolder(bundleState, factory);
                factoryValues.put(bundleState.getBundleId(), factoryHolder);
            }

            result = factoryHolder.getService();

            // If the service object returned by the ServiceFactory object is not an instanceof all the classes named
            // when the service was registered or the ServiceFactory object throws an exception,
            // null is returned and a Framework event of type {@link FrameworkEvent#ERROR}
            // containing a {@link ServiceException} describing the error is fired.
            if (result == null) {
                ServiceException sex = new ServiceException("Cannot get factory value for service "+ServiceState.this.toString(), ServiceException.FACTORY_ERROR);
                FrameworkEventsPlugin eventsPlugin = serviceManager.getFrameworkEventsPlugin();
                eventsPlugin.fireFrameworkEvent(bundleState, FrameworkEvent.ERROR, sex);
            }
        } catch (Throwable th) {
            ServiceException sex = new ServiceException("Cannot get factory value", ServiceException.FACTORY_EXCEPTION, th);
            FrameworkEventsPlugin eventsPlugin = serviceManager.getFrameworkEventsPlugin();
            eventsPlugin.fireFrameworkEvent(bundleState, FrameworkEvent.ERROR, sex);
        }
        return result;
    }

    void ungetScopedValue(AbstractBundleState bundleState) {
        Object value = valueProvider.getValue();
        if (value instanceof ServiceFactory) {
            ServiceFactoryHolder factoryHolder = getFactoryHolder(bundleState);
            if (factoryHolder != null) {
                try {
                    factoryHolder.ungetService();
                } catch (RuntimeException rte) {
                    ServiceException sex = new ServiceException("Cannot unget factory value", ServiceException.FACTORY_EXCEPTION, rte);
                    FrameworkEventsPlugin eventsPlugin = serviceManager.getFrameworkEventsPlugin();
                    eventsPlugin.fireFrameworkEvent(bundleState, FrameworkEvent.WARNING, sex);
                }
            }
        }
    }

    private ServiceFactoryHolder getFactoryHolder(AbstractBundleState bundleState) {
        return factoryValues != null ? factoryValues.get(bundleState.getBundleId()) : null;
    }

    ServiceRegistration getRegistration() {
        return registration;
    }

    Set<ServiceName> getServiceNames() {
        return Collections.unmodifiableSet(serviceNames);
    }

    @Override
    public ServiceReference getReference() {
        assertNotUnregistered();
        return reference;
    }

    @Override
    public void unregister() {
        assertNotUnregistered();
        unregisterInternal();
    }

    void unregisterInternal() {
        serviceManager.unregisterService(this);
        usingBundles.clear();
        registration = null;
    }

    @Override
    public Object getProperty(String key) {
        if (key == null)
            return null;
        return currProperties.get(key);
    }

    @Override
    public String[] getPropertyKeys() {
        List<String> result = new ArrayList<String>();
        if (currProperties != null) {
            Enumeration<String> keys = currProperties.keys();
            while (keys.hasMoreElements())
                result.add(keys.nextElement());
        }
        return result.toArray(new String[result.size()]);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public void setProperties(Dictionary properties) {
        assertNotUnregistered();

        // Remember the previous properties for a potential
        // delivery of the MODIFIED_ENDMATCH event
        prevProperties = currProperties;

        if (properties == null)
            properties = new Hashtable();

        properties.put(Constants.SERVICE_ID, currProperties.get(Constants.SERVICE_ID));
        properties.put(Constants.OBJECTCLASS, currProperties.get(Constants.OBJECTCLASS));
        currProperties = new CaseInsensitiveDictionary(properties);

        // This event is synchronously delivered after the service properties have been modified.
        FrameworkEventsPlugin eventsPlugin = serviceManager.getFrameworkEventsPlugin();
        eventsPlugin.fireServiceEvent(ownerBundle, ServiceEvent.MODIFIED, this);
    }

    Dictionary getPreviousProperties() {
        return prevProperties;
    }

    AbstractBundleState getServiceOwner() {
        return ownerBundle;
    }

    @Override
    public Bundle getBundle() {
        if (isUnregistered())
            return null;

        return ownerBundle;
    }

    void addUsingBundle(AbstractBundleState bundleState) {
        assert usingBundles != null;
        usingBundles.add(bundleState);
    }

    void removeUsingBundle(AbstractBundleState bundleState) {
        assert usingBundles != null;
        usingBundles.remove(bundleState);
    }

    Set<AbstractBundleState> getUsingBundlesInternal() {
        assert usingBundles != null;
        if (usingBundles.isEmpty())
            return Collections.emptySet();

        // Return an unmodifiable snapshot of the set (if becomes empty in the meantime does not harm
        return Collections.unmodifiableSet(new HashSet<AbstractBundleState>(usingBundles));
    }

    @Override
    public Bundle[] getUsingBundles() {
        assert usingBundles != null;
        if (usingBundles.isEmpty())
            return null;

        Set<Bundle> bundles = new HashSet<Bundle>();
        for (AbstractBundleState aux : usingBundles)
            bundles.add(aux);

        // re-check here in case usingBundles did change after first check
        if (bundles.isEmpty())
            return null;

        return bundles.toArray(new Bundle[bundles.size()]);
    }

    @Override
    public boolean isAssignableTo(Bundle bundle, String className) {
        if (bundle == null)
            throw new IllegalArgumentException("Null bundle");
        if (className == null)
            throw new IllegalArgumentException("Null className");

        AbstractBundleState bundleState = AbstractBundleState.assertBundleState(bundle);
        if (bundleState == ownerBundle)
            return true;

        Class<?> targetClass;
        try {
            targetClass = bundle.loadClass(className);
        } catch (ClassNotFoundException ex) {
            // If the requesting bundle does not have a wire to the
            // service package it cannot be constraint on that package.
            log.tracef("Requesting bundle [%s] cannot load class: %s", bundle, className);
            return true;
        }

        // For the bundle that registered the service referenced by this ServiceReference (registrant bundle);
        // find the source for the package. If no source is found then return true if the registrant bundle
        // is equal to the specified bundle; otherwise return false
        Class<?> serviceClass;
        try {
            serviceClass = ownerBundle.loadClass(className);
        } catch (ClassNotFoundException e) {
            log.tracef("Registrant bundle [%s] cannot load class: %s", ownerBundle, className);
            return true;
        }

        // If the package source of the registrant bundle is equal to the package source of the specified bundle
        // then return true; otherwise return false.
        if (targetClass != serviceClass) {
            log.tracef("Not assignable: %s", className);
            return false;
        }

        return true;
    }

    @Override
    public int compareTo(Object sref) {
        if (sref instanceof ServiceReference == false)
            throw new IllegalArgumentException("Invalid sref: " + sref);

        Comparator<ServiceReference> comparator = ServiceReferenceComparator.getInstance();
        return comparator.compare(this, (ServiceReference) sref);
    }

    int getServiceRanking() {
        Object prop = getProperty(Constants.SERVICE_RANKING);
        if (prop instanceof Integer == false)
            return 0;

        return ((Integer) prop).intValue();
    }

    boolean isUnregistered() {
        return registration == null;
    }

    void assertNotUnregistered() {
        if (isUnregistered())
            throw new IllegalStateException("Service is unregistered: " + this);
    }

    private boolean checkValidClassNames(AbstractBundleState bundleState, String[] classNames, Object value) {
        if (bundleState == null)
            throw new IllegalArgumentException("Null bundleState");
        if (classNames == null || classNames.length == 0)
            throw new IllegalArgumentException("Null classNames");
        if (value == null)
            throw new IllegalArgumentException("Null value");

        if (value instanceof ServiceFactory)
            return true;

        for (String className : classNames) {
            if (className == null)
                throw new IllegalArgumentException("Null className");

            try {
                Class<?> valueClass = value.getClass();
                // Use Class.forName with classloader argument as the classloader
                // might be null (for JRE provided types).
                Class<?> clazz = Class.forName(className, false, valueClass.getClassLoader());
                if (clazz.isAssignableFrom(valueClass) == false) {
                    String format = "Service interface [%s] loaded from [%s] is not assignable from [%s] loaded from [%s]";
                    log.errorf(format, className, clazz.getClassLoader(), valueClass.getName(), valueClass.getClassLoader());
                    return false;
                }
            } catch (ClassNotFoundException ex) {
                log.errorf("Cannot load [%s] from: %s", className, bundleState);
                return false;
            }
        }
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String toString() {
        Hashtable<String, Object> props = new Hashtable<String, Object>(currProperties);
        String[] classes = (String[]) props.get(Constants.OBJECTCLASS);
        props.put(Constants.OBJECTCLASS, Arrays.asList(classes));
        return "ServiceState" + props;
    }

    interface ValueProvider {
        Object getValue();
    }

    class ServiceFactoryHolder {

        final ServiceFactory factory;
        final AbstractBundleState bundleState;
        final AtomicInteger useCount;
        volatile Object value;
        private final Semaphore semaphore = new Semaphore(1);

        ServiceFactoryHolder(AbstractBundleState bundleState, ServiceFactory factory) {
            this.bundleState = bundleState;
            this.factory = factory;
            this.useCount = new AtomicInteger();
        }

        Object getService() {

            Object localValue = value;
            // Multiple calls to getService() return the same value
            if (localValue==null) {
                // The Framework must not allow this method to be concurrently called for the same bundle
                semaphore.acquireUninterruptibly();
                try {
                	// with this it is in rare cases possible that we call factory.getService twice, but that is better than not returning anything
                	if(useCount.get() == 0 || value==null) {
                		localValue = factory.getService(bundleState, getRegistration());
                		if (localValue == null) {
                			log.errorf("service factory %s returned a null value for %s",ServiceState.this.toString());
                			return null;
                		}
                		// The Framework will check if the returned service object is an instance of all the
                		// classes named when the service was registered. If not, then null is returned to the bundle.
                		if (checkValidClassNames(ownerBundle, (String[]) getProperty(Constants.OBJECTCLASS), localValue) == false)
                			return null;
                		value = localValue;
                	}
                	else
                	{
                		localValue = value;
                	}
                }
                finally {
                    semaphore.release();
                }
            }
            useCount.incrementAndGet();
            return localValue;
        }

        void ungetService() {
            // Call unget on the factory when done
            if (useCount.decrementAndGet() == 0) {
                semaphore.acquireUninterruptibly();
                try {
                	if(useCount.get() == 0) {
                		factory.ungetService(bundleState, getRegistration(), value);
                		value = null;
                	}
                }
                finally {
                    semaphore.release();
                }
            }
        }
    }
}
