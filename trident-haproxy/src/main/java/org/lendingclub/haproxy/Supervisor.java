package org.lendingclub.haproxy;

import java.io.File;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import com.google.common.base.Preconditions;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

public abstract class Supervisor implements Runnable {

	Logger logger = LoggerFactory.getLogger(Supervisor.class);

	MonitorDaemon monitor;

	String configDir;

	public Supervisor(MonitorDaemon m) {
		Preconditions.checkNotNull(m);
		this.monitor = m;
	}

	abstract int getPid();

	public boolean isMac() {
		return System.getProperty("os.name", "").toLowerCase().contains("mac");
	}

	public boolean isLinux() {
		return System.getProperty("os.name", "").toLowerCase()
				.contains("linux");
	}

	public abstract void scan() throws IOException, InterruptedException;

	public HashCode computeConfigHash() {

		try {
			HashCode code = Files.asByteSource(new File( ConfigCompiler.HAPROXY_CONFIG_PATH) ).hash(
					Hashing.sha1());
			return code;
		} catch (IOException e) {
			logger.info("!!!!!!!!!!!! problem while computing config hash !!!!!!!!!!!!!!!!!!");
			return Hashing.sha1().hashInt(0);
		}
	}

	public MonitorDaemon getMonitor() {
		return monitor;
	}

	public ApplicationContext getApplicationContext() {
		return getMonitor().getApplicationContext();
	}

	public File getPIDFile() {
		return new File(getConfigDir(), "haproxy.pid");
	}

	public File getConfigDir() {
		File confDir = new File( configDir );
		return confDir;
	}

	public File getBaseDir() {
		return new File(System.getProperty("tc.app.dir", "."));
	}

	public File getHAProxyConfigFile() {
		File f = new File(getConfigDir(), "haproxy.cfg");

		return f;
	}

	public File getHAProxyExecutable() {
		return new File("haproxy");
	}

	@Override
	public void run() {
		try {
			scan();
		} catch (Exception e) {
			logger.warn("", e);
		}
	}
}
