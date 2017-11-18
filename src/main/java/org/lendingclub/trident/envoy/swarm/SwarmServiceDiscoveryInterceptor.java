package org.lendingclub.trident.envoy.swarm;

import java.util.List;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.envoy.EnvoyServiceDiscoveryContext;
import org.lendingclub.trident.envoy.EnvoyServiceDiscoveryInterceptor;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.swarm.SwarmDiscoverySearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.MissingNode;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

@Component
public class SwarmServiceDiscoveryInterceptor implements EnvoyServiceDiscoveryInterceptor {

	Logger logger = LoggerFactory.getLogger(SwarmServiceDiscoveryInterceptor.class);

	@Autowired
	NeoRxClient neo4j;

	@Autowired
	SwarmClusterManager swarmClusterManager;

	@Override
	public void accept(EnvoyServiceDiscoveryContext ctx) {

		List<SwarmDiscoverySearch.Service> results = ctx.newSearch().search();
		// We should have a small number of services...probably 0-2 at this
		// point. If there are more, it is probably a defect of some kind.

		logger.info("found {} candidates...",results.size());
		results.forEach(service -> {

			logger.info("potentially eligible service: {}",service);
			if (!service.getAppId().isPresent()) {
				logger.info("service is not eligible becasue appId is not set");
			}
			else if (!service.getPort().isPresent()) {
				logger.info("service is not eligible becasue port is not set");
			}
			else {
				String cypher = "match (a:DockerService {serviceId:{serviceId}})--(t:DockerTask)--(h:DockerHost) return h.addr as addr, "+portLabelsReturnClause(service.getPort().get());

				neo4j.execCypher(cypher, "serviceId", service.getServiceId()).forEach(it -> {
					logger.info("host: " + it);
					String ip = it.path("addr").asText(null);
					int p = it.path("port").asInt(0);
					if (p > 0 && !Strings.isNullOrEmpty(ip)) {
						logger.info("adding host {}:{} for appId={},env={},subEnv={}", ip, p, service.getAppId().get(),
								ctx.getEnvironment().get(), ctx.getSubEnvironment().get());
						ctx.addHost(ip, p);
					}
				});
			}

		});

	}

	String portLabelsReturnClause(int port) {
		return "t.hostTcpPortMap_" + port + " as port";
	}

}
