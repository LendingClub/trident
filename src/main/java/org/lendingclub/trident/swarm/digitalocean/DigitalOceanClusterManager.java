package org.lendingclub.trident.swarm.digitalocean;

import java.util.Optional;

import org.apache.http.HttpHost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.log4j.spi.LoggerFactory;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.config.ConfigManager;
import org.lendingclub.trident.util.ProxyManager;
import org.lendingclub.trident.util.ProxyManager.ProxyConfig;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.PredefinedClientConfigurations;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.myjeeva.digitalocean.DigitalOcean;
import com.myjeeva.digitalocean.exception.DigitalOceanException;
import com.myjeeva.digitalocean.exception.RequestUnsuccessfulException;
import com.myjeeva.digitalocean.impl.DigitalOceanClient;

public class DigitalOceanClusterManager {

	Cache<String, DigitalOcean> clientCache = CacheBuilder.newBuilder().build();

	Logger logger = org.slf4j.LoggerFactory.getLogger(DigitalOceanClusterManager.class);
	
	@Autowired
	ConfigManager configManager;

	@Autowired
	ProxyManager proxyManager;
	
	public DigitalOcean getClient(String name) {
		try {
			DigitalOcean digitalOcean = clientCache.getIfPresent(name);
			if (digitalOcean != null) {
				return digitalOcean;
			}

			Optional<JsonNode> config = configManager.getConfig("digitalocean", name);
			if (!config.isPresent()) {
				throw new TridentException("DigitalOcean config not found: " + name);
			}

			String token = config.get().path("token").asText();
			HttpClientBuilder builder = HttpClientBuilder.create();
			Optional<ProxyConfig> proxy = proxyManager.getDefaultProxyConfig();
			if (proxy.isPresent()) {
				logger.info("using proxy for DigitalOcean");
				HttpHost proxyHost = new HttpHost(proxy.get().getHost(), proxy.get().getPort());
				builder.setProxy(proxyHost);
			}
			
			CloseableHttpClient httpClient = builder.build();
			DigitalOceanClient client = new DigitalOceanClient("v2", token,httpClient);
			client.getAccountInfo();

			clientCache.put(name, client);
			return client;
			
		} catch (DigitalOceanException | RequestUnsuccessfulException e) {
			throw new TridentException(e);
		}
	}
}
