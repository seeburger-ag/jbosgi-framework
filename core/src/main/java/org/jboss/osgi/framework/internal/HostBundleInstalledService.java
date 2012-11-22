/*
 * #%L
 * JBossOSGi Framework
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.internal.BundleStoragePlugin.InternalStorageState;
import org.jboss.osgi.framework.spi.ServiceTracker.SynchronousListenerServiceWrapper;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * Represents the INSTALLED state of a host bundle.
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Apr-2011
 */
final class HostBundleInstalledService extends UserBundleInstalledService<HostBundleState, HostBundleRevision> {

    static ServiceName addService(ServiceTarget serviceTarget, FrameworkState frameworkState, Deployment dep, ServiceListener<XBundle> listener) throws BundleException {
        ServiceName serviceName = frameworkState.getBundleManager().getServiceName(dep, Bundle.INSTALLED);
        HostBundleInstalledService service = new HostBundleInstalledService(frameworkState, dep);
        LOGGER.debugf("Installing %s %s", service.getClass().getSimpleName(), serviceName);
        ServiceBuilder<HostBundleState> builder = serviceTarget.addService(serviceName, new SynchronousListenerServiceWrapper<HostBundleState>(service));
        builder.addDependency(InternalServices.FRAMEWORK_CORE_SERVICES);
        if (listener != null) {
            builder.addListener(listener);
        }
        builder.install();
        return serviceName;
    }

    private HostBundleInstalledService(FrameworkState frameworkState, Deployment dep) throws BundleException {
        super(frameworkState, dep);
    }

    @Override
    HostBundleRevision createBundleRevision(Deployment deployment, OSGiMetaData metadata, InternalStorageState storageState) throws BundleException {
        return new HostBundleRevision(getFrameworkState(), deployment, metadata, storageState);
    }

    @Override
    HostBundleState createBundleState(HostBundleRevision revision, ServiceController<HostBundleState> controller, ServiceTarget serviceTarget) {
        HostBundleState hostState = new HostBundleState(getFrameworkState(), revision, controller, serviceTarget);
        HostBundleActiveService.addService(hostState.getServiceTarget(), hostState);
        return hostState;
    }
}
