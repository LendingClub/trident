package org.lendingclub.trident.agent;

import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.WebTarget;

import org.lendingclub.mercator.docker.DockerRestClient;
import org.lendingclub.mercator.docker.SwarmScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import io.macgyver.okrest3.OkRestClient;
import io.macgyver.okrest3.OkRestResponse;
import io.macgyver.okrest3.OkRestTarget;

public abstract class TridentAgent  {

	ObjectMapper mapper = new ObjectMapper();
	public static final String TRIDENT_BASE_URL="TRIDENT_BASE_URL";
	public static final String DEFAULT_TRIDENT_BASE_URL="http://localhost:8080";
	static OkRestClient tridentClient = new OkRestClient.Builder().withOkHttpClientConfig(cfg->{
		cfg.connectTimeout(5, TimeUnit.SECONDS);
	}).build();
	DockerClient dockerClient;
	Logger logger = LoggerFactory.getLogger(getClass());
	String tridentBaseUrl;
	ObjectNode staticDockerInfo = null;
	public String getTridentBaseUrl() {
		if (tridentBaseUrl!=null) {
			return tridentBaseUrl;
		}
		String val = System.getenv("TRIDENT_BASE_URL");
		if (!Strings.isNullOrEmpty(val)) {
			tridentBaseUrl = val;
		}
		else {
			tridentBaseUrl = DEFAULT_TRIDENT_BASE_URL;
		}
		return tridentBaseUrl;
	}
	
	public OkRestTarget getTridentBaseTarget() {
		return tridentClient.uri(getTridentBaseUrl());
	}
	
	@VisibleForTesting
	protected void setStaticDockerInfo(ObjectNode n) {
		staticDockerInfo = n;
	}
	JsonNode getDockerInfo() {
		if (staticDockerInfo==null) {

		JsonNode info = getDockerRestClient().getInfo();
		return info;
		}
		else {
			logger.warn("**** Using Static Docker Info Data -- should only be used in unit tests ***");
			return staticDockerInfo;
		}
	}
	DockerRestClient getDockerRestClient() {
		return DockerRestClient.forDockerClient(getDockerClient());
	}
	ObjectNode createIdentityEnvelope() {
		ObjectNode n = mapper.createObjectNode();
		
		
		JsonNode info = getDockerInfo();
		
		n.set("dockerInfo", info); // send the entire info object...a lot of which may be useful
		
		return n;
	}
	synchronized DockerClient getDockerClient() {
		if (dockerClient == null) {
			DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
					.withDockerHost("unix:///var/run/docker.sock")
					.build();
			dockerClient = DockerClientBuilder.getInstance(config).build();
		}
		return dockerClient;
	}
	
	public void sendEvent(String path, String eventName, JsonNode data) {
		ObjectNode envelope = createIdentityEnvelope();
		envelope.set(eventName, data);
		OkRestTarget target = getTridentBaseTarget().path(path);
		OkRestResponse response= null;
		try {
			logger.info("sending event: \n{}", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(envelope));
		}
		catch (Exception e) {
			logger.error("problem logging",e);
		}
		try {
		 response = target.contentType("application/json").post(envelope).execute();
		
		 }
		finally {
			logger.info("POST to {} : rc={}",target.getUrl(),response!=null ? response.response().code() : -1 );
		}
		
		
	}
}
