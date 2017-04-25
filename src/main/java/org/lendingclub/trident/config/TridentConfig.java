package org.lendingclub.trident.config;


import org.lendingclub.mercator.core.Projector;
import org.lendingclub.neorx.NeoRxClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.common.base.Strings;

@Configuration
public class TridentConfig {

	static Logger logger = LoggerFactory.getLogger(TridentConfig.class);
	@Value(value = "${neo4j.url:bolt://localhost:7687}")
	String neo4jUrl;

	@Value(value = "${neo4j.username:bolt:}")
	String neo4jUsername;

	@Value(value = "${neo4j.password:}")
	String neo4jPassword;

	@Bean(name = "localDockerClientSupplier")
	public org.lendingclub.mercator.docker.DockerClientSupplier localDockerClientSupplier() {

		return new org.lendingclub.mercator.docker.DockerClientSupplier.Builder().withLocalEngine().withName("local").build();
		
	}

	/*
	 * @Bean public UCPClientSupplier stageDockerClientFactory() {
	 * 
	 * return (UCPClientSupplier) new UCPClientSupplier.Builder()
	 * .withName("ucptest") .withEndpoints("tcp://myserver:443").withCertPath(
	 * "/Users/myuser/.docker/ucptest") .withBuilderConfig(b -> { b
	 * 
	 * .withDockerTlsVerify(true); }).build();
	 * 
	 * 
	 * }
	 */
	@Bean
	public NeoRxClient neo4j() {

		NeoRxClient.Builder b = new NeoRxClient.Builder();
		if (!Strings.isNullOrEmpty(neo4jUrl)) {
			b = b.withUrl(neo4jUrl);
		}
		if (!Strings.isNullOrEmpty(neo4jUsername)) {
			b = b.withCredentials(neo4jUsername, neo4jPassword);
		}
		neo4jUsername = null;
		neo4jPassword = null;
		NeoRxClient client = b.build();

		if (client.checkConnection()) {
			logger.info("connected to neo4j");
		} else {
			logger.warn("unable to connect to neo4j");
		}

		return client;
	}

	@Bean
	public Projector projector() {
		return new Projector.Builder().withNeoRxClient(neo4j()).build();
	}
	
	
}
