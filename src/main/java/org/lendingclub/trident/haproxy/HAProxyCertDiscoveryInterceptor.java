package org.lendingclub.trident.haproxy;

public interface HAProxyCertDiscoveryInterceptor {

	public void accept(HAProxyCertBundleDiscoveryContext ctx);
}