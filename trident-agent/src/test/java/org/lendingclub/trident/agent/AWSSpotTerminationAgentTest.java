package org.lendingclub.trident.agent;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.lendingclub.neorx.mock.MockNeoRxClient;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class AWSSpotTerminationAgentTest {

	@Rule
	public MockWebServer mockWebServer = new MockWebServer();
	
	
	@Test
	public void testIt() throws Exception {
		mockWebServer.enqueue(new MockResponse().setBody("12345"));
		AWSSpotTerminationAgent agent = new AWSSpotTerminationAgent();
		agent.isEC2 = true;
		agent.setMetadataBaseUrl("http://localhost:"+mockWebServer.getPort());
		agent.setMetadataAttribute("instance-id", "i-1234");
		agent.setMetadataAttribute("ami-id", "i-1234");
		agent.setMetadataAttribute("instance-type", "c4.large");
		agent.setMetadataAttribute("local-ipv4", "192.168.1.44");
		agent.setMetadataAttribute("spot/termination-time", "now");
		agent.makeRequest();
	}
	@Test
	public void testResponse() throws Exception {
		
		
	
		mockWebServer.enqueue(new MockResponse().setResponseCode(404));
		
		
		AWSSpotTerminationAgent agent = new AWSSpotTerminationAgent();
		agent.setMetadataBaseUrl("http://localhost:"+mockWebServer.getPort());
		
		agent.makeRequest();
		agent.makeRequest();
	}
	
	@Test
	public void testFailedConnect() throws Exception {
		
		
		
		AWSSpotTerminationAgent agent = new AWSSpotTerminationAgent();
		
		Assertions.assertThat(agent.scheduler.isShutdown()).isFalse();
	
		agent.setMetadataBaseUrl("http://localhost:"+mockWebServer.getPort());
		
		agent.makeRequest();
	

	}
}
