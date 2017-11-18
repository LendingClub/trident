package org.lendingclub.trident.swarm;

import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import org.lendingclub.trident.Trident;
import org.lendingclub.trident.util.JsonUtil;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.netty.WebTarget;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Entity;

public class SwarmServiceEditor {

	String swarmId;
	String serviceId;
	
	List<Consumer<ObjectNode>> consumerList = Lists.newArrayList();
	Logger logger = LoggerFactory.getLogger(SwarmServiceEditor.class);
	
	public SwarmServiceEditor withSwarmId(String id) {
		this.swarmId = id;
		return this;
	}
	public SwarmServiceEditor withServiceId(String id) {
		this.serviceId = id;
		return this;
	}
	public SwarmServiceEditor withServiceConfig(Consumer<ObjectNode> config) {
		consumerList.add(config);
		return this;
	}
	javax.ws.rs.client.WebTarget getWebTarget() {
		return Trident.getInstance().getApplicationContext().getBean(SwarmClusterManager.class).getSwarm(swarmId).getManagerWebTarget();
	}
	
	public SwarmServiceEditor withReplicaCount(int count) {
		return withServiceConfig(c-> {
			// set replica count here
			ObjectNode node = JsonUtil.createObjectNode().put("Replicas", count);
			ObjectNode replicated = JsonUtil.createObjectNode();
			replicated.set("Replicated", node);
			((ObjectNode) c.path("Spec")).set("Mode", replicated);
		});
	}
	public SwarmServiceEditor withRemoveLabel(String name) {
		return withServiceConfig(c->{
			// do it here
			( (ObjectNode) c.get("Spec").get("Labels")).remove(name);
		});
	}
	
	public SwarmServiceEditor withAddLabel(String name, String value) {
		return withServiceConfig(c->{
			// set it here
			( (ObjectNode) c.get("Spec").get("Labels")).put(name, value);
		});
	}

	void applyConfig(ObjectNode config) {
		consumerList.forEach(it->{
			it.accept(config);
		});
	}

	public ObjectNode getConfig() {
		return (ObjectNode) getWebTarget().path("/services").path(serviceId).queryParam("insertDefaults", "false")
				.request().get(JsonNode.class);

	}

	public void execute() {
		// fetch the existing service from the manager
		logger.info("existing config for service {} in swarm {}", serviceId, swarmId);
		ObjectNode config = (ObjectNode) getWebTarget().path("/services").path(serviceId).queryParam("insertDefaults", "false")
				.request().get(JsonNode.class);

		logger.info("current service config is {}", JsonUtil.prettyFormat(config));
		ObjectNode spec = (ObjectNode) config.path("Spec");

		spec.set("Name", config.path("Spec").path("Name"));
		spec.set("Labels", config.path("Spec").path("Labels"));
		spec.set("TaskTemplate", config.path("Spec").path("TaskTemplate"));
		spec.set("EndpointSpec", config.path("Spec").path("EndpointSpec"));
		spec.set("Endpoint", config.path("Endpoint"));
		
		// apply the config
		applyConfig((ObjectNode) config);
		
		String version = config.get("Version").get("Index").asText();

		logger.info( "service update request is {} ", JsonUtil.prettyFormat(config.path("Spec")) );
		
		// update the service
		JsonNode serviceUpdateResponse = getWebTarget().path("/services").path(config.path("ID").asText()).path("update")
				.queryParam("version", version).request()
				.post(Entity.entity(spec, javax.ws.rs.core.MediaType.APPLICATION_JSON), JsonNode.class);

		logger.info("service update response is {}", JsonUtil.prettyFormat(serviceUpdateResponse));
	}
}
