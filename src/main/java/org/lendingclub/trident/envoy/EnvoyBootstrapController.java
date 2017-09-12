package org.lendingclub.trident.envoy;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.io.Closer;

@Controller
@RequestMapping(value = "/api/trident/envoy")
public class EnvoyBootstrapController {

	static Logger logger = LoggerFactory.getLogger(EnvoyBootstrapController.class);

	@Autowired
	EnvoyManager envoyManager;

	@RequestMapping(value = "/config/{serviceCluster}/{serviceNode}", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> config(HttpServletRequest request,
			@PathVariable("serviceCluster") String serviceCluster, @PathVariable("serviceNode") String serviceNode) {

		Closer closer = Closer.create();
		EnvoyBootstrapConfigContext ctx = null;
		try {
			ctx = extract(request, serviceCluster, serviceNode);
			
			envoyManager.decorate(ctx);
			// now we decorate the response
			JsonNode data = JsonMetadataScrubber.scrub(ctx.getConfig());
		
			return ResponseEntity.ok(data);
		} finally {
			try {
				closer.close();
			} catch (IOException e) {
				logger.warn("problem closing", e);
			}
			if (ctx!=null) {
				ctx.log();
			}
		}
	}

	@RequestMapping(value = "/unified-config/{serviceCluster}/{serviceNode}", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> unifiedConfig(HttpServletRequest request,
			@PathVariable("serviceCluster") String serviceCluster, @PathVariable("serviceNode") String serviceNode) {

		

			ResponseEntity<JsonNode> r = config(request, serviceCluster, serviceNode);
			if (!r.getStatusCode().is2xxSuccessful()) {
				return ResponseEntity.status(r.getStatusCode()).build();
			}
			ObjectNode n = (ObjectNode) r.getBody();

			UnifiedConfigBuilder b = new UnifiedConfigBuilder().withServiceCluster(serviceCluster)
					.withServiceNode(serviceNode).withEnvoyConfig(n);
			b.resolveAll();
			JsonNode data = JsonMetadataScrubber.scrub(b.getConfig());
			logger.info("unified config - \n{}",JsonUtil.prettyFormat(data));
			return ResponseEntity.ok(data);
		

	}

	EnvoyBootstrapConfigContext extract(HttpServletRequest request, String serviceCluster, String serviceNode) {
		EnvoyBootstrapConfigContext ctx = new EnvoyBootstrapConfigContext();
		ctx.withServletRequest(request);
		ctx.withSkeleton(request);
		ctx.serviceNode = serviceNode;
		ctx.serviceCluster = serviceCluster;
		

		Matcher m = Pattern.compile("(\\S+)--(\\S+)--(\\S+)--(\\S+)").matcher(Strings.nullToEmpty(serviceCluster));
		if (m.matches()) {
			ctx.serviceZone = m.group(1);
			ctx.environment = m.group(2);
			ctx.subEnvironment = m.group(3);
			ctx.serviceCluster = m.group(4);
		}

		return ctx;
	}

	protected static String getBaseUrl(HttpServletRequest request) {

		List<String> list = Splitter.on("://").splitToList(request.getRequestURL().toString());

		String protocol = list.get(0);
		String hostAndPort = Splitter.on("/").splitToList(list.get(1)).get(0);
		return protocol + "://" + hostAndPort;

	}

}
