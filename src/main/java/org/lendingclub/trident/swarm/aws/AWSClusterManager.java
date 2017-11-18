package org.lendingclub.trident.swarm.aws;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;

import org.lendingclub.mercator.aws.ASGScanner;
import org.lendingclub.mercator.aws.AWSScannerBuilder;
import org.lendingclub.mercator.aws.EC2InstanceScanner;
import org.lendingclub.mercator.aws.LaunchConfigScanner;
import org.lendingclub.mercator.aws.SubnetScanner;
import org.lendingclub.mercator.aws.VPCScanner;
import org.lendingclub.mercator.core.Projector;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.Trident;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.cluster.TridentClusterManager;
import org.lendingclub.trident.crypto.CertificateAuthorityManager;
import org.lendingclub.trident.extension.BasicInterceptorGroup;
import org.lendingclub.trident.extension.InterceptorGroup;
import org.lendingclub.trident.provision.SwarmTemplateManager;
import org.lendingclub.trident.scheduler.DistributedTaskScheduler;
import org.lendingclub.trident.swarm.Swarm;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.swarm.SwarmNodeType;
import org.lendingclub.trident.swarm.aws.event.ClusterDestroyedEvent;
import org.lendingclub.trident.swarm.aws.task.AWSScannerTask;
import org.lendingclub.trident.util.JsonUtil;
import org.lendingclub.trident.util.TridentStartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.BlockDeviceMapping;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.DetachLoadBalancersRequest;
import com.amazonaws.services.autoscaling.model.Ebs;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeTagsRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeTagsResult;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;

public class AWSClusterManager implements TridentStartupListener, AWSInterceptors {

	@Autowired
	NeoRxClient neo4j;

	@Inject
	Projector projector;

	@Inject
	AWSAccountManager awsAccountManager;

	@Inject
	CertificateAuthorityManager certificateAuthorityManager;

	InterceptorGroup<ManagerLoadBalancerInterceptor> elbInterceptors = new BasicInterceptorGroup<>();
	InterceptorGroup<LaunchConfigInterceptor> launchConfigInterceptors = new BasicInterceptorGroup<>();
	
	InterceptorGroup<AutoScalingGroupInterceptor> asgDecorator = new BasicInterceptorGroup<>();
	InterceptorGroup<ManagerDnsRegistrationInterceptor> dnsDecoratorList = new BasicInterceptorGroup<>();

	@Autowired
	DistributedTaskScheduler taskScheduler;

	@Autowired
	SwarmTemplateManager swarmTemplateManager;
	
	@Autowired
	AWSMetadataSync metadataSync;

	@Autowired
	TridentClusterManager tridentClusterManager;

	@Autowired
	SwarmClusterManager swarmClusterMangaer;
	
	static Logger logger = LoggerFactory.getLogger(AWSClusterManager.class);

	class SwarmTemplateASGDecorator implements AutoScalingGroupInterceptor {

		@Override
		public void accept(JsonNode t, CreateAutoScalingGroupRequest u) {
			logger.info("invoke: {}", this);

			JsonNode template = loadTemplate(t);
			
			if (!template.isObject()) {
				return;
			}

			String zoneIdentifier = null;
			if (isManager(t)) {
				zoneIdentifier = template.path(AWS_MANAGER_SUBNETS).asText();

			} else {
				zoneIdentifier = template.path(AWS_WORKER_SUBNETS).asText();
			}
		
			if (!Strings.isNullOrEmpty(zoneIdentifier)) {
				u.withVPCZoneIdentifier(zoneIdentifier);
			}
			template.fields().forEachRemaining(it -> {
				if (t instanceof ObjectNode) {
					ObjectNode.class.cast(t).set(it.getKey(), it.getValue());
				}
			});
		}

	}

	private boolean isManager(JsonNode n) {
		return n.path(SWARM_NODE_TYPE).asText().equalsIgnoreCase("manager");
	}

	public static final String SWARM_NODE_TYPE="swarmNodeType";
	public static final String MANAGER_DNS_NAME="managerDnsName";
	
	public static final String MANAGER_SUBJECT_ALTERNATIVE_NAMES="managerSubjectAlternativeNames";
	public static final String TRIDENT_CLUSTER_ID="tridentClusterId";
	public static final String AWS_ACCOUNT="awsAccount";
	public static final String AWS_REGION="awsRegion";
	public static final String AWS_MANAGER_INSTANCE_TYPE="awsManagerInstanceType";
	public static final String AWS_MANAGER_IMAGE_ID="awsManagerImageId";
	public static final String AWS_MANAGER_INSTANCE_PROFILE="awsManagerInstanceProfile";
	public static final String AWS_MANAGER_SECURITY_GROUPS="awsManagerSecurityGroups";
	public static final String AWS_MANAGER_CLOUD_INIT="awsManagerCloudInit";
	public static final String AWS_MANAGER_SUBNETS="awsManagerSubnets";
	public static final String AWS_MANAGER_HOSTED_ZONE="awsManagerHostedZone";
	public static final String AWS_MANAGER_HOSTED_ZONE_ACCOUNT="awsManagerHostedZoneAccount";
	
	public static final String AWS_WORKER_INSTANCE_TYPE="awsWorkerInstanceType";
	public static final String AWS_WORKER_IMAGE_ID="awsWorkerImageId";
	public static final String AWS_WORKER_INSTANCE_PROFILE="awsWorkerInstanceProfile";
	public static final String AWS_WORKER_SECURITY_GROUPS="awsWorkerSecurityGroups";
	public static final String AWS_WORKER_CLOUD_INIT="awsWorkerCloudInit";
	public static final String AWS_WORKER_SUBNETS="awsWorkerSubnets";
	
	
	class SwarmTemplateLaunchConfigDecorator implements LaunchConfigInterceptor {
		@Override
		public void accept(JsonNode t, CreateLaunchConfigurationRequest u) {
			logger.info("invoke: {}", this);
			JsonNode template = loadTemplate(t);
			if (!template.isObject()) {
				return;
			}
			
			String instanceType = null;
			String imageId = null;
			String instanceProfile = null;
			String securityGroups = null;
			String cloudInit = null;
			if (isManager(t)) {
				instanceType = template.path(AWS_MANAGER_INSTANCE_TYPE).asText();
				imageId = template.path(AWS_MANAGER_IMAGE_ID).asText();
				instanceProfile = template.path(AWS_MANAGER_INSTANCE_PROFILE).asText();
				securityGroups = template.path(AWS_MANAGER_SECURITY_GROUPS).asText();
				cloudInit = template.path(AWS_MANAGER_CLOUD_INIT).asText();
			} else {
				instanceType = template.path(AWS_WORKER_INSTANCE_TYPE).asText();
				imageId = template.path(AWS_WORKER_IMAGE_ID).asText();
				instanceProfile = template.path(AWS_WORKER_INSTANCE_PROFILE).asText();
				securityGroups = template.path(AWS_WORKER_SECURITY_GROUPS).asText();
				cloudInit = template.path(AWS_WORKER_CLOUD_INIT).asText();
			}

			if (!Strings.isNullOrEmpty(cloudInit)) {

			
				u.withUserData(BaseEncoding.base64().encode(cloudInit.getBytes()));

			}
			if (!Strings.isNullOrEmpty(instanceType)) {
				u.withInstanceType(instanceType);
			}
			if (!Strings.isNullOrEmpty(imageId)) {
				u.withImageId(imageId);
			}
			if (!Strings.isNullOrEmpty(instanceProfile)) {
				u.withIamInstanceProfile(instanceProfile);
			}
			if (!Strings.isNullOrEmpty(securityGroups)) {
				u.withSecurityGroups(Splitter.on(",").omitEmptyStrings().trimResults().splitToList(securityGroups));
			}
			copyTemplateIntoContext(template, t);

		}

	}

	void copyTemplateIntoContext(String templateName, ObjectNode target) {
		Optional<JsonNode> template = swarmTemplateManager.getTemplate(templateName);
		if (!template.isPresent()) {
			throw new TridentException("swarm template not found: "+templateName);
		}
		copyTemplateIntoContext(template.get(), target);
	}
	void copyTemplateIntoContext(JsonNode template, JsonNode ctx) {
		
		if (ctx==null || !ctx.isObject()) {
			return;
		}
		ObjectNode context = (ObjectNode) ctx;
		template.fields().forEachRemaining(it -> {
			String key = it.getKey();
			if (key.equals("name")) {
				// do not let "name" be overwritten since this will copy the name to the template into the name of the swarm
			}
			else if (key.equals("id")) {
				// do not copy "id" into context
			}
			else {
				logger.info("copying {} from template to context",key);
				context.set(key, it.getValue());
			}
		});
	}
	class SwarmTemplateManagerDnsRegistrationDecorator implements ManagerDnsRegistrationInterceptor {

		@Override
		public void accept(JsonNode context, ChangeResourceRecordSetsRequest u) {
			logger.info("invoking {}", this);
			JsonNode template = loadTemplate(context);
			if (!template.isObject()) {
				return;
			}
			
			String hostedZone = template.path(AWS_MANAGER_HOSTED_ZONE).asText();
			if (!Strings.isNullOrEmpty(hostedZone)) {
				u.setHostedZoneId(hostedZone);
			}
		
			copyTemplateIntoContext(template, context);
		}

	}

	class SwarmTemplateLoadBalancerConfigDecorator implements ManagerLoadBalancerInterceptor {
		@Override
		public void accept(JsonNode t, CreateLoadBalancerRequest u) {
			logger.info("invoke: {}", this);
			JsonNode template = loadTemplate(t);
			if (!template.isObject()) {
				return;
			}
			copyTemplateIntoContext(template, t);
		}
	}

	public AWSClusterManager() {
		dnsDecoratorList.addInterceptor(new SwarmTemplateManagerDnsRegistrationDecorator());
		asgDecorator.addInterceptor(new SwarmTemplateASGDecorator());
		elbInterceptors.addInterceptor(new SwarmTemplateLoadBalancerConfigDecorator());
		launchConfigInterceptors.addInterceptor(new SwarmTemplateLaunchConfigDecorator());
	}

	public SwarmASGBuilder newManagerASGBuilder(String id) {
		return newManagerASGBuilder(JsonUtil.createObjectNode().put("tridentClusterId", id));
	}

	public SwarmASGBuilder newASGBuilder(ObjectNode data, SwarmNodeType nodeType) {
		String clusterId = data.path("tridentClusterId").asText(data.path("id").asText());
		checkClusterId(clusterId);
		SwarmASGBuilder b = new SwarmASGBuilder(data).withSwarmNodeType(nodeType)
				.withTridentClusterId(clusterId).withAccountManager(awsAccountManager).withAWSClusterManager(this);
		JsonNode n = neo4j.execCypher("match (s:DockerSwarm {tridentClusterId:{id}}) return s", "id", clusterId)
		.blockingFirst();
		String name = n.path("name").asText();
		String template = n.path("templateName").asText();
		b.withSwarmName(name).withTemplate(template);
		return b;
	}
	public SwarmASGBuilder newManagerASGBuilder(ObjectNode data) {
		return newASGBuilder(data,SwarmNodeType.MANAGER);
	}

	public SwarmASGBuilder newWorkerASGBuilder(ObjectNode data) {
		return newASGBuilder(data,SwarmNodeType.WORKER);
	}
	
	public long getSwarmCount(String id, String property) {
		String c = "match (a:DockerSwarm {tridentClusterId:{id}}) return a";
		return neo4j.execCypher(c, "id", id).blockingFirst(MissingNode.getInstance()).path(property).asLong(0);

	}

	public long incrementSwarmCount(String id, String property) {
		String c = "match (a:DockerSwarm {tridentClusterId:{id}}) return a";
		long val = neo4j.execCypher(c, "id", id).blockingFirst(MissingNode.getInstance()).path(property).asLong(-1);
		if (val < 0) {
			neo4j.execCypher("match (a:DockerSwarm {tridentClusterId:{id}}) set a." + property + "=1 return a", "id",
					id);
			return 1;
		} else {
			return neo4j.execCypher(
					"match (a:DockerSwarm {tridentClusterId:{id}}) set a." + property + "=a." + property + " return a",
					"id", id).blockingFirst().path(property).asLong();
		}
	}

	public SwarmASGBuilder newWorkerASGBuilder(String id) {
		return newWorkerASGBuilder(JsonUtil.createObjectNode().put("tridentClusterId", id));
	}



	private void checkClusterId(String clusterId) {
		if (Strings.isNullOrEmpty(clusterId)) {
			throw new TridentException("cluster id not specified");
		}

		int size = neo4j.execCypher("match (c:DockerSwarm {tridentClusterId:{id}}) return c", "id", clusterId).toList()
				.blockingGet().size();
		if (size < 1) {
			throw new TridentException("cluster not found: " + clusterId);
		}
	}

	public AWSScannerBuilder newScannerBuilder(String name, String region) {
		ClientConfiguration cc = awsAccountManager.getClientConfiguration(name);
		return projector.createBuilder(AWSScannerBuilder.class).withClientConfiguration(cc).withRegion(region)
				.withCredentials(awsAccountManager.getCredentialsProvider(name));

	}

	public void scanRegion(String accountName, String region) {

		newScannerBuilder(accountName, region).buildAccountScanner().scan();
		newScannerBuilder(accountName, region).build(VPCScanner.class).scan();
		newScannerBuilder(accountName, region).build(SubnetScanner.class).scan();
		newScannerBuilder(accountName, region).build(EC2InstanceScanner.class).scan();
		newScannerBuilder(accountName, region).build(LaunchConfigScanner.class).scan();
		newScannerBuilder(accountName, region).build(ASGScanner.class).scan();

		metadataSync.createMissingDockerSwarmNodes();

	}

	public ASGEditor createSwarmManagerASGEditor(String id) {
		return createSwarmASGEditor(id,"MANAGER");
	}
	public ASGEditor createSwarmWorkerASGEditor(String id) {
		return createSwarmASGEditor(id,"WORKER");
	}
	private ASGEditor createSwarmASGEditor(String id, String nodeType) {
		Swarm swarm = swarmClusterMangaer.getSwarm(id);
		String swarmClusterId = swarm.getSwarmClusterId().get();
		JsonNode n = neo4j.execCypher("match (a:DockerSwarm {swarmClusterId:{swarmClusterId}})--(x:AwsAsg {aws_tag_swarmNodeType:{swarmNodeType}}) return x limit 1","swarmClusterId",swarmClusterId,"swarmNodeType",nodeType).blockingSingle();
		
		return new ASGEditor().withAutoScalingGroupName(n.path("aws_autoScalingGroupName").asText()).withRegion(Regions.fromName(n.path("aws_region").asText())).withAccountName(n.path("aws_account").asText());
	}
	public void scanAll() {
		awsAccountManager.getSuppliers().forEach((name, supplier) -> {

			if (!name.equals("default")) {
				supplier.getRegions().forEach(it -> {
					scanRegion(name, it.getName());
				});
			}

		});
	}

	@Override
	public void onStart(org.springframework.context.ApplicationContext context) {
		taskScheduler.submitTask(AWSScannerTask.class);
	}

	public InterceptorGroup<LaunchConfigInterceptor> getLaunchConfigInterceptors() {
		return launchConfigInterceptors;
	}

	public InterceptorGroup<AutoScalingGroupInterceptor> getAutoScalingGroupInterceptors() {
		return asgDecorator;
	}

	public InterceptorGroup<ManagerDnsRegistrationInterceptor> getManagerDnsRegistrationInterceptors() {
		return this.dnsDecoratorList;
	}

	public InterceptorGroup<ManagerLoadBalancerInterceptor> getManagerLoadBalancerInterceptors() {
		return elbInterceptors;
	}


	public AWSAccountManager getAccountManager() {
		return awsAccountManager;
	}

	public AWSMetadataSync getMetadataSync() {
		return metadataSync;
	}

	protected void destroyLoadBalancer(String tridentClusterId, String elbName, String account, String region) {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(tridentClusterId));
		Preconditions.checkArgument(!Strings.isNullOrEmpty(elbName));
		Preconditions.checkArgument(!Strings.isNullOrEmpty(account));
		Preconditions.checkArgument(!Strings.isNullOrEmpty(region));

		AmazonElasticLoadBalancing client = awsAccountManager
				.getClient(account, AmazonElasticLoadBalancingClientBuilder.class,region);

		DescribeTagsRequest request = new DescribeTagsRequest();
		request.withLoadBalancerNames(elbName);
		DescribeTagsResult result = client.describeTags(request);

		AtomicBoolean clusterVerified = new AtomicBoolean(false);
		result.getTagDescriptions().forEach(td -> {
			td.getTags().forEach(tag -> {
				String key = Strings.nullToEmpty(tag.getKey());
				String val = Strings.nullToEmpty(tag.getValue());
				if (key.equals("tridentClusterId") && val.equals(tridentClusterId)) {
					clusterVerified.set(true);
				}
			});
		});

		if (clusterVerified.get()) {

			DeleteLoadBalancerRequest deleteRequest = new DeleteLoadBalancerRequest();
			deleteRequest.withLoadBalancerName(elbName);
			logger.info("deleting elb: {}", JsonUtil.prettyFormat(deleteRequest));
			client.deleteLoadBalancer(deleteRequest);
		} else {
			logger.warn("unable to verify that elb={} belonged to tridentClusterId={} in account={} region={}", elbName,
					tridentClusterId, account, region);
		}
	}

	protected void detachAndDestroyAsg(String tridentClusterId, String asgName, String account, String region) {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(tridentClusterId));
		Preconditions.checkArgument(!Strings.isNullOrEmpty(asgName));
		Preconditions.checkArgument(!Strings.isNullOrEmpty(account));
		Preconditions.checkArgument(!Strings.isNullOrEmpty(region));
		// A safety check here would be to look at the actual auto-scaling group
		// in AWS
		// and verify that the tag matches the intent. This prevents some
		// screw-up within neo4j from nuking the wrong ASG.

		// We will probably want other safety checking as well.

		AmazonAutoScaling client = awsAccountManager.getClient(account, AmazonAutoScalingClientBuilder.class,region);
		
	
		DescribeAutoScalingGroupsRequest describeRequest = new DescribeAutoScalingGroupsRequest()
					.withAutoScalingGroupNames(asgName);
		DescribeAutoScalingGroupsResult result = client.describeAutoScalingGroups(describeRequest);
		result.getAutoScalingGroups().forEach(asg -> {
			AtomicBoolean ownerVerified = new AtomicBoolean(false);
			AtomicBoolean clusterVerified = new AtomicBoolean(false);
			
			asg.getTags().forEach(tag -> {
				String key = Strings.nullToEmpty(tag.getKey());
				String val = Strings.nullToEmpty(tag.getValue());
				if (key.equals("tridentClusterId") && val.equals(tridentClusterId)) {
					clusterVerified.set(true);
				}
				if (key.equals("tridentOwnerId") && val.equals(tridentClusterManager.getTridentInstallationId())) {
					ownerVerified.set(true);
				}
			});
			
			if (clusterVerified.get() && ownerVerified.get()) { 
				//detach ELBs from ASG, but if this fails still delete ASG
				List<String> attachedElbs = asg.getLoadBalancerNames();
				try { 
					client.detachLoadBalancers(
							new DetachLoadBalancersRequest()
								.withAutoScalingGroupName(asgName)
								.withLoadBalancerNames(attachedElbs));
				} catch (RuntimeException e) { 
					logger.warn("unable to detach asg={} from elbs={} as part of tridentClusterId={} account={} region={} teardown",
							asgName, 
							attachedElbs, 
							tridentClusterId, 
							account, 
							region);
				}
				//delete ASG
				DeleteAutoScalingGroupRequest request = new DeleteAutoScalingGroupRequest()
						.withForceDelete(true)
						.withAutoScalingGroupName(asgName);
				client.deleteAutoScalingGroup(request);
			} else { 
				logger.warn("unable to verify that asg={} belonged to tridentClusterId={} in account={} region={}; will not delete ASG", 
						asgName, tridentClusterId, account, region);
			}
		});
	}

	public void destroyCluster(String tridentClusterId) {
		// What we do here is:
		// 1) Find all the corresponding entities in neo4j
		// 2) destroy those entities in AWS
		// 3) delete those entities in neo4j
		// 4) delete the swarm

		// There is a chance that we won't have a complete view in neo4j before
		// we start, but dangling entities
		// should get cleaned up eventually.
		
		
		// First, detach and delete ASGs
		neo4j.execCypher("match (a:AwsAsg) where a.aws_tag_tridentClusterId={tridentClusterId} return a",
				"tridentClusterId", tridentClusterId).forEach(asg -> {
					try { 
						detachAndDestroyAsg(tridentClusterId, asg.path("aws_autoScalingGroupName").asText(),
								asg.path("aws_account").asText(), asg.path("aws_region").asText());
					} catch (RuntimeException e) {
						logger.warn("could not delete asg={} account={} region={} of clusterId={}", 
								asg.path("aws_autoScalingGroupName").asText(), 
								asg.path("aws_account").asText(), 
								asg.path("aws_region").asText(), 
								tridentClusterId, e);
					}
				});

		// Second, delete ELBs
		neo4j.execCypher("match (a:AwsElb) where a.aws_tag_tridentClusterId={tridentClusterId} return a",
				"tridentClusterId", tridentClusterId).forEach(elb -> {
					try {
						destroyLoadBalancer(tridentClusterId, elb.path("aws_loadBalancerName").asText(),
								elb.path("aws_account").asText(), elb.path("aws_region").asText());
					} catch (RuntimeException e) { 
						logger.warn("could not delete elb={} account={} region={} of cluster={}",
								elb.path("aws_loadBalancerName").asText(),
								elb.path("aws_account").asText(),
								elb.path("aws_region").asText(), 
								tridentClusterId, e);
					}
				});

		// Delete the DockerSwarm from Neo4j
		neo4j.execCypher("match (a:DockerSwarm {tridentClusterId:{id}}) detach delete a", "id", tridentClusterId);
		
		// Send ClusterDestroyedEvent after successful cluster deletion
		new ClusterDestroyedEvent().withTridentClusterId(tridentClusterId).send();
	}

	static Optional<Ebs> getSecondaryEbs(CreateLaunchConfigurationRequest request) {
		AtomicReference<Ebs> rval = new AtomicReference<Ebs>(null);
		List<BlockDeviceMapping> bdm = request.getBlockDeviceMappings();
		if (bdm != null) {
			bdm.forEach(it -> {
				Ebs ebs = it.getEbs();
				if (ebs != null) {
					rval.set(ebs);
				}
			});
		}
		return java.util.Optional.ofNullable(rval.get());
	}

	static JsonNode loadTemplate(JsonNode n) {
		String template = n.path("templateName").asText();
		if (Strings.isNullOrEmpty(template)) {
			logger.info("no template specified");
			return MissingNode.getInstance();
		}

		SwarmTemplateManager stm = Trident.getApplicationContext().getBean(SwarmTemplateManager.class);
		return stm.getTemplate(template).orElse(MissingNode.getInstance());

	}
}
