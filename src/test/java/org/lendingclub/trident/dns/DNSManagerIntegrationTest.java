package org.lendingclub.trident.dns;

import org.junit.Ignore;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

public class DNSManagerIntegrationTest extends TridentIntegrationTest {

	
	@Autowired
	DNSManager dnsManager;
	
	@Test
	@Ignore
	public void testIt() {
		dnsManager.createARecordRequest("foo.nonprod.example.com").withValue("127.0.0.1").execute();
		dnsManager.createSRVRecordRequest("bar","tcp","nonprod.example.com")
				.withValue(0,0,8004,"foo.nonprod.example.com")
				.withValue(0,0,8005,"foo.nonprod.example.com")
				.execute();
	}
}
