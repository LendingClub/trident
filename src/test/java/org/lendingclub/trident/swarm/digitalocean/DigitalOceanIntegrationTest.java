package org.lendingclub.trident.swarm.digitalocean;

import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

import com.amazonaws.services.lightsail.AmazonLightsail;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;
import com.myjeeva.digitalocean.DigitalOcean;
import com.myjeeva.digitalocean.impl.DigitalOceanClient;
import com.myjeeva.digitalocean.pojo.Droplet;
import com.myjeeva.digitalocean.pojo.Image;
import com.myjeeva.digitalocean.pojo.Key;
import com.myjeeva.digitalocean.pojo.Region;

public class DigitalOceanIntegrationTest extends TridentIntegrationTest {

	@Autowired
	DigitalOceanClusterManager digitalOceanClusterManager;
	
	@Test
	public void testSpring() {
		Assertions.assertThat(digitalOceanClusterManager).isNotNull();
	}
	@Test
	@Ignore
	public void testIt() throws Exception {
		
		DigitalOcean c = digitalOceanClusterManager.getClient("test");
		

		
		c.getAvailableKeys(0).getKeys().forEach(it->{
			System.out.println(it.getId());
			System.out.println(it.getName());
			System.out.println(it);
		});
		
		c.getAvailableImages(0, 1000).getImages().forEach(it->{
			System.out.println(it);
		});
		
		/*
		
		Droplet d = new Droplet();
		d.setImage(new Image(28108256));
		d.setName("test6");
		d.setRegion(new Region("sfo1"));
		d.setSize("16gb");
	
		d.setEnablePrivateNetworking(true);
	
		
		d.setUserData("#!/bin/bash\necho 'foo' >/tmp/foo.txt");
		c.createDroplet(d);
		*/
	
	
		
	}
}
