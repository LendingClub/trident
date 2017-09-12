package org.lendingclub.trident.envoy;

import java.util.function.Consumer;

public interface EnvoyServiceDiscoveryDecorator {

	public void decorate(EnvoyServiceDiscoveryContext ctx);
}
