package org.lendingclub.trident.agent;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.JerseyClientBuilder;
import org.lendingclub.mercator.docker.DockerEventProcessor;
import org.lendingclub.mercator.docker.DockerRestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Event;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.EventsResultCallback;
import com.google.common.base.Strings;

public class DockerEventAgent extends TridentAgent {

	Logger logger = LoggerFactory.getLogger(DockerEventAgent.class);

	public static final String EVENT_PATH = "/api/trident/agent/docker-event";
	DockerClient dockerClient;

	ObjectMapper mapper = new ObjectMapper();

	AtomicLong timeNano = new AtomicLong();

	public void start() throws IOException, InterruptedException {

		logger.info("starting {} ", this);

		Consumer<JsonNode> consumer = new Consumer<JsonNode>() {

			@Override
			public void accept(JsonNode event) {
				try {
					post(event);
				} catch (Throwable e) {
					logger.warn("uncaught exception", e);
				}

			}

		};
	
		new DockerEventProcessor.Builder().withDockerRestClient(DockerRestClient.forDockerClient(getDockerClient())).addConsumer(consumer).build();

	}

	public void post(JsonNode event) {
		try {

		
			sendEvent(EVENT_PATH, "dockerEvent", event);

		} catch (Exception e) {
			logger.warn("problem sending event", e);
		}
	}

}
