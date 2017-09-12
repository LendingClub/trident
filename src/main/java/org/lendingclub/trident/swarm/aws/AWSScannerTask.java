package org.lendingclub.trident.swarm.aws;

import org.lendingclub.trident.scheduler.DistributedTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This task attempts to reduce redundant AWS scanning.
 * 
 * @author rschoening
 *
 */
public class AWSScannerTask extends DistributedTask {

	Logger logger = LoggerFactory.getLogger(AWSScannerTask.class);

	@Override
	public void run() {


			
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
