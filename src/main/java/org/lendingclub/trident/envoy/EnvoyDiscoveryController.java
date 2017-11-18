package org.lendingclub.trident.envoy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.io.Closer;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.model.ZipParameters;

@Controller
@RequestMapping(value = "/api/trident/envoy")
public class EnvoyDiscoveryController {

	Logger logger = LoggerFactory.getLogger(EnvoyDiscoveryController.class);

	
	@Autowired
	EnvoyManager envoyManager;
	
	
	@RequestMapping(value = "/config-bundle/{serviceCluster}/{serviceNode}", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<byte[]> configBundle(HttpServletRequest request,
			@PathVariable("serviceCluster") String serviceCluster, @PathVariable("serviceNode") String serviceNode)
			throws IOException, net.lingala.zip4j.exception.ZipException {

		Closer closer = Closer.create();
		File tempFile = null;
		try {
			HttpHeaders header = new HttpHeaders();
			header.setContentType(MediaType.parseMediaTypes("application/zip").get(0));
			String filename = "bundle.zip";
			header.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);

			tempFile = Files.createTempFile("tmp", "zip").toFile();
			tempFile.delete(); // delete the zero-byte file
			ZipFile zf = new ZipFile(tempFile);

			ZipParameters zp = new ZipParameters();
			zp.setSourceExternalStream(true);
			String envoyConfig = "config/envoy.cfg";
			zp.setFileNameInZip(envoyConfig);
			EnvoyBootstrapConfigContext  ctx = extractBootstrapConfigContext(request, serviceCluster, serviceNode);
			ctx.bundle = zf;
			envoyManager.invokeInterceptors(ctx);
			// now we decorate the response
			JsonNode data = JsonMetadataScrubber.scrub(ctx.getConfig());

			
			ByteArrayInputStream bis = new ByteArrayInputStream(JsonUtil.prettyFormat(data).getBytes());
			closer.register(bis);
			zf.addStream(bis, zp);
			return ResponseEntity.ok().headers(header).body(com.google.common.io.Files.toByteArray(tempFile));
		
		} finally {
			if (tempFile != null) {
				tempFile.delete();
			}
			if (closer != null) {
				closer.close();
			}
		}

	}

	@RequestMapping(value = "/config/{serviceCluster}/{serviceNode}", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> config(HttpServletRequest request,
			@PathVariable("serviceCluster") String serviceCluster, @PathVariable("serviceNode") String serviceNode) {

		Closer closer = Closer.create();
		EnvoyBootstrapConfigContext ctx = null;
		try {
			ctx = extractBootstrapConfigContext(request, serviceCluster, serviceNode);

			envoyManager.invokeInterceptors(ctx);
			// now we decorate the response
			JsonNode data = JsonMetadataScrubber.scrub(ctx.getConfig());

			return ResponseEntity.ok(JsonUtil.prettyFormat(data));
		} finally {
			try {
				closer.close();
			} catch (IOException e) {
				logger.warn("problem closing", e);
			}
			if (ctx != null) {
				ctx.log();
			}
		}
	}

	@RequestMapping(value = "/config-unified/{serviceCluster}/{serviceNode}", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> configUnified(HttpServletRequest request,
			@PathVariable("serviceCluster") String serviceCluster, @PathVariable("serviceNode") String serviceNode)
			throws IOException {

		ResponseEntity<String> r = config(request, serviceCluster, serviceNode);
		if (!r.getStatusCode().is2xxSuccessful()) {
			return ResponseEntity.status(r.getStatusCode()).build();
		}
		ObjectNode n = (ObjectNode) JsonUtil.getObjectMapper().readTree(r.getBody());

		UnifiedConfigBuilder b = new UnifiedConfigBuilder().withServiceCluster(serviceCluster)
				.withServiceNode(serviceNode).withEnvoyConfig(n);
		b.resolveAll();
		JsonNode data = JsonMetadataScrubber.scrub(b.getConfig());
		
	
		return ResponseEntity.ok(JsonUtil.prettyFormat(data));

	}

	EnvoyBootstrapConfigContext extractBootstrapConfigContext(HttpServletRequest request, String serviceCluster, String serviceNode) {
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
	
	
	@RequestMapping(value = "/v1/clusters/{serviceCluster}/{serviceNode}", method = {
			RequestMethod.GET }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> clusterDiscovery(HttpServletRequest request, @PathVariable("serviceCluster") String serviceCluster,
			@PathVariable("serviceNode") String serviceNode) {

		// https://lyft.github.io/envoy/docs/configuration/cluster_manager/cds.html

		EnvoyClusterDiscoveryContext ctx = null;
		try {
			ctx = extractClusterDiscoveryContext(request,serviceCluster, serviceNode);

			envoyManager.invokeInterceptors(ctx);

			
			
			JsonNode data = JsonMetadataScrubber.scrub(ctx.getConfig());		
		
			envoyManager.record(ctx);
			
			return ResponseEntity.ok(JsonUtil.prettyFormat(data));
		} finally {
			if (ctx!=null) {
				ctx.log();
			}
		}
	}

	protected EnvoyClusterDiscoveryContext extractClusterDiscoveryContext(HttpServletRequest request, String clusterName, String nodeName) {

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
	
	
	@RequestMapping(value = "/v1/listeners/{serviceCluster}/{serviceNode}", method = { RequestMethod.GET }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> listenerDiscovery(HttpServletRequest request, @PathVariable("serviceCluster") String serviceCluster, @PathVariable("serviceNode") String serviceNode) {

		// https://lyft.github.io/envoy/docs/configuration/listeners/lds.html
		
		EnvoyListenerDiscoveryContext ctx = null;
		try {
			ctx = extractListenerDiscoveryContext(request,serviceCluster,serviceNode);
			envoyManager.decorate(ctx);
		
			envoyManager.record(ctx);
			
			return ctx.toResponseEntity();
	
		}
		finally {
			ctx.log();
		}
	}
	
	EnvoyListenerDiscoveryContext extractListenerDiscoveryContext(HttpServletRequest request, String serviceCluster, String serviceNode) {
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
	
	
	@RequestMapping(value = "/v1/routes/{routeConfigName}/{serviceCluster}/{serviceNode}", method = {
			RequestMethod.GET }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> routeDiscovery(HttpServletRequest request,
			@PathVariable("routeConfigName") String routeConfig, @PathVariable("serviceCluster") String serviceCluster,
			@PathVariable("serviceNode") String serviceNode) {

		EnvoyRouteDiscoveryContext ctx = null;
		try {
			// https://lyft.github.io/envoy/docs/configuration/http_conn_man/rds.html
			ctx = extractRouteDiscoveryContext(request,routeConfig, serviceCluster, serviceNode);

			envoyManager.invokeInterceptors(ctx);
			
			envoyManager.record(ctx);
			
			return ctx.toResponseEntity();

		} finally {
			if (ctx != null) {
				ctx.log();
			}
		}
	}

	EnvoyRouteDiscoveryContext extractRouteDiscoveryContext(HttpServletRequest request,String route, String serviceCluster, String serviceNode) {
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
	

	@RequestMapping(value = "/v1/registration/{serviceName}", method = {
			RequestMethod.GET }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> serviceDiscovery(HttpServletRequest request,
			@PathVariable("serviceName") String serviceName) {

		EnvoyServiceDiscoveryContext ctx = null;
		try {
			ctx = extractServiceDiscoveryContext(request, serviceName);
			com.google.common.base.Preconditions.checkNotNull(ctx);
			envoyManager.invokeInterceptors(ctx);
			// https://lyft.github.io/envoy/docs/configuration/cluster_manager/sds.html

			envoyManager.record(ctx);
			
			return ctx.toResponseEntity();
		} finally {
			if (ctx != null) {
				ctx.log();
			}
		}

	}

	protected EnvoyServiceDiscoveryContext extractServiceDiscoveryContext(HttpServletRequest request, String serviceName) {

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
