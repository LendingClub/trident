package org.lendingclub.trident.chatops;

import java.io.IOException;

import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class HipChatProviderTest   {


	public MockWebServer server = new MockWebServer();
	
	@Test
	public void testIt() throws InterruptedException, IOException{
		Assertions.assertThat(server).isNotNull();
		server.enqueue(new MockResponse().setBody("{}"));
		
		ObjectNode config = JsonUtil.createObjectNode();
		config.put("url", server.url("/").toString());
		HipChatProvider p = new HipChatProvider();
		p.init(config);
		
		ChatMessage m = new ChatMessage(null).withMessage("hello").withRoom("myroom");
		p.send(m);
		
		RecordedRequest rr = server.takeRequest();
		
		Assertions.assertThat(rr.getMethod()).isEqualTo("POST");
		Assertions.assertThat(rr.getPath()).isEqualTo("/v2/room/myroom/notification");
		Assertions.assertThat(rr.getHeader("Authorization")).startsWith("Bearer");
		Assertions.assertThat(rr.getHeader("Content-type")).startsWith("application/json");
	
		JsonNode body = JsonUtil.getObjectMapper().readTree(rr.getBody().readByteArray());
		Assertions.assertThat(body.path("message").asText()).isEqualTo("hello");
		
	}
	

	
}
