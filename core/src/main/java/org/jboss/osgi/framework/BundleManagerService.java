/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.osgi.framework;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * Integration point for {@link Bundle} management.
 *
 * @author thomas.diesler@jboss.com
 * @since 24-Mar-2011
 */
public interface BundleManagerService extends Service<BundleManagerService> {

    /**
     * Get the service base name for the given bundle.
     */
    ServiceName getServiceName(Bundle bundle);

    /**
     * Install a bundle from the given deployment
     */
    ServiceName installBundle(ServiceTarget serviceTarget, Deployment dep) throws BundleException;

    /**
     * Uninstall the given deployment
     */
    void uninstallBundle(Deployment dep);

    /**
     * Get the bundle base name for the module identifier.
     */
    Bundle getBundle(ModuleIdentifier identifier);

    /**
     * Register a module with the OSGi layer.
     */
    ServiceName registerModule(ServiceTarget serviceTarget, Module module, OSGiMetaData metadata) throws BundleException;

    /**
     * Unregister a module from the OSGi layer.
     */
    void unregisterModule(ModuleIdentifier identifier);

}