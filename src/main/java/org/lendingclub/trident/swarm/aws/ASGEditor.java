package org.lendingclub.trident.swarm.aws;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lendingclub.trident.Trident;
import org.lendingclub.trident.util.JsonUtil;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsRequest;
import com.amazonaws.services.autoscaling.model.DescribeLaunchConfigurationsResult;
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class ASGEditor {

	
	AmazonAutoScalingClient client;
	Regions region;
	String accountName;
	String asgName;
	List<Consumer<CreateLaunchConfigurationRequest>> launchConfigConsumers = Lists.newArrayList();
	List<Consumer<UpdateAutoScalingGroupRequest>> asgConsumers = Lists.newArrayList();
	boolean executed=false;
	protected ASGEditor() {
		
	}
	public ASGEditor withAutoScalingClient(AmazonAutoScalingClient client) {
		this.client = client;
		return this;
	}

	private void checkClientNotInitialized() {
		if (client!=null) {
			throw new IllegalStateException("client already initialized");
		}
	}
	public ASGEditor withRegion(String region) {
		
		return withRegion(Regions.fromName(region));
	}
	public ASGEditor withRegion(Regions region) {
		checkClientNotInitialized();
		this.region = region;
		return this;
	}

	public ASGEditor withAccount(String id) {
		return withAccountName(id);
	}
	public ASGEditor withAccountName(String name) {
		checkClientNotInitialized();
		this.accountName = name;
		return this;
	}

	public ASGEditor withAutoScalingGroupName(String name) {
		this.asgName = name;
		return this;
	}

	public ASGEditor withAutoScalingGroup(Consumer<UpdateAutoScalingGroupRequest> consumer) {
		asgConsumers.add(consumer);
		return this;
	}

	public ASGEditor withLaunchConfig(Consumer<CreateLaunchConfigurationRequest> consumer) {
		launchConfigConsumers.add(consumer);
		return this;
	}

	public AmazonAutoScalingClient getASGClient() {
		if (client != null) {
			return client;
		}
		if (!Strings.isNullOrEmpty(accountName)) {
			AWSAccountManager mgr = Trident.getApplicationContext().getBean(AWSAccountManager.class);
			
			Regions r = region;
			if (region == null) {			
				r =Regions.fromName( mgr.getDefaultRegion(accountName));
			}
	
			client = (AmazonAutoScalingClient)  mgr.getClient(accountName,
					AmazonAutoScalingClientBuilder.class,r);
		}
		if (client==null) {
			throw new IllegalStateException("must set ASG Client or account");
		}
		return client;
	}

	@VisibleForTesting
	void configureAutoscalingGroup(UpdateAutoScalingGroupRequest updateAutoScalingGroupRequest) {
		Preconditions.checkState(!Strings.isNullOrEmpty(asgName),"asg name must be set");
		updateAutoScalingGroupRequest.setAutoScalingGroupName(asgName);
		asgConsumers.forEach(it -> {
			it.accept(updateAutoScalingGroupRequest);
		});
	}
	@VisibleForTesting
	void configureLaunchConfig(String launchConfigName, CreateLaunchConfigurationRequest r) {
		
		String newLaunchConfigName = launchConfigName;
		Matcher m = Pattern.compile(".*(-v(\\d{13}))").matcher(launchConfigName);
		if (m.matches()) {
			newLaunchConfigName = launchConfigName.replace(m.group(2), "");
		}
		newLaunchConfigName = newLaunchConfigName + "-v" + System.currentTimeMillis();

		r.setLaunchConfigurationName(newLaunchConfigName);
		
		launchConfigConsumers.forEach(it -> {
			it.accept(r);
		});

		// Amazon chokes on empty strings and wants to see null values.
		// Perhaps we can find a mechanical way to do this with Jackson or via mapping
		// method.
		if (Strings.isNullOrEmpty(r.getRamdiskId())) {
			r.setRamdiskId(null);
		}
		if (Strings.isNullOrEmpty(r.getKeyName())) {
			r.setKeyName(null);
		}
		if (Strings.isNullOrEmpty(r.getKernelId())) {
			r.setKernelId(null);
		}

		JsonUtil.logInfo("creating new launch config", r);
	}
	public void execute() {

		if (executed) {
			throw new IllegalStateException("execute() can only be called once");
		}
		executed=true;
		DescribeAutoScalingGroupsRequest describeAsgRequest = new DescribeAutoScalingGroupsRequest();
		describeAsgRequest.setAutoScalingGroupNames(ImmutableList.of(asgName));
		DescribeAutoScalingGroupsResult result = getASGClient().describeAutoScalingGroups(describeAsgRequest);
		AutoScalingGroup asg = result.getAutoScalingGroups().get(0);

		String launchConfigName = asg.getLaunchConfigurationName();
		
		DescribeLaunchConfigurationsRequest describeLaunchConfigRequest = new DescribeLaunchConfigurationsRequest();
		describeLaunchConfigRequest.setLaunchConfigurationNames(ImmutableList.of(launchConfigName));

		DescribeLaunchConfigurationsResult lcresult = getASGClient()
				.describeLaunchConfigurations(describeLaunchConfigRequest);

		CreateLaunchConfigurationRequest r = new ObjectMapper()
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.convertValue(lcresult.getLaunchConfigurations().get(0), CreateLaunchConfigurationRequest.class);

		UpdateAutoScalingGroupRequest uasgr = new UpdateAutoScalingGroupRequest();
		if (!launchConfigConsumers.isEmpty()) {

			configureLaunchConfig(launchConfigName, r);
			
			getASGClient().createLaunchConfiguration(r);
			uasgr.setLaunchConfigurationName(r.getLaunchConfigurationName());
		}
		

		configureAutoscalingGroup(uasgr);
		

		JsonUtil.logInfo("updating asg", uasgr);
		getASGClient().updateAutoScalingGroup(uasgr);

	}
}
