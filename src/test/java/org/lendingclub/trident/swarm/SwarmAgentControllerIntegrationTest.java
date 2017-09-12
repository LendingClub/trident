package org.lendingclub.trident.swarm;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.event.EventSystem;
import org.lendingclub.trident.swarm.SwarmAgentController;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.api.model.Event;
import com.google.common.collect.Lists;

import io.reactivex.schedulers.Schedulers;

public class SwarmAgentControllerIntegrationTest extends TridentIntegrationTest {

	@Autowired
	SwarmAgentController controller;
	
	@Autowired
	EventSystem eventSystem;
	
	String val = "{\n" + 
			"  \"dockerInfo\" : {\n" + 
			"    \"ID\" : \"Z7NO:7ILT:YHNK:SOGS:M4LA:SSNL:2XML:HJR4:YROA:VPSL:JSGE:KBIT\",\n" + 
			"    \"Containers\" : 6,\n" + 
			"    \"ContainersRunning\" : 6,\n" + 
			"    \"ContainersPaused\" : 0,\n" + 
			"    \"ContainersStopped\" : 0,\n" + 
			"    \"Images\" : 3,\n" + 
			"    \"Driver\" : \"overlay2\",\n" + 
			"    \"DriverStatus\" : [ [ \"Backing Filesystem\", \"extfs\" ], [ \"Supports d_type\", \"true\" ], [ \"Native Overlay Diff\", \"true\" ] ],\n" + 
			"    \"SystemStatus\" : null,\n" + 
			"    \"Plugins\" : {\n" + 
			"      \"Volume\" : [ \"local\" ],\n" + 
			"      \"Network\" : [ \"bridge\", \"host\", \"ipvlan\", \"macvlan\", \"null\", \"overlay\" ],\n" + 
			"      \"Authorization\" : null,\n" + 
			"      \"Log\" : [ \"awslogs\", \"fluentd\", \"gcplogs\", \"gelf\", \"journald\", \"json-file\", \"logentries\", \"splunk\", \"syslog\" ]\n" + 
			"    },\n" + 
			"    \"MemoryLimit\" : true,\n" + 
			"    \"SwapLimit\" : true,\n" + 
			"    \"KernelMemory\" : true,\n" + 
			"    \"CpuCfsPeriod\" : true,\n" + 
			"    \"CpuCfsQuota\" : true,\n" + 
			"    \"CPUShares\" : true,\n" + 
			"    \"CPUSet\" : true,\n" + 
			"    \"IPv4Forwarding\" : true,\n" + 
			"    \"BridgeNfIptables\" : true,\n" + 
			"    \"BridgeNfIp6tables\" : true,\n" + 
			"    \"Debug\" : true,\n" + 
			"    \"NFd\" : 69,\n" + 
			"    \"OomKillDisable\" : true,\n" + 
			"    \"NGoroutines\" : 193,\n" + 
			"    \"SystemTime\" : \"2017-09-06T16:54:01.904786413Z\",\n" + 
			"    \"LoggingDriver\" : \"json-file\",\n" + 
			"    \"CgroupDriver\" : \"cgroupfs\",\n" + 
			"    \"NEventsListener\" : 7,\n" + 
			"    \"KernelVersion\" : \"4.9.41-moby\",\n" + 
			"    \"OperatingSystem\" : \"Alpine Linux v3.5\",\n" + 
			"    \"OSType\" : \"linux\",\n" + 
			"    \"Architecture\" : \"x86_64\",\n" + 
			"    \"IndexServerAddress\" : \"https://index.docker.io/v1/\",\n" + 
			"    \"RegistryConfig\" : {\n" + 
			"      \"AllowNondistributableArtifactsCIDRs\" : [ ],\n" + 
			"      \"AllowNondistributableArtifactsHostnames\" : [ ],\n" + 
			"      \"InsecureRegistryCIDRs\" : [ \"127.0.0.0/8\" ],\n" + 
			"      \"IndexConfigs\" : {\n" + 
			"        \"docker.io\" : {\n" + 
			"          \"Name\" : \"docker.io\",\n" + 
			"          \"Mirrors\" : [ ],\n" + 
			"          \"Secure\" : true,\n" + 
			"          \"Official\" : true\n" + 
			"        }\n" + 
			"      },\n" + 
			"      \"Mirrors\" : [ ]\n" + 
			"    },\n" + 
			"    \"NCPU\" : 4,\n" + 
			"    \"MemTotal\" : 5189799936,\n" + 
			"    \"DockerRootDir\" : \"/var/lib/docker\",\n" + 
			"    \"HttpProxy\" : \"\",\n" + 
			"    \"HttpsProxy\" : \"\",\n" + 
			"    \"NoProxy\" : \"\",\n" + 
			"    \"Name\" : \"moby\",\n" + 
			"    \"Labels\" : null,\n" + 
			"    \"ExperimentalBuild\" : true,\n" + 
			"    \"ServerVersion\" : \"17.06.1-ce\",\n" + 
			"    \"ClusterStore\" : \"\",\n" + 
			"    \"ClusterAdvertise\" : \"\",\n" + 
			"    \"Runtimes\" : {\n" + 
			"      \"runc\" : {\n" + 
			"        \"path\" : \"docker-runc\"\n" + 
			"      }\n" + 
			"    },\n" + 
			"    \"DefaultRuntime\" : \"runc\",\n" + 
			"    \"Swarm\" : {\n" + 
			"      \"NodeID\" : \"jg50xndphmzmi0l01reickwnt\",\n" + 
			"      \"NodeAddr\" : \"192.168.65.2\",\n" + 
			"      \"LocalNodeState\" : \"active\",\n" + 
			"      \"ControlAvailable\" : true,\n" + 
			"      \"Error\" : \"\",\n" + 
			"      \"RemoteManagers\" : [ {\n" + 
			"        \"NodeID\" : \"jg50xndphmzmi0l01reickwnt\",\n" + 
			"        \"Addr\" : \"192.168.65.2:2377\"\n" + 
			"      } ],\n" + 
			"      \"Nodes\" : 1,\n" + 
			"      \"Managers\" : 1,\n" + 
			"      \"Cluster\" : {\n" + 
			"        \"ID\" : \"irzpme6bo0l0qa5l7bb682tpj\",\n" + 
			"        \"Version\" : {\n" + 
			"          \"Index\" : 30\n" + 
			"        },\n" + 
			"        \"CreatedAt\" : \"2017-09-03T04:19:34.488816973Z\",\n" + 
			"        \"UpdatedAt\" : \"2017-09-04T13:50:11.559414801Z\",\n" + 
			"        \"Spec\" : {\n" + 
			"          \"Name\" : \"default\",\n" + 
			"          \"Labels\" : { },\n" + 
			"          \"Orchestration\" : {\n" + 
			"            \"TaskHistoryRetentionLimit\" : 5\n" + 
			"          },\n" + 
			"          \"Raft\" : {\n" + 
			"            \"SnapshotInterval\" : 10000,\n" + 
			"            \"KeepOldSnapshots\" : 0,\n" + 
			"            \"LogEntriesForSlowFollowers\" : 500,\n" + 
			"            \"ElectionTick\" : 3,\n" + 
			"            \"HeartbeatTick\" : 1\n" + 
			"          },\n" + 
			"          \"Dispatcher\" : {\n" + 
			"            \"HeartbeatPeriod\" : 5000000000\n" + 
			"          },\n" + 
			"          \"CAConfig\" : {\n" + 
			"            \"NodeCertExpiry\" : 7776000000000000\n" + 
			"          },\n" + 
			"          \"TaskDefaults\" : { },\n" + 
			"          \"EncryptionConfig\" : {\n" + 
			"            \"AutoLockManagers\" : false\n" + 
			"          }\n" + 
			"        },\n" + 
			"        \"TLSInfo\" : {\n" + 
			"          \"TrustRoot\" : \"-----BEGIN CERTIFICATE-----\\nMIIBajCCARCgAwIBAgIUJHwjUfbUmxrHmZ9lbSenD3dfffMwCgYIKoZIzj0EAwIw\\nEzERMA8GA1UEAxMIc3dhcm0tY2EwHhcNMTcwOTAzMDQxNTAwWhcNMzcwODI5MDQx\\nNTAwWjATMREwDwYDVQQDEwhzd2FybS1jYTBZMBMGByqGSM49AgEGCCqGSM49AwEH\\nA0IABCTzQRvei/+T5leGn49I3pjSbpm9ZpzUbwAK2Z4071KvL9PrmWx20YSOMiFK\\nKOb7Q/N9jfPiotuF2EWv2y8iRwCjQjBAMA4GA1UdDwEB/wQEAwIBBjAPBgNVHRMB\\nAf8EBTADAQH/MB0GA1UdDgQWBBQZvY7MiVV8yJd4FWUGhcIwpIRfHDAKBggqhkjO\\nPQQDAgNIADBFAiEAnuhnDfD3+W3UxWxT8iWwwccYaNlrVNQ7jldsQpaTfuUCIAUX\\n4Z4GgHTgK10fXfBJouvrBk6WDkaLZf1cWUEe08mZ\\n-----END CERTIFICATE-----\\n\",\n" + 
			"          \"CertIssuerSubject\" : \"MBMxETAPBgNVBAMTCHN3YXJtLWNh\",\n" + 
			"          \"CertIssuerPublicKey\" : \"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEJPNBG96L/5PmV4afj0jemNJumb1mnNRvAArZnjTvUq8v0+uZbHbRhI4yIUoo5vtD832N8+Ki24XYRa/bLyJHAA==\"\n" + 
			"        },\n" + 
			"        \"RootRotationInProgress\" : false\n" + 
			"      }\n" + 
			"    },\n" + 
			"    \"LiveRestoreEnabled\" : false,\n" + 
			"    \"Isolation\" : \"\",\n" + 
			"    \"InitBinary\" : \"docker-init\",\n" + 
			"    \"ContainerdCommit\" : {\n" + 
			"      \"ID\" : \"6e23458c129b551d5c9871e5174f6b1b7f6d1170\",\n" + 
			"      \"Expected\" : \"6e23458c129b551d5c9871e5174f6b1b7f6d1170\"\n" + 
			"    },\n" + 
			"    \"RuncCommit\" : {\n" + 
			"      \"ID\" : \"810190ceaa507aa2727d7ae6f4790c76ec150bd2\",\n" + 
			"      \"Expected\" : \"810190ceaa507aa2727d7ae6f4790c76ec150bd2\"\n" + 
			"    },\n" + 
			"    \"InitCommit\" : {\n" + 
			"      \"ID\" : \"949e6fa\",\n" + 
			"      \"Expected\" : \"949e6fa\"\n" + 
			"    },\n" + 
			"    \"SecurityOptions\" : [ \"name=seccomp,profile=default\" ]\n" + 
			"  },\n" + 
			"  \"dockerEvent\" : {\n" + 
			"    \"Type\" : \"network\",\n" + 
			"    \"Action\" : \"connect\",\n" + 
			"    \"Actor\" : {\n" + 
			"      \"ID\" : \"7c9fc81a9864e5bd9182aa1a6034a605193303f19d581d3885853a5cb32fcd0f\",\n" + 
			"      \"Attributes\" : {\n" + 
			"        \"container\" : \"b0c29eaa7e99a97b6f6749661862350c5b6df63b49509824c3124a4a5883062f\",\n" + 
			"        \"name\" : \"bridge\",\n" + 
			"        \"type\" : \"bridge\"\n" + 
			"      }\n" + 
			"    },\n" + 
			"    \"scope\" : \"local\",\n" + 
			"    \"time\" : 1504716839,\n" + 
			"    \"timeNano\" : 1504716839774625172\n" + 
			"  }\n" + 
			"}";
	@Test
	public void testIt() throws Exception {
		
		ObjectNode n = (ObjectNode) JsonUtil.getObjectMapper().readTree(val);
		
		CountDownLatch latch = new CountDownLatch(1);

		List<String> errors = Lists.newArrayList();
		
		eventSystem.createConcurrentSubscriber(DockerEvent.class).withExecutor(Executors.newCachedThreadPool()).subscribe(it->{
		
			String swarmClusterId = it.getSwarmClusterId();
			if (swarmClusterId.equals(n.path("dockerInfo").path("Swarm").path("Cluster").path("ID").asText())) {
				latch.countDown();
			}
			if (!swarmClusterId.equals("irzpme6bo0l0qa5l7bb682tpj")) {
				errors.add("swarmClusterId incorrect");
			}
			if (!swarmClusterId.equals(n.path("dockerInfo").path("Swarm").path("Cluster").path("ID").asText())) {
				errors.add("swarmClusterId incorrect");
			}
			if (!it.getData().path("Actor").path("ID").asText().equals("7c9fc81a9864e5bd9182aa1a6034a605193303f19d581d3885853a5cb32fcd0f")) {
				errors.add("payload incorrect");
			}
	
		});
		
		controller.dockerEvent(n);
		Assertions.assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
		Assertions.assertThat(errors).isEmpty();

	}
	

}
