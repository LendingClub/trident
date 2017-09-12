package org.lendingclub.trident.swarm.aws;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.SwarmNodeType;
import org.lendingclub.trident.swarm.aws.SwarmASGBuilder;
import org.lendingclub.trident.util.JsonUtil;

public class AWSSwarmBuilderTest {

	
	@Test
	public void testIt() {
		SwarmASGBuilder b = new SwarmASGBuilder(JsonUtil.createObjectNode());
		
		Assertions.assertThat(b.getSwarmNodeType()).isEqualTo(SwarmNodeType.MANAGER);
		b.withSwarmNodeType(SwarmNodeType.WORKER);
		Assertions.assertThat(b.getSwarmNodeType()).isEqualTo(SwarmNodeType.WORKER);
		
		
		Assertions.assertThat(b.getRequestData()).isNotNull();
		
		Assertions.assertThat(b.getSwarmName()).isNull();
		b.getRequestData().put("name", "foo");
		Assertions.assertThat(b.getSwarmName()).isEqualTo("foo");
	
		
		Assertions.assertThat(b.getHostedZoneId()).isNull();
		b.withHostedZoneId("ABCD123");
		Assertions.assertThat(b.getHostedZoneId()).isEqualTo("ABCD123");
		
	}
}
