package org.lendingclub.trident.agent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.WebTarget;

import org.lendingclub.mercator.docker.SwarmScanner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DockerInfoAgent extends TridentAgent {

	ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

	
	public void sendInfo() {
		
		ObjectNode n = mapper.createObjectNode();
		
		sendEvent("/api/trident/agent/docker-info","info",n);
	
	}
	
	public void start() {
		
		logger.info("starting {}",this);
		executor.scheduleWithFixedDelay(new Runnable() {

			@Override
			public void run() {
				try {
					sendInfo();
				}
				catch (Exception e) {
					logger.warn("uncaught exception",e);
				}
				
			}
			
		}, 0,1, TimeUnit.MINUTES);
	}
}
