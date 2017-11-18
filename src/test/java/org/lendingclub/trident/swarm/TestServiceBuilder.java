package org.lendingclub.trident.swarm;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

import com.google.common.net.InetAddresses;
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

	public TestServiceBuilder addServiceTask() {
		Random rand = new Random(System.currentTimeMillis());
		String ipAddr = InetAddresses.fromInteger(rand.nextInt()).getHostAddress();
		int portLowerBound = 32768;
		int portUpperBound = 61000;
		int randPort = rand.nextInt(portUpperBound - portLowerBound + 1) + portLowerBound;

		neo4j.execCypher("merge (d: DockerTask {serviceId: {serviceId}}) set d.junitData=true, "
						+ " d.desiredState='running', d.serviceId={serviceId}, d.state='running', d.hostTcpPortMap_8080="+randPort+" return d",
				"serviceId", serviceId);

		neo4j.execCypher("match(d: DockerService {serviceId: {serviceId}}), (e: DockerTask {serviceId: {serviceId}})"
				+ " merge (d)-[:CONTAINS]->(e) return e" , "serviceId", serviceId);

		neo4j.execCypher("merge(d: DockerHost {leader: true, hostname: 'moby', addr: '"+ipAddr+"', junitData: true,"
				+ "uniqTestDataId: {serviceId}}) "
				+ "return d", "serviceId", serviceId);

		neo4j.execCypher("match(d: DockerTask {serviceId: {serviceId}}), (e: DockerHost {uniqTestDataId: {serviceId}}) "
				+ "merge (e)-[:CONTAINS]->(d)", "serviceId", serviceId);

		neo4j.execCypher(
				"match(d: DockerTask {serviceId: {serviceId}}), (e: DockerHost)return d",
				"serviceId", serviceId);

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
