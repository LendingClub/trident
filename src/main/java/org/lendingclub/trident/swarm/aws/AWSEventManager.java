package org.lendingclub.trident.swarm.aws;

import java.util.Map;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.reflex.aws.sqs.SQSAdapter;
import org.lendingclub.trident.cluster.TridentClusterManager;
import org.lendingclub.trident.event.EventSystem;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.AmazonAutoScalingException;
import com.amazonaws.services.autoscaling.model.PutNotificationConfigurationRequest;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.GetTopicAttributesRequest;
import com.amazonaws.services.sns.model.GetTopicAttributesResult;
import com.amazonaws.services.sns.model.SetSubscriptionAttributesRequest;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.GetQueueUrlResult;
import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import com.google.common.base.Supplier;
import com.google.common.collect.Maps;

@Component
public  class AWSEventManager {
	@Autowired
	AWSAccountManager awsAccountManager;

	@Autowired
	TridentClusterManager tridentClusterManager;

	@Autowired
	NeoRxClient neo4j;

	Logger logger = LoggerFactory.getLogger(AWSEventManager.class);
	public static final String TOPIC_NAME = "trident-aws-events";
	String topicName = TOPIC_NAME;

	Map<String, SQSAdapter> queueMap = Maps.newConcurrentMap();

	@Autowired
	EventSystem eventSystem;

	public String buildTopicArn(String account, Regions region) {
		return String.format("arn:aws:sns:%s:%s:%s", region.getName(), account, topicName);
	}
	public String buildQueueName() {
		return topicName + "-" + tridentClusterManager.getTridentInstallationId();
	}
	public String buildQueueArn( String account, Regions region) {
		return String.format("arn:aws:sqs:%s:%s:%s",region.getName(),account,buildQueueName());
	}

	public void attachNotificationToAutoScalingGroup(String asgName, String account, Regions region) {
		AmazonAutoScaling client = awsAccountManager.newClientBuilder(account, AmazonAutoScalingClientBuilder.class)
				.withRegion(region).build();

		String accountNumber = awsAccountManager.lookupAccountNumber(account);

		try {
			// this operation is idempotent

			createTridentAwsEventsTopic(account, region);

		} catch (RuntimeException e) {
			logger.info("create topic failed", e);
		}
		String topicArn = buildTopicArn(accountNumber, region);
		logger.info("subscribing asg={} to {}", asgName, topicArn);
		PutNotificationConfigurationRequest request = new PutNotificationConfigurationRequest();
		request.withTopicARN(topicArn);
		request.withAutoScalingGroupName(asgName);
		request.withNotificationTypes("autoscaling:EC2_INSTANCE_LAUNCH", "autoscaling:EC2_INSTANCE_LAUNCH_ERROR",
				"autoscaling:EC2_INSTANCE_TERMINATE", "autoscaling:EC2_INSTANCE_TERMINATE_ERROR",
				"autoscaling:TEST_NOTIFICATION");

		client.putNotificationConfiguration(request);

	}

	public void createAllSnsTopics() {
		awsAccountManager.getSuppliers().entrySet().forEach(it -> {
			it.getValue().getRegions().forEach(region -> {
				String account = null; // separate variable so that logging
										// statement below is less prone to NPEs
										// as we dereference
				try {
					account = it.getValue().getAccount().orElse(null);
					createTridentAwsEventsTopic(it.getValue().getAccount().get(), region);
				} catch (RuntimeException e) {

					logger.info("failed to create {} topic for account={} region={}", topicName, account,
							region.getName());
				}
			});
		});
	}

	String createTridentAwsEventsTopic(String account, Regions region) {
		logger.info("ensuring that sns topic {} exists in account={} region={}", topicName, account, region.getName());
		AmazonSNS client = awsAccountManager.newClientBuilder(account, AmazonSNSClientBuilder.class).withRegion(region)
				.build();
		CreateTopicResult result = client.createTopic(topicName);
		return result.getTopicArn();
	}

	Map<String, String> getTopicAttributes(String account, Regions region) {

		AmazonSNS client = awsAccountManager.newClientBuilder(account, AmazonSNSClientBuilder.class).withRegion(region)
				.build();

		GetTopicAttributesRequest gta = new GetTopicAttributesRequest();
		String arn = buildTopicArn(account, region);
		gta.withTopicArn(arn);

		GetTopicAttributesResult result = client.getTopicAttributes(gta);
		return result.getAttributes();
	}


	public void attachNotificationToAllTridentAutoScalingGroups() {
		neo4j.execCypher("match (a:AwsAsg) where exists(a.aws_tag_tridentClusterId) return a").forEach(it -> {
			String asgName = it.path("aws_autoScalingGroupName").asText();
			String region = it.path("aws_region").asText();
			String account = it.path("aws_account").asText();
			try {

				logger.info("ensuring that ASG notification are set for asg={} account={} region={}", asgName, account,
						region);
				attachNotificationToAutoScalingGroup(asgName, account, Regions.fromName(region));
			} catch (AmazonAutoScalingException e) {
				// This can get a bit voluminous if we end up with orphaned ASG
				// records in neo4j
				logger.warn("failed to attach notification to asg={} account={} region={} - {}", asgName, account,
						region, e.toString());

			} catch (RuntimeException e) {
				logger.warn("problem attaching notifications to ASG", e);
			}
		});

	}

	protected void startAllQueueConsumers() {
		awsAccountManager.getSuppliers().entrySet().forEach(it -> {
			it.getValue().getRegions().forEach(region -> {
				String account = null;
				try {
					account = it.getValue().getAccount().orElse(null);
					logger.info("creating queue+consumer for account={} region={}",account,region.getName());
					createQueueConsumer(account, region);
				} catch (RuntimeException e) {

					logger.info("failed to start queue consumer for account=" + account + " region=" + region.getName(),
							e);
				}
			});
		});
	}

	public void subscribeQueueToTopic(String queueArn, String topicArn, String account, Regions region) {
		
		
		AmazonSNS client = awsAccountManager.newClientBuilder(account, AmazonSNSClientBuilder.class).withRegion(region)
				.build();
		SubscribeRequest subscribeRequest = new SubscribeRequest();
		
		subscribeRequest.withProtocol("sqs").withTopicArn(topicArn).withEndpoint(queueArn);
		
		SubscribeResult result = client.subscribe(subscribeRequest);
		SetSubscriptionAttributesRequest subscriptionAttributesRequest = new SetSubscriptionAttributesRequest();
		subscriptionAttributesRequest.withSubscriptionArn(result.getSubscriptionArn()).withAttributeName("RawMessageDelivery").withAttributeValue("true");
		client.setSubscriptionAttributes(subscriptionAttributesRequest);
	}
	protected void createQueueConsumer(String account, Regions region) {
		AmazonSQSClient client = (AmazonSQSClient) awsAccountManager
				.newClientBuilder(account, AmazonSQSClientBuilder.class).withRegion(region).build();
		String queueUrl = null;
		String queueName = buildQueueName();
		String queueArn = buildQueueArn(account, region);
		boolean createQueue = false;
		try {
			GetQueueUrlResult result = client.getQueueUrl(queueName);
			queueUrl = result.getQueueUrl();
		} catch (QueueDoesNotExistException e) {
			createQueue = true;
		}

		if (createQueue) {
			CreateQueueRequest request = new CreateQueueRequest().withQueueName(queueName);		
			logger.info("ensuring SQS queue exists: name={} account={} region={}",queueName,account,region);
			CreateQueueResult createQueueResult = client.createQueue(request);
			logger.info("queue response: {}", JsonUtil.prettyFormat(createQueueResult));
			queueUrl = createQueueResult.getQueueUrl();
		}
		
		String topicArn = null;
		try {
			topicArn = buildTopicArn(account,region);
			subscribeQueueToTopic(queueArn, topicArn, account, region);
		} catch (RuntimeException e) {
			logger.warn("problem subscribing {} to {} - {}",queueUrl,topicArn,e.toString());
		
		}
		synchronized (this) {
			SQSAdapter adapter = queueMap.get(queueUrl);
			if (adapter != null) {
				logger.info("SQSAdapter already set up for {}", queueUrl);
				return;
			}
			Supplier<Boolean> liveCheck = new Supplier<Boolean>() {

				@Override
				public Boolean get() {
					return tridentClusterManager.isEligibleLeader();
				}
			};
			adapter = new SQSAdapter().withQueueUrl(queueUrl).withSQSClient(client)
					.withEventBus(eventSystem.getEventBus());

			adapter.setRunStateSupplier(liveCheck); // Do not consume messages
													// if we are not an eligible
													// leader of our Trident
													// cluster
			logger.info("starting {}", adapter);

			adapter.start(); // will throw an exception if we cannot access the
								// queue
			queueMap.put(queueUrl, adapter);
		}

	}

	
}
