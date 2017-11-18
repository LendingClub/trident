package org.lendingclub.trident.haproxy;

/**
 * Created by hasingh on 9/21/17.
 */
public interface HAProxyConfigDiscoveryInterceptor {

	public void interceptor(HAProxyConfigBundleDiscoveryContext ctx);
}
