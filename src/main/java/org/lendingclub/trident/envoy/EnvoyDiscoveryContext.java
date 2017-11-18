package org.lendingclub.trident.envoy;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.lendingclub.trident.Trident;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.swarm.SwarmDiscoverySearch;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;

public abstract class EnvoyDiscoveryContext {

	Logger logger = LoggerFactory.getLogger(getClass());

	ObjectNode config;

	String environment;
	String subEnvironment;
	String serviceNode;
	String serviceCluster;
	String serviceZone;
	String appId;
	HttpServletRequest request;

	public static final String TRIDENT_CDS_NAME="trident_cds";

	public static final String TRIDENT_SDS_NAME="trident_sds";

	public static final String DEFAULT_PLAIN_TEXT_LISTENER_NAME="trident-http";

	public static final String DEFAULT_ROUTE_CONFIG_NAME="default";

	public static final long DEFAULT_REFRESH_INTERVAL_MILLIS=TimeUnit.SECONDS.toMillis(15);

	public static final int DEFAULT_HTTP_LISTENER_PORT=5080;
	public static final int DEFAULT_HTTPS_LISTENER_PORT=5443;
	
	public static final String DEFAULT_CA_CERTS="/envoy/config/ca-certificates.crt";
	public static final String DEFAULT_ADMIN_ACCESS_LOG="/envoy/logs/admin-access.log";
	public EnvoyDiscoveryContext() {
		this(JsonUtil.createObjectNode());
	}

	public EnvoyDiscoveryContext(ObjectNode d) {
		this.config = d;
	}

	public <T extends EnvoyDiscoveryContext> T withServiceCluster(String name) {
		return withServiceGroup(name);
	}
	public <T extends EnvoyDiscoveryContext> T withServiceGroup(String name) {
		this.serviceCluster = name;
		return (T) this;
	}

	public <T extends EnvoyDiscoveryContext> T withServiceNode(String name) {
		this.serviceNode = name;
		return (T) this;
	}

	public <T extends EnvoyDiscoveryContext> T withAppId(String appId) {
		this.appId = appId;
		return (T) this;
	}

	public <T extends EnvoyDiscoveryContext> T withServiceZone(String name) {
		this.serviceZone = name;
		return (T) this;
	}

	public <T extends EnvoyDiscoveryContext> T withEnvironment(String name) {
		this.environment = name;
		return (T) this;
	}

	public <T extends EnvoyDiscoveryContext> T withSubEnvironment(String name) {
		this.subEnvironment = name;
		return (T) this;
	}

	public <T extends EnvoyDiscoveryContext> T withServletRequest(HttpServletRequest request) {
		this.request = request;
		return (T) this;
	}

	public ObjectNode getConfig() {
		return config;
	}

	public void setConfig(ObjectNode cfg) {
		this.config = cfg;
	}

	public Optional<String> getBaseUrl() {
		if (getServletRequest().isPresent()) {
			String url = getServletRequest().get().getRequestURL().toString();
			List<String> list = Splitter.on("://").omitEmptyStrings().splitToList(url);
			return Optional.ofNullable(
					list.get(0) + "://" + Strings.emptyToNull(Splitter.on("/").splitToList(list.get(1)).get(0)));
		}
		return Optional.empty();
	}

	public final Optional<String> getEnvironment() {
		return Optional.ofNullable(Strings.emptyToNull(environment));
	}

	public final Optional<String> getSubEnvironment() {
		return Optional.ofNullable(Strings.emptyToNull(subEnvironment));
	}

	public final Optional<String> getServiceZone() {
		return Optional.ofNullable(Strings.emptyToNull(serviceZone));
	}

	public final Optional<String> getServiceCluster() {
		return Optional.ofNullable(Strings.emptyToNull(serviceCluster));
	}

	public final Optional<String> getAppId() {
		return Optional.ofNullable(Strings.emptyToNull(appId));
	}

	public final Optional<String> getServiceNode() {
		return Optional.ofNullable(Strings.emptyToNull(serviceNode));
	}

	public final Optional<HttpServletRequest> getServletRequest() {
		return Optional.ofNullable(request);
	}

	public String toString() {
		return MoreObjects.toStringHelper(this).add("env", getEnvironment().orElse(null))
				.add("subenv", getSubEnvironment().orElse(null)).add("cluster", getServiceCluster().orElse(null))
				.add("zone", getServiceZone().orElse(null)).add("node", getServiceNode().orElse(null)).toString();
	}

	public ResponseEntity<String> toResponseEntity() {
		return ResponseEntity.ok(JsonUtil.prettyFormat(JsonMetadataScrubber.scrub(getConfig())));
	}

	public void log() {
		String path = getServletRequest().isPresent() ? getServletRequest().get().getRequestURI() : null;

		if (path!=null) {
			logger.info("GET {}",path);
		}
	}

	public SwarmDiscoverySearch newSearch() {
		SwarmDiscoverySearch search = Trident.getApplicationContext().getBean(SwarmClusterManager.class).newServiceDiscoverySearch();
				

		getEnvironment().ifPresent(it -> {
			logger.info("searching for env={}",it);
			search.withEnvironment(it);
		});
		getSubEnvironment().ifPresent(it -> {
			logger.info("searching for subenv={}",it);
			search.withSubEnvironment(it);
		});
		getServiceZone().ifPresent(it -> {
			logger.info("searching for region={}",it);
			search.withRegion(it);
		});	
		getServiceCluster().ifPresent(it->{
			logger.info("searching for serviceGroup={}",it);
			search.withServiceGroup(it);
		});
		getAppId().ifPresent(it->{
			logger.info("searching for appId={}",it);
			search.withAppId(it);
		});
		return search;
	}
}
