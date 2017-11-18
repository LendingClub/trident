package org.lendingclub.trident.swarm.aws;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.Trident;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.cluster.TridentClusterManager;
import org.lendingclub.trident.util.JsonUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.BaseEncoding;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;

public class SQSGatewayControllerTest extends TridentIntegrationTest {

	MockWebServer server = new MockWebServer();
	
	
	@Test
	public void testConvertClientRequest() {
		
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/api/trident/aws/gateway/arn:aws:sqs:us-east-1:123456789012:queue1/foo/bar");
		request.setParameter("fizz", "buzz");
		request.setServerPort(1234);
		request.setServerName("foo.example.com");
		request.setScheme("https");
		request.setMethod("GET");
		JsonNode n = new SQSGatewayController().withInstallationId("abcd123").convertClientRequestToJson(request);
		
		JsonUtil.logInfo("", n);
		Assertions.assertThat(n.path("type").asText()).isEqualTo("sqsGatewayRequest");
		Assertions.assertThat(n.has("requestId")).isTrue();
		Assertions.assertThat(n.path("requestPath").asText()).isEqualTo("/api/trident/foo/bar");
		Assertions.assertThat(n.path("clientRequestUrl").asText()).isEqualTo(request.getRequestURL().toString());
		Assertions.assertThat(n.path("requestQueueArn").asText()).isEqualTo("arn:aws:sqs:us-east-1:123456789012:queue1");
		Assertions.assertThat(n.path("responseQueueArn").asText()).isEqualTo("arn:aws:sqs:us-east-1:123456789012:trident-aws-events-abcd123");
		Assertions.assertThat(n.path("params").path("fizz").asText()).isEqualTo("buzz");
		Assertions.assertThat(n.path("requestMethod").asText()).isEqualTo("GET");
		Assertions.assertThat(n.path("clientRequestUrl").asText()).isEqualTo("https://foo.example.com:1234/api/trident/aws/gateway/arn:aws:sqs:us-east-1:123456789012:queue1/foo/bar");
	}
	
	@Test
	public void testIt() throws InterruptedException{
		server.enqueue(new MockResponse().setHeader("Content-type", "application/json").setBody("{}"));
		ObjectNode n = JsonUtil.createObjectNode();
		n.put("responseQueueArn", "arn:aws:sqs:us-east-1:123456789012:trident-aws-events-abcd123");
		n.put("requestId", "123456");
		n.put("requestPath", "/api/trident/fizz/buzz");
		ObjectNode p = JsonUtil.createObjectNode();
		n.set("params", p);
		p.put("foo", "bar");
		JsonNode response = new SQSGatewayController().withTargetUrl(server.url("/").toString()).invokeLocal(n);
		JsonUtil.logInfo("foo", response);
		
		RecordedRequest rr = server.takeRequest();
		Assertions.assertThat(rr.getPath()).isEqualTo("/api/trident/fizz/buzz");
		Assertions.assertThat(rr.getMethod()).isEqualTo("POST");
		
		Assertions.assertThat(rr.getBody().readUtf8()).contains("foo=bar");
		Assertions.assertThat(rr.getHeader("content-type")).isEqualTo("application/x-www-form-urlencoded");
	}
	
	@Test
	public void testBinary() {
		byte data[] = new byte[]{5,3,5,3};
		Buffer buffer = new Buffer();
		buffer.write(data);
		server.enqueue(new MockResponse().setHeader("Content-type", "application/octet-stream").setBody(buffer));
		ObjectNode n = JsonUtil.createObjectNode();
		n.put("responseQueueArn", "arn:aws:sqs:us-east-1:123456789012:trident-aws-events-abcd123");
		n.put("requestId", "123456");
		JsonNode response = new SQSGatewayController().withTargetUrl(server.url("/").toString()).invokeLocal(n);
		JsonUtil.logInfo("foo", response);
		
		Assertions.assertThat(response.path("type").asText()).isEqualTo("sqsGatewayResponse");
		Assertions.assertThat(response.path("requestId").asText()).isEqualTo("123456");
		Assertions.assertThat(response.path("responseCode").asInt()).isEqualTo(200);
		Assertions.assertThat(response.path("contentType").asText()).isEqualTo("application/octet-stream");
		Assertions.assertThat(BaseEncoding.base64().decode(response.path("responseBodyBase64").asText())).isEqualTo(data);
	}
	
	@Test
	public void testToResponseEntity() throws IOException {
		
		ObjectNode n = JsonUtil.createObjectNode();
		n.put("contentType", "application/json");
		n.put("responseCode", 200);
		n.put("responseBody", JsonUtil.createObjectNode().put("foo", "bar").toString());
		SQSGatewayController c = new SQSGatewayController();
		ResponseEntity re = c.toResponseEntity(n);
		
		Assertions.assertThat(re.getStatusCode().value()).isEqualTo(200);
		Assertions.assertThat(re.getHeaders().getFirst("Content-Type")).isEqualTo("application/json");
		Assertions.assertThat(JsonUtil.getObjectMapper().readTree(re.getBody().toString()).path("foo").asText()).isEqualTo("bar");

		
	}
	
	@Test
	public void testInitialization() {
		SQSGatewayController controller = Trident.getApplicationContext().getBean(SQSGatewayController.class);
		Assertions.assertThat(controller.targetUrl).isEqualTo("http://localhost:8080");
		
		Assertions.assertThat(controller.installationId).isEqualTo(Trident.getApplicationContext().getBean(TridentClusterManager.class).getTridentInstallationId());
		
		
	}
}
