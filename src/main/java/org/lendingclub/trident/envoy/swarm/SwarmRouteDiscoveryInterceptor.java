package org.lendingclub.trident.envoy.swarm;

import java.util.List;
import java.util.Set;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.envoy.EnvoyDiscoveryContext;
import org.lendingclub.trident.envoy.EnvoyRouteDiscoveryContext;
import org.lendingclub.trident.envoy.EnvoyRouteDiscoveryInterceptor;
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
public class SwarmRouteDiscoveryInterceptor implements EnvoyRouteDiscoveryInterceptor {

	Logger logger = LoggerFactory.getLogger(SwarmRouteDiscoveryInterceptor.class);
	@Autowired
	NeoRxClient neo4j;
	
	@Override
	public void accept(EnvoyRouteDiscoveryContext ctx) {
	
		ObjectNode routeConfig = ctx.getConfig();
		ArrayNode vhosts = JsonUtil.createArrayNode();

		
		routeConfig.put("validate_clusters", false);
		routeConfig.set("virtual_hosts", vhosts);

		
		if (ctx.getRouteConfigName().orElse("").equals(EnvoyDiscoveryContext.DEFAULT_ROUTE_CONFIG_NAME)) {
			return;
		}
		Set<String> appIdSet = Sets.newHashSet();
		if (!ctx.getEnvironment().isPresent()) {
			throw new TridentException("environment not passed in request");
		}
		ctx.newSearch().search().forEach(it ->{
	
			if (it.getAppId().isPresent()) {
				List<String> paths = it.getPaths();
				if (!paths.isEmpty()) {
						
							String fullyQualifiedCluster = SwarmClusterDiscoveryInterceptor.shortenClusterName(String.format("%s--%s--%s--%s", ctx.getServiceZone().orElse("unknown"), ctx.getEnvironment().orElse("unknown"),
									ctx.getSubEnvironment().orElse("default"), it.getAppId().get()));
							ObjectNode vhost = JsonUtil.createObjectNode();
							vhosts.add(vhost);
							vhost.put("name", "local_service");
							vhost.set("domains", JsonUtil.createArrayNode().add("*"));
							ArrayNode routes = JsonUtil.createArrayNode();
							vhost.set("routes", routes);
							paths.forEach(path->{
								ObjectNode route = JsonUtil.createObjectNode()
										.put("timeout_ms", 0).put("prefix", path).put("cluster", fullyQualifiedCluster);
							});
						
						}

					
					}
				});

	}

}
