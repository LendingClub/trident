package org.lendingclub.trident.haproxy;

import net.lingala.zip4j.core.ZipFile;

public class HAProxyCertBundleDiscoveryContext extends HAProxyBootstrapConfigContext {
	String environment;
	String subEnvironment;
	String serviceNode;
	String serviceCluster;
	String serviceZone;

	protected ZipFile bundle;

	public HAProxyCertBundleDiscoveryContext withConfigZipBundle(ZipFile zipFile) {
		this.bundle = zipFile;
		return this;
	}

	public HAProxyCertBundleDiscoveryContext() {
		super();
	}

	public HAProxyCertBundleDiscoveryContext( String d) {
		super(d);
	}
}
