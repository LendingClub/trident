package org.lendingclub.trident.swarm.aws.task;

import org.lendingclub.trident.cluster.TridentClusterManager;
import org.lendingclub.trident.provision.SwarmTemplateManager;
import org.lendingclub.trident.scheduler.DistributedTask;
import org.lendingclub.trident.swarm.aws.AWSAccountManager;
import org.lendingclub.trident.swarm.aws.AWSClusterManager;
import org.lendingclub.trident.swarm.aws.SwarmASGBuilder;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.CreateOrUpdateTagsRequest;
import com.amazonaws.services.autoscaling.model.Tag;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancing.model.AddTagsRequest;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53ClientBuilder;
import com.amazonaws.services.route53.model.AmazonRoute53Exception;
import com.amazonaws.services.route53.model.Change;
import com.amazonaws.services.route53.model.ChangeAction;
import com.amazonaws.services.route53.model.ChangeBatch;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsResult;
import com.amazonaws.services.route53.model.RRType;
import com.amazonaws.services.route53.model.ResourceRecord;
import com.amazonaws.services.route53.model.ResourceRecordSet;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

public class ManagerDnsRegistrationTask extends DistributedTask {

	Logger logger = LoggerFactory.getLogger(ManagerDnsRegistrationTask.class);

	@Override
	public void run() {

		String cypher = "match (s:DockerSwarm {tridentOwnerId:{tridentOwnerId}})--(a:AwsAsg)--(e:AwsElb) where EXISTS(s.managerDnsName) "
				+ "AND ((NOT exists(s.managerDnsNameRegistred)) OR NOT (s.managerDnsNameRegistered=true)) "
				+ " and a.aws_tag_swarmNodeType=\"MANAGER\" return \n" + "s.tridentClusterId as tridentClusterId,\n"
				+ "e.aws_dnsname as aws_dnsname,\n" + "s.name as tridentClusterName,\n"
				+ "e.aws_account as aws_account,\n" + "e.aws_region as aws_region,\n"
				+ "a.aws_autoScalingGroupName as aws_autoScalingGroupName, \n"
				+ "e.aws_loadBalancerName as aws_loadBalancerName, " + "s.managerDnsName as managerDnsName,"
				+ "s.managerDnsNameRegistered as managerDnsNameRegistered," + "s.templateName as templateName";
		logger.info("dns registration cypher: {}", cypher);
		getNeoRxClient()
				.execCypher(cypher, "tridentOwnerId",
						getApplicationContext().getBean(TridentClusterManager.class).getTridentInstallationId())
				.forEach(it -> {
					try {
						ObjectNode data = (ObjectNode) it;

						// Look for a swarm template and enrich our data set if
						// it is available
						JsonNode n = getApplicationContext().getBean(SwarmTemplateManager.class)
								.getTemplate(it.path("templateName").asText()).orElse(MissingNode.getInstance());
						n.fields().forEachRemaining(entry -> {
							String key = Strings.nullToEmpty(entry.getKey());
							if (key.equals("name")) {
								// do not propagate name
							} else {
								data.set(entry.getKey(), entry.getValue());
							}
						});

						registerDns(data);
					} catch (RuntimeException e) {
						logger.warn("dns registration problem", e);
					}
				});

	}

	protected void registerDns(ObjectNode n) {

		String tridentClusterId = n.path("tridentClusterId").asText();

		String elbDnsName = n.path("aws_dnsname").asText();
		String managerDnsName = n.path("managerDnsName").asText();

		JsonUtil.logInfo(ManagerDnsRegistrationTask.class, "considering dns registration...", n);

		if (Strings.isNullOrEmpty(managerDnsName)) {
			logger.info("managerDnsName is not set...nothing to register");
			return;
		}
		if (Strings.isNullOrEmpty(tridentClusterId)) {
			return;
		}

		managerDnsName = String.format(managerDnsName, n.path("tridentClusterName").asText());
		ChangeResourceRecordSetsRequest dnsRequest = new ChangeResourceRecordSetsRequest();
		ChangeBatch dnsChangeBatch = new ChangeBatch();
		dnsRequest.withChangeBatch(dnsChangeBatch);

		ResourceRecordSet recordSet = new ResourceRecordSet().withType(RRType.CNAME)
				.withName(convertDnsNameToRoute53Format(managerDnsName))
				.withResourceRecords(new ResourceRecord(elbDnsName)).withTTL(300L);
		Change change = new Change().withAction(ChangeAction.UPSERT).withResourceRecordSet(recordSet);
		dnsChangeBatch.getChanges().add(change);

		getTrident().getApplicationContext().getBean(AWSClusterManager.class).getManagerDnsRegistrationInterceptors().getInterceptors()
				.forEach(it -> {
					logger.info("applying {} to dns registration for {}", it, n);
					it.accept(n, dnsRequest);
				});

		if (Strings.isNullOrEmpty(dnsRequest.getHostedZoneId())) {
			logger.info("hostedZoneId not set...cannot register dns");
			return;
		}
		String region = n.path("aws_region").asText();
		String awsAccount = n.path("aws_account").asText();
		String dnsAccount = n.path(AWSClusterManager.AWS_MANAGER_HOSTED_ZONE_ACCOUNT).asText();

		// If a different dns account is not specified, default to use the AWS
		// account that
		// this swarm resides in. For us it happens to be that DNS resides in a
		// completely different account.
		// I suppose we could also consider supporting this as a role to assume.
		// But this is a bit more intuitive for this
		// use-case;
		if (Strings.isNullOrEmpty(dnsAccount)) {
			dnsAccount = awsAccount;
		}
		String elbName = n.path("aws_loadBalancerName").asText();
		JsonUtil.logInfo(getClass(), "Registering dns", dnsRequest);

		String dnsName = dnsRequest.getChangeBatch().getChanges().get(0).getResourceRecordSet().getName();

		try {
			AmazonRoute53 route53Client = getTrident().getApplicationContext().getBean(AWSAccountManager.class)
					.getClient(dnsAccount, AmazonRoute53ClientBuilder.class,region);
			ChangeResourceRecordSetsResult result = route53Client.changeResourceRecordSets(dnsRequest);

			JsonUtil.logInfo(ManagerDnsRegistrationTask.class, "result", result);

			if (!Strings.isNullOrEmpty(dnsName)) {
				// lop off the trailing dot
				if (dnsName.endsWith(".")) {
					dnsName = dnsName.substring(0, dnsName.length() - 1);
				}
				getNeoRxClient().execCypher(
						"match (s:DockerSwarm {tridentClusterId:{id}}) set s.managerDnsName={addr},s.managerDnsNameRegistered=true return s",
						"id", tridentClusterId, "addr", dnsName);
			}

			try {
				// It would be good at this point to tag the ASG

				CreateOrUpdateTagsRequest tr = new CreateOrUpdateTagsRequest();
				tr.withTags(new Tag().withKey("managerDnsName").withResourceType("auto-scaling-group")
						.withPropagateAtLaunch(true).withValue(dnsName)
						.withResourceId(n.path("aws_autoScalingGroupName").asText()));

				AmazonAutoScaling asgClient = getTrident().getApplicationContext().getBean(AWSAccountManager.class)
						.getClient(awsAccount, AmazonAutoScalingClientBuilder.class,region);
				asgClient.createOrUpdateTags(tr);

				AmazonElasticLoadBalancing elbClient = getTrident().getApplicationContext()
						.getBean(AWSAccountManager.class)
						.getClient(awsAccount, AmazonElasticLoadBalancingClientBuilder.class,region);
				com.amazonaws.services.elasticloadbalancing.model.Tag tag = new com.amazonaws.services.elasticloadbalancing.model.Tag()
						.withKey("managerDnsName").withValue(dnsName);
				elbClient.addTags(new AddTagsRequest().withTags(tag).withLoadBalancerNames(elbName));
			} catch (RuntimeException e) {
				// if this fails, a background task will sync this data
				// eventually...no big deal
				logger.info("problem setting tags after DNS registration", e);
			}
		} catch (AmazonRoute53Exception e) {
			logger.warn("could not register dns: {}", e.toString());
		}

	}

	static String convertDnsNameToRoute53Format(String s) {
		if (s == null) {
			return s;
		}

		if (s.endsWith(".")) {
			return s;
		}
		if (s.contains(".")) {
			// FQDN needs to end with a .
			return s + ".";
		} else {
			return s;
		}
	}
}
