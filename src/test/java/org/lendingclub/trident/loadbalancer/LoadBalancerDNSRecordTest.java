package org.lendingclub.trident.loadbalancer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.TridentIdentifier;
import org.lendingclub.trident.dns.DNSManager.RecordType;
import org.lendingclub.trident.loadbalancer.LoadBalancerDNSRecord;

import com.google.common.collect.Lists;

public class LoadBalancerDNSRecordTest {
	
	@Test
	public void createARecordShouldFailTest() { 
		try { 
			new LoadBalancerDNSRecord.Builder()
					.withTridentIdentifier(
							new TridentIdentifier()
									.withServiceGroup("www")
									.withEnvironment("demo")
									.withSubEnvironment("foobar")
									.withRegion("uw2")
									.withDomain("nonprod.domain.us"))
					.buildARecord();
			
		} catch (Exception e) { 
			Assertions.assertThat(e.getClass()).isEqualTo(IllegalArgumentException.class);
			Assertions.assertThat(e.getMessage()).contains("A Record value cannot be empty");
		}
	}
	
	@Test
	public void createARecordInvalidIPTest() { 
		try { 
			new LoadBalancerDNSRecord.Builder()
			.withTridentIdentifier(
					new TridentIdentifier()
							.withServiceGroup("www")
							.withEnvironment("demo")
							.withSubEnvironment("foobar")
							.withRegion("uw2")
							.withDomain("nonprod.domain.us"))
					.withIpRecords("0.0.0.0", "266.23.1.1")
					.buildARecord();
			
		} catch (Exception e) { 
			Assertions.assertThat(e.getClass()).isEqualTo(IllegalArgumentException.class);
			Assertions.assertThat(e.getMessage()).contains("invalid IP");
		}
	}
	
	@Test
	public void createARecordTest() { 
		LoadBalancerDNSRecord record = new LoadBalancerDNSRecord.Builder()
				.withTridentIdentifier(
						new TridentIdentifier()
								.withServiceGroup("www")
								.withEnvironment("demo")
								.withSubEnvironment("foobar")
								.withRegion("uw2")
								.withDomain("nonprod.domain.us"))
				.withIpRecord("1.1.1.1")
				.withIpRecord("1.2.3.4")
				.buildARecord();
		
		Assertions.assertThat(record.getRecordValue().size()).isEqualTo(2);
		Assertions.assertThat(record.getRecordValue().contains("1.1.1.1"));
		Assertions.assertThat(record.getRecordValue().contains("1.2.3.4"));
		Assertions.assertThat(record.getRecordName()).isEqualTo("www-demo-foobar-uw2.nonprod.domain.us");
		Assertions.assertThat(record.getRecordType()).isEqualTo(RecordType.A);
	}
	
	@Test
	public void createCNAMERecordTest() { 
		LoadBalancerDNSRecord record = new LoadBalancerDNSRecord.Builder()
				.withCNAME("foobar.domain.us")
				.withDomainNameRecords(Lists.newArrayList("foo.nonprod.us", "bar.nonprod.us", "foo.nonprod.com"))
				.buildCNAMERecord();
		
		Assertions.assertThat(record.getRecordValue().size()).isEqualTo(3);
		Assertions.assertThat(record.getRecordValue().contains("foo.nonprod.us"));
		Assertions.assertThat(record.getRecordValue().contains("bar.nonprod.us"));
		Assertions.assertThat(record.getRecordValue().contains("foo.nonprod.com"));
		Assertions.assertThat(record.getRecordName()).isEqualTo("foobar.domain.us");
		Assertions.assertThat(record.getRecordType()).isEqualTo(RecordType.CNAME);
	}
	
	@Test
	public void createSRVRecordTest() { 
		LoadBalancerDNSRecord record = new LoadBalancerDNSRecord.Builder()
				.withTridentIdentifier(
						new TridentIdentifier()
								.withServiceGroup("www")
								.withEnvironment("demo")
								.withSubEnvironment("foobar")
								.withRegion("uw2")
								.withDomain("nonprod.domain.us")
								.withPort(8080))
				.buildSRVRecord();
		
		Assertions.assertThat(record.getRecordValue().size()).isEqualTo(1);
		Assertions.assertThat(record.getRecordValue().contains("0 0 8080 www-demo-foobar-uw2.nonprod.domain.us"));
		Assertions.assertThat(record.getRecordName()).isEqualTo("_www-demo-foobar-uw2._tcp.nonprod.domain.us");
		Assertions.assertThat(record.getRecordType()).isEqualTo(RecordType.SRV);
	}
	
	@Test
	public void createSRVRecordMissingPortTest() { 
		try { 
			LoadBalancerDNSRecord record = new LoadBalancerDNSRecord.Builder()
					.withTridentIdentifier(
							new TridentIdentifier()
									.withServiceGroup("www")
									.withEnvironment("demo")
									.withSubEnvironment("foobar")
									.withRegion("uw2")
									.withDomain("nonprod.domain.us"))
					.buildSRVRecord();
		} catch (Exception e) { 
			Assertions.assertThat(e.getClass()).isEqualTo(IllegalArgumentException.class);
			Assertions.assertThat(e.getMessage()).contains("port cannot be null");
		}
	}
	
	@Test
	public void createSRVRecordEmptyStringTest() { 
		try { 
			LoadBalancerDNSRecord record = new LoadBalancerDNSRecord.Builder()
					.withTridentIdentifier(
							new TridentIdentifier()
									.withServiceGroup("www")
									.withEnvironment("demo")
									.withSubEnvironment("foobar")
									.withRegion("uw2")
									.withDomain("")
									.withPort(8080))
					.buildSRVRecord();
		} catch (Exception e) { 
			Assertions.assertThat(e.getClass()).isEqualTo(IllegalArgumentException.class);
			Assertions.assertThat(e.getMessage()).contains("domain cannot be null or empty");
		}
	}
}
