package org.lendingclub.trident.agent;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.Files;

import io.macgyver.okrest3.OkRestClient;

public abstract class AWSTridentAgent extends TridentAgent {

	String metadataUrl = "http://169.254.169.254/latest/meta-data";
	Cache<String, String> metadataCache = CacheBuilder.newBuilder().build();
	static OkRestClient metadaClient = new OkRestClient.Builder().withOkHttpClientConfig(it -> {
		it.connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS);
	}).build();

	Boolean isEC2 = null;

	public void setMetadataBaseUrl(String s) {
		this.metadataUrl = s;
	}

	public String getMetadataBaseUrl() {
		return metadataUrl;
	}

	public boolean isRunningInEC2() {
		if (isEC2 == null) {
			determineIfRunningInEC2();
		}
		return isEC2;
	}

	private void determineIfRunningInEC2() {
		if (isEC2 != null) {
			// we have already made a determination
		}
		File f = new File("/sys/hypervisor/uuid");
		if (!f.exists()) {
			// if this file is not available, we are definitely not in ec2
			isEC2 = false;
			return;
		}

		try {
			String val = Files.toString(f, Charsets.UTF_8).trim();
			if (!val.startsWith("ec2")) {
				// if the value does NOT start with "ec2" then we are not in ec2
				isEC2 = false;
				return;
			}

		} catch (Exception e) {

		}
		// We are PROBABLY in EC2 at this point, confirm with a positive
		// metadata response

		Optional<String> val = getMetadata("/instance-id", false);
		if (val.isPresent()) {
			isEC2 = true;
		}
		isEC2 = false;

	}

	public Optional<String> getMetadataAttribute(String attribute) {
		return getMetadata(attribute, true);
	}

	@VisibleForTesting
	public void setMetadataAttribute(String key, String val) {
		while (key.startsWith("/")) {
			key = key.substring(1);
		}
		metadataCache.put(key, val);
	}

	protected void cache(String attribue, String val) {
		if (Strings.isNullOrEmpty(attribue)) {
			return;
		}
		if (attribue.contains("spot/termination-time")) {
			// don't cache spot termination
			return;
		}
		metadataCache.put(attribue, val);
	}

	private Optional<String> getMetadata(String attribute, boolean checkEc2) {
		try {
			while (attribute.startsWith("/")) {
				attribute = attribute.substring(1);
			}
			String val = metadataCache.getIfPresent(attribute);
			if (val != null) {
				return Optional.ofNullable(val);
			}
			if (checkEc2 && (!isRunningInEC2())) {
				return Optional.empty();
			}
			val = metadaClient.url(metadataUrl).path(attribute).get().execute(String.class);
			cache(attribute, val);
			return Optional.ofNullable(val);
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	@Override
	ObjectNode createIdentityEnvelope() {

		ObjectNode n = super.createIdentityEnvelope();
		if (isRunningInEC2()) {
			ObjectNode aws = mapper.createObjectNode();
			aws.put("instance-id", getMetadataAttribute("instance-id").orElse(null));
			aws.put("ami-id", getMetadataAttribute("ami-id").orElse(null));
			aws.put("instance-type", getMetadataAttribute("instance-type").orElse(null));
			aws.put("local-ipv4", getMetadataAttribute("local-ipv4").orElse(null));
			n.set("awsMetadata", aws);
		}
		return n;
	}
}
