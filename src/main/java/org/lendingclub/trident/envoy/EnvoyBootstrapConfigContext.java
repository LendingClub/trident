package org.lendingclub.trident.envoy;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.lendingclub.trident.util.JsonUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

public class EnvoyBootstrapConfigContext extends AbstractEnvoyDiscoveryContext {

	String environment;
	String subEnvironment;
	String serviceNode;
	String serviceCluster;
	String serviceZone;

	public EnvoyBootstrapConfigContext() {
		super();

	}

	EnvoyBootstrapConfigContext withSkeleton(HttpServletRequest request) {
		withServletRequest(request);
		ObjectNode n = getConfig();

		ArrayNode listeners = JsonUtil.createArrayNode();
		n.set("listeners", listeners);

		{
			ObjectNode lds = JsonUtil.createObjectNode();
			n.set("lds", lds);

			lds.put("cluster", "service_discovery");
			lds.put("refresh_delay_ms", TimeUnit.SECONDS.toMillis(30));
		}
		/*
		 * Within filter.config section: "rds":{ "cluster":"discovery",
		 * "route_config_name":"foo", "refresh_delay_ms":5000 },
		 */

		ObjectNode admin = JsonUtil.createObjectNode();
		admin.put("access_log_path", "/tmp/admin_access.log");
		admin.put("address", "tcp://127.0.0.1:9901");
		n.set("admin", admin);

		ObjectNode clusterManager = JsonUtil.createObjectNode();

		{
			ObjectNode sds = JsonUtil.createObjectNode();
			clusterManager.set("sds", sds);
			n.set("cluster_manager", clusterManager);
			ObjectNode cluster = JsonUtil.createObjectNode();
			sds.set("cluster", cluster);

			cluster.put("name", "service_discovery");
			cluster.put("connect_timeout_ms", 250);
			cluster.put("type", "strict_dns");
			cluster.put("lb_type", "round_robin");

			ArrayNode clusterHosts = JsonUtil.createArrayNode();
			cluster.set("hosts", clusterHosts);

			ObjectNode sdsHost = JsonUtil.createObjectNode();
			sdsHost.put("url", EnvoyManager.toEnvoyFormatUrl(getBaseUrl().get()));
			clusterHosts.add(sdsHost);

			if (getBaseUrl().get().startsWith("https://")) {
				ObjectNode sslContext = JsonUtil.createObjectNode();
				sslContext.put("ca_cert_file", "/etc/ssl/certs/ca-certificates.crt");
				cluster.set("ssl_context", sslContext);
			}

			sds.put("refresh_delay_ms", TimeUnit.SECONDS.toMillis(30));
		}

		{
			ObjectNode cds = JsonUtil.createObjectNode();
			clusterManager.set("cds", cds);
			ObjectNode cluster = JsonUtil.createObjectNode();
			cds.set("cluster", cluster);
			cluster.put("name", "cluster_discovery");
			cluster.put("connect_timeout_ms", 250);
			cluster.put("type", "strict_dns");
			cluster.put("lb_type", "round_robin");

			ArrayNode clusterHosts = JsonUtil.createArrayNode();
			cluster.set("hosts", clusterHosts);

			ObjectNode cdsHost = JsonUtil.createObjectNode();
			cdsHost.put("url", EnvoyManager.toEnvoyFormatUrl(getBaseUrl().get()));
			clusterHosts.add(cdsHost);

			if (getBaseUrl().get().startsWith("https://")) {
				ObjectNode sslContext = JsonUtil.createObjectNode();
				sslContext.put("ca_cert_file", "/etc/ssl/certs/ca-certificates.crt");
				cluster.set("ssl_context", sslContext);
			}
			cds.put("refresh_delay_ms", TimeUnit.SECONDS.toMillis(30));
		}

		ArrayNode clusters = JsonUtil.createArrayNode();
		clusterManager.set("clusters", clusters);
		return this;
	}

	public ArrayNode getListeners() {
		JsonNode n = getConfig().path("listeners");
		if (n.isArray()) {
			return ArrayNode.class.cast(n);
		} else {
			ArrayNode x = JsonUtil.createArrayNode();

			getConfig().set("listeners", x);
			return x;
		}
	}

}
