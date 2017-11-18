package org.lendingclub.trident;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.annotation.PostConstruct;

import org.lendingclub.trident.util.TridentStartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import com.google.common.base.Preconditions;
import org.springframework.context.ApplicationListener;

public class Trident implements ApplicationListener<ApplicationReadyEvent> {

	static Trident instance = null;

	@Autowired
	private ApplicationContext applicationContext;

	Logger logger = LoggerFactory.getLogger(Trident.class);

	Version version = null;

	public class Version {
		Properties props = new Properties();

		public String getShortRevision() {
			return props.getProperty("shortRevision", "");
		}

		public String getRevision() {
			return props.getProperty("revision", "");
		}

		public String getVersion() {
			return props.getProperty("version", "");
		}

	}

	private static Trident init(Trident trident, ApplicationContext ctx) {

		trident.applicationContext = ctx;
		instance = trident;
		return instance;
	}

	public static Trident getInstance() {
		return instance;
	}

	@PostConstruct
	public void init() {
		init(this, applicationContext);
	}

	public static ApplicationContext getApplicationContext() {
		Preconditions.checkState(getInstance() != null);
		Preconditions.checkState(getInstance().applicationContext != null);
		return getInstance().applicationContext;
	}

	protected static boolean isExceptionSwallowable(RuntimeException e) {
		boolean foundNeo4j = false;
		boolean foundJunit = false;
		for (StackTraceElement ste : e.getStackTrace()) {
			if (ste.toString().toLowerCase().contains("neo4j")) {
				foundNeo4j = true;
			}
			if (ste.toString().toLowerCase().contains("junit")) {
				foundJunit = true;
			}
		}
		return foundJunit && foundNeo4j;
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {

		applicationReadyEvent.getApplicationContext().getBeansOfType(TridentStartupListener.class)
				.forEach((name, instance) -> {

					logger.info("invoking onStart() on TridentStartupListener instance {}", name);
					try {
						((TridentStartupListener) instance).onStart(applicationReadyEvent.getApplicationContext());
					} catch (RuntimeException e) {
						// TODO fail silently if the exception is due to neo4j
						// and this is called from a test.
						if (!isExceptionSwallowable(e))
							throw e;
					}
				});
	}

	public Version getVersion() {
		if (version == null) {
			try (InputStream is = getClass().getResourceAsStream("/trident-version.properties")) {
				try {
					Version v = new Version();
					v.props = new Properties();
					v.props.load(is);
			
				
					this.version = v;
					return this.version;
				} catch (IOException e) {
					logger.warn("could not load version",e);
					
				}
			}
			catch (IOException|RuntimeException e) {
				logger.warn("could not load version",e);
			}
			Version v = new Version();
			v.props = new Properties();
			this.version = v;
		}
		
		return version;
	}
}
