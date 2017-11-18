package org.lendingclub.trident.dns;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.lendingclub.trident.dns.DNSManager.DNSRequest;
import org.lendingclub.trident.dns.DNSManager.RecordType;
import org.lendingclub.trident.swarm.aws.AWSAccountManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53ClientBuilder;
import com.amazonaws.services.route53.model.Change;
import com.amazonaws.services.route53.model.ChangeAction;
import com.amazonaws.services.route53.model.ChangeBatch;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsResult;
import com.amazonaws.services.route53.model.ListHostedZonesRequest;
import com.amazonaws.services.route53.model.ListHostedZonesResult;
import com.amazonaws.services.route53.model.RRType;
import com.amazonaws.services.route53.model.ResourceRecord;
import com.amazonaws.services.route53.model.ResourceRecordSet;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Maps;

public class Route53ChangeExecutor implements DNSChangeExecutor {

	@Autowired
	AWSAccountManager accountManager;

	Logger logger = LoggerFactory.getLogger(Route53ChangeExecutor.class);

	class HostedZoneInfo {
		String hostedZone;
		String account;

		public String toString() {
			return MoreObjects.toStringHelper(this).add("account", account).add("hostedZone", hostedZone).toString();
		}
	}

	long lastCheckTs = 0;
	Map<String, HostedZoneInfo> zoneMap = Maps.newConcurrentMap();

	private Optional<HostedZoneInfo> search(DNSRequest request) {
		String domainName = DNSManager.extractDomain(request.name);
		HostedZoneInfo hz = zoneMap.get(domainName);
		if (hz != null) {
			return Optional.of(hz);
		}
		if (System.currentTimeMillis() - lastCheckTs < TimeUnit.MINUTES.toMillis(5)) {
			return Optional.empty();
		}
		lastCheckTs = System.currentTimeMillis();
		for (String key : accountManager.getSuppliers().keySet()) {
			index(key);
		}
		hz = zoneMap.get(domainName);
		if (hz == null) {
			return Optional.empty();
		}
		return Optional.of(hz);
	}

	private void index(String accountName) {
		AmazonRoute53 c = this.accountManager.getClient(accountName, AmazonRoute53ClientBuilder.class);


		ListHostedZonesRequest hzRequest = new ListHostedZonesRequest();
		ListHostedZonesResult result = c.listHostedZones();
		boolean hasMore = true;

		while (hasMore) {
			result.getHostedZones().forEach(it -> {

				String domainName = it.getName();
				while (domainName.endsWith(".")) {
					domainName = domainName.substring(0, domainName.length() - 1);
				}
				HostedZoneInfo hz = new HostedZoneInfo();
				hz.account = accountName;
				hz.hostedZone = it.getId();
				this.zoneMap.put(domainName, hz);

			});
			hasMore = result.isTruncated();
			if (hasMore) {
				hzRequest.withMarker(result.getMarker());
				result = c.listHostedZones(hzRequest);
			}
		}

	}

	@Override
	public boolean accepts(DNSRequest request) {
		Optional<HostedZoneInfo> hz = search(request);
		return hz.isPresent();
	}

	@Override
	public void execute(DNSRequest request) {
		Optional<HostedZoneInfo> hz = search(request);
		if (!hz.isPresent()) {
			throw new IllegalArgumentException("cannot process " + request);
		}
		ChangeResourceRecordSetsRequest dnsRequest = new ChangeResourceRecordSetsRequest();
		ChangeBatch dnsChangeBatch = new ChangeBatch();
		dnsRequest.withChangeBatch(dnsChangeBatch);
		dnsRequest.setHostedZoneId(hz.get().hostedZone);
		ResourceRecordSet recordSet = new ResourceRecordSet();
		if (request.recordType == RecordType.CNAME) {

			recordSet = recordSet.withType(RRType.CNAME).withResourceRecords(
					request.recordValues.stream().map(f -> new ResourceRecord(f)).collect(Collectors.toList()));
		} else if (request.recordType == RecordType.A) {
			recordSet = recordSet.withType(RRType.A).withResourceRecords(
					request.recordValues.stream().map(f -> new ResourceRecord(f)).collect(Collectors.toList()));

		} else if (request.recordType == RecordType.SRV) {
			recordSet = recordSet.withType(RRType.SRV).withResourceRecords(
					request.recordValues.stream().map(f -> new ResourceRecord(f)).collect(Collectors.toList()));

		} else {
			throw new IllegalArgumentException("unsupported record type: " + request.recordType);
		}

		recordSet.withName(convertDnsNameToRoute53Format(request.name)).withTTL(60L);
		Change change = new Change().withAction(ChangeAction.UPSERT).withResourceRecordSet(recordSet);
		dnsChangeBatch.getChanges().add(change);
		AmazonRoute53 client = accountManager.getClient(hz.get().account,AmazonRoute53ClientBuilder.class);
		ChangeResourceRecordSetsResult result = client.changeResourceRecordSets(dnsRequest);
		logger.info(result.toString());
	}

	static String convertDnsNameToRoute53Format(String s) {
		if (s == null) {
			return s;
		}

		if (s.endsWith(".")) {
			return s;
		}
		if (s.contains(".")) {
			// FQDN needs to end with a .
			return s + ".";
		} else {
			return s;
		}
	}
}
