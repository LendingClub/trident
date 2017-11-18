package org.lendingclub.trident.swarm.aws;

import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.swarm.SwarmNodeType;
import org.lendingclub.trident.swarm.aws.AWSClusterManager.SwarmTemplateASGDecorator;
import org.lendingclub.trident.swarm.aws.AWSClusterManager.SwarmTemplateLaunchConfigDecorator;
import org.lendingclub.trident.util.JsonUtil;
import org.rapidoid.u.U;
import org.springframework.beans.factory.annotation.Autowired;

import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.BaseEncoding;

public class AWSClusterManagerIntegrationTest extends TridentIntegrationTest {

	String NOT_FOUND = UUID.randomUUID().toString();
	@Inject
	AWSClusterManager clusterManager;

	@Inject
	NeoRxClient neo4j;

	@Autowired
	AWSMetadataSync metadataSync;

	@Test(expected = TridentException.class)
	public void testWorkerNotFound() {

		clusterManager.newWorkerASGBuilder(NOT_FOUND);
	}

	@Test(expected = TridentException.class)
	public void testManagerClusterNotFound() {
		clusterManager.newManagerASGBuilder(NOT_FOUND);
	}

	@Test
	public void testCreateManager() {
		String name = "junit-" + System.currentTimeMillis();
		String id = UUID.randomUUID().toString();
		neo4j.execCypher("merge (c:DockerSwarm {tridentClusterId:{id}}) set c.templateName='foo', c.name={name} return c", "id", id, "name",
				name);

		SwarmASGBuilder b = clusterManager.newManagerASGBuilder(id);

		Assertions.assertThat(b.getTridentClusterId()).isEqualTo(id);
		Assertions.assertThat(b.getSwarmNodeType()).isEqualTo(SwarmNodeType.MANAGER);
		Assertions.assertThat(b.getAWSClusterManager().getAccountManager()).isNotNull();

	}



	@Test
	public void testCreateWorkerBuilder() {
		String name = "junit-" + System.currentTimeMillis();
		String id = UUID.randomUUID().toString();
		neo4j.execCypher("merge (c:DockerSwarm {tridentClusterId:{id}}) set c.templateName='foo' , c.name={name} return c", "id", id, "name",
				name);
		
		SwarmASGBuilder b = clusterManager.newWorkerASGBuilder(id);

		Assertions.assertThat(b.getTridentClusterId()).isEqualTo(id);
		Assertions.assertThat(b.getSwarmNodeType()).isEqualTo(SwarmNodeType.WORKER);
		Assertions.assertThat(b.getAWSClusterManager().getAccountManager()).isNotNull();
		Assertions.assertThat(b.getAWSClusterManager()).isSameAs(clusterManager);
	}

	protected String createTestSwarmTemplate(String... kv) {
		String name = "junit-" + System.currentTimeMillis();

		Map<String, String> map = U.map(kv);
		ObjectNode n = JsonUtil.getObjectMapper().convertValue(map, ObjectNode.class);
		n.put("name", name);
		getNeoRxClient().execCypher("merge (a:DockerSwarmTemplate {templateName:{name}}) set a+={props}", "name", name, "props",
				n);

		return name;
	}

	
	@Test
	public void testManagerLaunchConfig() {
		SwarmTemplateLaunchConfigDecorator d = clusterManager.new SwarmTemplateLaunchConfigDecorator();

		String templateName = createTestSwarmTemplate(AWSClusterManager.AWS_MANAGER_IMAGE_ID,"i-1234",
				AWSClusterManager.AWS_WORKER_IMAGE_ID,"i-4321",
				AWSClusterManager.AWS_MANAGER_CLOUD_INIT,"foo");

		ObjectNode data = JsonUtil.createObjectNode();
		data.put(AWSClusterManager.SWARM_NODE_TYPE, "MANAGER");
		data.put("templateName", templateName);
		CreateLaunchConfigurationRequest lcr = new CreateLaunchConfigurationRequest();
		d.accept(data, lcr);

		Assertions.assertThat(lcr.getImageId()).isEqualTo("i-1234");
		Assertions.assertThat(lcr.getUserData()).isEqualTo(BaseEncoding.base64().encode("foo".getBytes()));
		Assertions.assertThat(data.path(AWSClusterManager.AWS_MANAGER_IMAGE_ID).asText()).isEqualTo("i-1234");
		Assertions.assertThat(data.path(AWSClusterManager.AWS_WORKER_IMAGE_ID).asText()).isEqualTo("i-4321");
		
		

	}
	@Test
	public void testASGManagerTemplate() {
		SwarmTemplateASGDecorator d = clusterManager.new SwarmTemplateASGDecorator();

		String templateName = createTestSwarmTemplate("awsManagerSubnets", "subnet-a,subnet-b", "awsWorkerSubnets",
				"subnet-c");

		ObjectNode data = JsonUtil.createObjectNode();
		data.put(AWSClusterManager.SWARM_NODE_TYPE, "MANAGER");
		data.put("templateName", templateName);
		CreateAutoScalingGroupRequest asgRequest = new CreateAutoScalingGroupRequest();
		d.accept(data, asgRequest);

		Assertions.assertThat(asgRequest.getVPCZoneIdentifier()).isEqualTo("subnet-a,subnet-b");

		Assertions.assertThat(data.path("awsWorkerSubnets").asText()).isEqualTo("subnet-c");
		Assertions.assertThat(data.path("awsManagerSubnets").asText()).isEqualTo("subnet-a,subnet-b");


	}
	
	@Test
	public void testWorkerASGTemplate() {
		SwarmTemplateASGDecorator d = clusterManager.new SwarmTemplateASGDecorator();

		String templateName = createTestSwarmTemplate("awsManagerSubnets", "subnet-a,subnet-b", "awsWorkerSubnets",
				"subnet-c");

	
			ObjectNode data = JsonUtil.createObjectNode();
			data.put("nodeType", "WORKER");
			data.put("templateName", templateName);
			CreateAutoScalingGroupRequest asgRequest = new CreateAutoScalingGroupRequest();
			d.accept(data, asgRequest);

			Assertions.assertThat(asgRequest.getVPCZoneIdentifier()).isEqualTo("subnet-c");
			Assertions.assertThat(data.path("awsWorkerSubnets").asText()).isEqualTo("subnet-c");
			Assertions.assertThat(data.path("awsManagerSubnets").asText()).isEqualTo("subnet-a,subnet-b");

	}

	@Test
	public void testCreateRelationships() {
		metadataSync.createMissingASGRelationships();
	}

	@After
	public void cleanup() {
		if (neo4j.checkConnection()) {
			neo4j.execCypher("match (c:DockerSwarm) where c.name=~'junit.*' detach delete c");
			neo4j.execCypher("match (c:DockerSwarmTemplate) where c.templateName=~'junit.*' detach delete c");
		}
	}
}
