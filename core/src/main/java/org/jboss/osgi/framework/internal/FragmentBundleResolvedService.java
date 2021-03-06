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

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;

/**
 * Represents the RESOLVED state of a fragment bundle.
 *
 * @author thomas.diesler@jboss.com
 * @since 23-May-2011
 */
final class FragmentBundleResolvedService extends UserBundleResolvedService<FragmentBundleState> {

    private final InjectedValue<FragmentBundleState> injectedBundleState = new InjectedValue<FragmentBundleState>();
    
    static void addService(ServiceTarget serviceTarget, FrameworkState frameworkState, ServiceName serviceName) {
        FragmentBundleResolvedService service = new FragmentBundleResolvedService(frameworkState);
        ServiceBuilder<FragmentBundleState> builder = serviceTarget.addService(serviceName.append("RESOLVED"), service);
        builder.addDependency(serviceName.append("INSTALLED"), FragmentBundleState.class, service.injectedBundleState);
        builder.setInitialMode(Mode.NEVER);
        builder.install();
    }

    private FragmentBundleResolvedService(FrameworkState frameworkState) {
        super(frameworkState);
    }

    @Override
    FragmentBundleState getBundleState() {
        return injectedBundleState.getValue();
    }
}
