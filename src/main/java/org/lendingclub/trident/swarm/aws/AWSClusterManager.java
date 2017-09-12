package org.lendingclub.trident.swarm.aws;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import org.lendingclub.mercator.aws.ASGScanner;
import org.lendingclub.mercator.aws.AWSScannerBuilder;
import org.lendingclub.mercator.aws.EC2InstanceScanner;
import org.lendingclub.mercator.aws.SubnetScanner;
import org.lendingclub.mercator.aws.VPCScanner;
import org.lendingclub.mercator.core.Projector;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.SwarmNodeType;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.cluster.TridentClusterManager;
import org.lendingclub.trident.crypto.CertificateAuthorityManager;
import org.lendingclub.trident.scheduler.DistributedTaskScheduler;
import org.lendingclub.trident.swarm.aws.event.ClusterDestroyedEvent;
import org.lendingclub.trident.util.JsonUtil;
import org.lendingclub.trident.util.TridentStartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancing.model.DescribeTagsRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeTagsResult;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;

public class AWSClusterManager implements TridentStartupListener {

	@Autowired
	NeoRxClient neo4j;

	@Inject
	Projector projector;

	@Inject
	AWSAccountManager awsAccountManager;

	@Inject 
	CertificateAuthorityManager certificateAuthorityManager;
	
	List<ManagerLoadBalancerDecorator> elbDecoratorList = com.google.common.collect.Lists.newCopyOnWriteArrayList();
	List<LaunchConfigDecorator> launchConfigDecoratorList = Lists.newCopyOnWriteArrayList();
	List<AutoScalingGroupDecorator> asgDecorator = Lists.newCopyOnWriteArrayList();
	List<ManagerDnsRegistrationDecorator> dnsDecoratorList = Lists.newCopyOnWriteArrayList();
	
	
	@Autowired 
	DistributedTaskScheduler taskScheduler;
	
	@Autowired
	AWSMetadataSync metadataSync;
	
	@Autowired
	TridentClusterManager tridentClusterManager;
	
	Logger logger = LoggerFactory.getLogger(AWSClusterManager.class);
	
	class StandardASGDecorator implements LaunchConfigDecorator {

		

		@Override
		public void accept(ObjectNode context, CreateLaunchConfigurationRequest request) {
			JsonNode data = context;
			
			String id = data.path("id").asText();
			String instanceType = data.path("instanceType").asText();
			List<String> subnets = AWSController.extractList(data.path("subnets"));
			String imageId = data.path("imageId").asText();
			String region = data.path("region").asText();
			String accountName = data.path("accountName").asText();
			String cloudInit = data.path("cloudInit").asText();
			String instanceRole = data.path("instanceRole").asText();
			List<String> securityGroups = AWSController.extractList(data.path("securityGroups"));

			
			if (!Strings.isNullOrEmpty(cloudInit)) {
				request.withUserData(BaseEncoding.base64().encode(cloudInit.getBytes()));
			}
			if (!Strings.isNullOrEmpty(imageId)) {
				request.withImageId(imageId);
			}
		
		//	if (!subnets.isEmpty()) {
		//		request.with
		//		builder = request.with(subnets.toArray(new String[0]));
		//	}
			if (!subnets.isEmpty()) {
				request.withSecurityGroups(securityGroups.toArray(new String[0]));
			}
			if (Strings.isNullOrEmpty(instanceType)) {
				request.withInstanceType("t2.medium");
			}
			else {
				request.withInstanceType(instanceType);
			}
			
			if (!Strings.isNullOrEmpty(instanceRole)) {
				request.withIamInstanceProfile(instanceRole);
			}
			
		}

		

	}

	public AWSClusterManager() {
		launchConfigDecoratorList.add(new StandardASGDecorator());
		
	}



	public SwarmASGBuilder newManagerASGBuilder(String id) {
		return newManagerASGBuilder(JsonUtil.createObjectNode().put("tridentClusterId",id));
	}
	public SwarmASGBuilder newManagerASGBuilder(ObjectNode data) {
		String clusterId = data.path("tridentClusterId").asText(data.path("id").asText());
		checkClusterId(clusterId);
		SwarmASGBuilder b = new SwarmASGBuilder(data).withSwarmNodeType(SwarmNodeType.MANAGER)
				.withTridentClusterId(clusterId).withAccountManager(awsAccountManager).withAWSClusterManager(this);

		String name = neo4j.execCypher("match (s:DockerSwarm {tridentClusterId:{id}}) return s","id",clusterId).blockingFirst().path("name").asText();
		b.withSwarmName(name);
		return b;
	}
	public long getSwarmCount(String id, String property) {
		String c = "match (a:DockerSwarm {tridentClusterId:{id}}) return a";
		return neo4j.execCypher(c,"id",id).blockingFirst(MissingNode.getInstance()).path(property).asLong(0);
		
	}
	public long incrementSwarmCount(String id, String property) {
		String c = "match (a:DockerSwarm {tridentClusterId:{id}}) return a";
		long val = neo4j.execCypher(c,"id",id).blockingFirst(MissingNode.getInstance()).path(property).asLong(-1);
		if (val<0) {
			neo4j.execCypher("match (a:DockerSwarm {tridentClusterId:{id}}) set a."+property+"=1 return a","id",id);
			return 1;
		}
		else {
			return neo4j.execCypher("match (a:DockerSwarm {tridentClusterId:{id}}) set a."+property+"=a."+property+" return a","id",id).blockingFirst().path(property).asLong();
		}
	}
	public SwarmASGBuilder newWorkerASGBuilder(String id) {
		return newWorkerASGBuilder(JsonUtil.createObjectNode().put("tridentClusterId",id));
	}
	public SwarmASGBuilder newWorkerASGBuilder(ObjectNode data) {
		String clusterId = data.path("tridentClusterId").asText(data.path("id").asText());
		checkClusterId(clusterId);
		SwarmASGBuilder b = new SwarmASGBuilder(data).withSwarmNodeType(SwarmNodeType.WORKER)
				.withTridentClusterId(clusterId).withAccountManager(awsAccountManager).withAWSClusterManager(this);
		String name = neo4j.execCypher("match (s:DockerSwarm {tridentClusterId:{id}}) return s","id",clusterId).blockingFirst().path("name").asText();
		b.withSwarmName(name);
		return b;
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
		newScannerBuilder(accountName, region).build(ASGScanner.class).scan();

		metadataSync.createMissingDockerSwarmNodes();

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

		
		taskScheduler.submitLocal(AWSScannerTask.class);
		
		
	}
	
	public List<LaunchConfigDecorator> getLaunchConfigDecorators() {
		return launchConfigDecoratorList;
				
	}
	public void addLaunchConfigDecorator(LaunchConfigDecorator d) {
		this.launchConfigDecoratorList.add(d);
	}
	public List<AutoScalingGroupDecorator> getAutoScalingGroupDecorators() {
		return asgDecorator;
				
	}
	public void addAutoScalingGroupDecorator(AutoScalingGroupDecorator d) {
		this.asgDecorator.add(d);
	}
	
	public void addManagerDnsRegistrationDecorator(ManagerDnsRegistrationDecorator d) {
		this.dnsDecoratorList.add(d);
	}
	public List<ManagerDnsRegistrationDecorator> getDnsRegistrationDecorators() {
		return dnsDecoratorList;
				
	}
	public void addElasticLoadBalancerDecorator(ManagerLoadBalancerDecorator d) {
		this.elbDecoratorList.add(d);
	}
	public List<ManagerLoadBalancerDecorator> getElasticLoadBlanacerDecorators() {
		return elbDecoratorList;
				
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
		
		AmazonElasticLoadBalancing client = awsAccountManager.newClientBuilder(account, AmazonElasticLoadBalancingClientBuilder.class).withRegion(region).build();
		
		DescribeTagsRequest request = new DescribeTagsRequest();
		request.withLoadBalancerNames(elbName);
		DescribeTagsResult result = client.describeTags(request);
		
		AtomicBoolean clusterVerified = new AtomicBoolean(false);
		result.getTagDescriptions().forEach(td->{
			td.getTags().forEach(tag->{
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
			logger.info("deleting elb: {}",JsonUtil.prettyFormat(deleteRequest));
			client.deleteLoadBalancer(deleteRequest);
		}
		else {
			logger.warn("unable to verify that elb={} belonged to tridentClusterId={} in account={} region={}",elbName,tridentClusterId,account,region);
		}
	
		
		
	}
	protected void destroyAsg(String tridentClusterId, String asgName, String account, String region) {
		
		// A safety check here would be to look at the acutal auto-scaling group in AWS
		// and verify that the tag matches the intent.  This prevents some screw-up within neo4j from nuking the wrong ASG.
		
		// We will probably want other safety checking as well.
		
		AmazonAutoScaling client = awsAccountManager.newClientBuilder(account, AmazonAutoScalingClientBuilder.class).withRegion(region).build();
		
		
		AtomicBoolean ownerVerified = new AtomicBoolean(false);
		AtomicBoolean clusterVerified = new AtomicBoolean(false);
		
		DescribeAutoScalingGroupsRequest describeRequest = new DescribeAutoScalingGroupsRequest();
		describeRequest.withAutoScalingGroupNames(asgName);
		DescribeAutoScalingGroupsResult result = client.describeAutoScalingGroups(describeRequest);
		result.getAutoScalingGroups().forEach(asg->{
			asg.getTags().forEach(tag->{
				String key = Strings.nullToEmpty(tag.getKey());
				String val = Strings.nullToEmpty(tag.getValue());
				if (key.equals("tridentClusterId") && val.equals(tridentClusterId)) {
					clusterVerified.set(true);
				}
				if (key.equals("tridentOwnerId") && val.equals(tridentClusterManager.getTridentInstallationId())) {
					ownerVerified.set(true);
				}
			});
			
		});
		
		if (clusterVerified.get() && ownerVerified.get()) {
			DeleteAutoScalingGroupRequest request = new DeleteAutoScalingGroupRequest();
			request.withForceDelete(true).withAutoScalingGroupName(asgName);		
			client.deleteAutoScalingGroup(request);
		}
		
		
	}
	
	public void destroyCluster(String tridentClusterId) {
		// What we do here is:
		// 1) Find all the corresponding entities in neo4j
		// 2) destroy those entities in AWS
		// 3) delete those entities in neo4j
		// 4) delete the swarm
		
		// There is a chance that we won't have a complete view in neo4j before we start, but dangling entiteis 
		// should get cleaned up eventually.
		
		new ClusterDestroyedEvent().withTridentClusterId(tridentClusterId).send();
		neo4j.execCypher("match (a:AwsAsg) where a.aws_tag_tridentClusterId={tridentClusterId} return a","tridentClusterId",tridentClusterId).forEach(asg->{
			destroyAsg(tridentClusterId,asg.path("aws_autoScalingGroupName").asText(),asg.path("aws_account").asText(),asg.path("aws_region").asText());
		});
		
		neo4j.execCypher("match (a:AwsElb) where a.aws_tag_tridentClusterId={tridentClusterId} return a","tridentClusterId",tridentClusterId).forEach(asg->{
			destroyLoadBalancer(tridentClusterId,asg.path("aws_loadBalancerName").asText(),asg.path("aws_account").asText(),asg.path("aws_region").asText());
		});
		
		neo4j.execCypher("match (a:DockerSwarm {tridentClusterId:{id}}) detach delete a","id",tridentClusterId);
	}
}
