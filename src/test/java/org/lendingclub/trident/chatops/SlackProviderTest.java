package org.lendingclub.trident.chatops;

import java.io.IOException;

import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.FormBody;
import okhttp3.RequestBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class SlackProviderTest   {


	public MockWebServer server = new MockWebServer();
	
	@Test
	public void testIt() throws InterruptedException, IOException{
		Assertions.assertThat(server).isNotNull();
		server.enqueue(new MockResponse().setBody("{}"));
		
		ObjectNode config = JsonUtil.createObjectNode();
		config.put("url", server.url("/api/").toString());
		config.put("token", "mytoken");
		SlackProvider p = new SlackProvider();
		p.init(config);
		
		ChatMessage m = new ChatMessage(null).withMessage("hello").withChannel("#mychannel");
		p.send(m);
		
		RecordedRequest rr = server.takeRequest();
		
		Assertions.assertThat(rr.getMethod()).isEqualTo("POST");
		Assertions.assertThat(rr.getPath()).isEqualTo("/api/chat.postMessage");

		Assertions.assertThat(rr.getHeader("Content-type")).startsWith("application/x-www-form-urlencoded");
		Assertions.assertThat(rr.getBody().readUtf8()).contains("channel=%23mychannel").contains("token=mytoken").contains("text=hello");
	
		
	}
	

	
}
