package org.lendingclub.trident.envoy;

import java.util.Optional;

import org.lendingclub.trident.util.JsonUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

public class EnvoyRouteDiscoveryContext extends AbstractEnvoyDiscoveryContext {

	String routeName;

	public Optional<String> getRouteConfigName() {
		return Optional.ofNullable(Strings.emptyToNull(routeName));
	}

}
