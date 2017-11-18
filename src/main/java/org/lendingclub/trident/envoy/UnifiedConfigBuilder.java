package org.lendingclub.trident.envoy;

import java.io.IOException;

import org.lendingclub.trident.Trident;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.util.JsonUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

import io.macgyver.okrest3.OkRestClient;

/**
 * UnifiedConfigBuilder renders an envoy config file as if it was monolithic.
 * This makes it a little easier to see the effective config.
 * 
 * @author rschoening
 *
 */
class UnifiedConfigBuilder {

	static OkRestClient client = new OkRestClient.Builder().disableCertificateVerification().build();

	ObjectNode config;

	String serviceCluster = null;
	String serviceNode = null;

	public UnifiedConfigBuilder() {

	}

	public UnifiedConfigBuilder withEnvoyConfig(ObjectNode baseConfig) {
		this.config = baseConfig;
		return this;
	}

	public UnifiedConfigBuilder withServiceCluster(String serviceCluster) {
		this.serviceCluster = serviceCluster;
		return this;
	}

	public UnifiedConfigBuilder withServiceNode(String serviceNode) {
		this.serviceNode = serviceNode;
		return this;
	}

	public ObjectNode getConfig() {
		return config;
	}

	public void resolveClusters() {
		try {
			JsonNode n = JsonUtil.getObjectMapper()
					.readTree(Trident.getApplicationContext().getBean(EnvoyDiscoveryController.class)
							.clusterDiscovery(null, serviceCluster, serviceNode).getBody());

			JsonNode arr = n.path("clusters");
			ArrayNode configClusters = null;
			JsonNode cc = getConfig().path("clusters");
			if (cc.isArray()) {
				configClusters = ArrayNode.class.cast(cc);
			} else {
				configClusters = JsonUtil.createArrayNode();
				ObjectNode xx = getConfig();
				xx = (ObjectNode) xx.get("cluster_manager");
				xx.set("clusters", configClusters);
			}
			if (arr.isArray()) {
				for (int i = 0; i < arr.size(); i++) {
					configClusters.add(arr.get(i));
				}

			}

			// Clear out the CDS reference
			if (getConfig().path("cluster_manager").isObject()) {
				ObjectNode.class.cast(getConfig().path("cluster_manager")).remove("cds");
			}
		} catch (IOException e) {
			throw new TridentException(e);
		}
	}

	public void resolveAll() {
		resolveListeners();
		resolveRoutes();
		resolveClusters();
		resolveServices();
	}

	void resolveRoutes() {

		getConfig().path("listeners").forEach(listener -> {

			listener.path("filters").forEach(f -> {
				try {
					if (f.path("name").asText().equals("http_connection_manager")) {

						String routeConfigName = f.path("config").path("rds").path("route_config_name").asText();
						if (!Strings.isNullOrEmpty(routeConfigName)) {
							JsonNode n = JsonUtil.getObjectMapper().readTree(Trident.getApplicationContext()
									.getBean(EnvoyDiscoveryController.class)
									.routeDiscovery(null, routeConfigName, serviceCluster, serviceNode).getBody());

							if (n.isObject()) {
								ObjectNode.class.cast(f.path("config")).set("route_config", n);
								ObjectNode.class.cast(f.path("config")).remove("rds");
							}
						}
					}
				} catch (IOException e) {
					throw new TridentException(e);
				}
			});
		});
	}

	public void resolveServices() {
		getConfig().path("cluster_manager").path("clusters").forEach(it -> {
			if (it.path("type").asText().equals("sds")) {
				try {
					JsonNode n = JsonUtil.getObjectMapper()
							.readTree(Trident.getApplicationContext().getBean(EnvoyDiscoveryController.class)
									.serviceDiscovery(null, it.path("service_name").asText()).getBody());

					ArrayNode hosts = JsonUtil.createArrayNode();
					n.path("hosts").forEach(host -> {
						String url = "tcp://" + host.path("ip_address").asText() + ":" + host.path("port").asInt();
						ObjectNode hostNode = JsonUtil.createObjectNode();
						hostNode.put("url", url);
						hosts.add(hostNode);
					});
					ObjectNode.class.cast(it).set("hosts", hosts);

					ObjectNode.class.cast(it).put("type", "strict_dns");
				} catch (IOException e) {
					throw new TridentException(e);
				}
			}
		});

		// Clear out the CDS reference
		if (getConfig().path("cluster_manager").isObject()) {
			ObjectNode.class.cast(getConfig().path("cluster_manager")).remove("sds");
		}
	}

	public void resolveListeners() {
		try {
			String clusterName = config.path("lds").path("cluster").asText();
			if (Strings.isNullOrEmpty(clusterName)) {
				return;
			}

			JsonNode n = JsonUtil.getObjectMapper()
					.readTree(Trident.getApplicationContext().getBean(EnvoyDiscoveryController.class)
							.listenerDiscovery(null, serviceCluster, serviceNode).getBody());
			JsonNode listeners = n.path("listeners");
			if (listeners.isArray()) {
				listeners.forEach(it -> {
					JsonNode anx = config.path("listeners");
					ArrayNode an = null;
					if (anx.isArray()) {
						an = (ArrayNode) anx;
					} else {
						an = JsonUtil.createArrayNode();
						config.set("listeners", an);
					}

					an.add(it);
				});
			}
			config.remove("lds");
		} catch (IOException e) {
			throw new TridentException(e);
		}

	}

	public String getDiscoveryUrlBase(String name) {
		JsonNode n = getClusterManagerByName(name);
		String url = n.path("hosts").path(0).path("url").asText();
		url = url.replace("tcp://", "http://");
		while (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		return url;

	}

	JsonNode getClusterManagerByName(String name) {
		JsonNode cluster = config.path("cluster_manager").path("sds").path("cluster");

		if (cluster.path("name").asText().equals(name)) {
			return ObjectNode.class.cast(cluster);
		}
		cluster = config.path("cluster_manager").path("cds").path("cluster");
		if (cluster.path("name").asText().equals(name)) {
			return ObjectNode.class.cast(cluster);
		}
		return MissingNode.getInstance();
	}
}
