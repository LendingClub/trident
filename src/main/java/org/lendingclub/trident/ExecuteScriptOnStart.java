package org.lendingclub.trident;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a convenience hook that allows some custom behavior to be implemented
 * as part of this project. It may not be obvious why this is here from an Open
 * Source perspective, but it is very useful to us.
 * 
 * @author rschoening
 *
 */
public class ExecuteScriptOnStart {

	static Logger logger = LoggerFactory.getLogger(ExecuteScriptOnStart.class);

	public void execute() {
		try {
			File script = new File("./bin/trident-on-start");
			if (script.exists()) {
				logger.info("executing {}",script.getCanonicalPath());
				Process p = Runtime.getRuntime().exec(script.getAbsolutePath());
				p.waitFor(30, TimeUnit.SECONDS);
				logger.info("exit code={}", p.exitValue());
				
			}
			else {
				logger.info("not found: {}",script.getCanonicalPath());
			}
		} catch (Exception e) {
			logger.warn("problem", e);
		}
	}
}
