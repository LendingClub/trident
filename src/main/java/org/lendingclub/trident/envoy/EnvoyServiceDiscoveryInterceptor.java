package org.lendingclub.trident.envoy;

import java.util.function.Consumer;

public interface EnvoyServiceDiscoveryInterceptor {

	public void accept(EnvoyServiceDiscoveryContext ctx);
}
