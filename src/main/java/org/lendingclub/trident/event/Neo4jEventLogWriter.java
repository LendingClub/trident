package org.lendingclub.trident.event;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.cluster.TridentClusterManager;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.CaseFormat;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.reactivex.functions.Consumer;

public class Neo4jEventLogWriter {

	public static final String EVENT_LOG_LABEL = "EventLog";

	Logger logger = LoggerFactory.getLogger(Neo4jEventLogWriter.class);

	@Autowired
	NeoRxClient neo4j;

	@Autowired
	EventSystem eventSystem;

	@Autowired
	TridentClusterManager tridentClusterManager;

	ScheduledExecutorService purgeExecutor = Executors.newSingleThreadScheduledExecutor();

	protected ObjectNode flattenAndSanitize(ObjectNode input) {

		ObjectNode out = JsonUtil.createObjectNode();
		input.path("data").fields().forEachRemaining(it -> {
			JsonNode val = it.getValue();
			if (val.isValueNode()) {
				out.set(lowerCamelCase(it.getKey()), val);
			}
		});
		input.fields().forEachRemaining(it -> {
			JsonNode val = it.getValue();
			if (val.isValueNode()) {
				out.set(lowerCamelCase(it.getKey()), val);
			}
		});
		out.put("eventRawData", input.path("data").toString());
		
		return out;
	}

	protected String lowerCamelCase(String str) { 
		if (str.startsWith("EC2")) { 
			return str.replace("EC2", "ec2");
		}
		return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, str);
	}
	
	class EventLogPurge implements Runnable {
		@Override
		public void run() {
			try {
				if (tridentClusterManager.isLeader()) {
					long cutoffMillis = TimeUnit.DAYS.toMillis(1);
					logger.info("purging old TridentEventLog entries...");
					neo4j.execCypher("match (t:TridentEventLog) where timestamp()-t.eventTs>{cutoff} detach delete t",
							"cutoff", cutoffMillis);
				} else {
					logger.info("only leader will purge TridentEventLog");
				}
			} catch (Exception e) {
				logger.warn("problem purging events");
			}
		}
	}

	class Neo4jWriter implements Consumer<TridentEvent> {
		@Override
		public void accept(TridentEvent tridentEvent) {

			try {
				logger.info("writing event: {}", tridentEvent);
				if (neo4j != null) {
					ObjectNode n = flattenAndSanitize(tridentEvent.getEnvelope());
					neo4j.execCypher("merge (t:TridentEventLog {eventId:{eventId}}) set t+={props}", "eventId",
							tridentEvent.getEventId(), "props", n);
				}
			} catch (RuntimeException e) {
				logger.warn("failed to write event to neo4j", e);
			}
		}
	}

	@PostConstruct
	public void init() {
		ThreadFactory tf = new ThreadFactoryBuilder().setNameFormat("Neo4jEventLogWriter-%d").setDaemon(true).build();
		eventSystem.createConcurrentSubscriber(TridentEvent.class).withExecutor(Executors.newFixedThreadPool(2, tf))
				.subscribe(new Neo4jWriter());

		// this schedules a periodic purge of the TridentEventLog
		purgeExecutor.scheduleWithFixedDelay(new EventLogPurge(), 10, 10, TimeUnit.MINUTES);
	}

}