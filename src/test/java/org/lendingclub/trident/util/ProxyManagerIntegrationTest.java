package org.lendingclub.trident.util;

import java.io.IOException;

import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.util.ProxyManager;
import org.springframework.beans.factory.annotation.Autowired;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class ProxyManagerIntegrationTest extends TridentIntegrationTest {

	@Autowired
	ProxyManager proxyManager;
	
	@Test
	public void testIt() throws IOException {
		
		
		OkHttpClient client = new OkHttpClient.Builder().proxySelector(proxyManager.getProxySelector()).build();
		
		client.newCall(new Request.Builder().url("http://www.google.com").build()).execute();
	}
}
