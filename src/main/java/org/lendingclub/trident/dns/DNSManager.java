package org.lendingclub.trident.dns;

import java.util.List;
import java.util.Set;

import org.lendingclub.trident.loadbalancer.LoadBalancerDNSRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@Component
public class DNSManager {

	@Autowired
	Route53ChangeExecutor awsExecutor;
	
	private static Logger logger = LoggerFactory.getLogger(DNSManager.class);
	
	List<DNSChangeExecutor> executorList = Lists.newArrayList();

	public DNSManager() {
		
	}
	
	public static enum RecordType {
		CNAME, A, SRV;
	}

	public abstract class DNSRequest {
		Set<String> recordValues = Sets.newHashSet();
		String name;
		RecordType recordType;

		public <T extends DNSRequest> T withName(String name) {
			this.name = name;
			return (T) this;
		}

		<T extends DNSRequest> T withRecordType(RecordType t) {
			this.recordType = t;
			return (T) this;
		}

		public <T extends DNSRequest> T withValue(String val) {
			recordValues.add(val);
			return (T) this;
		}
		
		public <T extends DNSRequest> T setValue(Set<String> recordValues) { 
			this.recordValues = recordValues;
			return (T) this;
		}

		public Set<String> getRecordValues() {
			return recordValues;
		}

		public String getName() {
			return name;
		}

		public RecordType getRecordType() {
			return recordType;
		}

		<T extends DNSRequest> T execute() {
			exec(this);
			return (T) this;
		}
	}

	class CNAMERecordRequest extends DNSRequest {

		public CNAMERecordRequest() {
			super();
			withRecordType(RecordType.CNAME);
		}

	}

	class SRVRecordRequest extends DNSRequest {
		
		
		
		public SRVRecordRequest() {
			super();
			withRecordType(RecordType.SRV);
		}
		
		public SRVRecordRequest withValue(int priority, int weight, int port, String val) {
			return withValue(String.format("%d %d %d %s", priority,weight,port,val));
		}
		
		
	}

	class ARecordRequest extends DNSRequest {
		public ARecordRequest() {
			super();
			withRecordType(RecordType.A);
		}
	}

	public ARecordRequest createARecordRequest(String name) {
		ARecordRequest request = new ARecordRequest().withName(name).withRecordType(RecordType.A);
		return request;
	}

	public CNAMERecordRequest createCNAMERecordRequest(String name) {
		CNAMERecordRequest request = new CNAMERecordRequest().withName(name).withRecordType(RecordType.CNAME);
		return request;
	}
	
	public SRVRecordRequest createSRVRecordRequest(String service, String proto, String domain) {
		if (!domain.endsWith(".")) {
			domain=domain+".";
		}
		String name = String.format("_%s._%s.%s", service,proto,domain);
		return createSRVRecordRequest(name);
	}
	
	public SRVRecordRequest createSRVRecordRequest(String name) {
		SRVRecordRequest request = new SRVRecordRequest().withName(name).withRecordType(RecordType.SRV);
		return request;
	}

	DNSChangeExecutor findExecutor(DNSRequest request) {

	
		for (DNSChangeExecutor x : executorList) {
			if (x.accepts(request)) {
				return x;
			}
		}

		throw new IllegalStateException("DNSChangeExecutor could not be found for domain: " + extractDomain(request.name));

	}

	public void register(Route53ChangeExecutor x) {
		this.executorList.add(x);
	}
	public static String extractDomain(String input) {

		String d = input;
		int idx = d.indexOf(".");
		if (idx < 0) {
			throw new IllegalArgumentException("invalid FQDN: " + d);
		}
		d = d.substring(idx+1);
		while (d.endsWith(".")) {
			d = d.substring(0, d.length() - 1);
		}
		if (d.contains("_")) {
			// if there is an underbar in the name, it is an SRV record...keep stripping until it is all gone
			return extractDomain(d);
		}
		return d;
	}
	
	public void exec(LoadBalancerDNSRecord record) { 
		String recordName = record.getRecordName();
		Set<String> recordValue = record.getRecordValue();
		RecordType recordType = record.getRecordType();
		
		Preconditions.checkArgument(!Strings.isNullOrEmpty(recordName), "recordName cannot be empty or null");
		Preconditions.checkArgument(!recordValue.isEmpty(), "recordValue cannot be empty");
		Preconditions.checkNotNull(recordType, "recordType cannot be null");
	
		logger.info("registering recordType={} {}={}", record.getRecordType(), record.getRecordName(), record.getRecordValue());
		if (recordType == RecordType.A) { 
			createARecordRequest(recordName).setValue(recordValue).execute();
		} else if (recordType == RecordType.CNAME) { 
			createCNAMERecordRequest(recordName).setValue(recordValue).execute();
		} else if (recordType == RecordType.SRV) { 
			createSRVRecordRequest(recordName).setValue(recordValue).execute();
		}
	}
	
	protected void exec(DNSRequest request) {
		findExecutor(request).execute(request);
	}

}
