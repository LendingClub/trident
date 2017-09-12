package org.lendingclub.trident.agent;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class AWSTridentAgentTest {

	@Rule
	public MockWebServer mockWebServer = new MockWebServer();
	@Test
	public void testIt() {
		
		AWSTridentAgent agent = new AWSTridentAgent() {
		};
		
		agent.isEC2=false;
		Assertions.assertThat(agent.isRunningInEC2()).isFalse();
		Assertions.assertThat(agent.getMetadataAttribute("foo").isPresent()).isFalse();
		
		agent.isEC2=true;
		Assertions.assertThat(agent.isRunningInEC2()).isTrue();
		Assertions.assertThat(agent.getMetadataAttribute("foo").isPresent()).isFalse();
		
		
	}
	
	@Test
	public void testInstanceId() {
		
		AWSTridentAgent agent = new AWSTridentAgent() {
		};
		
		agent.isEC2=true;
		agent.setMetadataBaseUrl(mockWebServer.url("/meta-data/latest").uri().toString());
		mockWebServer.enqueue(new MockResponse().setBody("i-abcdef123456"));
		
		
		Assertions.assertThat(agent.getMetadataAttribute("instance-id").get()).isEqualTo("i-abcdef123456");
		// The second call tests the cache
		Assertions.assertThat(agent.getMetadataAttribute("instance-id").get()).isEqualTo("i-abcdef123456");
		Assertions.assertThat(agent.isRunningInEC2()).isTrue();
		
		
		
	}
}
