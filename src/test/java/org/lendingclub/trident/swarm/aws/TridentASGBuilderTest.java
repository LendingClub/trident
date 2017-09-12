package org.lendingclub.trident.swarm.aws;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.SwarmNodeType;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.swarm.aws.SwarmASGBuilder;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.BaseEncoding;

public class TridentASGBuilderTest extends TridentIntegrationTest {

	Logger logger = LoggerFactory.getLogger(TridentASGBuilderTest.class);
	
	@Test
	public void testEmptyCloudInit() throws JsonProcessingException {
		SwarmASGBuilder b = new SwarmASGBuilder(JsonUtil.createObjectNode());
		Assertions.assertThat(b.getSwarmNodeType()).isEqualTo(SwarmNodeType.MANAGER);
		b.withSwarmNodeType(SwarmNodeType.WORKER);
		b.withTridentClusterId("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
		CreateLaunchConfigurationRequest request = new CreateLaunchConfigurationRequest();

		b.injectCloudInit(request);

		String cloudInit = new String(BaseEncoding.base64().decode(request.getUserData()));
		Assertions.assertThat(cloudInit.trim()).startsWith("#!/bin/bash");
		Assertions.assertThat(cloudInit).contains("/api/trident/provision/node-init?id=2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae")
				.contains("node-init?id=").contains("&nodeType=WORKER");
		
	

	}
	
	@Test
	public void testCloudInitWithScript() {
		SwarmASGBuilder b = new SwarmASGBuilder(JsonUtil.createObjectNode());
		b.withSwarmNodeType(SwarmNodeType.WORKER);
		b.withTridentClusterId("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae");
	b.withCloudInitScript("#!/bin/sh");
		

		b.injectCloudInit(b.launchConfigRequest);

		String cloudInit = new String(BaseEncoding.base64().decode(b.launchConfigRequest.getUserData()));
		Assertions.assertThat(cloudInit.trim()).startsWith("#!/bin/sh");
		Assertions.assertThat(cloudInit).contains("/api/trident/provision/node-init?id=2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae")
				.contains("node-init?id=").contains("&nodeType=WORKER").contains("curl -k 'http");

	}
}
