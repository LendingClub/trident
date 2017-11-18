package org.lendingclub.trident.envoy;

import java.util.function.Consumer;

public interface EnvoyRouteDiscoveryInterceptor {

	public void accept(EnvoyRouteDiscoveryContext ctx);
}
