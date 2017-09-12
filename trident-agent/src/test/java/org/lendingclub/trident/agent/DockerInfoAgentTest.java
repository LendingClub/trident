package org.lendingclub.trident.agent;

import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class DockerInfoAgentTest extends TridentAgentTest {

		
	@Rule
	public MockWebServer mockTrident = new MockWebServer();
	

	
	@Test
	public void testIt() throws InterruptedException, JsonProcessingException, IOException {
		
	
		mockTrident.enqueue(new MockResponse().setBody("{}"));
		DockerInfoAgent agent = new DockerInfoAgent();
		//agent.isEC2 = true; //force to true
		agent.tridentBaseUrl = mockTrident.url("/api/trident/agent/info").toString();
		agent.staticDockerInfo = TridentAgentTest.getMockDockerInfo();
		agent.sendInfo();
		
		RecordedRequest rr = mockTrident.takeRequest();
		
		
		JsonNode n = mapper.readTree(rr.getBody().readUtf8());
		
		System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(n));
	}
}
