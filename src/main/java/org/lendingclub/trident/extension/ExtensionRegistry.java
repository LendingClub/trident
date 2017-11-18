package org.lendingclub.trident.extension;

import org.lendingclub.trident.envoy.EnvoyClusterDiscoveryInterceptor;
import org.lendingclub.trident.envoy.EnvoyInterceptors;
import org.lendingclub.trident.envoy.EnvoyManager;
import org.lendingclub.trident.haproxy.HAProxyInterceptors;
import org.lendingclub.trident.haproxy.HAProxyManager;
import org.lendingclub.trident.provision.SwarmNodeManager;
import org.lendingclub.trident.provision.SwarmNodeProvisionInterceptor;
import org.lendingclub.trident.swarm.aws.AWSClusterManager;
import org.lendingclub.trident.swarm.aws.AWSInterceptors;
import org.lendingclub.trident.swarm.aws.AutoScalingGroupInterceptor;
import org.lendingclub.trident.swarm.aws.LaunchConfigInterceptor;
import org.lendingclub.trident.swarm.aws.ManagerLoadBalancerInterceptor;
import org.lendingclub.trident.swarm.platform.AppClusterCommandInterceptor;
import org.lendingclub.trident.swarm.platform.AppClusterManager;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Convenience class that aggregates customizations in one place.
 * @author rschoening
 *
 */
public class ExtensionRegistry {

	@Autowired
	AppClusterManager appClusterManager;
	
	@Autowired
	AWSClusterManager awsClusterManager;
	
	@Autowired
	SwarmNodeManager swarmNodeManager;
	
	@Autowired
	EnvoyManager envoyManager;
	
	@Autowired
	HAProxyManager haproxyManager;

	
	public AWSInterceptors getAWSInterceptors() {
		return awsClusterManager;
	}
	
	
	public InterceptorGroup<AppClusterCommandInterceptor> getAppClusterCommandInterceptors() {
		return appClusterManager.getAppClusterCommandInterceptors();
	}
	
	public InterceptorGroup<SwarmNodeProvisionInterceptor> getSwarmNodeProvisionInterceptors() {
		return swarmNodeManager.getSwarmNodeProvisionInterceptors();
	}
	

	public EnvoyInterceptors getEnvoyInterceptors() {
		return envoyManager;
	}
	
	public HAProxyInterceptors getHAProxyInterceptors() {
		return haproxyManager;
	}
	
	
}
