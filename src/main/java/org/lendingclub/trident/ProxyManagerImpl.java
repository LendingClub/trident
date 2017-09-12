package org.lendingclub.trident;

import java.util.Optional;

import org.lendingclub.trident.config.ConfigManager;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;

public class ProxyManagerImpl extends ProxyManager {

	@Autowired
	ConfigManager configManager;
	
	class ProxyConfigImpl implements ProxyConfig {
		JsonNode data;

		ProxyConfigImpl(JsonNode n) {
			this.data = n;
		}
		@Override
		public String getHost() {
			return data.path("host").asText();
		}

		@Override
		public int getPort() {
			return data.path("port").asInt(8080);
		}
		@Override
		public String getNonProxyHosts() {
			return data.path("nonProxyHosts").asText();
		}
	}
	@Override
	public Optional<ProxyConfig> getProxyConfig(String name) {
		Optional<JsonNode> n = configManager.getConfig("proxy", name);
		if (!n.isPresent()) {
			return Optional.empty();
		}
		
		return Optional.of(new ProxyConfigImpl(n.get()));
		
	}

}
