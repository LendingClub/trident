package org.lendingclub.trident.loadbalancer;

import java.util.Collection;
import java.util.Set;

import org.lendingclub.trident.TridentIdentifier;
import org.lendingclub.trident.dns.DNSManager.RecordType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class LoadBalancerDNSRecord {
	
	private final String recordName;
	private final Set<String> recordValue;
	private final RecordType recordType;
	
	public LoadBalancerDNSRecord(String recordName, Set<String> recordValue, RecordType recordType) { 
		this.recordName = recordName;
		this.recordValue = recordValue;
		this.recordType = recordType;
	}
	
	public String getRecordName() { 
		return recordName;
	}
	
	public Set<String> getRecordValue() { 
		return recordValue;
	}
	
	public RecordType getRecordType() {  
		return recordType;
	}
	
	public static class Builder {
		TridentIdentifier identifier;
		
		//used only for CNAME record
		String cname;

		//used only for A Record
		Set<String> ipRecordSet = Sets.newHashSet();
		
		//used only for CNAME Record
		Set<String> domainNameRecordSet = Sets.newHashSet();
		
		Logger logger = LoggerFactory.getLogger(LoadBalancerDNSRecord.class);
		
		private static final String SRV_RECORD_PROTOCOL = "tcp";
		
		public Builder withTridentIdentifier(TridentIdentifier identifier) {
			this.identifier = identifier;
			return this;
		}
		
		/**
		 * Add a CNAME alias for a CNAME record
		 */
		public Builder withCNAME(String cname) {
			this.cname = cname;
			return this;
		}
		
		/**
		 * Add an IP value for an A Record
		 */
		public Builder withIpRecord(String ip) {
			return withIpRecords(Lists.newArrayList(ip));
		}
		
		/**
		 * Add IP values for an A Record
		 */
		public Builder withIpRecords(String... ips) {
			return withIpRecords(Lists.newArrayList(ips));
		}
		
		/**
		 * Add a collection of IP values for an A Record
		 */
		public Builder withIpRecords(Collection<String> ips) { 
			for (String ip : ips) { 
				validateIp(ip);
				this.ipRecordSet.add(ip);
			}
			return this;
		}
		
		/**
		 * Add a domain name for a CNAME Record
		 */
		public Builder withDomainNameRecord(String domainName) {
			return withDomainNameRecords(Lists.newArrayList(domainName));
		}
		
		/**
		 * Add domain names for a CNAME Record
		 */
		public Builder withDomainNameRecords(String... domainNames) {
			return withDomainNameRecords(Lists.newArrayList(domainNames));
		}
		
		/**
		 * Add a collection of domain names for a CNAME Record
		 */
		public Builder withDomainNameRecords(Collection<String> domainNames) { 
			for (String d : domainNames) { 
				this.domainNameRecordSet.add(d);
			}
			return this;
		}
		
		/**
		 * Builds an A Record that points to the given set of IP addresses.
		 * Requires IP records, serviceGroup, env, subEnv, region and domain.
		 */
		public LoadBalancerDNSRecord buildARecord() { 
			Preconditions.checkNotNull(identifier, "trident identifier cannot be null");
			Preconditions.checkArgument(identifier.getServiceGroup().isPresent(), "service group cannot be null or empty");
			Preconditions.checkArgument(identifier.getEnvironment().isPresent(), "environment cannot be null or empty");
			Preconditions.checkArgument(identifier.getSubEnvironment().isPresent(), "subEnvironment cannot be null or empty");
			Preconditions.checkArgument(identifier.getRegion().isPresent(), "region cannot be null or empty");
			Preconditions.checkArgument(identifier.getDomain().isPresent(), "domain cannot be null or empty");
			Preconditions.checkArgument(!ipRecordSet.isEmpty(), "A Record value cannot be empty. Please add at least one IP record.");
			
			return new LoadBalancerDNSRecord(
					String.format("%s-%s-%s-%s.%s", 
							identifier.getServiceGroup().get(), 
							identifier.getEnvironment().get(), 
							identifier.getSubEnvironment().get(), 
							identifier.getRegion().get(), 
							identifier.getDomain().get()),
					ipRecordSet,
					RecordType.A);
		}
		
		/**
		 * Builds a CNAME Record that points to the given set of domain names.
		 * Requires cname alias and domain name records.
		 */
		public LoadBalancerDNSRecord buildCNAMERecord() { 
			Preconditions.checkArgument(!Strings.isNullOrEmpty(cname), "cname cannot be null or empty");
			Preconditions.checkArgument(!domainNameRecordSet.isEmpty(), "CNAME Record value cannot be empty. Please add at least one domain name record.");
			
			return new LoadBalancerDNSRecord(cname, domainNameRecordSet, RecordType.CNAME);
		}
		
		/**
		 * Builds an SRV Record that points to a value generated from the given
		 * port, serviceGroup, env, subEnv, region and domain.
		 */
		public LoadBalancerDNSRecord buildSRVRecord() { 
			Preconditions.checkNotNull(identifier, "trident identifier cannot be null");
			Preconditions.checkArgument(identifier.getServiceGroup().isPresent(), "service group cannot be null or empty");
			Preconditions.checkArgument(identifier.getEnvironment().isPresent(), "environment cannot be null or empty");
			Preconditions.checkArgument(identifier.getSubEnvironment().isPresent(), "subEnvironment cannot be null or empty");
			Preconditions.checkArgument(identifier.getRegion().isPresent(), "region cannot be null or empty");
			Preconditions.checkArgument(identifier.getDomain().isPresent(), "domain cannot be null or empty");
			Preconditions.checkArgument(identifier.getPort().isPresent(), "port cannot be null");

			return new LoadBalancerDNSRecord(
					String.format("_%s-%s-%s-%s._%s.%s", 
							identifier.getServiceGroup().get(), 
							identifier.getEnvironment().get(), 
							identifier.getSubEnvironment().get(), 
							identifier.getRegion().get(), 
							SRV_RECORD_PROTOCOL, 
							identifier.getDomain().get()), 
					Sets.newHashSet(String.format("0 0 %d %s-%s-%s-%s.%s ", 
							identifier.getPort().get(), 
							identifier.getServiceGroup().get(), 
							identifier.getEnvironment().get(), 
							identifier.getSubEnvironment().get(), 
							identifier.getRegion().get(), 
							identifier.getDomain().get())), 
					RecordType.SRV);
		}
		
		public TridentIdentifier getIdentifier() {
			return identifier;
		}
		
		public String getCname() {
			return cname;
		}
	
		public Set<String> getIpRecords() {
			return ipRecordSet;
		}
		
		public Set<String> getDomainNameRecords() {
			return domainNameRecordSet;
		}
		
		protected void validateIp(String ip) { 
			if (!ip.matches(
					"\\b(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\."
					+ "(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\."
					+ "(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\."
					+ "(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\b")) { 
				throw new IllegalArgumentException("invalid IP: " + ip);
			}
		}
	}
}