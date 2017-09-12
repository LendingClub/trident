package org.lendingclub.trident.swarm.aws;

import java.io.IOException;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.swarm.aws.AWSTerminatedNodeCleanupTask;
import org.lendingclub.trident.util.JsonUtil;

import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.Reservation;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;


public class AWSTerminatedNodeCleanupTaskTest  {

	
	@Test
	public void testIsCandidate() throws IOException {
		String json = "{\"ID\":\"rlnzpxd8kh8kps15z8hdu5pn7\",\"Version\":{\"Index\":287},\"CreatedAt\":\"2017-07-22T16:17:41.418006718Z\",\"UpdatedAt\":\"2017-07-22T22:22:48.583332995Z\",\"Spec\":{\"Labels\":{\"aws_instanceId\":\"i-0ee9980486862f727\"},\"Role\":\"worker\",\"Availability\":\"active\"},\"Description\":{\"Hostname\":\"uw2-docker-swarm-worker-nprd-517b7d\",\"Platform\":{\"Architecture\":\"x86_64\",\"OS\":\"linux\"},\"Resources\":{\"NanoCPUs\":2000000000,\"MemoryBytes\":3705356288},\"Engine\":{\"EngineVersion\":\"17.06.0-ce\",\"Plugins\":[{\"Type\":\"Log\",\"Name\":\"awslogs\"},{\"Type\":\"Log\",\"Name\":\"fluentd\"},{\"Type\":\"Log\",\"Name\":\"gcplogs\"},{\"Type\":\"Log\",\"Name\":\"gelf\"},{\"Type\":\"Log\",\"Name\":\"journald\"},{\"Type\":\"Log\",\"Name\":\"json-file\"},{\"Type\":\"Log\",\"Name\":\"logentries\"},{\"Type\":\"Log\",\"Name\":\"splunk\"},{\"Type\":\"Log\",\"Name\":\"syslog\"},{\"Type\":\"Network\",\"Name\":\"bridge\"},{\"Type\":\"Network\",\"Name\":\"host\"},{\"Type\":\"Network\",\"Name\":\"macvlan\"},{\"Type\":\"Network\",\"Name\":\"null\"},{\"Type\":\"Network\",\"Name\":\"overlay\"},{\"Type\":\"Volume\",\"Name\":\"local\"}]},\"TLSInfo\":{\"TrustRoot\":\"-----BEGIN CERTIFICATE-----\\nMIIBazCCARCgAwIBAgIUc4e8hq2d3FI/457mJA88JTiOHGMwCgYIKoZIzj0EAwIw\\nEzERMA8GA1UEAxMIc3dhcm0tY2EwHhcNMTcwNzE5MTcyNDAwWhcNMzcwNzE0MTcy\\nNDAwWjATMREwDwYDVQQDEwhzd2FybS1jYTBZMBMGByqGSM49AgEGCCqGSM49AwEH\\nA0IABODanct3ynU0BJ7oSkvAltrxpAPAfQQn0j5ZQabz0AVGvgSCuJ9RhXOdL4wP\\nolRHlNc2zGivCQa3l7eLbUFJry6jQjBAMA4GA1UdDwEB/wQEAwIBBjAPBgNVHRMB\\nAf8EBTADAQH/MB0GA1UdDgQWBBSu9QAdTfz4o5Rxpu+OfLRFQBx6pTAKBggqhkjO\\nPQQDAgNJADBGAiEAraKLiKBkb66+iLwhRMkII3weQhZNHZyMA6mh50xNlLoCIQCA\\nHd587ejnDHvdoNTfZNvBtm9Sfk3SxvYrOiJ0B/jh7Q==\\n-----END CERTIFICATE-----\\n\",\"CertIssuerSubject\":\"MBMxETAPBgNVBAMTCHN3YXJtLWNh\",\"CertIssuerPublicKey\":\"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE4Nqdy3fKdTQEnuhKS8CW2vGkA8B9BCfSPllBpvPQBUa+BIK4n1GFc50vjA+iVEeU1zbMaK8JBreXt4ttQUmvLg==\"}},\"Status\":{\"State\":\"down\",\"Message\":\"heartbeat failure\",\"Addr\":\"10.81.123.125\"}}";
		JsonNode n = JsonUtil.getObjectMapper().readTree(json);
		AWSTerminatedNodeCleanupTask task = new AWSTerminatedNodeCleanupTask();
		
		System.out.println(task.isCandidate(n));
		
		String tridentClusterId = n.path("tridentClusterId").asText();
		String awsInstance = n.path("Spec").path("Labels").path("aws_instanceId").asText();
		
		System.out.println(awsInstance);
	}
	@Test
	public void testResponse() {
		Assertions.assertThat(new AWSTerminatedNodeCleanupTask().responseProvesInstanceIsMissing(new DescribeInstancesResult())).isFalse();
		
		DescribeInstancesResult r = new DescribeInstancesResult();
		r.withReservations(Lists.newArrayList());
		Assertions.assertThat(new AWSTerminatedNodeCleanupTask().responseProvesInstanceIsMissing(r)).isFalse();
		
		Reservation reservation = new Reservation();
		r.withReservations(reservation);
		
		Instance x = new Instance();
		reservation.withInstances(x);
		x.withState(new InstanceState().withCode(64));
		
		Assertions.assertThat(new AWSTerminatedNodeCleanupTask().responseProvesInstanceIsMissing(r)).isFalse();
		
		x = new Instance();
		reservation.withInstances(x);
		x.withState(new InstanceState().withCode(48));
		
		Assertions.assertThat(new AWSTerminatedNodeCleanupTask().responseProvesInstanceIsMissing(r)).isTrue();
		
	}
	@Test
	public void testExceptions() {
		Assertions.assertThat(new AWSTerminatedNodeCleanupTask().exceptionProvesInstanceIsMissing(new RuntimeException())).isFalse();
		Assertions.assertThat(new AWSTerminatedNodeCleanupTask().exceptionProvesInstanceIsMissing(new AmazonEC2Exception("foo"))).isFalse();
		
		
		AmazonEC2Exception x = new AmazonEC2Exception("hello");
		x.setErrorCode("InvalidInstanceID.NotFound");
		Assertions.assertThat(new AWSTerminatedNodeCleanupTask().exceptionProvesInstanceIsMissing(x)).isTrue();
		
		x = new AmazonEC2Exception("hello");
		x.setErrorCode("Something");
		Assertions.assertThat(new AWSTerminatedNodeCleanupTask().exceptionProvesInstanceIsMissing(x)).isFalse();
		
	}
}
