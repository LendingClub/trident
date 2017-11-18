package org.lendingclub.trident.git;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.OpenSshConfig.Host;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.config.ConfigManager;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.MoreExecutors;
import com.jcraft.jsch.Identity;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class GitRepoManager {
	Map<String, GitRepo> repos = Maps.newConcurrentMap();

	@Autowired
	ConfigManager configManager;
	
	Logger logger = LoggerFactory.getLogger(GitRepoManager.class);

	ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

	int timeoutSecs = 30;
	public synchronized GitRepo register(String name, JsonNode cfg) {

		if (repos.containsKey(name)) {
			throw new IllegalStateException("already registered: " + name);
		}
		GitRepo w = new GitRepo();
		w.config = cfg;
		Runnable r = new Runnable() {

			@Override
			public void run() {
				try {
					w.getGit();
					w.fetch();

				} catch (Exception e) {
					logger.warn("problem", e);
					w.forceClone();
				}

			}
		};
		repos.put(name,w);
		int refreshIntervalSecs = Math.max(10,cfg.path("refreshInterval").asInt(60));
		executor.scheduleWithFixedDelay(r,refreshIntervalSecs,refreshIntervalSecs , TimeUnit.SECONDS);
		return w;

	}

	public synchronized GitRepo getGitRepo(String name) {
		
		GitRepo repo = repos.get(name);
		if (repo!=null) {
			return repo;
		}
		
		Optional<JsonNode> cfg = configManager.getConfig("git", name);
		if (cfg.isPresent()) {
			return register(name, cfg.get());
		}
		
		throw new TridentException("git repo not found: "+name);
		
	}
	public class GitRepo {
		JsonNode config;
		Git git;
		SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {

			@Override
			protected JSch createDefaultJSch(FS fs) throws JSchException {
				try {
					JSch jsch = super.createDefaultJSch(fs);

					String privateKeyPassword = config.path("privateKeyPassword").asText();
					String privateKey = config.path("privateKey").asText();

					if (!Strings.isNullOrEmpty(privateKey)) {
						if (privateKey.contains("PRIVATE")) {
							logger.info("using inline key");
							Path path = Files.createTempFile("tmp", "tmp");
							com.google.common.io.Files.write(privateKey, path.toFile(), Charsets.UTF_8);
							jsch.removeAllIdentity();
							jsch.addIdentity(path.toFile().getAbsolutePath(), Strings.emptyToNull(privateKeyPassword));
						} else {

							jsch.addIdentity(privateKey, Strings.emptyToNull(privateKeyPassword));
						}

					}

					return jsch;
				} catch (IOException e) {
					throw new JSchException("problem initializing ssh", e);
				}
			}

			@Override
			protected void configure(Host hc, Session session) {
				java.util.Properties config = new java.util.Properties();
				config.put("StrictHostKeyChecking", "no");
				session.setConfig(config);

			}
		};

		TransportConfigCallback newTransportConfigCallback() {
			TransportConfigCallback callback = new TransportConfigCallback() {

				@Override
				public void configure(Transport transport) {
					SshTransport sshTransport = (SshTransport) transport;
					sshTransport.setSshSessionFactory(sshSessionFactory);
					sshTransport.setTimeout(timeoutSecs);
				}
			};
			return callback;
		}

		public void fetch() throws IOException, GitAPIException {
			Git git = getGit();
			Stopwatch sw = Stopwatch.createStarted();
			git.fetch().setTimeout(timeoutSecs).setTransportConfigCallback(newTransportConfigCallback()).call();
			logger.info("fetch took {} ms", sw.elapsed(TimeUnit.MILLISECONDS));
		}

		public String getString(String branch, String path) throws IOException, GitAPIException {
			return CharStreams.toString(new InputStreamReader(getInputStream(branch, path)));
		}

		public InputStream getInputStream(String branch, String path) throws IOException, GitAPIException {
			Stopwatch sw = Stopwatch.createStarted();

			Repository repo = getGit().getRepository();
	
			ObjectReader reader = repo.newObjectReader();
		
			ObjectId id = repo.resolve(branch);
			RevWalk rw = new RevWalk(repo);
			RevCommit rc = rw.parseCommit(id);
			RevTree tree = rc.getTree();

			TreeWalk tw = TreeWalk.forPath(repo, path, rw.parseCommit(id).getTree());

			if (tw != null) {

				byte[] data = reader.open(tw.getObjectId(0)).getBytes();
				if (sw.elapsed(TimeUnit.MILLISECONDS)>100) {
					logger.info("get contents took {} ms", sw.elapsed(TimeUnit.MILLISECONDS));
				}
				return new ByteArrayInputStream(data);
			} else {
				throw new FileNotFoundException("could not load " + path);
			}
		}

		public synchronized void forceClone() {
			if (git != null) {
				Git x = git;
				git = null;
				try {
					x.close();
					getGit();
				} catch (Exception e) {
					logger.info("problem closing", e);
				}
			}
		}

		public synchronized Git getGit() throws IOException, GitAPIException {
			if (git == null) {

				Stopwatch sw = Stopwatch.createStarted();
				Path path = Files.createTempDirectory("trident");
				String url = config.path("url").asText();
				if (Strings.isNullOrEmpty(url)) {
					throw new IOException("git url not set");
				}
				logger.info("cloning {} to {}", url, path);

				git = Git.cloneRepository().setDirectory(path.toFile())
						.setTransportConfigCallback(newTransportConfigCallback()).setBare(true)
						.setCloneAllBranches(true).setTimeout(timeoutSecs).setURI(url).call();
				logger.info("clone took {} ms", sw.elapsed(TimeUnit.MILLISECONDS));

			}
			return git;
		}

	}

	
}
