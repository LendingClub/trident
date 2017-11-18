package org.lendingclub.trident.swarm.aws;

import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.swarm.aws.AWSAccountManager.CredentialsSupplier;
import org.lendingclub.trident.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AWSAccountManagerImplTest extends TridentIntegrationTest {

	@Autowired
	AWSAccountManager clientManager;


	
	@Test
	public void testRegions() {
		ObjectNode n = JsonUtil.createObjectNode();
		CredentialsSupplier cs = new AWSAccountManager().new DeclarativeCredentialsSupplier(n);
		
		Assertions.assertThat(cs.getRegions()).containsOnly(Regions.US_WEST_2);
		
		n.put("regions", "us-east-2, us-east-1");
		cs = new AWSAccountManager().new DeclarativeCredentialsSupplier(n);	
		Assertions.assertThat(cs.getRegions()).containsOnly(Regions.US_EAST_1,Regions.US_EAST_2);
		
		n.put("regions", "us-west-1, us-east-x");
		cs = new AWSAccountManager().new DeclarativeCredentialsSupplier(n);	
		Assertions.assertThat(cs.getRegions()).containsOnly(Regions.US_WEST_1);
		
		n.set("regions", JsonUtil.createArrayNode().add("us-west-1").add("us-east-2"));
		cs = new AWSAccountManager().new DeclarativeCredentialsSupplier(n);	
		Assertions.assertThat(cs.getRegions()).containsOnly(Regions.US_WEST_1,Regions.US_EAST_2);
	}
}
