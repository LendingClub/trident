package org.lendingclub.trident.envoy;

import java.util.function.Consumer;

public interface EnvoyClusterDiscoveryInterceptor {

	public void accept(EnvoyClusterDiscoveryContext ctx);
}
