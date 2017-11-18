package org.lendingclub.trident.swarm.aws.task;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.lendingclub.trident.scheduler.DistributedTask;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.swarm.aws.AWSAccountManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.InstanceState;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

public class AWSTerminatedNodeCleanupTask extends DistributedTask {

	Logger logger = LoggerFactory.getLogger(AWSTerminatedNodeCleanupTask.class);

	@Override
	public void run() {

		getNeoRxClient().execCypher("match (s:DockerSwarm) return s").forEach(it -> {
			String tridentClusterId = it.path("tridentClusterId").asText();
			logger.info("inspecting {} for terminated nodes", tridentClusterId);
			try {

				scanTridentCluster(tridentClusterId);
			} catch (RuntimeException e) {
				logger.warn("problem scanning cluster: " + tridentClusterId, e);
			}
		});

	}

	protected void scanTridentCluster(String id) {
		if (Strings.isNullOrEmpty(id)) {
			return;
		}
		logger.info("scanning tridentClusterId={} for terminated nodes",id);
		getApplicationContext().getBean(SwarmClusterManager.class).getSwarm(id).getManagerWebTarget().path("/nodes").request()
				.buildGet().invoke(JsonNode.class).forEach(node -> {
					try {
					
						scanNode(id,node);
					} catch (RuntimeException e) {
						logger.warn("problem scanning node: {} - " + e.toString(), node);
					}
				});
	}

	
	protected void scanNode(String tridentClusterId, JsonNode n) {
		logger.info("checking to see if this node is terminated tridentClusterId={} node={}",tridentClusterId, n);
		// look for nodes that are down and are in AWS
		if (isCandidate(n)) {
			String nodeId = n.path("ID").asText();
			String awsInstance = n.path("Spec").path("Labels").path("aws_instanceId").asText();
			// We need to find the aws account for this instance, but we can't
			// use the neo4j graph because if the
			// instance was terminated, the neo4j entity will be gone.
			AtomicReference<String> account = new AtomicReference<String>(null);
			AtomicReference<String> region = new AtomicReference<String>(null);
			getNeoRxClient().execCypher("match (s:DockerSwarm {tridentClusterId:{id}})--(g:AwsAsg {aws_tag_swarmNodeType:\"MANAGER\"}) return g", "id",
					tridentClusterId).forEach(asg -> {
						logger.info("found manager ASG for cluster: {}",asg);
						String asgAccount = asg.path("aws_account").asText();
						String asgRegion  = asg.path("aws_region").asText();
						if (!Strings.isNullOrEmpty(asgAccount)) {
							account.set(asgAccount);
						}
						if (!Strings.isNullOrEmpty(asgRegion)) {
							region.set(asgRegion);
						}
						
					});
			logger.info("checking node={} instance={} in account={} region={}",nodeId,awsInstance,account.get(),region.get());
			if (!Strings.isNullOrEmpty(account.get()) && !Strings.isNullOrEmpty(region.get())) {
				if (isEc2NodeMissing(n, awsInstance, account.get(), region.get())) {
					maybeRemoveNode(n, awsInstance, account.get(), region.get(), tridentClusterId);
				}
				else {
					logger.info("taking no action because instance={} was not found to be missing");
				}
			}
			else {
				logger.info("could not locate account/region for node...will not take action");
			}
		}
		
	}

	protected void maybeRemoveNode(JsonNode n, String awsInstance, String account, String region,
			String tridentClusterId) {
		logger.info("removing terminated node: {}", n);

		try {
			String nodeId = n.path("ID").asText();
			boolean force = false;
			getApplicationContext().getBean(SwarmClusterManager.class).getSwarm(tridentClusterId).getManagerWebTarget()
					.path("/nodes").path(nodeId).queryParam("force", force).request().buildDelete()
					.invoke(String.class);
		} catch (RuntimeException e) {
			logger.info("failed to remove node " + n, e);
		}

	}

	@VisibleForTesting
	protected boolean responseProvesInstanceIsMissing(DescribeInstancesResult result) {

		AtomicBoolean present = new AtomicBoolean(false);

		result.getReservations().forEach(it -> {
			it.getInstances().forEach(x -> {
				InstanceState is = x.getState();
				if (is != null && is.getCode() == 48) {
					present.set(true);
				}
			});
		});
		return present.get();

	}

	@VisibleForTesting
	protected boolean exceptionProvesInstanceIsMissing(RuntimeException e) {

		if (!(e instanceof AmazonEC2Exception)) {
			return false;
		}

		AmazonEC2Exception ec2Exception = (AmazonEC2Exception) e;
		String errorCode = ec2Exception.getErrorCode();
		if (errorCode != null && errorCode.equals("InvalidInstanceID.NotFound")) {
			return true;
		}
		return false;
	}

	protected boolean isEc2NodeMissing(JsonNode n, String awsInstance, String account, String region) {

		try {
			AmazonEC2 ec2 = getApplicationContext().getBean(AWSAccountManager.class)
					.getClient(account, AmazonEC2ClientBuilder.class,region);
		
			DescribeInstancesRequest request = new DescribeInstancesRequest();
			request.withInstanceIds(awsInstance);
			DescribeInstancesResult result = ec2.describeInstances(request);
			if (responseProvesInstanceIsMissing(result)) {
				return true;
			} else {
				return false;
			}

		} catch (AmazonEC2Exception e) {
			if (exceptionProvesInstanceIsMissing(e)) {
				return true;
			}

		}
		return false;

	}

	protected boolean isCandidate(JsonNode n) {

		String awsInstance = n.path("Spec").path("Labels").path("aws_instanceId").asText();
		String state = n.path("Status").path("State").asText();

		if (Strings.isNullOrEmpty(awsInstance) || Strings.isNullOrEmpty(state)) {
			return false;
		}
		if (awsInstance.startsWith("i-") && state.toLowerCase().startsWith("down")) {
			return true;
		}
		return false;
	}
}
