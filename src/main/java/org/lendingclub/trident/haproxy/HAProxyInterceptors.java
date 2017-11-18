package org.lendingclub.trident.haproxy;

import org.lendingclub.trident.extension.InterceptorGroup;

public interface HAProxyInterceptors {

	public InterceptorGroup<HAProxyBootstrapConfigInterceptor> getHAProxyBootstrapConfigInterceptors() ;

	public InterceptorGroup<HAProxyHostDiscoveryInterceptor> getHAProxyHostDiscoveryInterceptors() ;

	public InterceptorGroup<HAProxyConfigDiscoveryInterceptor> getHAProxyConfigBundleDiscoveryInterceptors();

	public InterceptorGroup<HAProxyCertDiscoveryInterceptor> getHAProxyCertBundleDiscoveryInterceptors() ;
}
