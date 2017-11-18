package org.lendingclub.trident.envoy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import javax.servlet.http.HttpServletRequest;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharStreams;

@Controller
@RequestMapping(value = "/api/trident/envoy")
public class EnvoyAdminRelayController {

	ObjectMapper mapper = new ObjectMapper();
	Logger logger = LoggerFactory.getLogger(EnvoyAdminRelayController.class);

	@Autowired
	NeoRxClient neo4j;
	
	@RequestMapping(value = "/admin-relay/{node}/listeners", method = {
			RequestMethod.POST }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> listeners(HttpServletRequest request, @PathVariable("node") String node)
			throws IOException {

		try (InputStream inputStream = request.getInputStream()) {
			JsonNode listeners = mapper.readTree(inputStream);

			// data is simple, like this: ["0.0.0.0:5080"]

			logger.info("listeners for {}: {}", node, listeners);
			return ResponseEntity.ok(JsonUtil.createObjectNode().put("status", "ok"));
		}
	}

	@RequestMapping(value = "/admin-relay/{node}/clusters", method = {
			RequestMethod.POST }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> clusters(HttpServletRequest request, @PathVariable("node") String node)
			throws IOException {

		// group--env--subenv--app::default_priority::max_connections::1024
		// group--env--subenv--app::default_priority::max_pending_requests::1024
		// group--env--subenv--app::default_priority::max_requests::1024
		// group--env--subenv--app::default_priority::max_retries::3
		// group--env--subenv--app::high_priority::max_connections::1024
		// group--env--subenv--app::high_priority::max_pending_requests::1024
		// group--env--subenv--app::high_priority::max_requests::1024
		// group--env--subenv--app::high_priority::max_retries::3
		// group--env--subenv--app::0.0.0.0:0::cx_active::0
		// group--env--subenv--app::0.0.0.0:0::cx_connect_fail::0
		// group--env--subenv--app::0.0.0.0:0::cx_total::0
		// group--env--subenv--app::0.0.0.0:0::rq_active::0
		// group--env--subenv--app::0.0.0.0:0::rq_timeout::0
		// group--env--subenv--app::0.0.0.0:0::rq_total::0
		// group--env--subenv--app::0.0.0.0:0::health_flags::healthy
		// group--env--subenv--app::0.0.0.0:0::weight::1
		// group--env--subenv--app::0.0.0.0:0::zone::
		// group--env--subenv--app::0.0.0.0:0::canary::false
		// group--env--subenv--app::0.0.0.0:0::success_rate::-1
		long lineCount = 0;
		try (BufferedReader br = request.getReader()) {
			String line = null;
			while ((line = br.readLine()) != null) {
				lineCount++;
				// dump it out
			}
		
			
		}
		
		return ResponseEntity.ok(JsonUtil.createObjectNode().put("status", "ok"));
	}

	@RequestMapping(value = "/admin-relay/{node}/routes", method = {
			RequestMethod.POST }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> routes(HttpServletRequest request, @PathVariable("node") String node)
			throws IOException {

		try (InputStream inputStream = request.getInputStream()) {
			JsonNode routes = mapper.readTree(inputStream);

			
		}
		return ResponseEntity.ok(JsonUtil.createObjectNode().put("status", "ok"));
	}

	@RequestMapping(value = "/admin-relay/{node}/certs", method = {
			RequestMethod.POST }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> certs(HttpServletRequest request, @PathVariable("node") String node) throws IOException {
		//{
		//	"ca_cert": "Certificate Path: /envoy/config/ca-certificates.crt, Serial Number: b92f60cc889fa17a4609b85b706c8aaf, Days until Expiration: 3963",
		//	"cert_chain": ""
		//}
		try (Reader reader = request.getReader()) {
			JsonNode certInfo = mapper.readTree(reader);
			JsonUtil.logInfo(getClass(), "certs", certInfo);
		}
		return ResponseEntity.ok(JsonUtil.createObjectNode().put("status", "ok"));
	}

	@RequestMapping(value = "/admin-relay/{node}/stats", method = {
			RequestMethod.POST }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> stats(HttpServletRequest request, @PathVariable("node") String node) throws IOException {

		try (BufferedReader br = request.getReader()) {
			String line = null;
			while ((line = br.readLine()) != null) {
				// dump it out
			}
			
		}
		
		return ResponseEntity.ok(JsonUtil.createObjectNode().put("status", "ok"));
	}

	@RequestMapping(value = "/admin-relay/{node}/server_info", method = {
			RequestMethod.POST }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> serverInfo(HttpServletRequest request, @PathVariable("node") String node) throws IOException {

	
		try (BufferedReader br = request.getReader()) {
			String line = br.readLine().trim();
			CharStreams.exhaust(br);
			logger.info("server_info: {}",line);
			neo4j.execCypher("match (x:EnvoyInstance {node:{node}}) set x.serverInfo={serverInfo}","node",node,"serverInfo",line);
			
		}
		return ResponseEntity.ok(JsonUtil.createObjectNode().put("status", "ok"));
	}

}
