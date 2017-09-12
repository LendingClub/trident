package org.lendingclub.trident.envoy;

import java.util.Optional;

import org.lendingclub.trident.util.JsonUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.MoreObjects;

public class EnvoyServiceDiscoveryContext extends AbstractEnvoyDiscoveryContext {

	public class Host {
		ObjectNode data;
		public Host withIp(String ip) {
			data.put("ip_address", ip);
			return this;
		}
		public Host withPort(int port) {
			data.put("port", port);
			return this;
		}
		public Host withAvailabilityZone(String zone) {
			return withTag("az",zone);
	
		}
		public Host withCanary(boolean canary) {
			return withTag("canary",canary);
			
		}
		public Host withWeight(int weight) {
			return withTag("load_balancing_weight",weight);
			
		}
		protected Host withTag(String name, Object val) {
			JsonNode tags = data.path("tags");
			ObjectNode n = null;
			if (tags.isObject()) {
				n = (ObjectNode) tags;
			}
			else {
				n = JsonUtil.createObjectNode();
				data.set("tags", n);
			}
			if (val == null) {
				n.remove(name);
			}
			else if (val instanceof String) {
				n.put(name, (String) val);
			}
			else if (val instanceof Integer) {
				n.put (name, (Integer) val);
			}
			else if (val instanceof Boolean) {
				n.put (name, (Boolean) val);
			}
			else {
				throw new IllegalArgumentException("unsupported type: "+val);
			}
			return this;
		}
	}
	
	String serviceName;	
	public Optional<String> getServiceName() {
		return Optional.ofNullable(serviceName);
	}
	public EnvoyServiceDiscoveryContext withServiceName(String n) {
		this.serviceName = n;
		return this;
	}
	public Host addHost(String host, int port) {
		Host h = new Host();
		 h.data = JsonUtil.createObjectNode();
		h.withIp(host).withPort(port);
		JsonNode arr = getConfig().get("hosts");
		ArrayNode hosts = null;
		if (arr!=null && arr.isArray()) {
			hosts = (ArrayNode) arr;
		}
		else {
			hosts = JsonUtil.createArrayNode();
			config.set("hosts", hosts);
		}
	
		hosts.add(h.data);
		return h;
	}
	
	public String toString() {
		return MoreObjects.toStringHelper(this).add("env", getEnvironment().orElse(null))
				.add("subenv", getSubEnvironment().orElse(null))
				.add("name", getServiceName().orElse(null))
				.add("zone", getServiceZone().orElse(null))
				.toString();
	}
}
