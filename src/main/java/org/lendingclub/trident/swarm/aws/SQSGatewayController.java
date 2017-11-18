package org.lendingclub.trident.swarm.aws;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.reflex.aws.sqs.SQSAdapter.SQSMessage;
import org.lendingclub.reflex.eventbus.ReflexBus;
import org.lendingclub.trident.cluster.TridentClusterManager;
import org.lendingclub.trident.event.EventSystem;
import org.lendingclub.trident.swarm.aws.event.AWSEvent;
import org.lendingclub.trident.util.JsonUtil;
import org.lendingclub.trident.util.TridentStartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.util.Base64;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;

import io.macgyver.okrest3.OkRestClient;
import io.macgyver.okrest3.OkRestResponse;
import io.macgyver.okrest3.OkRestTarget;
import io.reactivex.schedulers.Schedulers;
import okhttp3.FormBody;
import okhttp3.RequestBody;

@Controller
@RequestMapping(value = "/api/trident/aws/gateway")
public class SQSGatewayController implements TridentStartupListener {

	public static final String GATEWAY_REQUEST="sqsGatewayRequest";
	public static final String GATEWAY_RESPONSE="sqsGatewayResponse";
	Logger logger = LoggerFactory.getLogger(SQSGatewayController.class);

	@Autowired
	AWSAccountManager awsAccountManager;

	@Autowired
	TridentClusterManager tridentClusterManager;

	@Autowired
	NeoRxClient neo4j;

	@Autowired
	EventSystem eventSystem;

	OkRestClient client = new OkRestClient.Builder().build();

	String installationId;

	String targetUrl = "http://localhost:8080";

	@VisibleForTesting
	SQSGatewayController withInstallationId(String id) {
		this.installationId = id;
		return this;
	}

	@VisibleForTesting
	SQSGatewayController withTargetUrl(String url) {
		this.targetUrl = url;
		return this;
	}

	@Override
	public void onStart(ApplicationContext context) {
		installationId = tridentClusterManager.getTridentInstallationId();
		eventSystem.createConcurrentSubscriber(AWSEvent.class).withScheduler(Schedulers.io()).subscribe(c -> {
			try {

				String type = c.getData().path("type").asText();
				if (type.equals(GATEWAY_REQUEST)) {
					JsonNode jsonResponse = invokeLocal(c.getData());

					String responseArn = c.getData().path("responseQueueArn").asText();
					if (Strings.isNullOrEmpty(responseArn)) {
						throw new IllegalArgumentException("responseQueueArn not set");
					}
					AmazonSQS client = getSendQueue(responseArn);

					String queueName = Splitter.on(":").splitToList(responseArn).get(5);
					String targetUrl = client.getQueueUrl(queueName).getQueueUrl();
					JsonUtil.logInfo("sending SQS response", jsonResponse);
					client.sendMessage(targetUrl, jsonResponse.toString());

				} else if (type.equals(GATEWAY_RESPONSE)) {
					handleResponse(c.getData());
				}
			} catch (Exception e) {
				logger.warn("", e);
			}

		});
	}

	JsonNode invokeLocal(JsonNode data) {
		JsonUtil.logInfo("request", data);
		// make a local request

		OkRestTarget target = client.url(targetUrl).path(data.path("requestPath").asText());

		logger.info("invoking: {}",target.getUrl());
		FormBody.Builder formBody = new FormBody.Builder();

		String baseUrl = data.path("tridentBaseUrl").asText();
		if (!Strings.isNullOrEmpty(baseUrl)) {
			formBody.add("tridentBaseUrl", baseUrl);
		}

		data.path("params").fields().forEachRemaining(it -> {
			if (it.getValue().isTextual()) {
				formBody.add(it.getKey(), it.getValue().asText());
			}
		});
		OkRestResponse response = target.post(formBody.build()).execute();
		logger.info("Response: " + response.response().code());

		ObjectNode jsonResponse = JsonUtil.createObjectNode();
		jsonResponse.put("type", GATEWAY_RESPONSE);

		String requestId = data.path("requestId").asText();
		Preconditions.checkArgument(!Strings.isNullOrEmpty(requestId), "requestId not set");
		jsonResponse.put("requestId", data.get("requestId").asText());
		jsonResponse.put("responseCode", response.response().code());
		String contentType = response.response().header("Content-type");
		jsonResponse.put("contentType", contentType);
		try {
			if (isBinary(response.response())) {
				String val = BaseEncoding.base64().encode(response.response().body().bytes());
				jsonResponse.put("responseBodyBase64", val);
			} else {
				jsonResponse.put("responseBody", response.response().body().string());
			}
		} catch (IOException e) {
			logger.warn("error marshalling response", e);
		}

		return jsonResponse;

	}

	private boolean isBinary(okhttp3.Response r) {
		String contentType = r.header("Content-type");
		if (Strings.isNullOrEmpty(contentType)) {
			return false;
		}
		contentType = contentType.toLowerCase();
		return (contentType.contains("octet-stream") || contentType.contains("application/zip"));
	}
	private void handleResponse(JsonNode data) {

		// we have received a message, but we may be on a different node than

		String requestId = data.path("requestId").asText();

		String encoded = BaseEncoding.base64().encode(data.toString().getBytes());

		neo4j.execCypher(
				"merge (a:SQSGatewayMessage {requestId:{requestId}}) set a.message={message},a.createTs=timestamp()",
				"requestId", requestId, "message", encoded);

	}

	private void removeMessage(String id) {
		try {
			neo4j.execCypher("match (a:SQSGatewayMessage) where a.requestId={requestId} or timestamp()-a.createTs> delete a","requestId",id);
		}
		catch (RuntimeException e) {
			logger.warn("could not delete SQSGatewayMessage: {}",id);
		}
	}
	JsonNode convertClientRequestToJson(HttpServletRequest request) {

		String path = request.getRequestURI();
		String arn = Splitter.on("/").omitEmptyStrings().splitToList(path).get(4);
		ObjectNode r = JsonUtil.createObjectNode();

		ObjectNode params = JsonUtil.createObjectNode();

		path = Splitter.on(arn).splitToList(path).get(1);
		r.put("type", GATEWAY_REQUEST);
		r.put("requestId", UUID.randomUUID().toString());
		r.put("requestMethod", request.getMethod());
		if (path.startsWith("/api/trident")) {
			r.put("requestPath", path);
		} else {
			r.put("requestPath", "/api/trident" + path);
		}

		r.put("clientRequestUrl", request.getRequestURL().toString());
		r.put("requestQueueArn", arn);

		// returnQueue will be the sending queue region+account with the queue changed
		// to our own installation id
		List<String> returnArn = Lists.newArrayList(Splitter.on(":").splitToList(arn));
		if (returnArn.size() < 5) {
			throw new IllegalArgumentException("not sqs arn: " + arn);
		}
		returnArn.set(5, "trident-aws-events-" + installationId);// + tridentClusterManager.getTridentInstallationId());
		r.put("responseQueueArn", Joiner.on(":").join(returnArn));
		r.set("params", params);

		request.getParameterMap().keySet().forEach(k -> {
			String val = request.getParameter(k);
			params.put(k, val);
		});

		return r;
	}

	AmazonSQS getSendQueue(String arn) {
		List<String> x = Splitter.on(":").splitToList(arn);
		// arn:aws:sqs:us-east-1:123456789012:queue1
		String region = x.get(3);
		String account = x.get(4);
		String queue = x.get(5);

		AmazonSQS sqs = awsAccountManager.getClient(account, AmazonSQSClientBuilder.class, region);

		return sqs;

	}

	@RequestMapping(value = "/{arn}/**", method = { RequestMethod.GET,
			RequestMethod.POST })
	public ResponseEntity<String> gateway(@PathVariable("arn") String arn, HttpServletRequest request) {

		// 1 convert request to JSON
		JsonNode r = convertClientRequestToJson(request);

		JsonUtil.logInfo("sending message", r);
		// 2 find the corresponding queue and send it
		AmazonSQS sqs = getSendQueue(arn);
		String queueName = Splitter.on(":").splitToList(arn).get(5);

		SendMessageRequest sendMessageRequest = new SendMessageRequest(sqs.getQueueUrl(queueName).getQueueUrl(),
				r.toString());
		sqs.sendMessage(sendMessageRequest);

		// 3 wait for the corresponding response
		return waitForResponse(r, TimeUnit.SECONDS.toMillis(30));

	}

	ResponseEntity toResponseEntity(JsonNode n) {
		BodyBuilder bb = ResponseEntity.status(n.path("responseCode").asInt(500));
		if (n.has("contentType")) {
			bb.header("Content-type", n.path("contentType").asText());
		}
		if (n.has("responseBodyBase64")) {
			byte data [] = Base64.decode(n.path("responseBodyBase64").asText());
			return bb.body(data);
		}
		else {
			return bb.body(n.path("responseBody").asText());
		}
		
	
	}
	ResponseEntity getResponse(String id) {
		try {
			logger.info("looking for message id " + id);
			String encoded = neo4j
					.execCypher("match (a:SQSGatewayMessage {requestId:{requestId}}) return a", "requestId", id)
					.blockingFirst(MissingNode.getInstance()).path("message").asText(null);
			if (encoded == null) {
				return null;
			}
			JsonNode message = JsonUtil.getObjectMapper().readTree(BaseEncoding.base64().decode(encoded));

			
			JsonUtil.logInfo("unmarshalled response", message);
			removeMessage(id);
			return toResponseEntity(message);
			
		} catch (IOException e) {
			logger.warn("problem", e);
			removeMessage(id);
			return ResponseEntity.status(500).body("# "+e.toString());
		}
	
	}

	synchronized ResponseEntity<String> waitForResponse(JsonNode n, long timeoutMillis) {
		long ts = System.currentTimeMillis();
		long exp = ts + timeoutMillis;

		String id = n.path("requestId").asText();
		while (System.currentTimeMillis() < exp) {
			try {
				ResponseEntity<String> x = getResponse(id);
				if (x != null) {
					return x;
				}
				wait(500);
			} catch (Exception e) {
				// continue
			}
		}
		return ResponseEntity.status(500).body("# timeout");
	}

}
