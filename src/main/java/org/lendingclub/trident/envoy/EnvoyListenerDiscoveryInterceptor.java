package org.lendingclub.trident.envoy;

import java.util.function.Consumer;

public interface EnvoyListenerDiscoveryInterceptor {

	public void accept(EnvoyListenerDiscoveryContext ctx);
}
