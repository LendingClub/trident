package org.lendingclub.trident.swarm.aws.task;

import java.util.concurrent.atomic.AtomicBoolean;

import org.lendingclub.trident.config.ConfigManager;
import org.lendingclub.trident.scheduler.DistributedTask;
import org.lendingclub.trident.swarm.aws.AWSClusterManager;
import org.lendingclub.trident.swarm.aws.AWSMetadataSync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

/**
 * This task attempts to reduce redundant AWS scanning.
 * 
 * @author rschoening
 *
 */
public class AWSScannerTask extends DistributedTask {

	Logger logger = LoggerFactory.getLogger(AWSScannerTask.class);

	public static final String AWS_SCANNER_ENABLED="awsScannerEnabled";
	
	private static AtomicBoolean disabledWarningIssued = new AtomicBoolean(false);
	public boolean isEnabled() {
		
		
		boolean b = getApplicationContext().getBean(ConfigManager.class).getConfig("trident", "default").orElse(MissingNode.getInstance()).path(AWS_SCANNER_ENABLED).asBoolean(true);
		
		// Issue a warning, but only once
		if (b==false && disabledWarningIssued.getAndSet(true)==false) {		
			logger.warn("Config type=trident name=default {}=false",AWSScannerTask.AWS_SCANNER_ENABLED);
		}
		if (b) {
			disabledWarningIssued.set(false);
		}
		return b;
	}
	@Override
	public void run() {

		JsonNode n = getApplicationContext().getBean(ConfigManager.class).getConfig("trident", "default").orElse(MissingNode.getInstance());
		if (!isEnabled()) {
			return;
		}
			
			safeExecute(() -> {
				// Make sure that all swarm metadata is tagged on ASGs
				AWSMetadataSync sync = getApplicationContext().getBean(AWSMetadataSync.class);
				sync.writeTagsForAllSwarms();
				// note that we would need to rescan all the ASGs
			});
			
			safeExecute(() -> {
				// rescan everything.  Note that we do this after the sync above so that we end up with all tags in neo4j
				getApplicationContext().getBean(AWSClusterManager.class).scanAll();
			});
			
			safeExecute(() -> {
				// Now backfill and DockerSwarm nodes that might be missing
				AWSMetadataSync sync = getApplicationContext().getBean(AWSMetadataSync.class);
				sync.createMissingDockerSwarmNodes();
			});
			
		

	}

	private void safeExecute(Runnable r) {
		try {
			r.run();
		} catch (Exception e) {
			logger.warn("problem executing", e);
		}
	}
}
