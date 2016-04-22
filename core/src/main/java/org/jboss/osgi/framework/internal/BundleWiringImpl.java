package org.jboss.osgi.framework.internal;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleLoadException;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

public class BundleWiringImpl implements BundleWiring {

	private Bundle state;
	static final Logger log = Logger.getLogger(BundleWiringImpl.class);

	public BundleWiringImpl(Bundle abstractBundleState) {
		this.state = abstractBundleState;
	}

	@Override
	public Bundle getBundle() {
		return state;
	}

	@Override
	public boolean isCurrent() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isInUse() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public List<BundleCapability> getCapabilities(String namespace) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<BundleRequirement> getRequirements(String namespace) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<BundleWire> getProvidedWires(String namespace) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<BundleWire> getRequiredWires(String namespace) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BundleRevision getRevision() {
		// TODO Auto-generated method stub
		return null;
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
		throw new UnsupportedOperationException();
	}

}

