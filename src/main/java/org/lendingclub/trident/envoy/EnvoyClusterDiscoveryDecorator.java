package org.lendingclub.trident.envoy;

import java.util.function.Consumer;

public interface EnvoyClusterDiscoveryDecorator {

	public void decorate(EnvoyClusterDiscoveryContext ctx);
}
