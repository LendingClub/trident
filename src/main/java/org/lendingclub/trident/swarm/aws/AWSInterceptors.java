package org.lendingclub.trident.swarm.aws;

import java.util.function.BiConsumer;

import org.lendingclub.trident.provision.SwarmNodeProvisionInterceptor;
import org.lendingclub.trident.extension.InterceptorGroup;
import org.lendingclub.trident.provision.SwarmNodeProvisionContext;

import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public interface AWSInterceptors {
	public InterceptorGroup<org.lendingclub.trident.swarm.aws.ManagerDnsRegistrationInterceptor> getManagerDnsRegistrationInterceptors();

	public InterceptorGroup<AutoScalingGroupInterceptor> getAutoScalingGroupInterceptors();

	public InterceptorGroup<LaunchConfigInterceptor> getLaunchConfigInterceptors();

	public InterceptorGroup<ManagerLoadBalancerInterceptor> getManagerLoadBalancerInterceptors();
}
