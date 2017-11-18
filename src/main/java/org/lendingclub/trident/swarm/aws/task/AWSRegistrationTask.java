package org.lendingclub.trident.swarm.aws.task;

import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import org.lendingclub.trident.scheduler.DistributedTask;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.swarm.aws.AWSClusterManager;
import org.lendingclub.trident.swarm.aws.AWSMetadataSync;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.reactivex.functions.Consumer;

public class AWSRegistrationTask extends DistributedTask {

	Logger logger = LoggerFactory.getLogger(AWSRegistrationTask.class);

	@Override
	public void run() {
		// find all the registration tasks that have aws_instanceId
		// label the node in docker
		// tag the ec2 instance in ec2
		// mark the registration as complete

		getNeoRxClient().execCypher("match (a:DockerRegistration) where exists(a.awsInstanceId) and timestamp() - a.createTs < "+TimeUnit.MINUTES.toMillis(60)+" return a")
				.forEach(new DockerNodeLabeler());
		getNeoRxClient().execCypher("match (a:DockerRegistration) where exists(a.awsInstanceId) and timestamp() - a.createTs < "+TimeUnit.MINUTES.toMillis(60)+" return a")
				.forEach(new EC2InstanceTagger());

	}

	protected void markAttribute(JsonNode data, String attribute, String val) {
		getNeoRxClient().execCypher("match (a:DockerRegistration {swarmNodeId:{id}}) set a." + attribute + "={val}",
				"id", data.get("swarmNodeId").asText(), "val", val);
	}

	class DockerNodeLabeler implements Consumer<JsonNode> {

		@Override
		public void accept(JsonNode t) {

			try {
				if (t.path("nodeLabelStatus").asText().equals("complete")) {
					return;
				}
				String instanceId = t.path("awsInstanceId").asText();
				String swarmNodeId = t.path("swarmNodeId").asText();
				String tridentClusterId = t.path("tridentClusterId").asText();

				// 1 obtain a connection to the cluster

				WebTarget wt = getApplicationContext().getBean(SwarmClusterManager.class).getSwarm(tridentClusterId)
					.getManagerWebTarget();

				// write the attribute in the form consistent with mercator
				addLabelToNode(wt, swarmNodeId, "aws_instanceId", instanceId);

				// set the completion marker

				markAttribute(t, "nodeLabelStatus", "complete");
				
	
			} catch (RuntimeException e) {
				logger.warn("could not label node", e);
				markAttribute(t, "nodeLabelStatus", "failed");
			}

		}

	}

	protected void addLabelToNode(WebTarget wt, String swarmNodeId, String key, String val) {

	
	
		wt.path("nodes").path(swarmNodeId).request().buildGet().invoke(JsonNode.class);
		JsonNode response = wt.path("nodes").path(swarmNodeId).request().buildGet().invoke(JsonNode.class);

	
		ObjectNode spec = (ObjectNode) response.path("Spec");
		String version = response.get("Version").get("Index").asText();

		ObjectNode labels = (ObjectNode) spec.get("Labels");

		labels.put(key, val);

		wt.path("nodes").path(swarmNodeId).path("update").queryParam("version", version).request()
				.buildPost(Entity.entity(spec.toString(), MediaType.APPLICATION_JSON)).invoke(String.class);

	}

	class EC2InstanceTagger implements Consumer<JsonNode> {

		@Override
		public void accept(JsonNode t) {
			try {
				if (t.path("ec2TagStatus").asText().equals("complete")) {
					return;
				}

				String tridentClusterId = t.path("tridentClusterId").asText();
				String instanceId = t.path("awsInstanceId").asText();
				String swarmNodeId = t.path("swarmNodeId").asText();

				
				JsonNode n = getNeoRxClient().execCypher("match (s:DockerSwarm {tridentClusterId:{id}})--(a:AwsAsg) return a","id",tridentClusterId).blockingFirst(MissingNode.getInstance());
				String account = n.path("aws_account").asText();
				String region = n.path("aws_region").asText();
				
				AmazonEC2 ec2 = getApplicationContext().getBean(AWSClusterManager.class).getAccountManager().getClient(account, AmazonEC2ClientBuilder.class,region);
				
				
				CreateTagsRequest tagRequest = new com.amazonaws.services.ec2.model.CreateTagsRequest();
				
				tagRequest.withResources(instanceId);
				tagRequest.withTags(new Tag().withKey("swarmNodeId").withValue(swarmNodeId));
				ec2.createTags(tagRequest);
				
				// after completion (this is ok to fail), we sync all the attributes to AWS
				getApplicationContext().getBean(AWSMetadataSync.class).writeTagsForSwarm(tridentClusterId);
				
				markAttribute(t, "ec2TagStatus", "complete");

			} catch (RuntimeException e) {
				logger.warn("could not tag ec2 instance", e);
				markAttribute(t, "ec2TagStatus", "failed");
			}

		}

	}

}
