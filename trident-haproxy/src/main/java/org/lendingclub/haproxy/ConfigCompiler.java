package org.lendingclub.haproxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import groovy.text.SimpleTemplateEngine;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by hasingh on 11/14/16.
 */
@Component
public class ConfigCompiler implements Runnable {

	Logger logger = LoggerFactory.getLogger(ConfigCompiler.class);

	static ObjectMapper objectMapper = new ObjectMapper();

	public String templateDataDirectory;
	public String appDir;

	public static final String HAPROXY_CERT_PATH="/trident-haproxy/config/cert.pem";

	public static final String HAPROXY_CONFIG_PATH="/trident-haproxy/config/haproxy.cfg";

	public static final String HAPROXY_CONFIG_TEMPLATE_PATH="/trident-haproxy/config/haproxy.cfg.gsp";

	public static final String HAPROXY_CONFIG_DIR="/trident-haproxy";

//	@Autowired
//	EndpointResolver endpointResolver;

	public String serversFilePath;
	public String routesFilePath;
	public String templateFilePath;
	public String configDir;
	public String certFilePath;

	@Autowired
	public ConfigCompiler(
			@Value("${TemplateDataDirectory:#{null}}") String templateDataDirectory,
			@Value("${tc.app.dir:#{null}}") String appDir,
			@Value("${serversFilePath:#{null}}") String serversFilePath,
			@Value("${routesFilePath:#{null}}") String routesFilePath,
			@Value("${templateFilePath:#{null}}") String templateFilePath,
			@Value("${configDir:#{null}}") String configDir,
			@Value("${certFilePath:#{null}}") String certFilePath) {

		this.templateDataDirectory = templateDataDirectory;
		this.appDir = appDir;
		this.serversFilePath = serversFilePath;
		this.routesFilePath = routesFilePath;
		this.templateFilePath = templateFilePath;
		this.configDir = configDir;
		this.certFilePath = certFilePath;
	}

	public String getDataDirectoryPath() {
//		if(templateDataDirectory == null) {
//			return appDir+"/remote-data";
//		}
//		return appDir+"/"+templateDataDirectory;
		return configDir;
	}


	public void addSortedPathListWithApps(ObjectNode configDataJson) throws JsonProcessingException{
		ArrayNode allApps = (ArrayNode) configDataJson.path("apps");

		ImmutableSortedMap.Builder pathsAndApps = new ImmutableSortedMap.Builder<String, String>(Collections.reverseOrder());

		for(JsonNode currApp: allApps) {
			logger.info( "app: {}",
					new ObjectMapper()
							.writerWithDefaultPrettyPrinter()
							.writeValueAsString((Object) currApp) );
			ArrayNode currAppPaths = (ArrayNode) currApp.path("paths");
			currAppPaths.forEach( appPath -> {
				pathsAndApps.put(appPath.asText(""), currApp.path("appId").asText(""));
			});
		}

		ImmutableSortedMap<String, String> sortedPathsAndApps = pathsAndApps.build();

		ArrayNode allPathsAndApps = objectMapper.createArrayNode();

		sortedPathsAndApps.forEach((path, appId) -> {
			allPathsAndApps.add( objectMapper.createObjectNode().put("path", path).put("appId", appId) );
		});

		configDataJson.set("sortedPathsAndApps", allPathsAndApps);

	}

	public void addHashesForAllServers(ObjectNode configDataJson) {
		ArrayNode allApps = (ArrayNode) configDataJson.path("apps");
		ObjectNode serversAndHashes = objectMapper.createObjectNode();

		for(JsonNode currApp: allApps) {
			ArrayNode liveNodes = (ArrayNode) currApp.path("live");
			for(JsonNode server: liveNodes) {
				serversAndHashes.put(server.asText(""), Hashing.sha1().newHasher().putString(server.asText(""), Charset.defaultCharset()).hash().toString());
			}
			ArrayNode darkNodes = (ArrayNode) currApp.path("dark");
			for(JsonNode server: darkNodes) {
				serversAndHashes.put(server.asText(""), Hashing.sha1().newHasher().putString(server.asText(""), Charset.defaultCharset()).hash().toString());
			}
		}
		configDataJson.set("serversAndHashes", serversAndHashes);
	}

	public ArrayNode enhanceServerArrayWithIndexesAndHashes(ArrayNode servers) {
		ArrayNode serversWithIndexesAndHashes = objectMapper.createArrayNode();
		for(int i = 0; i < servers.size(); i++) {
			ObjectNode serverWithHashAndIndex = objectMapper.createObjectNode();
			String server = servers.get(i).asText("");
			serverWithHashAndIndex.put("server", server);
			serverWithHashAndIndex.put("index", i);
			serverWithHashAndIndex.put("hash", Hashing.sha1().newHasher().putString(server,
					Charset.defaultCharset()).hash().toString());
			serversWithIndexesAndHashes.add(serverWithHashAndIndex);
		}
		return serversWithIndexesAndHashes;
	}

	public ObjectNode getServersInfoFromEtcd(String appId) {
//		String endpoint = endpointResolver.getEndpointFor(appId);
		URL url = null;
		try {
//			url = new URL(endpoint);
			ObjectNode result = objectMapper.createObjectNode();
			result = result.put("appId", appId);
			result = result.put("livePool", "A");
			ObjectNode serversNode = objectMapper.createObjectNode();
			serversNode.set("A", objectMapper.createArrayNode().add(url.getHost()+":8080"));
			serversNode.set("B", objectMapper.createArrayNode());
			result = (ObjectNode) result.set("servers", serversNode);
			return result;
		}
		catch (Exception e) {
			throw new IllegalStateException("Endpoint not found in etcd for "+ appId);
		}
	}

	public ObjectNode getServersInfoForApp(String appId, ObjectNode servers) {

		//try to get servers info from servers json file first
		try {
			ArrayNode serversInfo = ( (ArrayNode) servers.path("apps"));
			for(int i = 0; i < serversInfo.size(); i++) {
				JsonNode app = serversInfo.get(i);
				if(app.path("appId").asText("").equals(appId)) {
					return (ObjectNode) app;
				}
			}
		}
		catch (Exception e) {
			logger.info("cannot get info from servers file trying etcd resolution", e);
		}

		//try to get servers info from etcd since it couldn't be found in servers file
		try {
			return getServersInfoFromEtcd(appId);
		}
		catch (IllegalStateException e) {
			throw e;
		}
	}

	public void addIndexAndHashesForAllServers(ObjectNode configDataJson, ObjectNode servers) throws JsonProcessingException {
		ArrayNode allApps = (ArrayNode) configDataJson.path("apps");

		for(int i = 0; i < allApps.size(); i++) {
			ObjectNode currApp = getServersInfoForApp(allApps.get(i).path("appId").asText(""), servers);
			logger.info( "appServer info: {}", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString( currApp) );
			String livePool = currApp.path("livePool").asText("");
			ArrayNode liveNodes = (ArrayNode) currApp.path("servers").path(livePool);
			ArrayNode liveNodesWithIndexesAndHashes = enhanceServerArrayWithIndexesAndHashes(liveNodes);
			((ObjectNode) allApps.get(i)).set("live", liveNodesWithIndexesAndHashes);
			String darkPool = "B";
			if(livePool.equals("B")) darkPool = "A";
			ArrayNode darkNodes = (ArrayNode) currApp.path("servers").path(darkPool);
			ArrayNode darkNodesWithIndexesAndHashes = enhanceServerArrayWithIndexesAndHashes(darkNodes);
			((ObjectNode) allApps.get(i)).set("dark", darkNodesWithIndexesAndHashes);
		}
	}

//	public void addEndpointsFromServersFile(ObjectNode finalData, ObjectNode serversData) {
//
//	}

//	public void addSortedPathListWithApps(ObjectNode ) {
//
//	}

	/** public void run() {
		try {
//			Reader configDataReader = new FileReader(getDataDirectoryPath()+"/data.json");
			Map<String, Object> data = Maps.newHashMap();
			logger.info( "########### config compilation result ######### {}", new ModelAndView(
					"/Users/hasingh/code/escher/oss"
							+ "/trident/trident-haproxy/haproxy"
							+ "/config/haproxy.cfg", data).getView() );



			new SimpleTemplateEngine().createTemplate().make();

			Reader serversDataReader = new FileReader( serversFilePath );
			Reader configTemplateReader = new FileReader( templateFilePath );
			Reader routesReader = new FileReader( routesFilePath );
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode jsonNode = (ObjectNode) mapper.readTree(routesReader);
			ObjectNode serversJson = (ObjectNode) mapper.readTree(serversDataReader);

			addSortedPathListWithApps(jsonNode);
			addIndexAndHashesForAllServers(jsonNode, serversJson);
			jsonNode  = jsonNode.put("certAbsolutePath",certFilePath);
			Files.write(new File("enhanced-data.json").toPath(), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode).getBytes());
			Map jsonAsMap = new ObjectMapper().convertValue(jsonNode, Map.class);

			com.samskivert.mustache.Template tmpl = Mustache.compiler().defaultValue("dne").compile(configTemplateReader);
			String compiledTemplate = tmpl.execute(jsonAsMap);

			Writer configWriter = new FileWriter(new File(getDataDirectoryPath(), "haproxy.cfg"));

			boolean configFileExists = Files.exists(new File(getDataDirectoryPath(), "haproxy.cfg").toPath());
			String currentConfigContents = null;
			if(configFileExists) {
				Reader currentConfigReader = new FileReader(new File(getDataDirectoryPath(), "haproxy.cfg"));

				currentConfigContents = org.apache.commons.io.IOUtils.toString(currentConfigReader);
				currentConfigReader.close();
			}

			if(!configFileExists || !compiledTemplate.equals(currentConfigContents) ) {
				configWriter.append(compiledTemplate);
				configWriter.close();
			}
			configTemplateReader.close();

		}
		catch(Exception e) {
			logger.info("Problem recompiling config template ", e);
		}
	} */

	public String getCurrConfigContents() {

		String currConfigContents = "";

		try {
			Reader currentConfigReader = new FileReader("/trident-haproxy/config/haproxy.cfg");
			currConfigContents = IOUtils.toString(currentConfigReader);
		}
		catch (Exception e) {
			logger.info("problem getting curr config contents ", e);
		}
		return currConfigContents;
	}

	public void run() {

		try {
			String currConfigContents = getCurrConfigContents();

			String compiledTemplate = new SimpleTemplateEngine()
					.createTemplate(
							new File("/trident-haproxy/config/haproxy.cfg.gsp"))
					.make(Maps.newHashMap()).toString();
			logger.info("######## COMPILED TEMPLATE IS {} #############", compiledTemplate);

			if(!currConfigContents.equals(compiledTemplate)) {
				Files.write(Paths.get("/trident-haproxy/config/haproxy.cfg"), compiledTemplate.getBytes());
			}
			else {
				logger.info("config hasn't changed. no need to rewrite");
			}
		}
		catch (Exception e) {
			logger.info("######## CANNOT COMPILE TEMPLATE  ##########", e);
		}
	}
}
