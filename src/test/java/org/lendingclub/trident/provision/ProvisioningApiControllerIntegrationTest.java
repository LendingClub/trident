package org.lendingclub.trident.provision;

import java.util.List;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.SwarmNodeType;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.crypto.CryptoService;
import org.lendingclub.trident.provision.ProvisioningApiController.SwarmToken;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;

public class ProvisioningApiControllerIntegrationTest extends TridentIntegrationTest {

	Logger logger = LoggerFactory.getLogger(ProvisioningApiControllerIntegrationTest.class);
	@Autowired
	public ProvisioningApiController controller;

	@Autowired
	public NeoRxClient neo4j;

	@Autowired
	public CryptoService cryptoService;

	@Autowired
	public ProvisioningManager provisioningManager;

	List<String> testClusterIdQueue = Lists.newArrayList();

	@Test
	public void testNodeInitMissingClusterId() {

		MockHttpServletRequest r = new MockHttpServletRequest();
		ResponseEntity<String> re = controller.nodeInit(r);

		Assertions.assertThat(re.getStatusCode().is2xxSuccessful()).isTrue();
		String body = re.getBody();

		Assertions.assertThat(body).startsWith("#!/bin/bash").contains("sleep")
				.contains("/api/trident/provision/node-init");

	}

	@After
	public void deleteTestClusters() {
		testClusterIdQueue.forEach(it -> {

			neo4j.execCypher("match (c:DockerSwarm {tridentClusterId:{id}}) delete c", "id", it);

		});
	}

	public void enqueueClusterDelete(String id) {
		testClusterIdQueue.add(id);
	}

	@Test
	public void testNodeInitWithInvalidClusterId() {

		MockHttpServletRequest r = new MockHttpServletRequest();
		r.addParameter("id", "junit-" + System.currentTimeMillis());
		ResponseEntity<String> re = controller.nodeInit(r);

		Assertions.assertThat(re.getStatusCode().is4xxClientError()).isTrue();

		Assertions.assertThat(re.getStatusCode().is4xxClientError());
		// Assertions.assertThat(body).startsWith("#!/bin/bash").contains("sleep").contains("/api/trident/provision/node-init");

	}



	@Test
	public void testNodeInitJoinWorker() {
		String clusterId = createTestCluster("127.0.0.1:2377", "mgr", "foo");

		{
			MockHttpServletRequest r = new MockHttpServletRequest();
			r.addParameter("id", clusterId);

			ResponseEntity<String> re = controller.nodeInit(r);

			Assertions.assertThat(re.getStatusCode().is2xxSuccessful()).isTrue();
			String body = re.getBody();

			Assertions.assertThat(body).startsWith("#!/bin/bash").contains("id=" + clusterId)
					.contains("/api/trident/provision/docker-install").contains("nodeType=WORKER");

			Assertions
					.assertThat(
							controller.getProvisioningManager().findTridentCluster(clusterId).get().path("tridentClusterId").asText())
					.isEqualTo(clusterId);
			Assertions
					.assertThat(
							controller.getProvisioningManager().findTridentCluster(clusterId).get().path("tridentClusterId").asText())
					.isEqualTo(clusterId);
		}

		{
			MockHttpServletRequest r = new MockHttpServletRequest();
			r.addParameter("id", clusterId);
			r.addParameter("nodeType", "WORKER");
			ResponseEntity<String> re = controller.nodeInit(r);

			Assertions.assertThat(re.getStatusCode().is2xxSuccessful()).isTrue();
			String body = re.getBody();

			Assertions.assertThat(body).startsWith("#!/bin/bash").contains("id=" + clusterId)
					.contains("/api/trident/provision/docker-install").contains("nodeType=WORKER");

			Assertions
					.assertThat(
							controller.getProvisioningManager().findTridentCluster(clusterId).get().path("tridentClusterId").asText())
					.isEqualTo(clusterId);
			Assertions
					.assertThat(
							controller.getProvisioningManager().findTridentCluster(clusterId).get().path("tridentClusterId").asText())
					.isEqualTo(clusterId);
		}
	}

	@Test
	public void testNodeInitJoinManager() {
		String clusterId = createTestCluster("127.0.0.1:2377", "mgr", "foo");

		MockHttpServletRequest r = new MockHttpServletRequest();
		r.addParameter("id", clusterId);
		r.addParameter("nodeType", "MANAGER");
		ResponseEntity<String> re = controller.nodeInit(r);

		Assertions.assertThat(re.getStatusCode().is2xxSuccessful()).isTrue();
		String body = re.getBody();

		Assertions.assertThat(body).startsWith("#!/bin/bash").contains("id=" + clusterId)
				.contains("/api/trident/provision/docker-install").contains("nodeType=MANAGER");

		Assertions
				.assertThat(controller.getProvisioningManager().findTridentCluster(clusterId).get().path("tridentClusterId").asText())
				.isEqualTo(clusterId);
		Assertions
				.assertThat(controller.getProvisioningManager().findTridentCluster(clusterId).get().path("tridentClusterId").asText())
				.isEqualTo(clusterId);

	}

	@Test
	public void testNodeInitJoinWithoutSeed() {
		String clusterId = createTestCluster();

		{
			MockHttpServletRequest r = new MockHttpServletRequest();
			r.addParameter("id", clusterId);

			ResponseEntity<String> re = controller.nodeInit(r);

			Assertions.assertThat(re.getStatusCode().is2xxSuccessful()).isTrue();
			String body = re.getBody();

			Assertions.assertThat(body).startsWith("#!/bin/bash").contains("id=" + clusterId)
					.contains("/api/trident/provision/docker-install").contains("nodeType=WORKER");

			Assertions
					.assertThat(
							controller.getProvisioningManager().findTridentCluster(clusterId).get().path("tridentClusterId").asText())
					.isEqualTo(clusterId);
			Assertions
					.assertThat(
							controller.getProvisioningManager().findTridentCluster(clusterId).get().path("tridentClusterId").asText())
					.isEqualTo(clusterId);
		}
		{
			MockHttpServletRequest r = new MockHttpServletRequest();
			r.addParameter("id", clusterId);
			r.addParameter("nodeType", "WORKER");
			ResponseEntity<String> re = controller.nodeInit(r);

			Assertions.assertThat(re.getStatusCode().is2xxSuccessful()).isTrue();
			String body = re.getBody();

			Assertions.assertThat(body).startsWith("#!/bin/bash").contains("id=" + clusterId)
					.contains("/api/trident/provision/docker-install").contains("nodeType=WORKER");

			Assertions
					.assertThat(
							controller.getProvisioningManager().findTridentCluster(clusterId).get().path("tridentClusterId").asText())
					.isEqualTo(clusterId);
			Assertions
					.assertThat(
							controller.getProvisioningManager().findTridentCluster(clusterId).get().path("tridentClusterId").asText())
					.isEqualTo(clusterId);
		}

	}



	String createTestCluster() {
		return createTestCluster(null, null, null);
	}

	String createTestCluster(String managerAddress, String managerToken, String workerToken) {
		String clusterId = "junit-" + System.currentTimeMillis();
		String clusterIdEncoded = UUID.randomUUID().toString();



		provisioningManager.mergeTridentCluster(clusterIdEncoded,"workerJoinToken",workerToken,"managerAddress",managerAddress,"managerJoinToken",managerToken);
		
		
		
		enqueueClusterDelete(clusterIdEncoded);
	
		return clusterIdEncoded;
	}

	@Test
	public void testNodeInitWithClusterId() {

		String clusterId = createTestCluster();

		MockHttpServletRequest r = new MockHttpServletRequest();
		r.addParameter("id", clusterId);
		ResponseEntity<String> re = controller.nodeInit(r);

		JsonNode n = controller.getProvisioningManager().findTridentCluster(clusterId).get();

		logResponse(re);
		Assertions.assertThat(re.getStatusCode().is2xxSuccessful()).isTrue();
		String body = re.getBody();

		Assertions.assertThat(body).startsWith("#!/bin/bash").contains("id=" + clusterId)
				.contains("/api/trident/provision/docker-install").contains("nodeType=WORKER");

	}

	@Test
	public void testNodeInitManagerWithClusterId() {

		String clusterId = createTestCluster();

		MockHttpServletRequest r = new MockHttpServletRequest();
		r.addParameter("id", clusterId);
		r.addParameter("nodeType", "MANAGER");
		ResponseEntity<String> re = controller.nodeInit(r);

		JsonNode n = controller.getProvisioningManager().findTridentCluster(clusterId).get();

		logResponse(re);
		Assertions.assertThat(re.getStatusCode().is2xxSuccessful()).isTrue();
		String body = re.getBody();

		Assertions.assertThat(body).startsWith("#!/bin/bash").contains("id=" + clusterId)
				.contains("/api/trident/provision/docker-install").contains("nodeType=MANAGER");

	}

	public void logResponse(ResponseEntity<String> r) {
		logger.info("response: rc=" + r.getStatusCode() + "\n" + r.getBody());
	}

	@Test
	public void testDockerInstall() {
		String id = createTestCluster();

		MockHttpServletRequest r = new MockHttpServletRequest();
		r.addParameter("id", id);
		ResponseEntity<String> re = controller.dockerInstall(r);
		Assertions.assertThat(re.getStatusCode().is2xxSuccessful());
		String body = re.getBody();
		logResponse(re);

		Assertions.assertThat(body).startsWith("#!/bin/bash").contains("systemctl start docker.service")
				.contains("/api/trident/provision/swarm-join").contains("nodeType=WORKER");

	}

	@Test
	public void testDockerInstallManager() {
		String id = createTestCluster();

		MockHttpServletRequest r = new MockHttpServletRequest();
		r.addParameter("id", id);
		r.addParameter("nodeType", SwarmNodeType.MANAGER.toString());

		ResponseEntity<String> re = controller.dockerInstall(r);
		Assertions.assertThat(re.getStatusCode().is2xxSuccessful());
		String body = re.getBody();
		logResponse(re);

		Assertions.assertThat(body).startsWith("#!/bin/bash").contains("systemctl start docker.service")
				.contains("/api/trident/provision/swarm-join").contains("nodeType=MANAGER");

	}

	@Test
	public void testDockerInstallWithoutClusterId() {

		MockHttpServletRequest r = new MockHttpServletRequest();

		ResponseEntity<String> re = controller.dockerInstall(r);
		logResponse(re);
		Assertions.assertThat(re.getStatusCode().is4xxClientError()).isTrue();

	}

	@Test
	public void testDockerInstallWithClusterIdNotFound() {

		MockHttpServletRequest r = new MockHttpServletRequest();
		r.setParameter("id", "not_found");
		ResponseEntity<String> re = controller.dockerInstall(r);
		logResponse(re);
		Assertions.assertThat(re.getStatusCode().value()).isEqualTo(404);

	}

	@Test
	public void testCreate() {
		String id = createTestCluster("a", "b", "c");

		JsonNode n = neo4j.execCypher("match (a:DockerSwarm {tridentClusterId:{id}}) return a", "id", id).blockingFirst();

		Assertions.assertThat(cryptoService.decryptString(n.path("workerJoinToken").asText())).isEqualTo("c");
		Assertions.assertThat(cryptoService.decryptString(n.path("managerJoinToken").asText())).isEqualTo("b");
		Assertions.assertThat(n.path("managerAddress").asText()).isEqualTo("a");
	}

	@Test
	public void testSwarmJoinAsWorker() {

		String address = "127.0.0.1:2377";
		String workerToken = UUID.randomUUID().toString();
		String maangerToken = UUID.randomUUID().toString();
		String id = createTestCluster(address, maangerToken, workerToken);

		MockHttpServletRequest r = new MockHttpServletRequest();
		r.addParameter("id", id);
		r.addParameter("nodeType", "WORKER");

		ResponseEntity<String> re = controller.swarmJoin(r);
		Assertions.assertThat(re.getStatusCode().is2xxSuccessful());
		String body = re.getBody();
		logResponse(re);

		Assertions.assertThat(body).startsWith("#!/bin/bash").contains("id=" + id)
				.contains("nodeType=WORKER").contains("docker swarm join --token " + workerToken + " " + address)
				.contains("/api/trident/provision/ready");

	}

	@Test
	public void testSwarmJoinAsManager() {

		String address = "127.0.0.1:2377";
		String workerToken = UUID.randomUUID().toString();
		String maangerToken = "manager-" + UUID.randomUUID().toString();
		String id = createTestCluster(address, maangerToken, workerToken);

		MockHttpServletRequest r = new MockHttpServletRequest();
		r.addParameter("id", id);
		r.addParameter("nodeType", SwarmNodeType.MANAGER.toString());

		ResponseEntity<String> re = controller.swarmJoin(r);
		Assertions.assertThat(re.getStatusCode().is2xxSuccessful());
		String body = re.getBody();
		logResponse(re);

		
		Assertions.assertThat(body).startsWith("#!/bin/bash").contains("id=" + id)
				.contains("nodeType=MANAGER").contains("docker swarm join --token " + maangerToken + " " + address)
				.contains("/api/trident/provision/ready");

	}

	@Test
	public void testSwarmJoinWorkerWithoutWorkerToken() {

		// What we are testing here is the case where there is not (yet) a
		// worker token for the
		// cluster. We detect this condition and tell the client to try again.
		String id = createTestCluster();

		MockHttpServletRequest r = new MockHttpServletRequest();
		r.addParameter("id", id);
		r.addParameter("nodeType", SwarmNodeType.WORKER.toString());

		ResponseEntity<String> re = controller.swarmJoin(r);
		Assertions.assertThat(re.getStatusCode().is2xxSuccessful());
		String body = re.getBody();
		logResponse(re);

		Assertions.assertThat(body).contains("#!/bin/bash").contains("sleep 15")
				.contains("/api/trident/provision/swarm-join").contains("id=" + id)
				.contains("nodeType=WORKER");

	}

	@Test
	public void testSwarmInit() {
		String id = createTestCluster();

		JsonNode tridentClusterNode = controller.getProvisioningManager().findTridentCluster(id).get();

		Assertions.assertThat(tridentClusterNode.path("tridentClusterId").asText()).isEqualTo(id);

		MockHttpServletRequest r = new MockHttpServletRequest();
		r.addParameter("id", id);
		r.addParameter("nodeType", SwarmNodeType.MANAGER.toString());
		r.addParameter("workerTokenOutput", WORKER_JOIN_TOKEN_OUTPUT);
		r.addParameter("managerTokenOutput", MANAGER_JOIN_TOKEN_OUTPUT);

		ResponseEntity<String> re = controller.swarmInit(r);
		Assertions.assertThat(re.getStatusCode().is2xxSuccessful());
		String body = re.getBody();
		Assertions.assertThat(body).contains("#!/bin/bash").contains("Ready");

		tridentClusterNode = controller.getProvisioningManager().findTridentCluster(id).get();


		Assertions.assertThat(tridentClusterNode.path("managerAddress").asText()).isEqualTo("192.168.0.10:2377");
		Assertions.assertThat(tridentClusterNode.path("managerJoinToken").asText())
				.isEqualTo("SWMTKN-1-4foerbfmruk8eal17kib0zyu28pkgggsz2pgqhw8abk96fix1p-6mruygvfm2a5jtbvy1ta4d6lx");
		Assertions.assertThat(tridentClusterNode.path("workerJoinToken").asText())
				.isEqualTo("SWMTKN-1-4foerbfmruk8eal17kib0zyu28pkgggsz2pgqhw8abk96fix1p-2j5rx4coyegh3mqp94np9x39k");

	}

	@Test
	public void testSwarmJoinManagerWithoutManagerToken() {

		// What we are testing here is the case where there is not (yet) a
		// manager token for the
		// cluster. Go ahead and become the manager.
		String id = createTestCluster("127.0.0.1:2377", null, null);

		
		MockHttpServletRequest r = new MockHttpServletRequest();
		r.addParameter("id", id);
		r.addParameter("nodeType", SwarmNodeType.MANAGER.toString());

		ResponseEntity<String> re = controller.swarmJoin(r);
		Assertions.assertThat(re.getStatusCode().is2xxSuccessful());
		String body = re.getBody();
		logResponse(re);

		Assertions.assertThat(body).contains("#!/bin/bash").contains("/api/trident/provision/swarm-initialized")
				.contains("id=" + id).contains("nodeType=MANAGER").contains("docker swarm init")
				.contains("docker swarm join-token manager").contains("docker swarm join-token manager");

	}

	@Test
	public void testSwarmJoinManagerWithManagerToken() {

		// What we are testing here is the case where there is not (yet) a
		// manager token for the
		// cluster. Go ahead and become the manager.
		String id = createTestCluster("127.0.0.1:2377", "mgr", "worker-token");

		MockHttpServletRequest r = new MockHttpServletRequest();
		r.addParameter("id", id);
		r.addParameter("nodeType", SwarmNodeType.MANAGER.toString());

		ResponseEntity<String> re = controller.swarmJoin(r);
		Assertions.assertThat(re.getStatusCode().is2xxSuccessful());
		String body = re.getBody();
		logResponse(re);

		Assertions.assertThat(body).contains("#!/bin/bash").contains("/api/trident/provision/ready")
				.contains("id=" + id).contains("nodeType=MANAGER").contains("docker swarm join");

	}

	@Test
	public void testSwarmJoinWorkerWithWorkerToken() {

		// What we are testing here is the case where there is not (yet) a
		// manager token for the
		// cluster. Go ahead and become the manager.
		String id = createTestCluster("127.0.0.1:2377", "mgr", "worker-token");

		MockHttpServletRequest r = new MockHttpServletRequest();
		r.addParameter("id", id);
		r.addParameter("nodeType", SwarmNodeType.WORKER.toString());

		ResponseEntity<String> re = controller.swarmJoin(r);
		Assertions.assertThat(re.getStatusCode().is2xxSuccessful());
		String body = re.getBody();
		logResponse(re);

		Assertions.assertThat(body).contains("#!/bin/bash").contains("/api/trident/provision/ready")
				.contains("id=" + id).contains("nodeType=WORKER").contains("docker swarm join");

	}

	final String MANAGER_JOIN_TOKEN_OUTPUT = "VG8gYWRkIGEgbWFuYWdlciB0byB0aGlzIHN3YXJtLCBydW4gdGhlIGZvbGxvd2luZyBjb21tYW"
			+ "5kOgoKICAgIGRvY2tlciBzd2FybSBqb2luIFwKICAgIC0tdG9rZW4gU1dNVEtOLTEtNGZvZXJiZm1ydWs4ZWFsMTdr"
			+ "aWIwenl1Mjhwa2dnZ3N6MnBncWh3OGFiazk2Zml4MXAtNm1ydXlndmZtMmE1anRidnkxdGE0ZDZseCBcCiAgICA"
			+ "xOTIuMTY4LjAuMTA6MjM3NwogICAg";

	@Test
	public void testManagerToken() {

		ProvisioningApiController c = new ProvisioningApiController();

		SwarmToken token = c.extractJoinToken(MANAGER_JOIN_TOKEN_OUTPUT).get();
		logger.info("manager token: {}", token);
		Assertions.assertThat(token.getNodeType()).isEqualTo(SwarmNodeType.MANAGER);
		Assertions.assertThat(token.getAddress()).isEqualTo("192.168.0.10:2377");
		Assertions.assertThat(token.getToken())
				.isEqualTo("SWMTKN-1-4foerbfmruk8eal17kib0zyu28pkgggsz2pgqhw8abk96fix1p-6mruygvfm2a5jtbvy1ta4d6lx");

	}

	final String WORKER_JOIN_TOKEN_OUTPUT = "VG8gYWRkIGEgd29ya2VyIHRvIHRoaXMgc3dhcm0sIHJ1biB0aGUgZm9sbG93aW5nIGNvbW1hb"
			+ "mQ6CgogICAgZG9ja2VyIHN3YXJtIGpvaW4gXAogICAgLS10b2tlbiBTV01US04tMS00Zm9lcmJmbXJ1azhl"
			+ "YWwxN2tpYjB6eXUyOHBrZ2dnc3oycGdxaHc4YWJrOTZmaXgxcC0yajVyeDRjb3llZ2gzbXFwOTRucDl4M"
			+ "zlrIFwKICAgIDE5Mi4xNjguMzAuMzoyMzc3CiAK";

	@Test
	public void testWorkerSwarmJoinTokenManagerOutput() {
		ProvisioningApiController c = new ProvisioningApiController();

		SwarmToken token = c.extractJoinToken(WORKER_JOIN_TOKEN_OUTPUT).get();
		logger.info("worker token: {}", token);

		Assertions.assertThat(token.getAddress()).isEqualTo("192.168.30.3:2377");
		Assertions.assertThat(token.getToken())
				.isEqualTo("SWMTKN-1-4foerbfmruk8eal17kib0zyu28pkgggsz2pgqhw8abk96fix1p-2j5rx4coyegh3mqp94np9x39k");
		Assertions.assertThat(token.getNodeType()).isEqualTo(SwarmNodeType.WORKER);
	}
}
