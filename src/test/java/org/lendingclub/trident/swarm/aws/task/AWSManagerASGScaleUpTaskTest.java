package org.lendingclub.trident.swarm.aws.task;

import java.time.Instant;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.lendingclub.trident.util.DockerDateFormatter;
import org.lendingclub.trident.util.JsonUtil;


public class AWSManagerASGScaleUpTaskTest {

	
	@Test
	public void testIt() {
		
		AWSManagerASGScaleUpTask task = new AWSManagerASGScaleUpTask().withDryRun(true);
		String ts = DockerDateFormatter.DOCKER_DATE_TIME_FORMATTER.format(Instant.now());
		Assertions.assertThat(AWSManagerASGScaleUpTask.isScaleUpRequired(JsonUtil.createObjectNode().put("swarmNodeType", "MANAGER").put("aws_desiredCapacity", 1).put("createdAt", ts))).isTrue();
		Assertions.assertThat(AWSManagerASGScaleUpTask.isScaleUpRequired(JsonUtil.createObjectNode().put("swarmNodeType", "MANAGER").put("aws_desiredCapacity", 3).put("createdAt", ts))).isFalse();
		Assertions.assertThat(AWSManagerASGScaleUpTask.isScaleUpRequired(JsonUtil.createObjectNode().put("swarmNodeType", "WORKER").put("aws_desiredCapacity", 3).put("createdAt", ts))).isFalse();
		Assertions.assertThat(AWSManagerASGScaleUpTask.isScaleUpRequired(JsonUtil.createObjectNode().put("swarmNodeType", "MANAGER").put("aws_desiredCapacity", 1).put("createdAt", ts))).isTrue();
		Assertions.assertThat(AWSManagerASGScaleUpTask.isScaleUpRequired(JsonUtil.createObjectNode().put("swarmNodeType", "MANAGER").put("aws_desiredCapacity", 1).put("createdAt","2017-11-15T12:49:57.336000000Z" ))).isFalse();
	}
}
