package org.lendingclub.trident.haproxy;

/**
 * Created by hasingh on 9/21/17.
 */
public interface HAProxyBootstrapConfigInterceptor {

	public void accept(HAProxyBootstrapConfigDiscoveryContext ctx);

}
