package org.lendingclub.trident.plugin.authz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import io.macgyver.okrest3.OkRestClient;
import io.macgyver.okrest3.OkRestTarget;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;

public class ProxyClient {

  public static boolean FAIL_OPEN_BY_DEFAULT = true;
  ObjectMapper mapper = new ObjectMapper();
  OkRestClient client;

  boolean failOpen = FAIL_OPEN_BY_DEFAULT;
  String tridentUrl = null;
  Logger logger = LoggerFactory.getLogger(ProxyClient.class);

  public ProxyClient() {

    withEnvironmentConfig();
  }

  public ProxyClient withTridentUrl(String url) {
    this.tridentUrl = url;
    return this;
  }

  public ProxyClient withFailOpen(boolean b) {
    this.failOpen = b;
    return this;
  }

  public ProxyClient withEnvironmentConfig() {
    withTridentUrl(System.getenv("TRIDENT_URL"));

    String val = System.getenv("FAIL_OPEN");
    this.failOpen = (val == null) || (!val.trim().toLowerCase().equals("false"));

    return this;
  }

  public ProxyClient initIfNecessary() {
    if (client != null) {
      return this;
    }
    client =
        new OkRestClient.Builder()
            .withOkHttpClientConfig(
                x -> {
                  x.connectTimeout(5, TimeUnit.SECONDS)
                      .readTimeout(5, TimeUnit.SECONDS)
                      .writeTimeout(5, TimeUnit.SECONDS);
                })
            .withOkRestClientConfig(
                cc -> {
                  cc.disableCertificateVerification();
                })
            .build();
    return this;
  }

  public JsonNode fail(String msg, Exception e) {

    boolean allow = failOpen;

    if (e != null) {
      logger.warn(e.toString());
      return mapper.createObjectNode().put("Allow", allow).put("Msg", msg).put("Err", e.toString());
    } else {
      logger.warn("problem", e);
      return mapper.createObjectNode().put("Allow", allow).put("Msg", msg).put("Err", msg);
    }
  }

  public JsonNode proxyRequest(Request request) {
    try {
      JsonNode requestJson = mapper.readTree(request.body());

      return proxyRequest(request.pathInfo(), requestJson);
    } catch (IOException e) {
      return fail("auhtz error", e);
    }
  }

  public JsonNode proxyRequest(String path, JsonNode n) {
    try {

      System.getenv()
          .entrySet()
          .forEach(
              it -> {
                logger.info("ENV - {}: {}", it.getKey(), it.getValue());
              });
      initIfNecessary();
      if (client == null) {
        return fail("trident client not set", null);
      }

      if (Strings.isNullOrEmpty(tridentUrl)
          || tridentUrl.contains("example.con")
          || (!tridentUrl.startsWith("http"))) {
        return fail("TRIDENT_URL not set", null);
      }
      String user = n.path("User").asText();
      String authenticationMethod = n.path("UserAuthNMethod").asText();
      String uri = n.path("RequestUri").asText();
      String method = n.path("RequestMethod").asText();
      logger.info(
          "POST {} user={} authMethod={} method={} uri={}",
          path,
          user,
          authenticationMethod,
          method,
          uri);

      OkRestTarget t = client.url(tridentUrl).path("/api/trident/docker-authz-plugin").path(path);
      JsonNode tridentResponse = t.contentType("application/json").post(n).execute(JsonNode.class);
      return tridentResponse;
    } catch (RuntimeException e) {
      logger.info("problem", e);
      return fail("authz_request_error", e);
    }
  }
}
