package org.lendingclub.trident.plugin.authz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class ProxyClientTest {

  MockWebServer mockServer = new MockWebServer();
  ObjectMapper mapper = new ObjectMapper();

  @Test
  public void testAuthZReq() throws InterruptedException {

    mockServer.enqueue(new MockResponse().setBody("{}"));

    ProxyClient client = new ProxyClient();
    client.withTridentUrl(mockServer.url("/").toString());

    ObjectNode data = mapper.createObjectNode();

    client.proxyRequest("/AuthZPlugin.AuthZReq", data);

    RecordedRequest rr = mockServer.takeRequest();

    Assertions.assertThat(rr.getPath())
        .isEqualTo("/api/trident/docker-authz-plugin/AuthZPlugin.AuthZReq");
  }

  @Test
  public void testAuthZRes() {

    mockServer.enqueue(new MockResponse().setBody("{}"));

    ProxyClient client = new ProxyClient();
    client.withTridentUrl(mockServer.url("/").toString());

    ObjectNode data = mapper.createObjectNode();

    client.proxyRequest("/AuthZPlugin.AuthZRes", data);
  }
}
