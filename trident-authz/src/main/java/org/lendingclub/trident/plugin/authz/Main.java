package org.lendingclub.trident.plugin.authz;

import static spark.Spark.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  static ObjectMapper mapper = new ObjectMapper();

  public static void main(String[] args) {

    Logger logger = LoggerFactory.getLogger(Main.class);

    ProxyClient proxyClient = new ProxyClient();

    post(
        "/Plugin.Activate",
        (request, response) -> {
          logger.info("POST {}", request.pathInfo());

          return "{\"Implements\":[\"authz\"]}";
        });

    post(
        "/AuthZPlugin.AuthZReq",
        (request, response) -> {
          JsonNode n = proxyClient.proxyRequest(request);

          return n.toString();
        });
    post(
        "/AuthZPlugin.AuthZRes",
        (request, response) -> {
          JsonNode n = proxyClient.proxyRequest(request);

          return n.toString();
        });
  }
}
