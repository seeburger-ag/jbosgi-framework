/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.logging.Logger;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.ModuleLoaderProvider;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleIdentity;

/**
 * Integration point for the {@link ModuleLoader}.
 *
 * @author thomas.diesler@jboss.com
 * @since 20-Apr-2011
 */
final class DefaultModuleLoaderIntegration extends ModuleLoader implements ModuleLoaderProvider {

    // Provide logging
    static final Logger log = Logger.getLogger(DefaultModuleLoaderIntegration.class);

    private ConcurrentMap<ModuleIdentifier, ModuleHolder> moduleSpecs = new ConcurrentHashMap<ModuleIdentifier, ModuleHolder>();

    static void addService(ServiceTarget serviceTarget) {
        ModuleLoaderProvider service = new DefaultModuleLoaderIntegration();
        ServiceBuilder<ModuleLoaderProvider> builder = serviceTarget.addService(Services.MODULE_LOADER_PROVIDER, service);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private DefaultModuleLoaderIntegration() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        log.debugf("Starting: %s", context.getController().getName());
    }

    @Override
    public void stop(StopContext context) {
        log.debugf("Stopping: %s", context.getController().getName());
    }

    @Override
    public ModuleLoaderProvider getValue() {
        return this;
    }

    @Override
    public ModuleLoader getModuleLoader() {
        return this;
    }

    @Override
    public ModuleSpec findModule(ModuleIdentifier identifier) throws ModuleLoadException {
        ModuleHolder moduleHolder = moduleSpecs.get(identifier);
        return moduleHolder != null ? moduleHolder.getModuleSpec() : null;
    }

    @Override
    protected Module preloadModule(ModuleIdentifier identifier) throws ModuleLoadException {
        Module module = null;
        ModuleHolder moduleHolder = moduleSpecs.get(identifier);
        if (moduleHolder != null) {
            module = moduleHolder.getModule();
            if (module == null) {
                module = loadModuleLocal(identifier);
                moduleHolder.setModule(module);
            }
        }
        return module;
    }

    @Override
    public void addModule(ModuleSpec moduleSpec) {
        log.tracef("addModule: %s", moduleSpec.getModuleIdentifier());
        ModuleIdentifier identifier = moduleSpec.getModuleIdentifier();
        ModuleHolder existing = moduleSpecs.putIfAbsent(identifier, new ModuleHolder(moduleSpec));
        if (existing != null)
            throw new IllegalStateException("Module already exists: " + identifier);
    }

    @Override
    public void addModule(Module module) {
        log.tracef("addModule: %s", module.getIdentifier());
        ModuleIdentifier identifier = module.getIdentifier();
        ModuleHolder existing = moduleSpecs.putIfAbsent(identifier, new ModuleHolder(module));
        if (existing != null) {
            throw new IllegalStateException("Module already exists: " + identifier);
        }
    }

    @Override
    public void removeModule(ModuleIdentifier identifier) {
        log.tracef("removeModule: %s", identifier);
        moduleSpecs.remove(identifier);
        try {
            Module module = loadModuleLocal(identifier);
            if (module != null) {
                unloadModuleLocal(module);
            }
        } catch (ModuleLoadException ex) {
            // ignore
        }
    }

    @Override
    public ModuleIdentifier getModuleIdentifier(XModule resModule) {
        XModuleIdentity moduleId = resModule.getModuleId();
        String slot = moduleId.getVersion().toString();
        int revision = moduleId.getRevision();
        if (revision > 0) {
            slot += "-rev" + revision;
        }
        String name = Constants.JBOSGI_PREFIX + "." + moduleId.getName();
        return ModuleIdentifier.create(name, slot);
    }

    @Override
    public void setAndRelinkDependencies(Module module, List<DependencySpec> dependencies) throws ModuleLoadException {
        super.setAndRelinkDependencies(module, dependencies);
    }

    @Override
    public String toString() {
        return DefaultModuleLoaderIntegration.class.getSimpleName();
    }

    static class ModuleHolder {

        private ModuleSpec moduleSpec;
        private Module module;

        ModuleHolder(ModuleSpec moduleSpec) {
            if (moduleSpec == null)
                throw new IllegalArgumentException("Null moduleSpec");
            this.moduleSpec = moduleSpec;
        }

        ModuleHolder(Module module) {
            if (module == null)
                throw new IllegalArgumentException("Null module");
            this.module = module;
        }

        ModuleSpec getModuleSpec() {
            return moduleSpec;
        }

        Module getModule() {
            return module;
        }

        void setModule(Module module) {
            this.module = module;
        }
    }
}