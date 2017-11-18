package org.lendingclub.trident.envoy;

import java.util.Optional;

import org.lendingclub.trident.util.JsonUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

public class EnvoyRouteDiscoveryContext extends EnvoyDiscoveryContext {

	String routeName;

	public EnvoyRouteDiscoveryContext() {
		super();
		
		getConfig().put("validate_clusters", false);
		getConfig().set("virtual_hosts", JsonUtil.createArrayNode());
	}

	public ArrayNode getVirtualHosts() {
		return (ArrayNode) getConfig().get("virtual_hosts");
	}
	public Optional<String> getRouteConfigName() {
		return Optional.ofNullable(Strings.emptyToNull(routeName));
	}

}
