package org.lendingclub.haproxy;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class GitPuller implements Runnable {

	Logger logger = LoggerFactory.getLogger(GitPuller.class);


	String REPO_SSH_LINK;


	String PRIVATE_KEY_PATH;

	String PUBLIC_KEY_PATH;

	public String getRepoSSHCloneLink() {
		return REPO_SSH_LINK;
	}

	public String getPrivateKeyPath() {
		return PRIVATE_KEY_PATH;
	}

	public String getPublicKeyPath() {
		return PUBLIC_KEY_PATH;
	}

	CredentialsProvider allowHosts = new CredentialsProvider() {


		@Override
		public boolean supports(CredentialItem... items) {
			for(CredentialItem item : items) {
				if((item instanceof CredentialItem.YesNoType)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
			for(CredentialItem item : items) {
				if(item instanceof CredentialItem.YesNoType) {
					((CredentialItem.YesNoType)item).setValue(true);
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean isInteractive() {
			return false;
		}
	};

	public SshSessionFactory getScramjetSessionFactory() {
		SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {

			@Override
			protected void configure(OpenSshConfig.Host host,Session session) {
				session.setConfig("StrictHostKeyChecking", "no");
			}

			@Override
			protected JSch getJSch(final OpenSshConfig.Host hc, FS fs) throws JSchException{
				JSch jSch = super.getJSch(hc, fs);
				jSch.removeAllIdentity();

// 				jSch.addIdentity(ApplicationConfig
//						.getInstance()
//						.getConfDir()
//						.getAbsolutePath()+
//						"/.ssh/id_rsa");
				try {
					jSch.addIdentity( "haproxy-data-repo",
							fileToBytes(getPrivateKeyPath()),
							fileToBytes(getPublicKeyPath()),
							"".getBytes());
				}
				catch (IOException ioe) {
					throw new FatalBeanException("error reading private key file");
				}
				return jSch;
			}

			@Override
			protected JSch createDefaultJSch(FS fs) throws JSchException{
				JSch defaultJSch = super.createDefaultJSch(fs);

//				defaultJSch.addIdentity(ApplicationConfig
//						.getInstance()
//						.getConfDir()
//						.getAbsolutePath()+
//						"/.ssh/id_rsa",
//						ApplicationConfig
//								.getInstance()
//								.getConfDir()
//								.getAbsolutePath()+
//								"/.ssh/id_rsa.pub",
//						"".getBytes());
				defaultJSch.setKnownHosts(System.getProperty("user.dir")+"/.ssh/known_hosts");
				byte[] publicKeyBytes = new byte[0];
				byte[] privateKeyBytes = new byte[0];
				try {
					publicKeyBytes = fileToBytes(getPublicKeyPath()) ;
					privateKeyBytes = fileToBytes(getPrivateKeyPath());
				}
				catch (IOException ioe) {
					throw new FatalBeanException("error reading in public or private key bytes");
				}
				defaultJSch.addIdentity(
						"haproxy-data-repo",
						privateKeyBytes,
						publicKeyBytes,
						"".getBytes());

				return defaultJSch;
			}

		};
		return sshSessionFactory;
	}

	public byte[] fileToBytes(String pathToFile) throws IOException {
		byte[] fileAsBytes = null;
		try (FileInputStream fs = new FileInputStream(new File(pathToFile))) {
			fileAsBytes = IOUtils.toByteArray(fs);
		}
		return fileAsBytes;
	}

	public String getScramjetDataDirectoryAbsolutePath() {
//		return ApplicationConfig.getInstance().getAppDir().getAbsolutePath()+"/haproxy-data/.git";
		return System.getProperty("user.dir");
	}

	public void cleanUpPreviousGitPullerDataRepo() throws IOException {
		try {
			FileUtils.delete(new File( System.getProperty("user.dir")+"/remote-data" ), FileUtils.RECURSIVE);
		}
		catch (IOException ioe) {
			logger.info("cannot delete repo, probably because it was never cloned");
		}
	}

	@Autowired
	public GitPuller(
			@Value("${REPO_SSH_LINK}") String REPO_SSH_LINK,
			@Value("${PRIVATE_KEY_PATH}") String PRIVATE_KEY_PATH,
			@Value("${PUBLIC_KEY_PATH}") String PUBLIC_KEY_PATH ) throws GitAPIException, IOException {

		this.REPO_SSH_LINK = REPO_SSH_LINK;

		this.PRIVATE_KEY_PATH = PRIVATE_KEY_PATH;

		this.PUBLIC_KEY_PATH = PUBLIC_KEY_PATH;

		try {
			cleanUpPreviousGitPullerDataRepo();
			logger.info("trying to clone {}", getRepoSSHCloneLink());
			Git.cloneRepository()
					.setURI(getRepoSSHCloneLink())
					.setDirectory(new File(System.getProperty("user.dir")+"/remote-data"))
					.setTransportConfigCallback(new TransportConfigCallback() {
						@Override
						public void configure(Transport transport) {
							SshTransport sshTransport = (SshTransport) transport;
							sshTransport.setSshSessionFactory( getScramjetSessionFactory() );
						}})
					.setCredentialsProvider(allowHosts)
					.call();
		}
		catch (GitAPIException e) {
//			logger.error("cannot clone {}", SCRAMJET_DATA_REPO_CLONE_SSH, e);
			throw e;
		}

	}

	@Override
	public void run() {
		try {
			logger.info("cloning from git");
			FileRepositoryBuilder builder = new FileRepositoryBuilder();

			Repository repository = builder
					.setWorkTree(new File(System.getProperty("user.dir")+"/remote-data"))
					.setMustExist(true)
					.build();

			Git git = new Git(repository);

			git.pull()
					.setTransportConfigCallback(new TransportConfigCallback() {
						@Override
						public void configure(Transport transport) {
							SshTransport sshTransport = (SshTransport) transport;
							sshTransport.setSshSessionFactory( getScramjetSessionFactory() );
						}})
					.setCredentialsProvider(allowHosts).call();
		}
		catch (GitAPIException gae) {
			logger.error("cannot pull from data repo", gae);
		}
		catch (IOException ioe) {
			logger.error("cannot pull from remote data repo", ioe);
		}
	}

}