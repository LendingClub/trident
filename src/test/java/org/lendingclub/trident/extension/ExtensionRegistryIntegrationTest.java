package org.lendingclub.trident.extension;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.Trident;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.provision.SwarmTemplateInterceptor;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.springframework.beans.factory.annotation.Autowired;

public class ExtensionRegistryIntegrationTest extends TridentIntegrationTest {

	@Autowired
	ExtensionRegistry extensionRegistry;
	
	
	@Test
	public void testIt() {
		Assertions.assertThat(extensionRegistry).isNotNull();
		
		Assertions.assertThat(extensionRegistry.getSwarmNodeProvisionInterceptors().getInterceptors().get(0).getClass()).isEqualTo(SwarmTemplateInterceptor.class);
		
		
		Assertions.assertThat(extensionRegistry.getAppClusterCommandInterceptors().getInterceptors()).isEmpty();
		
		Assertions.assertThat(extensionRegistry.getAWSInterceptors().getAutoScalingGroupInterceptors().getInterceptors().get(0).getClass().getName()).contains("$SwarmTemplateASGDecorator");
		
		Assertions.assertThat(extensionRegistry.getAWSInterceptors().getLaunchConfigInterceptors().getInterceptors().get(0).getClass().getName()).contains("AWSClusterManager$SwarmTemplateLaunch");
		Assertions.assertThat(extensionRegistry.getAWSInterceptors().getManagerDnsRegistrationInterceptors().getInterceptors().get(0).getClass().getName()).contains("$SwarmTemplateManagerDns");
		Assertions.assertThat(extensionRegistry.getAWSInterceptors().getManagerLoadBalancerInterceptors().getInterceptors().get(0).getClass().getName()).contains("$SwarmTemplateLoadBal");
	}
}
