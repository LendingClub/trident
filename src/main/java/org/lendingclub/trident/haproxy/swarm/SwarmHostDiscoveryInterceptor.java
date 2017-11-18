package org.lendingclub.trident.haproxy.swarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.haproxy.HAProxyDiscoveryContext;
import org.lendingclub.trident.haproxy.HAProxyHostDiscoveryContext;
import org.lendingclub.trident.haproxy.HAProxyHostDiscoveryInterceptor;
import org.lendingclub.trident.haproxy.HAProxyHostInfoValidator;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.swarm.SwarmDiscoverySearch.Service;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Created by hasingh on 9/21/17.
 */
public class SwarmHostDiscoveryInterceptor implements HAProxyHostDiscoveryInterceptor {

	Logger logger = LoggerFactory.getLogger(SwarmHostDiscoveryInterceptor.class);

	@Autowired
	NeoRxClient neo4j;

	@Autowired
	SwarmClusterManager swarmClusterManager;

	@Override
	public void accept(HAProxyHostDiscoveryContext ctx) {
		String environment = ctx.getEnvironment().orElse(null);
		String subEnvironment = ctx.getSubEnvironment().orElse("default");
		String zone = ctx.getServiceZone().orElse(null);
		String serviceGroup = ctx.getServiceCluster().orElse(null);
		String appId = ctx.getAppId().orElse(null);
		String region = ctx.getRegion().orElse(null);

		logger.info("finding clusters for zone={} serviceGroup={} env={} subenv={} region={}", zone, serviceGroup, environment,
				subEnvironment, region);

		try {
		
		
			List<Service> services = swarmClusterManager.newServiceDiscoverySearch().withAppId(appId).withRegion(zone).withServiceGroup(serviceGroup)
					.withEnvironment(environment).withSubEnvironment(subEnvironment).withRegion(region).search();
			logger.info("found {} services",services.size());
			for (Service service: services) {
				logger.info("service: {}",service);
				// Now that we found the service, we have to find the hosts that are associaated
				addService( ctx, service);
				
			}
		} catch (RuntimeException e) {
			logger.warn("problem discovering services",e);
		}

	}
	
	void addService(HAProxyDiscoveryContext ctx, Service service) {
		logger.info("potentially eligible service: {}",service);
		if (!service.getAppId().isPresent()) {
			logger.info("service is not eligible becasue appId is not set");
		}
		else if (!service.getPort().isPresent()) {
			logger.info("service is not eligible becasue port is not set");
		}
		else {
			String cypher = "match (a:DockerService {serviceId:{serviceId}})--(t:DockerTask)--(h:DockerHost) where t.state='running' return h.addr as addr, "+portLabelsReturnClause(service.getPort().get());

			ArrayNode hosts = JsonUtil.createArrayNode();

			neo4j.execCypher(cypher, "serviceId", service.getServiceId()).forEach(it -> {
				logger.info("host: " + it);
				String ip = it.path("addr").asText(null);
				int p = it.path("port").asInt(0);
				if (p > 0 && !Strings.isNullOrEmpty(ip)) {
					logger.info("adding host {}:{} for appId={},env={},subEnv={}", ip, p, service.getAppId().get(),
							ctx.getEnvironment().get(), ctx.getSubEnvironment().get());


					ObjectNode host = JsonUtil.createObjectNode();
					host.put("host", ip);
					host.put("port",p);
					// priority should be determined by the value of label_tsdBlueGreenState
					host.put("priority", 256);
					logger.info("service info: {}", JsonUtil.prettyFormat(service));
					if(service.getData().path("s").path("label_tsdBlueGreenState").asText("").equals("dark")
							|| service.getData().path("s").path("label_tsdBlueGreenState").asText("")
							.equals("drain") ) {
						host.put("priority", 0);
					}
					hosts.add(host);
				}
			});

			ctx.getConfig().put("stickySessions", false);

			ArrayNode hostsArray = JsonUtil.createArrayNode();


			if(ctx.getConfig().get("hosts") == null) {
				ctx.getConfig().set("hosts", hostsArray);
			}
			else {
				hostsArray = (ArrayNode) ctx.getConfig().get("hosts");
			}

			for(JsonNode host: hosts) {
				hostsArray.add(host);
			}

			hostsArray = HAProxyHostInfoValidator.filterOutInvalidHostInfo(hostsArray);

			ctx.getConfig().set("hosts", hostsArray);
		}
	}
	
	String portLabelsReturnClause(int port) {
		return "t.hostTcpPortMap_" + port + " as port";
	}
}
