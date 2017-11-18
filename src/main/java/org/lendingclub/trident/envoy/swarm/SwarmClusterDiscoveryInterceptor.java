package org.lendingclub.trident.envoy.swarm;

import java.util.List;
import java.util.Set;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.envoy.EnvoyClusterDiscoveryContext;
import org.lendingclub.trident.envoy.EnvoyClusterDiscoveryInterceptor;
import org.lendingclub.trident.envoy.EnvoyClusterDiscoveryContext.Cluster;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.swarm.SwarmDiscoverySearch;
import org.lendingclub.trident.swarm.SwarmDiscoverySearch.Service;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;

@Component
public class SwarmClusterDiscoveryInterceptor implements EnvoyClusterDiscoveryInterceptor {

	Logger logger = LoggerFactory.getLogger(SwarmClusterDiscoveryInterceptor.class);

	@Autowired
	NeoRxClient neo4j;

	@Autowired
	SwarmClusterManager swarmClusterManager;

	/**
	 * Envoy has a 60-char limitation on cluster names.  The cluster names don't actually convey information.  They just provide the linkage between 
	 * routes and clusters.  If we are under 60 chars, we use the natural key.  If we pass 60 chars, we truncate and append the SHA1.
	 * @param input
	 * @return
	 */
	public static String shortenClusterName(String input) {
	
		if (input.length()>60) {
			
			String sha1 = BaseEncoding.base32Hex().encode(Hashing.sha1().hashBytes(input.getBytes()).asBytes()).substring(0,12);
			
			String abbreviated =  input+"-"+sha1;
			 abbreviated = abbreviated.substring(abbreviated.length()-60);
			return abbreviated;
		}
		return input;
	
	}
	@Override
	public void accept(EnvoyClusterDiscoveryContext ctx) {
		String environment = ctx.getEnvironment().orElse(null);
		String subEnvironment = ctx.getSubEnvironment().orElse(null);
		String zone = ctx.getServiceZone().orElse(null);
		String serviceGroup = ctx.getServiceCluster().orElse(null);

		logger.info("finding clusters for zone={} serviceGroup={} env={} subenv={} ", zone, serviceGroup, environment, subEnvironment);
		List<Service> list = ctx.newSearch().search();
		logger.info("count: {}",list);
		// TODO: We want to be able to aggregate services of like id.  For example, blue/green or canaries.
		list.forEach(it -> {
			JsonUtil.logInfo(getClass(), "candidate service", it.getData());
			if (it.getAppId().isPresent()) {
				Cluster cluster = ctx
						.addCluster(zone, environment, subEnvironment, serviceGroup,it.getAppId().get())
						.withAttribute("connect_timeout_ms", 250).withAttribute("type", "sds")
						.withAttribute("lb_type", "round_robin");
				logger.info("Added cluster: {}", cluster);
			}
		});

	}

}
