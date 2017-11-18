package org.lendingclub.trident.util;

import javax.annotation.PostConstruct;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.Trident;
import org.lendingclub.trident.cluster.TridentClusterManager;
import org.lendingclub.trident.config.ConfigManager;
import org.lendingclub.trident.swarm.SwarmEventManager;
import org.lendingclub.trident.swarm.aws.task.AWSScannerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.google.common.base.Strings;

public class TridentStartupConfigurator implements TridentStartupListener {

	Logger logger = LoggerFactory.getLogger(TridentStartupConfigurator.class);
	@Autowired
	NeoRxClient neo4j;
	
	@Autowired
	TridentClusterManager tridentClusterManager;
	
	@Autowired
	ConfigManager configManager;
	
	@PostConstruct
	public void onStart()  {
		setupConfig();
	}
	
	
	private void setupConfig() {
		if (neo4j.checkConnection()==false) {
			return;
		}
		// temporary update to move trom name=installation to name=default
		JsonNode n = neo4j.execCypher("match (c:Config {type:'trident',name:'installation'}) return c").blockingFirst(NullNode.getInstance());
		if (!Strings.isNullOrEmpty(n.path("installationId").asText())) {
			configManager.setValueIfNotSet("trident", "default", "installationId", n.path("installationId").asText(), false);
		}
		neo4j.execCypher("match (c:Config {type:'trident',name:'installation'}) delete c");
		
		// This actually sets up Config if it isn't set up
		tridentClusterManager.getTridentInstallationId();
		
		configManager.setValueIfNotSet("trident", "default", SwarmEventManager.SWARM_EVENT_STREAM_ENABLED, Boolean.TRUE.toString(), false);
		configManager.setValueIfNotSet("trident", "default", AWSScannerTask.AWS_SCANNER_ENABLED, Boolean.TRUE.toString(), false);
		
		
		configManager.reload();
	}


	@Override
	public void onStart(ApplicationContext context) {
		Trident.Version v = Trident.getInstance().getVersion();

		String banner = "\n" + 
				" _____        _      _               _   \n" + 
				"/__   \\ _ __ (_)  __| |  ___  _ __  | |_ \n" + 
				"  / /\\/| '__|| | / _` | / _ \\| '_ \\ | __|\n" + 
				" / /   | |   | || (_| ||  __/| | | || |_ \n" + 
				" \\/    |_|   |_| \\__,_| \\___||_| |_| \\__|\n" + 
				"                                         \n" + 
				"revision: "+v.getShortRevision()+"  version: "+v.getVersion()+"\n";
		logger.info("trident ready\n"+banner);
	}
}
