package org.lendingclub.trident.envoy;

import java.util.Optional;

import org.lendingclub.trident.util.JsonUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class EnvoyListenerDiscoveryContext extends EnvoyDiscoveryContext {

	

	

	public ArrayNode getListeners() {
		JsonNode n = getConfig().path("listeners");
		if (n.isArray()) {
			return ArrayNode.class.cast(n);
		} else {
			ArrayNode x = JsonUtil.createArrayNode();

			getConfig().set("listeners", x);
			return x;
		}
	}
	
}
