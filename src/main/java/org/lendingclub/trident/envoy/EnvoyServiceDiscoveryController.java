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

import com.google.common.base.Strings;

@Controller
@RequestMapping(value = "/api/trident/envoy")
public class EnvoyServiceDiscoveryController {

	static Logger logger = LoggerFactory.getLogger(EnvoyServiceDiscoveryController.class);

	@Autowired
	EnvoyManager envoyManager;

	@RequestMapping(value = "/v1/registration/{serviceName}", method = {
			RequestMethod.GET }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> serviceDiscovery(HttpServletRequest request,
			@PathVariable("serviceName") String serviceName) {

		EnvoyServiceDiscoveryContext ctx = null;
		try {
			ctx = extractMetadata(request, serviceName);
			com.google.common.base.Preconditions.checkNotNull(ctx);
			envoyManager.decorate(ctx);
			// https://lyft.github.io/envoy/docs/configuration/cluster_manager/sds.html

			return ctx.toResponseEntity();
		} finally {
			if (ctx != null) {
				ctx.log();
			}
		}

	}

	protected EnvoyServiceDiscoveryContext extractMetadata(HttpServletRequest request, String serviceName) {

		// We may end up looking at headers or other metadata, but envoy does
		// not support that yet. So we encode this
		// metadata in the name itself. We use double-dash because other natural
		// separator characters (colon for example)
		// are rejected by envoy.

		EnvoyServiceDiscoveryContext ctx = new EnvoyServiceDiscoveryContext().withServletRequest(request);
		Matcher m = Pattern.compile("(\\S+)--(\\S+)--(\\S+)--(\\S+)--(\\S+)")
				.matcher(Strings.nullToEmpty(serviceName).trim());
		if (m.matches()) {
			ctx.serviceZone = m.group(1);
			ctx.environment = m.group(2);
			ctx.subEnvironment = m.group(3);
			ctx.serviceCluster = m.group(4);
			ctx.serviceName = m.group(5);
		} else {
			ctx.serviceName = serviceName;
		}
		ctx.getConfig().set("hosts", JsonUtil.createArrayNode());

		return ctx;
	}

}
