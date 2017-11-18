package org.lendingclub.trident.swarm.aws;

import java.util.List;
import java.util.function.Consumer;

import org.lendingclub.mercator.aws.ASGScanner;
import org.lendingclub.mercator.aws.AWSScannerBuilder;
import org.lendingclub.mercator.core.Projector;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.Trident;
import org.lendingclub.trident.TridentEndpoints;
import org.lendingclub.trident.cluster.TridentClusterManager;
import org.lendingclub.trident.swarm.SwarmNodeType;
import org.lendingclub.trident.swarm.aws.event.AutoScalingGroupCreatedEvent;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.BlockDeviceMapping;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupResult;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationResult;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest;
import com.amazonaws.services.autoscaling.model.Ebs;
import com.amazonaws.services.autoscaling.model.LaunchConfiguration;
import com.amazonaws.services.autoscaling.model.Tag;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;

/**
 * This class manages the construction of ASGs, ELBs and DNS for a swarm.
 * 
 * @author rschoening
 *
 */
public class SwarmASGBuilder {

	Logger logger = LoggerFactory.getLogger(SwarmASGBuilder.class);
	private AWSAccountManager accountManager;
	private AWSClusterManager awsClusterManager;

	List<LaunchConfigInterceptor> launchConfigDecoratorList = Lists.newCopyOnWriteArrayList();
	List<AutoScalingGroupInterceptor> asgDecorator = Lists.newCopyOnWriteArrayList();

	CreateLaunchConfigurationRequest launchConfigRequest = new CreateLaunchConfigurationRequest();
	CreateLaunchConfigurationResult launchConfigResult = null;
	
	ObjectNode request = JsonUtil.createObjectNode();

	LaunchConfiguration launchConfiguration;
	AutoScalingGroup autoScalingGroup;
	LoadBalancerDescription loadBalancer;

	AmazonAutoScaling asgClientRef;

	CreateAutoScalingGroupRequest asgRequest = new CreateAutoScalingGroupRequest();
	CreateAutoScalingGroupResult asgResult;

	CreateLoadBalancerRequest createLoadBalancerRequest = null;
	CreateLoadBalancerResult createLoadBalancerResult = null;

	long timestamp = System.currentTimeMillis();

	String tridentBaseUrl = null;

	public static final String DEFAULT_INSTANCE_TYPE="t2.medium";
	
	protected SwarmASGBuilder(ObjectNode data) {
		this.request = data;
		if (!data.has(AWSClusterManager.SWARM_NODE_TYPE)) {
			data.put(AWSClusterManager.SWARM_NODE_TYPE, SwarmNodeType.MANAGER.toString());
		} else {
			SwarmNodeType.valueOf(data.path(AWSClusterManager.SWARM_NODE_TYPE).asText());
		}
	}

	public String getRegion() {
		String region = request.path(AWSClusterManager.AWS_REGION).asText(null);
		if (Strings.isNullOrEmpty(region)) {
			region = request.path(AWSClusterManager.AWS_REGION).asText(null);
		}
		return region;
	}

	public String getTridentClusterId() {
		return request.path("tridentClusterId").asText(null);
	}

	public AmazonAutoScaling getASGClient() {

		if (asgClientRef == null) {
			Preconditions.checkArgument(getRegion() != null, "region must be set");
			Preconditions.checkArgument(!Strings.isNullOrEmpty(getAccountName()), "accountName must be set");
			this.asgClientRef = accountManager.getClient(getAccountName(), AmazonAutoScalingClientBuilder.class,getRegion());
	
		}
		return asgClientRef;
	}

	public AutoScalingGroup describeAutoScalingGroup() {

		if (autoScalingGroup != null) {
			return autoScalingGroup;
		}

		DescribeAutoScalingGroupsRequest dasgr = new DescribeAutoScalingGroupsRequest();
		dasgr.setAutoScalingGroupNames(Lists.newArrayList(asgRequest.getAutoScalingGroupName()));
		this.autoScalingGroup = getASGClient().describeAutoScalingGroups(dasgr).getAutoScalingGroups().get(0);

		return autoScalingGroup;
	}

	public ObjectNode getRequestData() {
		return request;
	}

	public LaunchConfiguration describeLaunchConfig() {
		if (launchConfiguration != null) {
			return launchConfiguration;
		}

		DescribeLaunchConfigurationsRequest dlcr = new DescribeLaunchConfigurationsRequest();
		dlcr.setLaunchConfigurationNames(Lists.newArrayList(launchConfigRequest.getLaunchConfigurationName()));
		this.launchConfiguration = getASGClient().describeLaunchConfigurations(dlcr).getLaunchConfigurations().get(0);
		return launchConfiguration;
	}

	public void execute() {
		
		Preconditions.checkState(!Strings.isNullOrEmpty(getTridentClusterId()), "tridentClusterId must be set");
		Preconditions.checkState(accountManager != null, "accountManager must be set");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(getTemplate()), "template must be set");

		awsClusterManager.copyTemplateIntoContext(getTemplate(), this.request);
		String clusterName = awsClusterManager.neo4j
				.execCypher("match (s:DockerSwarm {tridentClusterId:{id}}) return s", "id", getTridentClusterId())
				.blockingFirst().path("name").asText();

		String generatedId = "swarm-" + getSwarmNodeType().toString().toLowerCase() + "-" + clusterName + "-"
				+ Long.toHexString(timestamp);
		if (launchConfigRequest.getLaunchConfigurationName() == null) {
			launchConfigRequest.setLaunchConfigurationName(generatedId);
		}

		BlockDeviceMapping blockDeviceMapping = new BlockDeviceMapping();
		blockDeviceMapping.setDeviceName("/dev/sdh");
		Ebs ebs = new Ebs();
		ebs.setDeleteOnTermination(true);
		ebs.setVolumeSize(20);
		ebs.setVolumeType("gp2");
		ebs.setEncrypted(true);
		blockDeviceMapping.setEbs(ebs);
		List<BlockDeviceMapping> bdmList = Lists.newArrayList(blockDeviceMapping);
		launchConfigRequest.setBlockDeviceMappings(bdmList);
		launchConfigRequest.withInstanceType(DEFAULT_INSTANCE_TYPE);
		Preconditions.checkNotNull(awsClusterManager);

		JsonUtil.logInfo(SwarmASGBuilder.class, "swarmJsonNode", request);
		launchConfigDecoratorList.forEach(it -> {
			logger.info("applying decorator {} to {}", it, this);
			it.accept(request, launchConfigRequest);
		});

		injectCloudInit(launchConfigRequest);
		JsonUtil.logInfo(getClass(), "launchConfig", launchConfigRequest);

		launchConfigResult = getASGClient().createLaunchConfiguration(launchConfigRequest);

		asgRequest.setLaunchConfigurationName(launchConfigRequest.getLaunchConfigurationName());

		if (asgRequest.getAutoScalingGroupName() == null) {
			asgRequest.setAutoScalingGroupName(generatedId);
		}

		String nameTag = getSwarmNodeType() == SwarmNodeType.MANAGER ? "swarm-manager" : "swarm-worker";
		List<Tag> list = Lists.newArrayList(
				new Tag().withKey("Name").withPropagateAtLaunch(true).withValue(generatedId),
				new Tag().withKey("tridentClusterId").withValue(getTridentClusterId()).withPropagateAtLaunch(true),
				new Tag().withKey("swarmNodeType").withValue(getSwarmNodeType().toString()).withPropagateAtLaunch(true),
				new Tag().withKey("tridentOwnerId").withValue(getTridentClusterManager().getTridentInstallationId()).withPropagateAtLaunch(true),
				new Tag().withKey("tridentClusterName").withValue(clusterName).withPropagateAtLaunch(true));
		
		
		
		if (Strings.isNullOrEmpty(asgRequest.getAutoScalingGroupName())) {
			asgRequest.setAutoScalingGroupName(generatedId);
		}

		if (getSwarmNodeType() == SwarmNodeType.MANAGER) {
			// 100x easier if we start all manager clusters out with 1 node then
			// scale up
			asgRequest.setMaxSize(3);
			asgRequest.setMinSize(1);
			asgRequest.setDesiredCapacity(1);
		} else if (getSwarmNodeType() == SwarmNodeType.WORKER) {
			if (asgRequest.getMaxSize() == null) {
				asgRequest.setMaxSize(10);
			}
			if (asgRequest.getMinSize() == null) {
				asgRequest.setMinSize(1);
			}
			if (asgRequest.getDesiredCapacity() == null) {
				asgRequest.setDesiredCapacity(3);
			}

		}

		asgRequest.setDesiredCapacity(
				Math.max(asgRequest.getMinSize(), Math.min(asgRequest.getMaxSize(), asgRequest.getDesiredCapacity())));

		asgRequest.setTags(list);

		this.asgDecorator.forEach(it -> {
			logger.info("applying ASG decorator {} to {}", it, this);
			it.accept(request, asgRequest);
		});

		this.asgResult = getASGClient().createAutoScalingGroup(asgRequest);
		
		new AutoScalingGroupCreatedEvent()
			.withTridentClusterId(getTridentClusterId())
			.withAttribute("aws_autoScalingGroupName",asgRequest.getAutoScalingGroupName())
			.withMessage(
					String.format("Autoscaling group %s created in account %s", asgRequest.getAutoScalingGroupName(), getAccountName()))
			.send();
	
		// set the desired dns name if it is not set
		if (getSwarmNodeType() == SwarmNodeType.MANAGER) {
			String dnsName = request.path(AWSClusterManager.MANAGER_DNS_NAME).asText();
			
			String san = request.path(AWSClusterManager.MANAGER_SUBJECT_ALTERNATIVE_NAMES).asText();
			if (!Strings.isNullOrEmpty(dnsName)) {
				
				// If dnsName contains a %s, merge the clusterName into it
				dnsName = String.format(dnsName, clusterName);
				getAWSClusterManager().neo4j.execCypher(
						"match (s:DockerSwarm {tridentClusterId:{id}}) where not exists (s.managerDnsName) set s.managerDnsName={dnsName}",
						"id", getTridentClusterId(), "dnsName", dnsName);
			}
			if (!Strings.isNullOrEmpty(san)) {
				getAWSClusterManager().neo4j.execCypher(
						"match (s:DockerSwarm {tridentClusterId:{id}}) where not exists (s.managerSubjectAlternativeNames) set s.managerSubjectAlternativeNames={san}",
						"id", getTridentClusterId(), "san", san);
			}

		}

		createRelationships();
	}

	public SwarmASGBuilder withAutoScalingGroupConfig(Consumer<CreateAutoScalingGroupRequest> c) {
		c.accept(this.asgRequest);
		return this;
	}

	public String getSwarmName() {
		return getRequestData().path("name").asText(null);
	}

	public SwarmASGBuilder withManagerDnsAccountName(String val) {
		getRequestData().put(AWSClusterManager.AWS_MANAGER_HOSTED_ZONE_ACCOUNT, val);
		return this;
	}

	public String getManagerDnsAccountName() {
		String val = getRequestData().path(AWSClusterManager.AWS_MANAGER_HOSTED_ZONE_ACCOUNT).asText();
		if (Strings.isNullOrEmpty(val)) {
			return getAccountName();
		} else {
			return val;
		}
	}

	public String getAccountName() {
		return getRequestData().path(AWSClusterManager.AWS_ACCOUNT).asText(null);
	}

	public SwarmASGBuilder withAccountName(String name) {
		getRequestData().put(AWSClusterManager.AWS_ACCOUNT, name);
		return this;
	}

	public SwarmASGBuilder withAccountManager(AWSAccountManager m) {
		this.accountManager = m;
		return this;
	}

	public SwarmASGBuilder withAWSClusterManager(AWSClusterManager m) {
		this.awsClusterManager = m;

		this.launchConfigDecoratorList.addAll(m.launchConfigInterceptors.getInterceptors());
		this.asgDecorator.addAll(m.asgDecorator.getInterceptors());

		return this;
	}

	public String getTridentBaseUrl() {
		return request.path("tridentBaseUrl").asText(Trident.getApplicationContext().getBean(TridentEndpoints.class).getAPIEndpoint());
	}
	public SwarmASGBuilder withTridentBaseUrl(String url) {
		request.put("tridentBaseUrl", url);
		return this;
	}
	public SwarmASGBuilder withSwarmName(String name) {
		request.put("tridentClusterName", name);
		request.put("name", name);
		return this;
	}
	public SwarmASGBuilder withSwarmNodeType(SwarmNodeType nodeType) {
		request.put(AWSClusterManager.SWARM_NODE_TYPE, nodeType.toString());
		return this;
	}

	public SwarmASGBuilder withManagerDnsName(String dns) {
		request.put(AWSClusterManager.MANAGER_DNS_NAME, dns);

		return this;
	}

	public SwarmASGBuilder withTridentClusterId(String id) {

		request.put(AWSClusterManager.TRIDENT_CLUSTER_ID, id);
		return this;
	}

	public SwarmASGBuilder withRegion(Regions region) {
		request.put(AWSClusterManager.AWS_REGION, region.toString());
		return this;
	}

	public SwarmASGBuilder withRegion(String region) {
		if (Strings.isNullOrEmpty(region)) {
			throw new IllegalArgumentException("region cannot be null or empty");
		}
		request.put(AWSClusterManager.AWS_REGION, Regions.fromName(region).toString());
		return this;
	}

	public SwarmASGBuilder withCloudInitScript(String script) {
		this.launchConfigRequest.setUserData(BaseEncoding.base64().encode(script.getBytes()));
		return this;
	}

	protected void injectCloudInit(CreateLaunchConfigurationRequest request) {
		String s = com.google.common.base.Strings.nullToEmpty(request.getUserData());
		if (!Strings.isNullOrEmpty(s)) {
			s = new String(BaseEncoding.base64().decode(s));
		}

		if (Strings.isNullOrEmpty(s.trim())) {
			s = "#!/bin/bash\n";
		}
		
		String command = String.format("curl -k '%s/provision/node-init/%s/%s' | bash -x",
				getTridentBaseUrl(), getTridentClusterId(),
				getSwarmNodeType().toString());

		s += "\n\n";
		s += command;
		s += "\n";

		logger.info("cloud init: \n<<<\n" + s + "\n>>>");

		request.setUserData(BaseEncoding.base64().encode(s.getBytes()));
	}

	public SwarmNodeType getSwarmNodeType() {
		return SwarmNodeType.valueOf(request.path(AWSClusterManager.SWARM_NODE_TYPE).asText(SwarmNodeType.MANAGER.toString()));
	}

	private void createRelationships() {
		Projector projector = Trident.getApplicationContext().getBean(Projector.class);
		String arn = describeAutoScalingGroup().getAutoScalingGroupARN();
		String name = describeAutoScalingGroup().getAutoScalingGroupName();

		AWSScannerBuilder scannerBuilder = projector.createBuilder(AWSScannerBuilder.class)
				.withCredentials(accountManager.getCredentialsProvider(getAccountName())).withRegion(getRegion());

		// Slightly annoying thing here is that Mercator does its own AWS client
		// construction, while Trident has its own. We really need to move
		// mercator builder
		// construction into the Trident AccountManager which is responsible for
		// all AWS client construction (sorting out credentials, proxy config,
		// etc.)
		// Until we do that, we have this slightly messy bit of code.
		ClientConfiguration cc = accountManager.getClientConfiguration(getAccountName());
		if (cc != null) {
			scannerBuilder.withClientConfiguration(cc);
		}

		ASGScanner scanner = scannerBuilder.buildASGScanner();

		scanner.scanASGNames(name);

		NeoRxClient neo4j = Trident.getApplicationContext().getBean(NeoRxClient.class);
		String cypher = "match (a:AwsAsg {aws_autoScalingGroupARN:{asgArn}}), (t:DockerSwarm {tridentClusterId:{tridentClusterId}}) merge (t)-[x:PROVIDED_BY]->(a)";
		neo4j.execCypher(cypher, "asgArn", arn, AWSClusterManager.TRIDENT_CLUSTER_ID, getTridentClusterId());
	}

	public SwarmASGBuilder addAutoScalingGroupDecorator(AutoScalingGroupInterceptor g) {
		this.asgDecorator.add(g);
		return this;
	}

	public SwarmASGBuilder addLaunchConfigDecorator(LaunchConfigInterceptor g) {
		this.launchConfigDecoratorList.add(g);
		return this;
	}

	public String getManagerDnsName() {
		return getRequestData().path(AWSClusterManager.MANAGER_DNS_NAME).asText(null);
	}

	public String getHostedZoneId() {
		return getRequestData().path(AWSClusterManager.AWS_MANAGER_HOSTED_ZONE).asText(null);
	}

	public SwarmASGBuilder withHostedZoneId(String id) {
		getRequestData().put(AWSClusterManager.AWS_MANAGER_HOSTED_ZONE, id);

		return this;
	}

	public AWSClusterManager getAWSClusterManager() {
		return this.awsClusterManager;
	}
	
	public TridentClusterManager getTridentClusterManager() {
		return Trident.getApplicationContext().getBean(TridentClusterManager.class);
	}

	public String getTemplate() {
		return request.path("templateName").asText(null);
	}
	public SwarmASGBuilder withTemplate(String template) {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(template),"template cannot be null or empty");
		this.request.put("templateName", template);
		return this;
	}
}
