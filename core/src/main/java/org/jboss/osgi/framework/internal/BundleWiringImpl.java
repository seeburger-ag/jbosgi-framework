package org.jboss.osgi.framework.internal;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleLoadException;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.resolver.XCapability;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XRequirement;
import org.jboss.osgi.resolver.XWire;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

public class BundleWiringImpl implements BundleWiring {

	private Bundle state;
	private XModule xmodule;
	private BundleRevisionImpl revision;
	static final Logger log = Logger.getLogger(BundleWiringImpl.class);

	public BundleWiringImpl(Bundle abstractBundleState, XModule xmodule) {
		this.state = abstractBundleState;
		this.xmodule = xmodule;
		this.revision = new BundleRevisionImpl(this);
	}

	@Override
	public Bundle getBundle() {
		return state;
	}

	@Override
	public boolean isCurrent() {
		return xmodule != null && xmodule.isResolved();
	}

	@Override
	public boolean isInUse() {
		return xmodule != null && xmodule.getWires()!=null;
	}

	@Override
	public List<BundleCapability> getCapabilities(String namespace) {
		if(xmodule==null)
			return Collections.emptyList();
		List<XCapability> capabilities = xmodule.getCapabilities();
		List<BundleCapability> results = new ArrayList<BundleCapability>(capabilities.size());
		for (XCapability xCapability : capabilities) {
			results.add(new BundleCapabilityImpl(xCapability));
		}
		return results;
	}

	@Override
	public List<BundleRequirement> getRequirements(String namespace) {
		if(xmodule==null)
			return Collections.emptyList();
		List<XRequirement> requirements = xmodule.getRequirements();
		List<BundleRequirement> results = new ArrayList<BundleRequirement>(requirements.size());
		for (XRequirement xRequirement : requirements) {
			results.add(new BundleRequirementImpl(xRequirement));
		}
		return results;
	}

	@Override
	public List<BundleWire> getProvidedWires(String namespace) {
		//TODO: implement this
		return Collections.emptyList();
	}

	@Override
	public List<BundleWire> getRequiredWires(String namespace) {
		if(xmodule==null)
			return Collections.emptyList();
		List<XWire> wires = xmodule.getWires();
		if(wires==null)
			return null;
		List<BundleWire> results = new ArrayList<BundleWire>(wires.size());
		for (XWire xWire : wires) {
			results.add(new BundleWireImpl(xWire));
		}
		return results;
	}

	@Override
	public BundleRevision getRevision() {
		return revision;
	}

	@Override
	public ClassLoader getClassLoader() {
		try {

			if (state instanceof AbstractBundleState) {
				AbstractBundleState bundleState = (AbstractBundleState) state;
				return bundleState.getCurrentRevision().getModuleClassLoader();
			}
			//if it's not an abstract bundle state, it must be the framework, so we use the own classloader
			return getClass().getClassLoader();
		} catch (ModuleLoadException e) {
			log.errorv("Failed to retrieve the bundle classloader for {0}",state,e);
		}
		return getClassLoader();
	}

	@Override
	public List<URL> findEntries(String path, String filePattern, int options) {
		if( (options & FINDENTRIES_RECURSE) == FINDENTRIES_RECURSE)
		{
			List<URL> results = new ArrayList<URL>();
			Enumeration<URL> entries = state.findEntries(path, filePattern, true);
			while(entries.hasMoreElements())
			{
				results.add(entries.nextElement());
			}
			return results;
		}
		return null;
	}

	@Override
	public Collection<String> listResources(String path, String filePattern, int options) {
		if(!isInUse())
		{
			return null;
		}
		Enumeration<String> paths = state.getEntryPaths(path);
		if(paths==null)
			return Collections.emptyList();
		List<String> patternList = null;

		if(filePattern!=null)
		{
			patternList = SimpleFilter.parseSubstring(filePattern);
		}
		List<String> results = new ArrayList<String>();
		while (paths.hasMoreElements()) {
			String string = (String) paths.nextElement();
			if(matches(string,patternList))
				results.add(string);
		}
		return results;
	}

	private boolean matches(String resource, List<String> filePattern) {

		if(filePattern==null || filePattern.isEmpty())
			return true;
        if (resource.charAt(resource.length() - 1) == '/')
        {
            resource = resource.substring(0, resource.length() - 1);
        }
        return SimpleFilter.compareSubstring(filePattern, resource);
	}

	private class BundleWireImpl implements BundleWire
	{
		private XWire delegate;

		public BundleWireImpl(XWire delegate) {
			super();
			this.delegate = delegate;
		}

		@Override
		public BundleCapability getCapability() {
			return new BundleCapabilityImpl(delegate.getCapability());
		}

		@Override
		public BundleRequirement getRequirement() {
			return new BundleRequirementImpl(delegate.getRequirement());
		}

		@Override
		public BundleWiring getProviderWiring() {
			return BundleWiringImpl.this;
		}

		@Override
		public BundleWiring getRequirerWiring() {
			//TODO: implement this
//			XModule importer = delegate.getImporter();
//			if(importer==null)
				return null;
		}


	}

	private class BundleCapabilityImpl implements BundleCapability
	{

		private XCapability capability;



		public BundleCapabilityImpl(XCapability capability) {
			super();
			this.capability = capability;
		}

		@Override
		public String getNamespace() {
			return capability.getName();
		}

		@Override
		public Map<String, String> getDirectives() {
			return capability.getDirectives();
		}

		@Override
		public Map<String, Object> getAttributes() {
			return capability.getAttributes();
		}

		@Override
		public BundleRevision getRevision() {
			Bundle bundle = capability.getModule().getAttachment(Bundle.class);
			BundleWiring wiring = bundle.adapt(BundleWiring.class);
			return wiring.getRevision();
		}
	}

	private static class BundleRevisionImpl implements BundleRevision
	{

		private BundleWiringImpl wiring;

		public BundleRevisionImpl(BundleWiringImpl wiring) {
			this.wiring = wiring;
		}

		@Override
		public Bundle getBundle() {
			return wiring.getBundle();
		}

		@Override
		public String getSymbolicName() {
			return wiring.getBundle().getSymbolicName();
		}

		@Override
		public Version getVersion() {
			return wiring.getBundle().getVersion();
		}

		@Override
		public List<BundleCapability> getDeclaredCapabilities(String namespace) {
			return wiring.getCapabilities(namespace);
		}

		@Override
		public List<BundleRequirement> getDeclaredRequirements(String namespace) {
			return wiring.getRequirements(namespace);
		}

		@Override
		public int getTypes() {
			if(wiring.getBundle().getHeaders().get(Constants.FRAGMENT_HOST)!=null)
				return BundleRevision.TYPE_FRAGMENT;
			return 0;
		}

		@Override
		public BundleWiring getWiring() {
			return wiring;
		}
	}

	private class BundleRequirementImpl implements BundleRequirement {

		private XRequirement delegate;

		public BundleRequirementImpl(XRequirement delegate) {
			this.delegate = delegate;
		}

		@Override
		public String getNamespace() {
			return delegate.getName();
		}

		@Override
		public Map<String, String> getDirectives() {
			return delegate.getDirectives();
		}

		@Override
		public Map<String, Object> getAttributes() {
			return delegate.getAttributes();
		}

		@Override
		public BundleRevision getRevision() {
			return revision;
		}

		@Override
		public boolean matches(BundleCapability capability) {
			//TODO: implement
			return false;
		}

	}
}


