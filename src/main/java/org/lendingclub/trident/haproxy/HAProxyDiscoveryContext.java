package org.lendingclub.trident.haproxy;

/**
 * Created by hasingh on 9/20/17.
 */

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.lendingclub.trident.Trident;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.swarm.SwarmDiscoverySearch;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;

public abstract class HAProxyDiscoveryContext {

	Logger logger = LoggerFactory.getLogger(getClass());

	ObjectNode config;

	String environment;
	String subEnvironment;
	String serviceNode;
	String serviceCluster;
	String serviceZone;
	String appId;
	String region;
	HttpServletRequest request;

	public HAProxyDiscoveryContext() {
		this(JsonUtil.createObjectNode());
	}

	public HAProxyDiscoveryContext(ObjectNode d) {
		this.config = d;
	}

	public <T extends HAProxyDiscoveryContext> T withServiceCluster(String name) {
		return withServiceGroup(name);
	}
	public <T extends HAProxyDiscoveryContext> T withServiceGroup(String name) {
		this.serviceCluster = name;
		return (T) this;
	}

	public <T extends HAProxyDiscoveryContext> T withServiceNode(String name) {
		this.serviceNode = name;
		return (T) this;
	}

	public <T extends HAProxyDiscoveryContext> T withAppId(String appId) {
		this.appId = appId;
		return (T) this;
	}

	public <T extends HAProxyDiscoveryContext> T withServiceZone(String name) {
		this.serviceZone = name;
		return (T) this;
	}

	public <T extends HAProxyDiscoveryContext> T withEnvironment(String name) {
		this.environment = name;
		return (T) this;
	}

	public <T extends HAProxyDiscoveryContext> T withSubEnvironment(String name) {
		this.subEnvironment = name;
		return (T) this;
	}

	public <T extends HAProxyDiscoveryContext> T withServletRequest(HttpServletRequest request) {
		this.request = request;
		return (T) this;
	}

	public <T extends HAProxyDiscoveryContext> T withRegion(String region) {
		this.region = region;
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

	public final Optional<String> getRegion() {
		return Optional.ofNullable(Strings.emptyToNull(region));
	}

	public String toString() {
		return MoreObjects.toStringHelper(this).add("env", getEnvironment().orElse(null))
				.add("subenv", getSubEnvironment().orElse(null)).add("cluster", getServiceCluster().orElse(null))
				.add("zone", getServiceZone().orElse(null)).add("node", getServiceNode().orElse(null)).toString();
	}

	public ResponseEntity<JsonNode> toResponseEntity() {
		return ResponseEntity.ok(this.config);
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
