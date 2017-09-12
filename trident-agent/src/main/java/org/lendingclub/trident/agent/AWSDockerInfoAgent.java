package org.lendingclub.trident.agent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class AWSDockerInfoAgent extends AWSTridentAgent {

	ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	public void sendInfo() {

		ObjectNode n = mapper.createObjectNode();

		sendEvent("/api/trident/agent/aws/docker-info","info", n);

	}

	public void start() {
		if (isRunningInEC2()) {
			logger.info("starting {} because we are in EC2", this);
			executor.scheduleWithFixedDelay(new Runnable() {

				@Override
				public void run() {
					try {
						sendInfo();
					} catch (Exception e) {
						logger.warn("uncaught exception", e);
					}

				}

			}, 0, 1, TimeUnit.MINUTES);
		} else {
			logger.info("not starting {} beacause we are not in EC2", this);
		}
	}
}
