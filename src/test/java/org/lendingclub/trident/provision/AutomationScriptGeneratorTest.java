package org.lendingclub.trident.provision;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.provision.SwarmNodeProvisionContext;

import com.google.common.io.BaseEncoding;

public class AutomationScriptGeneratorTest extends TridentIntegrationTest {

	@Test
	public void testIt() {
		String scriptOutput = new SwarmNodeProvisionContext().withScriptTemplateName("docker-install").generateScript();

		Assertions.assertThat(scriptOutput).contains("export DOCKER_BRIDGE_IP='192.168.127.1/24'");
		Assertions.assertThat(scriptOutput).contains("export DOCKER_GWBRIDGE_SUBNET='192.168.128.0/24'");
		Assertions.assertThat(scriptOutput).contains("\"bip\":\"${DOCKER_BRIDGE_IP}\"");
		System.out.println(scriptOutput);
	}

	class SwarmInitInfo {
		String token;
		String address;
	}
	@Test
	public void testIt2() {

		String encoded = "U3dhcm0gaW5pdGlhbGl6ZWQ6IGN1cnJlbnQgbm9kZSAocmZnYmV1ZnJ0aGptNWNlam0xcGVwYWppcykgaXM"
				+ "gbm93IGEgbWFuYWdlci4KClRvIGFkZCBhIHdvcmtlciB0byB0aGlzIHN3YXJtLCBydW4gdGhlIGZvbGxvd2luZyBjb21tYW5kOgo"
				+ "KICAgIGRvY2tlciBzd2FybSBqb2luIFwKICAgIC0tdG9rZW4gU1dNVEtOLTEtNWR4MmZ3bXlwYjR1cG0ybW1hY3N1MzRuMWRyeWE"
				+ "1bXlvODM1d21tMXY2eXR2NzIydzUtNnh2djFxMGx1em9lMTVzZThjeGVlNDRydSBcCiAgICAxMC44MS40MC4yMzk6MjM3NwoKVG8"
				+ "gYWRkIGEgbWFuYWdlciB0byB0aGlzIHN3YXJtLCBydW4gJ2RvY2tlciBzd2FybSBqb2luLXRva2VuIG1hbmFnZXInIGFuZCBmb2x"
				+ "sb3cgdGhlIGluc3RydWN0aW9ucy4K";

		
		String s = "Swarm initialized: current node (rfgbeufrthjm5cejm1pepajis) is now a manager.\n" + "\n"
				+ "To add a worker to this swarm, run the following command:\n" + "\n" + "    docker swarm join \\\n"
				+ "    --token SWMTKN-1-5dx2fwmypb4upm2mmacsu34n1drya5myo835wmm1v6ytv722w5-6xvv1q0luzoe15se8cxee44ru \\\n"
				+ "    10.81.40.239:2377\n" + "\n"
				+ "To add a manager to this swarm, run 'docker swarm join-token manager' and follow the instructions.\n"
				+ "";

		String plain = new String(BaseEncoding.base64().decode(encoded.trim()));

		Pattern p = Pattern.compile(".*(SWMTKN\\S+).*?(\\d+\\.\\d+\\.\\d+:\\d+).*",Pattern.DOTALL | Pattern.MULTILINE);
		Matcher m = p.matcher(s);
		if (m.matches()) {
			System.out.println(m.group(1));
			System.out.println(m.group(2));
		}


	}
	
	
	
}
