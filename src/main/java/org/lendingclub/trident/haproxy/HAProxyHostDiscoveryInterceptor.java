package org.lendingclub.trident.haproxy;

/**
 * Created by hasingh on 9/21/17.
 */
public interface HAProxyHostDiscoveryInterceptor {

	public void accept(HAProxyHostDiscoveryContext ctx);
}
