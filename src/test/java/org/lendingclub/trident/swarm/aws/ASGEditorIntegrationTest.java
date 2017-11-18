package org.lendingclub.trident.swarm.aws;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.util.JsonUtil;

import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest;

public class ASGEditorIntegrationTest extends TridentIntegrationTest {

	
	@Test
	public void testLaunchConfig() {
		
		CreateLaunchConfigurationRequest request = new CreateLaunchConfigurationRequest();
		
		new ASGEditor().withLaunchConfig(it->{
			it.setKeyName("foo");
		}).configureLaunchConfig("test", request);
	
		
		JsonUtil.logInfo("launch config", request);
		
		Assertions.assertThat(request.getKeyName()).isEqualTo("foo");
		Assertions.assertThat(request.getLaunchConfigurationName()).startsWith("test-v15");
		/*new ASGEditor().withAccountName("lab").withASGName("swarm-worker-x2-15f83fe4a41").withLaunchConfig(c->{
			c.setEbsOptimized(true);
			c.setInstanceType("t2.xlarge");
		}).execute();*/
		
	}
	
	@Test
	public void testAutoScaling() {
		
		UpdateAutoScalingGroupRequest uasgr = new UpdateAutoScalingGroupRequest();
		
		new ASGEditor().withAutoScalingGroupName("foobar").withAutoScalingGroup(it->{
			it.setDesiredCapacity(10);
		}).configureAutoscalingGroup(uasgr);
	
		
		JsonUtil.logInfo("asg", uasgr);
		Assertions.assertThat(uasgr.getAutoScalingGroupName()).isEqualTo("foobar");
		Assertions.assertThat(uasgr.getDesiredCapacity()).isEqualByComparingTo(10);
		
		
	}
}
