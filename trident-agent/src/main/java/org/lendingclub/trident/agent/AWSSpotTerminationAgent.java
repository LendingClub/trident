package org.lendingclub.trident.agent;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.macgyver.okrest3.OkRestClient;
import io.macgyver.okrest3.OkRestException;
import io.macgyver.okrest3.OkRestResponse;
import io.macgyver.okrest3.OkRestTarget;

public class AWSSpotTerminationAgent extends AWSTridentAgent {

	ObjectMapper mapper = new ObjectMapper();
	Logger logger = LoggerFactory.getLogger(AWSSpotTerminationAgent.class);

	OkRestClient client = new OkRestClient.Builder().withOkHttpClientConfig(it -> {
		it.connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS);
	}).build();
	ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	class SpotTerminationProcessor implements Runnable {

		@Override
		public void run() {

		}

	}

	protected void makeRequest() {

		Optional<String> terminationTime = Optional.empty();
		try {

			logger.info("checking spot termination notification...");

			terminationTime = getMetadataAttribute("/spot/termination-time");

		} catch (Exception e) {

			logger.info("could not reach metadata service: {}", e.toString());

		}
		try {
			if (terminationTime.isPresent()) {
				notifyTrident(terminationTime.get());
			}
		} catch (Exception e) {
			logger.info("could not POST data to trident",e);
		}

	}

	public void start() {

		if (isRunningInEC2()) {
			logger.info("starting {}...",this);
			scheduler.scheduleWithFixedDelay(new SpotTerminationProcessor(), 0, 5, TimeUnit.SECONDS);
		} else {
			logger.info("not starting {} because we are not in ec2",this);
			scheduler.shutdown();
		}
	}

	protected void notifyTrident(String val) {

		ObjectNode envelope = createIdentityEnvelope();
		ObjectNode data = mapper.createObjectNode();
		envelope.set("data", data);
		data.put("terminationTime", val);
		OkRestTarget target = getTridentBaseTarget().path("/api/trident/agent/aws/spot-termination");
		try {
			logger.info("spot-termination-notification: {}",
					mapper.writerWithDefaultPrettyPrinter().writeValueAsString(envelope));

			target.post(envelope)
					.addHeader("content-type", "application/json").execute();
		} catch (IOException | RuntimeException e) {
			logger.info("could not POST to "+target.getUrl(),e);
		}

	}

}
