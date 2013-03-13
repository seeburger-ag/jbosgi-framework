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
package org.jboss.osgi.framework.spi;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.osgi.framework.internal.SystemPathsImpl;

/**
 * A plugin manages the Framework's system packages.
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public class SystemPathsPlugin extends AbstractIntegrationService<SystemPaths> {

    private final FrameworkBuilder frameworkBuilder;

    public SystemPathsPlugin(FrameworkBuilder frameworkBuilder) {
        super(IntegrationServices.SYSTEM_PATHS_PLUGIN);
        this.frameworkBuilder = frameworkBuilder;
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<SystemPaths> builder) {
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    protected SystemPaths createServiceValue(StartContext startContext) throws StartException {
        return new SystemPathsImpl(frameworkBuilder);
    }
}