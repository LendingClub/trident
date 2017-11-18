package org.lendingclub.trident.event;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.lendingclub.trident.Trident;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.uuid.EthernetAddress;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

public class TridentEvent {
	static Logger logger = LoggerFactory.getLogger(TridentEvent.class);
	public static NoArgGenerator uuidGenerator = Generators.timeBasedGenerator(EthernetAddress.fromInterface());

	static DateTimeFormatter utcFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
			.withZone(ZoneOffset.UTC);

	private ObjectNode envelope = JsonUtil.createObjectNode();

	private Instant eventTs = Instant.now();

	EventSystem eventSystem;

	private String eventType = generateEventType();

	boolean sent = false;

	Supplier<String> hostnameSupplier = Suppliers.memoize(TridentEvent::determineHostname);
	public TridentEvent() {
		withEnvelopeAttribute("eventId", uuidGenerator.generate().toString());

		envelope.put("eventTs", eventTs.toEpochMilli());
		envelope.put("eventDate", utcFormatter.format(eventTs));
		envelope.put("eventType", eventType);
		envelope.put("eventSource", hostnameSupplier.get());
		envelope.set("data", JsonUtil.createObjectNode());
	}

	public <T extends TridentEvent> T withTridentClusterId(String tridentClusterId) {
		return withEnvelopeAttribute("tridentClusterId", tridentClusterId);
	}

	public <T extends TridentEvent> T withTridentClusterName(String name) {
		return withEnvelopeAttribute("tridentClusterName", name);
	}

	public <T extends TridentEvent> T withMessage(String message) {
		return withEnvelopeAttribute("eventMessage", message);
	}
	public <T extends TridentEvent> T withEventSource(String source) {
		return withEnvelopeAttribute("eventSource", source);
	}
	public <T extends TridentEvent> T withEnvelopeAttribute(String key, String val) {
		envelope.put(key, val);
		return (T) this;
	}

	public <T extends TridentEvent> T withAttribute(String key, String val) {
		ObjectNode n = (ObjectNode) envelope.get("data");
		n.put(key, val);
		return (T) this;
	}


	public <T extends TridentEvent> T withPayload(ObjectNode d) {
		Preconditions.checkNotNull(d);
		envelope.set("data", d);
		return (T) this;
	}

	public String getEventId() {
		return envelope.get("eventId").asText(null);
	}

	public static String determineHostname() {

		String hostname = "localhost";

		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			logger.info("could not determine hostname");
		}

		try {
			if (hostname == null || hostname.contains("localhost")) {
				hostname = InetAddress.getLocalHost().getHostAddress();
			}
		} catch (UnknownHostException e) {
			logger.info("could not determine hostname");
		}
		return hostname;

	}

	public ObjectNode getData() {
		return (ObjectNode) envelope.get("data");
	}

	public ObjectNode getEnvelope() {
		return envelope;
	}

	public <T extends TridentEvent> T publish() {
		return send();
	}

	public <T extends TridentEvent> T send() {
		try {
			if (eventSystem == null) {
				this.eventSystem = Trident.getApplicationContext().getBean(EventSystem.class);
			}
			this.eventSystem.post(this);

		} catch (Exception e) {
			logger.warn("problem sending event", e);
		}
		return (T) this;
	}

	protected String generateEventType() {
		String name = getClass().getName().replace(getClass().getPackage().getName() + ".", "");
		java.util.List<String> list = Splitter.on("$").splitToList(name);
		String x = list.get(list.size() - 1);
		if (x.length() > 3) {
			// Don't allow anonymous inner classes
			name = x;
		}
		return "trident." + x;
	}
	
	public String getEventSource() {
		return envelope.path("eventSource").asText();
	}
	public String getEventMessage() {
		return getEnvelope().path("eventMessage").asText();
	}
	
	public String toString() {
		return MoreObjects.toStringHelper(this).add("eventId", getEventId()).toString();
	}
}
