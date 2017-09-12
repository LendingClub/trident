package org.lendingclub.trident.envoy;

import java.util.function.Consumer;

public interface EnvoyRouteDiscoveryDecorator {

	public void decorate(EnvoyRouteDiscoveryContext ctx);
}
