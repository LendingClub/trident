package org.lendingclub.haproxy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import io.macgyver.okrest3.OkRestClient;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Created by hasingh on 9/18/17.
 */
public class HostInfoUtils {
	org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HostInfoUtils.class);

	ObjectMapper mapper = new ObjectMapper();

	public static OkRestClient okRestClient = new OkRestClient.Builder().build();

	class HostInfo {
		String hostName;
		int port;
		int priority;
		boolean stickySessions;


		public HostInfo(String hostName, int port, int priority, boolean stickySessions) {

			this.hostName = hostName;
			this.port = port;
			this.priority = priority;
			this.stickySessions = stickySessions;

		}

		public String getHostName() {
			return hostName;
		}

		public int getPort() {
			return port;
		}

		public int getPriority() {
			return priority;
		}

		public boolean isStickySessions() {
			return stickySessions;
		}

	}

	public Optional<HostInfo> tryToGetHostInfoFromLocal(String appId) {

		String appIdWithoutHyphens = appId.replaceAll("-", "_");
		String expectedEndpoint = appIdWithoutHyphens+"_url";

		if( ! Strings.isNullOrEmpty( System.getenv(expectedEndpoint) ) ) {
			logger.info("found local endpoint {} for app {}", System.getenv(expectedEndpoint), appId);
			try {
				URL url = new URL(System.getenv(expectedEndpoint));

				if( url.getHost().equals("localhost") ) {

					return Optional.of(
							new HostInfo("docker.for.mac.localhost", url.getPort(),
									256, false) );
				}
			}
			catch (Exception e) {
				logger.info("problem getting local endpoint for {}", appId, e);
			}
		}
		return Optional.empty();

	}

	public List<HostInfo> getHostInfoForAppId(String appId) throws JsonProcessingException {

		logger.info("########## getting host info for {} ##########", appId);
		List<HostInfo> hostInfo = Lists.newArrayList();

		String tsdCluster = System.getenv("TSD_CLUSTER");
		String tsdEnv = System.getenv("TSD_ENV");
		String tsdSubenv = System.getenv("TSD_SUBENV");
		String tsdNode = MonitorDaemon.getTsdNode();
		String tsdRegion = System.getenv("TSD_REGION");

		Optional<HostInfo> localHostInfo = tryToGetHostInfoFromLocal(appId);

		if(localHostInfo.isPresent()) {
			return Lists.newArrayList(localHostInfo.get());
		}

		if(Strings.isNullOrEmpty(tsdRegion)) {
			tsdRegion = "local";
		}

		String tridentAPIBaseEndpoint = System.getenv("TSD_BASE_URL");
		JsonNode rawHostInfo = okRestClient
				.uri(tridentAPIBaseEndpoint+"/api/trident/haproxy/v1/hosts")
				.queryParam("appId", appId)
				.queryParam("serviceCluster", tsdCluster)
				.queryParam("serviceNode", tsdNode)
				.queryParam("environment", tsdEnv)
				.queryParam("subEnvironment", tsdSubenv)
				.queryParam("region", tsdRegion)
				.get().execute(JsonNode.class);


		logger.info("########## host info result {} ############",
				mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rawHostInfo));

		boolean stickySessions = rawHostInfo.path("stickySessions").asBoolean();

		try {
			Iterator<JsonNode> rawHostInfoIterator = ((ArrayNode) rawHostInfo.path("hosts")).iterator();

			while( rawHostInfoIterator.hasNext() ) {
				JsonNode host = rawHostInfoIterator.next();
				logger.info("####### host field name is {} ###########", host.path("host").asText(""));
				HostInfo currHost = new HostInfo(
						host.path("host").asText(),
						host.path("port").asInt(1234) ,
						host.path("priority").asInt(0),
						stickySessions);
				hostInfo.add(currHost);
			}
			logger.info("found {} hosts for {}", hostInfo.size(), appId);
			return hostInfo;
		} catch (Exception e) {
			return Lists.newArrayList();
		}
	}


}
