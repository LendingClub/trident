package org.lendingclub.trident.envoy.swarm;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.envoy.EnvoyDiscoveryContext;
import org.lendingclub.trident.envoy.EnvoyListenerDiscoveryContext;
import org.lendingclub.trident.envoy.EnvoyListenerDiscoveryInterceptor;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;

@Component
public class SwarmListenerDiscoveryInterceptor implements EnvoyListenerDiscoveryInterceptor {

	@Autowired
	NeoRxClient neo4j;

	Logger logger = LoggerFactory.getLogger(SwarmListenerDiscoveryInterceptor.class);

	@Override
	public void accept(EnvoyListenerDiscoveryContext ctx) {

		String environment = ctx.getEnvironment().orElse("unknown");
		String subEnvironment = ctx.getSubEnvironment().orElse("default");
		String zone = ctx.getServiceZone().orElse("unknown");

		logger.info("finding listeners for zone={} env={} subenv={} ", zone, environment, subEnvironment);

		ObjectNode listener = JsonUtil.createObjectNode();
		listener.put("address", String.format("tcp://0.0.0.0:%d",EnvoyDiscoveryContext.DEFAULT_HTTP_LISTENER_PORT));
		listener.put("name", EnvoyDiscoveryContext.DEFAULT_PLAIN_TEXT_LISTENER_NAME);
		ArrayNode filters = JsonUtil.createArrayNode();
		listener.set("filters", filters);
		ObjectNode filter = JsonUtil.createObjectNode();
		filters.add(filter);
		filter.put("type", "read").put("name", "http_connection_manager");

		ObjectNode config = JsonUtil.createObjectNode();
		filter.set("config", config);
		config.put("codec_type", "auto");
		config.put("stat_prefix", "mystats");
		config.set("rds", JsonUtil.createObjectNode().put("cluster", EnvoyDiscoveryContext.TRIDENT_SDS_NAME)
				.put("route_config_name", EnvoyDiscoveryContext.DEFAULT_ROUTE_CONFIG_NAME).put("refresh_delay_ms", EnvoyDiscoveryContext.DEFAULT_REFRESH_INTERVAL_MILLIS));
		config.set("filters", JsonUtil.createArrayNode().add(JsonUtil.createObjectNode().put("type", "decoder")
				.put("name", "router").set("config", JsonUtil.createObjectNode())));

	
	

		ctx.getListeners().add(listener);

	}

}
