package org.lendingclub.trident.envoy;

import java.util.function.Consumer;

public interface EnvoyListenerDiscoveryDecorator {

	public void decorate(EnvoyListenerDiscoveryContext ctx);
}
