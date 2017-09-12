package org.lendingclub.trident.swarm.aws;

import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.swarm.aws.AWSMetadataSync;
import org.lendingclub.trident.util.JsonUtil;

import com.amazonaws.services.autoscaling.model.Tag;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AWSMetadataSyncTest {

	
	@Test
	public void testIt() {
		
		AWSMetadataSync sync = new AWSMetadataSync();
		
		
		ObjectNode n = JsonUtil.createObjectNode();
		
		
		Assertions.assertThat(sync.createTagsForSwarm(n)).isEmpty();
		
		n.put("tridentClusterId", "abc");
		n.put("tridentOwnerId", "foo");
		assertTag(sync.createTagsForSwarm(n),"tridentClusterId","abc",true);
		assertTag(sync.createTagsForSwarm(n),"tridentOwnerId","foo",true);
		
	}
	
	private void assertTag(List<Tag> list, String name, String val, boolean b) {
		Optional<Tag> tag = findTag(list,name);
		if (!tag.isPresent()) {
			Assertions.fail("expected tag name: "+name);
		}
		Assertions.assertThat(tag.get().getKey()).isEqualTo(name);
		Assertions.assertThat(tag.get().getValue()).isEqualTo(val);
		Assertions.assertThat(tag.get().isPropagateAtLaunch()).isEqualTo(b);
	}
	private Optional<Tag> findTag(List<Tag> list, String name) {
		return list.stream().filter(p->p.getKey().equals(name)).findFirst();
	}
}
