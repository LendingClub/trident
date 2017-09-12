package org.lendingclub.trident.swarm;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.lendingclub.mercator.docker.DockerRestClient;
import org.lendingclub.trident.cluster.TridentClusterManager;
import org.lendingclub.trident.event.EventSystem;
import org.lendingclub.trident.util.JsonUtil;
import org.lendingclub.trident.util.TridentStartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.api.DockerClient;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import com.google.common.io.Closer;

@Component
public class SwarmEventManager implements TridentStartupListener {

	Logger logger = LoggerFactory.getLogger(SwarmEventManager.class);

	@Autowired
	SwarmClusterManager clusterManager;
	Map<String, EventContext> callbackMap = Maps.newConcurrentMap();

	ScheduledExecutorService watchdogExecutor = Executors.newScheduledThreadPool(5);

	@Autowired
	EventSystem eventSystem;

	@Autowired
	TridentClusterManager tridentClusterManager;

	@Autowired
	IncrementalScannerEventConsumer incrementalScannerEventConsumer;

	class EventContext implements Runnable {
		String name;
		DockerClient client;
		AtomicLong lastEventNano = new AtomicLong(TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()));

		public DockerClient getClient() {
			return client;
		}

		public DockerRestClient getRestClient() {
			return DockerRestClient.forDockerClient(getClient());
		}

		@Override
		public void run() {
			
			
			
			Closer closer = Closer.create();
			try {
				DockerRestClient restClient = getRestClient();
				logger.info("polling for events from swarm={} url={}", name, restClient.getWebTarget().getUri().toString());
				long since = lastEventNano.get() + 1;
				long until = System.currentTimeMillis() * 1000000;
				until = Math.max(since, until);
				since = Math.min(since, until);
				Preconditions.checkArgument(since <= until);
				Reader r = restClient.getWebTarget().path("/events").queryParam("since", formatNanoTime(since))
						.queryParam("until", formatNanoTime(until)).request().get(Reader.class);
				closer.register(r);
				StringBuffer sb = new StringBuffer();
				CharStreams.readLines(r).forEach(it -> {
					if (it.startsWith("{")) {
						tryDispatch(sb);
						sb.setLength(0);
						sb.append(it);
					} else {
						sb.append(it);
						if (tryDispatch(sb)) {
							sb.setLength(0);
						}
					}
				});
			} catch (Exception e) {
				logger.warn("problem fetching events", e);
			} finally {
				try {
					closer.close();
				} catch (IOException e) {
					logger.warn("problem closing", e);
				}
			}
		}

		boolean tryDispatch(StringBuffer sb) {

			ObjectNode n = null;
			try {
				n = (ObjectNode) JsonUtil.getObjectMapper().readTree(sb.toString());

			} catch (Exception e) {
				return false;
			}
			long nanoTime = n.path("timeNano").asLong();
			if (nanoTime > lastEventNano.get()) {
				lastEventNano.set(Math.max(lastEventNano.get(), nanoTime));

				boolean publish = false;
				if (!publish && tridentClusterManager.isLeader()) {
					publish = true;
				}
				if (!publish && Strings.nullToEmpty(System.getProperty("os.name")).toLowerCase().contains("os x")) {
					// in local dev, it is annoying to have to wait for leader election for events to flow, so we 
					// just turn it on
					publish = true;
				}

				if (publish) {
					// it is helpful to have the cluster context available
					// with the event.
					String swarmClusterId = getRestClient().getSwarmClusterId().get();
					eventSystem.post(
							new DockerEvent().withPayload(n).withEnvelopeAttribute("swarmClusterId", swarmClusterId)
								);
				}

			}
			return true;
		}
	}

	static String formatNanoTime(long val) {
		String stringVal = Long.toString(val);
		return stringVal.substring(0, stringVal.length() - 9) + "." + stringVal.substring(stringVal.length() - 9);

	}

	public void subscribeSwarmEvents(String swarmName) {

		EventContext c = callbackMap.get(swarmName);
		if (c == null) {
			logger.info("no event callback found...subscribing to events");
			EventContext ctx = new EventContext();
			ctx.client = clusterManager.getSwarmManagerClient(swarmName);
			ctx.name = swarmName;
			watchdogExecutor.scheduleWithFixedDelay(ctx, 0, 5, TimeUnit.SECONDS);
			callbackMap.put(swarmName, ctx);
		} else {
			logger.info("akready subscribed to events from {}", swarmName);
		}

	}

	@Override
	public void onStart(ApplicationContext context) {
		Runnable r = new Runnable() {

			@Override
			public void run() {
				clusterManager.getSwarmNames().forEach(it -> {
					subscribeSwarmEvents(it);
				});

			}
		};
		watchdogExecutor.scheduleWithFixedDelay(r, 0, 1, TimeUnit.MINUTES);

		eventSystem.createConcurrentSubscriber(DockerEvent.class).withExecutor(Executors.newSingleThreadExecutor())
				.subscribe(incrementalScannerEventConsumer);
	}

}
