package org.lendingclub.trident.envoy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.lendingclub.trident.util.JsonUtil;
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
public class EnvoyRouteDiscoveryController {

	@Autowired
	EnvoyManager envoyManager;

	@RequestMapping(value = "/v1/routes/{routeConfigName}/{serviceCluster}/{serviceNode}", method = {
			RequestMethod.GET }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> routeDiscovery(HttpServletRequest request,
			@PathVariable("routeConfigName") String routeConfig, @PathVariable("serviceCluster") String serviceCluster,
			@PathVariable("serviceNode") String serviceNode) {

		EnvoyRouteDiscoveryContext ctx = null;
		try {
			// https://lyft.github.io/envoy/docs/configuration/http_conn_man/rds.html
			ctx = extract(request,routeConfig, serviceCluster, serviceNode);

			envoyManager.decorate(ctx);
			return ctx.toResponseEntity();

		} finally {
			if (ctx != null) {
				ctx.log();
			}
		}
	}

	EnvoyRouteDiscoveryContext extract(HttpServletRequest request,String route, String serviceCluster, String serviceNode) {
		EnvoyRouteDiscoveryContext ctx = new EnvoyRouteDiscoveryContext().withServletRequest(request);

		ctx.serviceNode = serviceNode;
		ctx.serviceCluster = serviceCluster;
		ctx.routeName = route;
		Matcher m = Pattern.compile("(\\S+)--(\\S+)--(\\S+)--(\\S+)").matcher(Strings.nullToEmpty(serviceCluster));
		if (m.matches()) {
			ctx.serviceZone = m.group(1);
			ctx.environment = m.group(2);
			ctx.subEnvironment = m.group(3);
			ctx.serviceCluster = m.group(4);
		}

		return ctx;
	}
}
