package org.lendingclub.trident.swarm.aws;

import java.util.Collection;
import java.util.List;

import org.lendingclub.trident.cluster.TridentClusterManager;
import org.lendingclub.trident.event.TridentEvent;
import org.lendingclub.trident.scheduler.DistributedTask;
import org.lendingclub.trident.swarm.aws.event.LoadBalancerAttachedEvent;
import org.lendingclub.trident.swarm.aws.event.LoadBalancerCreatedEvent;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.AttachLoadBalancersRequest;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest;
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsResult;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.Listener;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class ManagerELBCreationTask extends DistributedTask {

	Logger logger = LoggerFactory.getLogger(ManagerELBCreationTask.class);

	@Override
	public void run() {

		// Look for ASGs that don't have an ELB

		getNeoRxClient().execCypher(
				"match (d:DockerSwarm)--(a:AwsAsg {aws_tag_swarmNodeType:'MANAGER'})  OPTIONAL MATCH (e:AwsElb)--(a)  where e=null return d.tridentClusterId as tridentClusterId,"
						+ " a.aws_account as aws_account, " + " a.aws_region as aws_region, "
						+ " d.tridentOwnerId as tridentOwnerId, "
						+ " a.aws_autoScalingGroupName as aws_autoScalingGroupName,"
						+ " a.aws_tag_swarmNodeType as aws_tag_swarmNodeType")
				.forEach(it -> {
					try {
						maybeCreateElasticLoadBalancer((ObjectNode) it);
					} catch (RuntimeException e) {
						logger.warn("", e);
					}
				});
	}

	protected void maybeCreateElasticLoadBalancer(ObjectNode n) {

		String tridentClusterId = n.path("tridentClusterId").asText();

		logger.info("considering creating ELB for: {}", n);

		if (!n.path("tridentOwnerId").asText()
				.equals(getApplicationContext().getBean(TridentClusterManager.class).getTridentInstallationId())) {
			logger.info("this is not our cluster...leave it alone...");
			return;
		}
		AmazonAutoScaling asgClient = getApplicationContext().getBean(AWSAccountManager.class)
				.newClientBuilder(n.get("aws_account").asText(), AmazonAutoScalingClientBuilder.class)
				.withRegion(n.get("aws_region").asText()).build();
		AWSClusterManager awsClusterManager = getApplicationContext().getBean(AWSClusterManager.class);

		String awsAccount = n.path("aws_account").asText();
		String awsRegion = n.path("aws_region").asText();
		String asgName = n.path("aws_autoScalingGroupName").asText();
		DescribeAutoScalingGroupsResult result = asgClient
				.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest()
						.withAutoScalingGroupNames(n.path("aws_autoScalingGroupName").asText()));
		if (result.getAutoScalingGroups().isEmpty()) {
			logger.info("asg not found: {}", asgName);
			return;
		}
		AutoScalingGroup asg = result.getAutoScalingGroups().get(0);
		List<String> loadBalancerNames = asg.getLoadBalancerNames();
		if (!loadBalancerNames.isEmpty()) {
			// trigger a re-scan so that the graph is established
			awsClusterManager.newScannerBuilder(awsAccount, awsRegion).buildELBScanner()
					.scanLoadBalancerNames(loadBalancerNames.get(0));
			awsClusterManager.newScannerBuilder(awsAccount, awsRegion).buildASGScanner()
					.scanASGNames(asg.getAutoScalingGroupName());
			logger.info("ASG already has an ELB...nothing to do");
			return;
		}

		logger.info("creating ELB for {}", n);

		DescribeLaunchConfigurationsRequest lcRequest = new DescribeLaunchConfigurationsRequest()
				.withLaunchConfigurationNames(asg.getLaunchConfigurationName());
		DescribeLaunchConfigurationsResult lcResult = asgClient.describeLaunchConfigurations(lcRequest);

		Collection<String> subnets = com.google.common.base.Splitter.on(",").omitEmptyStrings()
				.splitToList(asg.getVPCZoneIdentifier());
		Collection<String> securityGroups = lcResult.getLaunchConfigurations().get(0).getSecurityGroups();

		if (getApplicationContext().getBean(AWSClusterManager.class).incrementSwarmCount(tridentClusterId,
				"createElbAttempts") > 5) {
			logger.warn("too many elb creation attempts - " + JsonUtil.prettyFormat(n));
			return;
		}
		logger.info("creating load balancer for swarm manager ASG: " + asg.getAutoScalingGroupName());
		AmazonElasticLoadBalancing elbClient = awsClusterManager.awsAccountManager
				.newClientBuilder(awsAccount, AmazonElasticLoadBalancingClientBuilder.class).withRegion(awsRegion)
				.build();

		CreateLoadBalancerRequest createLoadBalancerRequest = createLoadBalancerRequest(tridentClusterId, subnets,
				securityGroups);

		awsClusterManager.elbDecoratorList.forEach(it -> {
			it.accept(n, createLoadBalancerRequest);
		});
		JsonUtil.logInfo(ManagerELBCreationTask.class, "creating ELB", createLoadBalancerRequest);
		CreateLoadBalancerResult createLoadBlanacerResult = elbClient.createLoadBalancer(createLoadBalancerRequest);

		new LoadBalancerCreatedEvent()
				.withAttribute("aws_loadBalancerName", createLoadBalancerRequest.getLoadBalancerName())
				.withAttribute("aws_region", awsRegion).withAttribute("aws_account", awsAccount)
				.withTridentClusterId(tridentClusterId).send();

		try {
			AttachLoadBalancersRequest attachRequest = new AttachLoadBalancersRequest();
			attachRequest.withLoadBalancerNames(createLoadBalancerRequest.getLoadBalancerName());
			attachRequest.withAutoScalingGroupName(asg.getAutoScalingGroupName());
			logger.info("attaching load balancer ({}) to asg ({})", attachRequest.getLoadBalancerNames().get(0),
					asg.getAutoScalingGroupName());
			asgClient.attachLoadBalancers(attachRequest);
			awsClusterManager.newScannerBuilder(awsAccount, awsRegion).buildELBScanner()
					.scanLoadBalancerNames(createLoadBalancerRequest.getLoadBalancerName());
			
			new LoadBalancerAttachedEvent()
			.withAttribute("aws_loadBalancerName", createLoadBalancerRequest.getLoadBalancerName())
			.withAttribute("aws_region", awsRegion).withAttribute("aws_account", awsAccount)
			.withAttribute("aws_autoScalingGroupName",asg.getAutoScalingGroupName())
			.withTridentClusterId(tridentClusterId).send();

			return;
		} catch (RuntimeException e) {
			// if attachment fails we end up with an ELB hanging around
			elbClient.deleteLoadBalancer(new DeleteLoadBalancerRequest()
					.withLoadBalancerName(createLoadBalancerRequest.getLoadBalancerName()));
		}

		awsClusterManager.newScannerBuilder(awsAccount, awsRegion).buildELBScanner()
				.scanLoadBalancerNames(createLoadBalancerRequest.getLoadBalancerName());

	}

	protected CreateLoadBalancerRequest createLoadBalancerRequest(String tridentClusterId, Collection<String> subnets,
			Collection<String> securityGroups) {

		Preconditions.checkArgument(!Strings.isNullOrEmpty(tridentClusterId), "tridentClusterId not set");
		CreateLoadBalancerRequest clbr = new CreateLoadBalancerRequest();
		clbr.withSecurityGroups(securityGroups);
		clbr.withScheme("internal");
		clbr.withSubnets(subnets);

		com.amazonaws.services.elasticloadbalancing.model.Tag tag = new com.amazonaws.services.elasticloadbalancing.model.Tag()
				.withKey("tridentClusterId").withValue(tridentClusterId);
		com.amazonaws.services.elasticloadbalancing.model.Tag ownerTag = new com.amazonaws.services.elasticloadbalancing.model.Tag()
				.withKey("tridentOwnerId")
				.withValue(getApplicationContext().getBean(TridentClusterManager.class).getTridentInstallationId());

		clbr.withTags(tag);

		// There is an annoying 32-character limit on elb names
		String name = "swarm-manager-" + Long.toHexString(System.currentTimeMillis());
		clbr.setLoadBalancerName(name);

		Listener listener = new Listener("TCP", 2376, 2376);
		clbr.withListeners(listener);
		return clbr;
	}

}
