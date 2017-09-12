package org.lendingclub.trident.envoy;

import java.util.Optional;

import org.lendingclub.trident.envoy.EnvoyServiceDiscoveryContext.Host;
import org.lendingclub.trident.envoy.swarm.SwarmClusterDiscoveryDecorator;
import org.lendingclub.trident.util.JsonUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class EnvoyClusterDiscoveryContext extends AbstractEnvoyDiscoveryContext {

	public class Cluster {
		ObjectNode data = JsonUtil.createObjectNode();
		
		public Cluster withAttribute(String name, String val) {
			data.put(name, val);
			return this;
		}
		public Cluster withAttribute(String name, int val) {
			data.put(name, val);
			return this;
		}
		public Cluster withAttribute(String name, JsonNode val) {
			data.set(name, val);
			return this;
		}
		public Cluster withName(String name) {
			data.put("name", name);
			return this;
		}
		public Cluster withServiceName(String serviceName) {
			data.put("service_name", serviceName);
			return this;
		}
		public Cluster withServiceName(String region, String env, String subEnv, String serviceGroup, String name) {
			return withName(String.format("%s--%s--%s--%s--%s", region,env,subEnv,serviceGroup, name));
		}
		public Cluster withName(String region, String env, String subEnv, String serviceGroup, String name) {
			return withName(String.format("%s--%s--%s--%s--%s", region,env,subEnv,serviceGroup, name));
		}
	

	}
	public Cluster addCluster(String region, String env, String subEnv, String serviceGroup, String name) {
		return addCluster(String.format("%s--%s--%s--%s--%s", region,env,subEnv,serviceGroup, name));
	}
	public Cluster addCluster(String name) {
		
		// envoy has a 60-char limitation on the cluster name
		String shortenedName = SwarmClusterDiscoveryDecorator.shortenClusterName(name);
		
		Cluster h = new Cluster().withName(shortenedName);
		
		JsonNode arr = getConfig().get("clusters");
		ArrayNode clusters = null;
		if (arr!=null && arr.isArray()) {
			clusters = (ArrayNode) arr;
		}
		else {
			clusters = JsonUtil.createArrayNode();
			config.set("clusters", clusters);
		}
	
		clusters.add(h.data);
		
     
        // Some sensible defaults!!!  
		// Since we are in CDS, it only makes sense to default the service to use SDS.

        h.withAttribute("connect_timeout_ms",250).withAttribute("type", "sds").withAttribute("lb_type", "round_robin").withAttribute("service_name", name);
		return h;
	}
	public EnvoyClusterDiscoveryContext() {
		super();
		getConfig().set("clusters", JsonUtil.createArrayNode());
	}

	public EnvoyClusterDiscoveryContext(ObjectNode d) {
		super(d);
	}

}
