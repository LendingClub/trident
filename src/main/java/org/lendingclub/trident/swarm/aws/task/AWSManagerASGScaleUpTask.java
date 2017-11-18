package org.lendingclub.trident.swarm.aws.task;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.lendingclub.trident.Trident;
import org.lendingclub.trident.scheduler.DistributedTask;
import org.lendingclub.trident.swarm.aws.ASGEditor;
import org.lendingclub.trident.swarm.aws.AWSAccountManager;
import org.lendingclub.trident.swarm.aws.AWSClusterManager;
import org.lendingclub.trident.util.DockerDateFormatter;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;

/**
 * This task will look at all the manager ASGs and attempt to adjust them to a replica count of 3.
 * @author rschoening
 *
 */
public class AWSManagerASGScaleUpTask extends DistributedTask {

	Logger logger = LoggerFactory.getLogger(AWSManagerASGScaleUpTask.class);
	boolean dryRun = false;

	/*
	 * 
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {

		// find all the manager ASGs that:

		// a) have a desiredCapacity of 1
		// b) have been created in the last hour
		// c) have a fully-formed cluster
		String cypher = "match (a:AwsAsg {aws_tag_swarmNodeType:'MANAGER'} )--(x:DockerSwarm)--(h:DockerHost) "
				+ " return " + " distinct " + " a.aws_account as aws_account," + " a.aws_region as aws_region,"
				+ " x.name as swarmName," + " x.swarmClusterId as swarmClusterId,"
				+ " a.aws_autoScalingGroupName as aws_autoScalingGroupName,"
				+ " a.aws_tag_swarmNodeType as swarmNodeType," + " a.aws_minSize as aws_minSize, "
				+ " a.aws_maxSize as aws_maxSize," + " a.aws_desiredCapacity as aws_desiredCapacity,"
				+ " x.createdAt as createdAt";

		getNeoRxClient().execCypher(cypher).filter(AWSManagerASGScaleUpTask::isScaleUpRequired)
				.forEach(it -> {
			try {
				scaleUpManagerASG(it);
			}
			catch (Exception e) {
				logger.warn("problem adjusting ASG size",e);
			}
		});
	}
	@VisibleForTesting
	public AWSManagerASGScaleUpTask withDryRun(boolean b) {
		this.dryRun = b;
		return this;
	}
	static boolean isScaleUpRequired(JsonNode n) {
	
		long ageMillis  = System.currentTimeMillis() - DockerDateFormatter.parse(n.path("createdAt").asText())
		.orElse(Instant.ofEpochSecond(0L)).toEpochMilli();
	
		return n.path("aws_desiredCapacity").asInt() == 1 &&
				ageMillis < TimeUnit.HOURS.toMillis(1) &&
				n.path("swarmNodeType").asText().equals("MANAGER");
				
	}
	AWSClusterManager getAWSClusterManager() {
		return Trident.getApplicationContext().getBean(AWSClusterManager.class);
	}

	void scaleUpManagerASG(JsonNode n) {
		String swarmClusterId = n.path("swarmClusterId").asText();

		if (dryRun) {
			JsonUtil.logInfo(getClass(), "scaling up manager ASG (DRY RUN)", n);
		} else {
			JsonUtil.logInfo(getClass(), "scaling up manager ASG", n);
			getAWSClusterManager().createSwarmManagerASGEditor(swarmClusterId).withAutoScalingGroup(cfg -> {
				cfg.setMaxSize(3);
				cfg.setMinSize(3);
				cfg.setDesiredCapacity(3);
			}).execute();
			
			mercatorScan(n);
		}

	}

	public void mercatorScan(JsonNode n) {

		JsonUtil.logInfo(getClass(), "rescanning ASG", n);

		getAWSClusterManager().newScannerBuilder(n.path("aws_account").asText(),n.path("aws_region").asText()).buildASGScanner().scanASGNames(n.path("aws_autoScalingGroupName").asText());
		
	
	}
}
