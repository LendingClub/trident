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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

@Controller
@RequestMapping(value = "/api/trident/envoy")
public class EnvoyListenerDiscoveryController {

	Logger logger = LoggerFactory.getLogger(EnvoyListenerDiscoveryController.class);
	
	@Autowired
	EnvoyManager envoyManager;
	

	@RequestMapping(value = "/v1/listeners/{serviceCluster}/{serviceNode}", method = { RequestMethod.GET }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> listenerDiscovery(HttpServletRequest request, @PathVariable("serviceCluster") String serviceCluster, @PathVariable("serviceNode") String serviceNode) {

		// https://lyft.github.io/envoy/docs/configuration/listeners/lds.html
		
		EnvoyListenerDiscoveryContext ctx = null;
		try {
			ctx = extract(request,serviceCluster,serviceNode);
			envoyManager.decorate(ctx);
		
		
			return ctx.toResponseEntity();
	
		}
		finally {
			ctx.log();
		}
	}
	
	EnvoyListenerDiscoveryContext extract(HttpServletRequest request, String serviceCluster, String serviceNode) {
		EnvoyListenerDiscoveryContext ctx = new EnvoyListenerDiscoveryContext().withServletRequest(request);
	
		ctx.serviceNode = serviceNode;
		ctx.serviceCluster=serviceCluster;

		Matcher m = Pattern.compile("(\\S+)--(\\S+)--(\\S+)--(\\S+)").matcher(Strings.nullToEmpty(serviceCluster));
		if (m.matches()) {
			ctx.serviceZone = m.group(1);
			ctx.environment = m.group(2);
			ctx.subEnvironment = m.group(3);
			ctx.serviceCluster = m.group(4);
		}
		ctx.getConfig().set("listeners", JsonUtil.createArrayNode());
		return ctx;
	}

}
