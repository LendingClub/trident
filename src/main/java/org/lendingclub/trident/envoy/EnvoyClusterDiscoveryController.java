package org.lendingclub.trident.envoy;

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
import com.google.common.base.Strings;

@Controller
@RequestMapping(value = "/api/trident/envoy")
public class EnvoyClusterDiscoveryController {

	Logger logger = LoggerFactory.getLogger(EnvoyClusterDiscoveryController.class);
	@Autowired
	EnvoyManager envoyManager;


	@RequestMapping(value = "/v1/clusters/{serviceCluster}/{serviceNode}", method = {
			RequestMethod.GET }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> clusterDiscovery(HttpServletRequest request, @PathVariable("serviceCluster") String serviceCluster,
			@PathVariable("serviceNode") String serviceNode) {

		// https://lyft.github.io/envoy/docs/configuration/cluster_manager/cds.html

		EnvoyClusterDiscoveryContext ctx = null;
		try {
			ctx = extractMetadata(request,serviceCluster, serviceNode);

			envoyManager.decorate(ctx);

			
			JsonNode data = JsonMetadataScrubber.scrub(ctx.getConfig());		
		
			return ResponseEntity.ok(JsonUtil.prettyFormat(data));
		} finally {
			if (ctx!=null) {
				ctx.log();
			}
		}
	}

	protected EnvoyClusterDiscoveryContext extractMetadata(HttpServletRequest request, String clusterName, String nodeName) {

		// We may end up looking at headers or other metadata, but envoy does
		// not support that yet. So we encode this
		// metadata in the name itself. We use double-dash because other natural
		// separator characters (colon for example)
		// are rejected by envoy.

		EnvoyClusterDiscoveryContext ctx = new EnvoyClusterDiscoveryContext();
		ctx.withServletRequest(request);
		Matcher m = Pattern.compile("(\\S+)--(\\S+)--(\\S+)--(\\S+)").matcher(Strings.nullToEmpty(clusterName).trim());
		if (m.matches()) {

			ctx.serviceZone = m.group(1);
			ctx.environment = m.group(2);
			ctx.subEnvironment = m.group(3);
			ctx.serviceCluster = m.group(4);
		} else {
			ctx.serviceCluster = clusterName;
		}

		ctx.serviceNode = nodeName;

		ctx.getConfig().set("clusters", JsonUtil.createArrayNode());
		return ctx;
	}
}
