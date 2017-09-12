package org.lendingclub.trident.envoy.swarm;

import java.util.Set;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.envoy.EnvoyListenerDiscoveryContext;
import org.lendingclub.trident.envoy.EnvoyListenerDiscoveryDecorator;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;


public class SwarmListenerDiscoveryDecorator implements EnvoyListenerDiscoveryDecorator {

	@Autowired
	NeoRxClient neo4j;

	Logger logger = LoggerFactory.getLogger(SwarmListenerDiscoveryDecorator.class);

	@Override
	public void decorate(EnvoyListenerDiscoveryContext ctx) {

		String environment = ctx.getEnvironment().orElse("unknown");
		String subEnvironment = ctx.getSubEnvironment().orElse("default");
		String zone = ctx.getServiceZone().orElse("unknown");

		logger.info("finding listeners for zone={} env={} subenv={} ", zone, environment, subEnvironment);

		ObjectNode listener = JsonUtil.createObjectNode();
		listener.put("address", "tcp://0.0.0.0:10000");
		listener.put("name", "dynamic");
		ArrayNode filters = JsonUtil.createArrayNode();
		listener.set("filters", filters);
		ObjectNode filter = JsonUtil.createObjectNode();
		filters.add(filter);
		filter.put("type", "read").put("name", "http_connection_manager");

		ObjectNode config = JsonUtil.createObjectNode();
		filter.set("config", config);
		config.put("codec_type", "auto");
		config.put("stat_prefix", "foo");
		config.set("rds", JsonUtil.createObjectNode().put("cluster", "service_discovery")
				.put("route_config_name", "foo").put("refresh_delay_ms", 10000));
		config.set("filters", JsonUtil.createArrayNode().add(JsonUtil.createObjectNode().put("type", "decoder")
				.put("name", "router").set("config", JsonUtil.createObjectNode())));

	
	

		ctx.getListeners().add(listener);

	}

}
