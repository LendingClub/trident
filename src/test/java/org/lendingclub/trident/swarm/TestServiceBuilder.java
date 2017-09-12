package org.lendingclub.trident.swarm;

import java.util.UUID;

import org.assertj.core.util.Strings;
import org.lendingclub.neorx.NeoRxClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

public class TestServiceBuilder {
	NeoRxClient neo4j;
	String swarmClusterId;
	String swarmName="junit-"+System.currentTimeMillis();
	String serviceId = null;
	String getServiceId() {
		return serviceId;
	}
	
	public TestServiceBuilder(NeoRxClient neo4j) {
		this.neo4j = neo4j;
	}
	public TestServiceBuilder addService(String id) {
		this.serviceId = id;
		JsonNode swarm = neo4j.execCypher("match (d:DockerSwarm {name:{name}}) return d", "name", swarmName)
				.blockingFirst(MissingNode.getInstance());
		swarmClusterId = swarm.path("swarmClusterId").asText();
		
		if (Strings.isNullOrEmpty(swarmClusterId)) {
			swarmClusterId = "junit-" + UUID.randomUUID().toString();
			neo4j.execCypher(

					"merge (d:DockerSwarm {swarmClusterId:{swarmClusterId},name:{swarmName}}) set d.junitData=true return d",
					"swarmClusterId", swarmClusterId, "swarmName", swarmName);
		}
		
		
		neo4j.execCypher(
				"merge (s:DockerService {serviceId:{serviceId}}) set s.name={name}, s.junitData=true return s",
				"serviceId", serviceId,"name",serviceId);
		
		neo4j.execCypher(
				"match (d:DockerSwarm {swarmClusterId:{swarmClusterId}}),(s:DockerService {serviceId:{serviceId}}) MERGE (d)-[x:CONTAINS]->(s)",
				"swarmClusterId", swarmClusterId, "serviceId", serviceId);
		addLabel(SwarmDiscoverySearch.TSD_APP_ID,serviceId);
		return this;
	}
	public TestServiceBuilder addLabel(String name, String val) {
		checkServiceId();
		neo4j.execCypher(
				"merge (s:DockerService {serviceId:{serviceId}}) set s.label_"+name+"={val} return s",
				"serviceId", serviceId,"val",val);
		return this;
		
	}
	protected void checkServiceId() {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId),"addService() must be called first");
	}
	public TestServiceBuilder withAppId(String id) {
		checkServiceId();
		return addLabel("tsdAppId", id);
	}
	public TestServiceBuilder withServiceGroup(String id) {
		checkServiceId();
		return addLabel("tsdServiceGroup", id);
	}
	public TestServiceBuilder withEnvironment(String id) {
		checkServiceId();
		return addLabel("tsdEnv", id);
	}
	public TestServiceBuilder withSubEnvironment(String id) {
		checkServiceId();
		return addLabel("tsdSubEnv", id);
	}
	public TestServiceBuilder withPort(int port) {
		checkServiceId();
		return addLabel("tsdPort",Integer.toString(port));
	}
	public TestServiceBuilder withPaths(String ...paths) {
		checkServiceId();
		return addLabel("tsdPath",Joiner.on(",").join(paths));
	}
}
