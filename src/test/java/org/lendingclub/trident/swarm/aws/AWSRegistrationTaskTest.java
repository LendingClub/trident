package org.lendingclub.trident.swarm.aws;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import org.glassfish.jersey.client.ClientConfig;
import org.junit.Ignore;
import org.junit.Test;
import org.lendingclub.trident.swarm.aws.AWSRegistrationTask;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;

public class AWSRegistrationTaskTest {

	String test="{\"ID\":\"w48xempra29uouv3sg5ow43no\",\"Version\":{\"Index\":17},\"CreatedAt\":\"2017-07-22T17:03:01.355079775Z\",\"UpdatedAt\":\"2017-07-22T17:52:06.129224089Z\",\"Spec\":{\"Labels\":{\"a\":\"b\"},\"Role\":\"manager\",\"Availability\":\"active\"},\"Description\":{\"Hostname\":\"moby\",\"Platform\":{\"Architecture\":\"x86_64\",\"OS\":\"linux\"},\"Resources\":{\"NanoCPUs\":4000000000,\"MemoryBytes\":5189804032},\"Engine\":{\"EngineVersion\":\"17.06.0-ce\",\"Plugins\":[{\"Type\":\"Log\",\"Name\":\"awslogs\"},{\"Type\":\"Log\",\"Name\":\"fluentd\"},{\"Type\":\"Log\",\"Name\":\"gcplogs\"},{\"Type\":\"Log\",\"Name\":\"gelf\"},{\"Type\":\"Log\",\"Name\":\"journald\"},{\"Type\":\"Log\",\"Name\":\"json-file\"},{\"Type\":\"Log\",\"Name\":\"logentries\"},{\"Type\":\"Log\",\"Name\":\"splunk\"},{\"Type\":\"Log\",\"Name\":\"syslog\"},{\"Type\":\"Network\",\"Name\":\"bridge\"},{\"Type\":\"Network\",\"Name\":\"host\"},{\"Type\":\"Network\",\"Name\":\"ipvlan\"},{\"Type\":\"Network\",\"Name\":\"macvlan\"},{\"Type\":\"Network\",\"Name\":\"null\"},{\"Type\":\"Network\",\"Name\":\"overlay\"},{\"Type\":\"Volume\",\"Name\":\"local\"}]},\"TLSInfo\":{\"TrustRoot\":\"-----BEGIN CERTIFICATE-----\\nMIIBajCCARCgAwIBAgIUYOMosEHcmqFEsdiLxmiuQWy3yyMwCgYIKoZIzj0EAwIw\\nEzERMA8GA1UEAxMIc3dhcm0tY2EwHhcNMTcwNzIyMTY1ODAwWhcNMzcwNzE3MTY1\\nODAwWjATMREwDwYDVQQDEwhzd2FybS1jYTBZMBMGByqGSM49AgEGCCqGSM49AwEH\\nA0IABCSejLq9XrUgYHpBKRaYrBeJYQ4rzBkdFp1i3uTqNyK5zVoziCGzZMze8STy\\naFld2XHV8w2FVBHWdoVUFfnxUcyjQjBAMA4GA1UdDwEB/wQEAwIBBjAPBgNVHRMB\\nAf8EBTADAQH/MB0GA1UdDgQWBBTGzs9vw//VwHIoUGjquGLYuedJXzAKBggqhkjO\\nPQQDAgNIADBFAiB/JJAB1zPmn1h0r2k4H3o2jLn1J47sUnydO0rXJlv7LwIhAK/S\\nzLulxXpfQboyYAVgQErgiZqoSm5/YhUcopASFBcM\\n-----END CERTIFICATE-----\\n\",\"CertIssuerSubject\":\"MBMxETAPBgNVBAMTCHN3YXJtLWNh\",\"CertIssuerPublicKey\":\"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEJJ6Mur1etSBgekEpFpisF4lhDivMGR0WnWLe5Oo3IrnNWjOIIbNkzN7xJPJoWV3ZcdXzDYVUEdZ2hVQV+fFRzA==\"}},\"Status\":{\"State\":\"ready\",\"Addr\":\"192.168.65.2\"},\"ManagerStatus\":{\"Leader\":true,\"Reachability\":\"reachable\",\"Addr\":\"192.168.65.2:2377\"}}\n" + 
			"";
	@Test
	@Ignore
	public void teestIt() {
		ClientConfig cfg = new ClientConfig();
		cfg.register(JacksonJaxbJsonProvider.class);
		WebTarget wt = ClientBuilder.newClient(cfg).target("http://localhost:2233");
		
		
		new AWSRegistrationTask().addLabelToNode(wt, "w48xempra29uouv3sg5ow43no", "a", "b");
	}
}
