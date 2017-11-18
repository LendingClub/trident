package org.lendingclub.trident.envoy;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.lendingclub.trident.util.JsonUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

import net.lingala.zip4j.core.ZipFile;

public class EnvoyBootstrapConfigContext extends EnvoyDiscoveryContext {

	String environment;
	String subEnvironment;
	String serviceNode;
	String serviceCluster;
	String serviceZone;

	protected ZipFile bundle;
	
	public EnvoyBootstrapConfigContext() {
		super();

	}

	public EnvoyBootstrapConfigContext withConfigZipBundle(ZipFile zipFile) {
		this.bundle = zipFile;
		return this;
	}
	EnvoyBootstrapConfigContext withSkeleton(HttpServletRequest request) {
		withServletRequest(request);
		ObjectNode n = getConfig();

		ArrayNode listeners = JsonUtil.createArrayNode();
		n.set("listeners", listeners);

		{
			ObjectNode lds = JsonUtil.createObjectNode();
			n.set("lds", lds);

			lds.put("cluster", EnvoyDiscoveryContext.TRIDENT_SDS_NAME);
			lds.put("refresh_delay_ms", EnvoyDiscoveryContext.DEFAULT_REFRESH_INTERVAL_MILLIS);
		}


		ObjectNode admin = JsonUtil.createObjectNode();
		admin.put("access_log_path", DEFAULT_ADMIN_ACCESS_LOG);
		admin.put("address", "tcp://0.0.0.0:9901");
		n.set("admin", admin);

		ObjectNode clusterManager = JsonUtil.createObjectNode();

		{
			ObjectNode sds = JsonUtil.createObjectNode();
			clusterManager.set("sds", sds);
			n.set("cluster_manager", clusterManager);
			ObjectNode cluster = JsonUtil.createObjectNode();
			sds.set("cluster", cluster);

			cluster.put("name", EnvoyDiscoveryContext.TRIDENT_SDS_NAME);
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
				sslContext.put("ca_cert_file", EnvoyDiscoveryContext.DEFAULT_CA_CERTS);
				cluster.set("ssl_context", sslContext);
			}

			sds.put("refresh_delay_ms", EnvoyDiscoveryContext.DEFAULT_REFRESH_INTERVAL_MILLIS);
		}

		{
			ObjectNode cds = JsonUtil.createObjectNode();
			clusterManager.set("cds", cds);
			ObjectNode cluster = JsonUtil.createObjectNode();
			cds.set("cluster", cluster);
			cluster.put("name", EnvoyDiscoveryContext.TRIDENT_CDS_NAME);
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
				sslContext.put("ca_cert_file", EnvoyDiscoveryContext.DEFAULT_CA_CERTS);
				cluster.set("ssl_context", sslContext);
			}
			cds.put("refresh_delay_ms", EnvoyDiscoveryContext.DEFAULT_REFRESH_INTERVAL_MILLIS);
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

	public Optional<ZipFile> getConfigZipBundle() {
		return Optional.ofNullable(bundle);
	}
}
