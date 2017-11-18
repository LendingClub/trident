package org.lendingclub.trident.envoy;

import org.lendingclub.trident.extension.InterceptorGroup;

public interface EnvoyInterceptors {

	public InterceptorGroup<EnvoyServiceDiscoveryInterceptor> getServiceDiscoveryInterceptors();

	public InterceptorGroup<EnvoyClusterDiscoveryInterceptor> getClusterDiscoveryInterceptors();
	public InterceptorGroup<EnvoyRouteDiscoveryInterceptor> getRouteDiscoveryInterceptors() ;
	public InterceptorGroup<EnvoyListenerDiscoveryInterceptor> getListenerDiscoveryInterceptors() ;
	public InterceptorGroup<EnvoyBootstrapConfigInterceptor> getBootstrapInterceptors();
}
