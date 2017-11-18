package org.lendingclub.trident.event;

import javax.annotation.PostConstruct;

import org.lendingclub.reflex.aws.sqs.SQSAdapter;
import org.lendingclub.reflex.aws.sqs.SQSAdapter.SQSMessage;
import org.lendingclub.reflex.operator.ExceptionHandlers;
import org.lendingclub.trident.swarm.aws.event.AWSEvent;
import org.lendingclub.trident.swarm.aws.event.AutoScalingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.reactivex.Observable;

public class AWSEventLogWriter {
	
	Logger logger = LoggerFactory.getLogger(AWSEventLogWriter.class);
	
	@Autowired
	EventSystem eventSystem;
	
	@PostConstruct
	public void init() { 
		Observable<JsonNode> awsEventsObservable = eventSystem
			.createObservable(SQSMessage.class)
			.flatMap(SQSAdapter.newJsonMessageExtractor());
			
		awsEventsObservable
			.filter(json -> json.path("Event").asText().startsWith("autoscaling"))
			.subscribe(ExceptionHandlers.safeConsumer(it -> { 
				
				new AutoScalingEvent()
						.withPayload((ObjectNode)it)
						.withMessage(
								String.format("Auto-scaling group %s (%s) event: %s. Description: %s. Cause: %s",
										it.path("AutoScalingGroupName").asText(),
										it.path("AccountId").asText(),
										it.path("Event").asText(),
										it.path("Description").asText(),
										it.path("Cause").asText()))
						.publish();
			}, logger));
		
		awsEventsObservable
		.filter(json -> !json.path("Event").asText().startsWith("autoscaling"))
		.subscribe(ExceptionHandlers.safeConsumer(it -> { 
			eventSystem.post(
					new AWSEvent()
							.withPayload((ObjectNode)it)
							.withMessage("AWS Event occurred."));
		}, logger));
	}

}