package org.lendingclub.trident.haproxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.io.Closer;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Created by hasingh on 9/18/17.
 */

@Controller 
@RequestMapping(value="/api/trident/haproxy")
public class HAProxyDiscoveryController {

	Logger logger = LoggerFactory.getLogger(HAProxyDiscoveryController.class);

	ObjectMapper mapper = new ObjectMapper();

	@Autowired HAProxyManager HAProxyManager;

	@RequestMapping(value = "/v1/hosts", method = { RequestMethod.GET }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getHostInfo(
			@RequestParam("appId") String appId,
			@RequestParam("serviceCluster") String serviceCluster,
			@RequestParam("serviceNode") String serviceNode,
			@RequestParam("environment") String environment,
			@RequestParam("subEnvironment") String subEnvironment,
			@RequestParam("region") String region) {

		if(Strings.isNullOrEmpty(region)) {
			region="local";
		}

		logger.info("######### RECEIVED HOST INFO REQUEST FOR appId {} serviceCluster {}, serviceNode {},"
						+ " environment {}, subEnvironment {}, region {} ##############",
				appId, serviceCluster, serviceNode, environment, subEnvironment, region);

		HAProxyHostDiscoveryContext ctx = new HAProxyHostDiscoveryContext();

		ctx = ctx.withAppId(appId);

		ctx = ctx.withServiceCluster(serviceCluster);

		ctx = ctx.withServiceNode(serviceNode);

		ctx = ctx.withEnvironment(environment);

		ctx = ctx.withSubEnvironment(subEnvironment);

		ctx = ctx.withRegion(region);

		HAProxyManager.recordCheckIn(serviceNode, environment, subEnvironment, serviceCluster, region);

		HAProxyManager.decorate(ctx);

		return ResponseEntity.ok(JsonUtil.prettyFormat(ctx.getConfig()) );

	}


	@RequestMapping(value = "/v1/config-bundle", method = {
			RequestMethod.GET })
	public ResponseEntity<byte []> getBootstrapConfig(
			HttpServletRequest request,
			@RequestParam("serviceCluster") String serviceCluster,
			@RequestParam("serviceNode") String serviceNode,
			@RequestParam("environment") String environment,
			@RequestParam("subEnvironment") String subEnvironment,
			@RequestParam("region") String region) throws IOException, ZipException {

		if(Strings.isNullOrEmpty(region)) {
			region="local";
		}

		logger.info("######### RECEIVED CONFIG BUNDLE REQUEST FOR serviceCluster {}, serviceNode {},"
						+ " environment {}, subEnvironment {}, region {} ##############",
				serviceCluster, serviceNode, environment, subEnvironment, region);

		HAProxyConfigBundleDiscoveryContext ctx = new HAProxyConfigBundleDiscoveryContext();

		ctx = ctx.withServiceCluster(serviceCluster);

		ctx = ctx.withServiceNode(serviceNode);

		ctx = ctx.withEnvironment(environment);

		ctx = ctx.withSubEnvironment(subEnvironment);

		ctx = ctx.withRegion(region);

		ctx.withSkeleton(request);

		HAProxyManager.recordCheckIn(serviceNode, environment, subEnvironment, serviceCluster, region);
		HAProxyManager.decorate(ctx);

		Closer closer = Closer.create();
		File tempFile = null;
		try {
			HttpHeaders header = new HttpHeaders();
			header.setContentType(MediaType.parseMediaTypes("application/zip").get(0));
			String filename = "config-bundle.zip";
			header.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);

			tempFile = Files.createTempFile("tmp", "zip").toFile();
			tempFile.delete(); // delete the zero-byte file
			ZipFile zf = new ZipFile(tempFile);

			ZipParameters zp = new ZipParameters();
			zp.setSourceExternalStream(true);
			String envoyConfig = "config/haproxy.cfg.gsp";
			zp.setFileNameInZip(envoyConfig);


			ByteArrayInputStream bis = new ByteArrayInputStream(ctx.getConfig().getBytes());
			closer.register(bis);
			zf.addStream(bis, zp);
			
			if(ctx.getConfig().getBytes().length == 0) {
				// Existing code did not add the entry if it was zero-bytes.  It's not clear if there was a legitimate reason for that.
				// However this caused problems because the zip file was not created at all.  
				zf.removeFile("config/haproxy.cfg.gsp");
			}
			
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


	@RequestMapping(value = "/v1/config", method = {
			RequestMethod.GET })
	public ResponseEntity<String> getBootstrapConfigTemplate(
			HttpServletRequest request,
			@RequestParam("serviceCluster") String serviceCluster,
			@RequestParam("serviceNode") String serviceNode,
			@RequestParam("environment") String environment,
			@RequestParam("subEnvironment") String subEnvironment,
			@RequestParam("region") String region) throws IOException, ZipException {

		//serviceCluster=www&serviceNode=asdfasdf&environment=demo&subEnvironment=default&region=us-west-2

		if(Strings.isNullOrEmpty(region)) {
			region="local";
		}

		logger.info("######### RECEIVED CONFIG BUNDLE REQUEST FOR serviceCluster {}, serviceNode {},"
						+ " environment {}, subEnvironment {}, region {} ##############",
				serviceCluster, serviceNode, environment, subEnvironment, region);

		HAProxyConfigBundleDiscoveryContext ctx = new HAProxyConfigBundleDiscoveryContext();

		ctx = ctx.withServiceCluster(serviceCluster);

		ctx = ctx.withServiceNode(serviceNode);

		ctx = ctx.withEnvironment(environment);

		ctx = ctx.withSubEnvironment(subEnvironment);

		ctx = ctx.withRegion(region);

		ctx.withSkeleton(request);

		HAProxyManager.recordCheckIn(serviceNode, environment, subEnvironment, serviceCluster, region);

		HAProxyManager.decorate(ctx);

		return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(ctx.getConfig());

	}


	@RequestMapping(value = "/v1/cert", method = {
			RequestMethod.GET })
	public ResponseEntity<byte []> getCertMaterial(
			HttpServletRequest request,
			@RequestParam("serviceCluster") String serviceCluster,
			@RequestParam("serviceNode") String serviceNode,
			@RequestParam("environment") String environment,
			@RequestParam("subEnvironment") String subEnvironment,
			@RequestParam("region") String region) throws IOException, ZipException {

		if(Strings.isNullOrEmpty(region)) {
			region="local";
		}

		logger.info("######### RECEIVED cert REQUEST FOR serviceCluster {}, serviceNode {},"
						+ " environment {}, subEnvironment {}, region {} ##############",
				serviceCluster, serviceNode, environment, subEnvironment, region);

		HAProxyCertBundleDiscoveryContext ctx = new HAProxyCertBundleDiscoveryContext();

		ctx = ctx.withServiceCluster(serviceCluster);

		ctx = ctx.withServiceNode(serviceNode);

		ctx = ctx.withEnvironment(environment);

		ctx = ctx.withSubEnvironment(subEnvironment);

		ctx = ctx.withRegion(region);

		HAProxyManager.decorate(ctx);

		Closer closer = Closer.create();
		File tempFile = null;
		try {
			HttpHeaders header = new HttpHeaders();
			header.setContentType(MediaType.parseMediaTypes("application/zip").get(0));
			String filename = "cert-bundle.zip";
			header.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);

			tempFile = Files.createTempFile("tmp", "zip").toFile();
			tempFile.delete(); // delete the zero-byte file
			ZipFile zf = new ZipFile(tempFile);

			ZipParameters zp = new ZipParameters();
			zp.setSourceExternalStream(true);
			String envoyConfig = "config/cert.pem";
			zp.setFileNameInZip(envoyConfig);


			ByteArrayInputStream bis = new ByteArrayInputStream(ctx.getConfig().getBytes());
			closer.register(bis);
			zf.addStream(bis, zp);
			if(ctx.getConfig().getBytes().length > 0) {
				return ResponseEntity.ok().headers(header).body(com.google.common.io.Files.toByteArray(tempFile));
			}
			return ResponseEntity.noContent().build();

		} finally {
			if (tempFile != null) {
				tempFile.delete();
			}
			if (closer != null) {
				closer.close();
			}
		}

	}

	
}
