package org.lendingclub.haproxy;

import static com.google.common.collect.Lists.newArrayList;

import java.io.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.io.CharStreams;

public class BasicSupervisor extends Supervisor {

	public BasicSupervisor(MonitorDaemon m, String configDir) {
		super(m);
		super.configDir = configDir;
		cfgHash.set(computeConfigHash());
		// TODO Auto-generated constructor stub
	}

	AtomicReference<Optional<Integer>> pidRef = new AtomicReference(
			Optional.empty());

	AtomicReference<HashCode> cfgHash = new AtomicReference<HashCode>();

	@Override
	public int getPid() {
		return pidRef.get().orElse(-1);
	}

	@Override
	public void scan() {
		try {
			startHAProxy();
		}
		catch (Exception e) {
			logger.info("cannot start HAProxy ", e);
		}
	}

	public boolean checkHAProxyConfig() throws InterruptedException,
			IOException {
		return execute(newArrayList(getHAProxyExecutable().getAbsolutePath(),
				"-f", getHAProxyConfigFile().getCanonicalPath(), "-c")) == 0;
	}

	public void makeAppLocalHaproxyExecutable() {
		logger.info("making app local haproxy executable by running: {}",
				"chmod a+x " + getHAProxyExecutable().getAbsolutePath());
		try {
			execute( newArrayList( "chmod a+x ", getHAProxyExecutable().getAbsolutePath() ));
		}
		catch (Exception e) {
			logger.info("could not make app local haproxy executable ", e);
		}
	}

	public boolean isConfigInvalid() throws InterruptedException, IOException {
		return execute(newArrayList("haproxy",
				"-f", ConfigCompiler.HAPROXY_CONFIG_PATH, "-c")) != 0;
	}

	public void setPid() {

		try {
			List<String> lines = Lists.newArrayList();
			Process p = new ProcessBuilder().redirectErrorStream(true)
					.command("ps","-ef").start();
			try (InputStreamReader r = new InputStreamReader(p.getInputStream())) {
				lines = CharStreams.readLines(r);

			}

			int rc = p.waitFor();

			Function<String, Stream<Integer>> x = new Function<String, Stream<Integer>>() {

				@Override
				public Stream<Integer> apply(String t) {
					String val = Splitter.on(" ").omitEmptyStrings().splitToList(t)
							.get(0);

					return Stream.of(Integer.parseInt(val));
				}
			};

			Optional<Integer> xx = lines
					.stream()
					.filter(s -> s.contains("haproxy") && s.contains("haproxy.pid"))
					.flatMap(x).findFirst();

			pidRef.set(xx);
		} catch (Exception e) {
			logger.info("could not set pidref", e);
		}
	}

	public void startHAProxy() throws InterruptedException, IOException {

		if(computeConfigHash().toString().equals(cfgHash.get().toString()) && getPid() != -1) {
			logger.info("computed config hash {}, last config hash {}", computeConfigHash().toString(), cfgHash.get().toString());
			logger.info("####### config has not changed, so not [re]starting #############");
			return;
		}

		if(isConfigInvalid()) {
			logger.info("####### not [re]starting because compiled config is invalid ########");
			return;
		}

		List<String> haproxyStartCommand = Lists.newArrayList();
		haproxyStartCommand.add("./trident-haproxy/zero-downtime-restart.sh");
		haproxyStartCommand.add("/trident-haproxy/config/haproxy.cfg");
		logger.info("haproxy command {}", Joiner.on(" ").join(haproxyStartCommand));
		cfgHash.set(computeConfigHash());

		execute(haproxyStartCommand);
		setPid();
	}

	int execute(List<String> args) throws IOException, InterruptedException {
		System.out.println("Starting haproxy");
		Process p = new ProcessBuilder().redirectErrorStream(true)
				.command(args).start();

		logger.info("executing: {}", Joiner.on(" ").join(args));
		String output = readToString(p.getInputStream());
		logger.warn("output: {}", output);
		int rc = p.waitFor();
		logger.info("rc: {}", rc);
		return rc;
	}

	String readToString(InputStream is) throws IOException {
		try (InputStreamReader r = new InputStreamReader(is)) {
			return CharStreams.toString(r);
		}

	}
}
