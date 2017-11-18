package org.lendingclub.trident.util;

import org.apache.catalina.core.ApplicationContext;
import org.lendingclub.neorx.NeoRxClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class TridentSchemaManager extends org.lendingclub.mercator.core.SchemaManager
		implements TridentStartupListener {

	Logger logger = LoggerFactory.getLogger(TridentSchemaManager.class);

	public TridentSchemaManager(NeoRxClient client) {
		super(client);

	}

	@Override
	public void onStart(org.springframework.context.ApplicationContext context) {

		try {

			applyUniqueConstraint("TridentCA", "id");

			getNeoRxClient().execCypher("match (a:AwsAsg)-[p:PROVIDES]->(d:DockerSwarm) detach delete p"); // temporary
																											// to
																											// remove
																											// junk

		} catch (RuntimeException e) {
			logger.warn("", e);
		}

	}

}
