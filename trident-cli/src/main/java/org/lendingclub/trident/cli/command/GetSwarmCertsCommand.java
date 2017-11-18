package org.lendingclub.trident.cli.command;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import org.lendingclub.okrest3.OkRestTarget;
import org.lendingclub.trident.cli.CLIFatalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;

import net.lingala.zip4j.exception.ZipException;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;

/**
 * lc2 Command to communicate with trident to obtain certs for docker swarms
 * Created by shpatel on 7/25/17.
 */

@Parameters(commandDescription = "Obtain cert bundle for given docker swarm")
public class GetSwarmCertsCommand extends Command {

	@Parameter(names = { "-s" }, description = "name of docker swarm")
	String swarmName;

	Logger logger = LoggerFactory.getLogger(GetSwarmCertsCommand.class);
	ObjectMapper mapper = new ObjectMapper();

	public void getAndWriteCertsToHomeFolder(String token) throws IOException {

		if (swarmName == null) {
			throw new CLIFatalException("Please enter \"-s <swarm-name>\" after command");
		}

		OkRestTarget target = getTridentRestTarget().path("/api/swarm-clusters").path(swarmName)
				.path("/download-client-certs/");

	
		// note spelling error in token...
		org.lendingclub.okrest3.OkRestResponse response = target.addHeader("x-macgyver-token",token).addHeader("x-magyver-token", token).get().execute();
	
		if (response.response().code() == 401) {
			throw new CLIFatalException("Unable to authorize (Status code 401).  Maybe you want to try 'lc2 login'?");
		}

		else if (response.response().isSuccessful()) {

			File targetDir = new File(System.getProperty("user.home"), ".docker/" + swarmName);
			targetDir.mkdirs();

			try (InputStream is = response.response().body().byteStream()) {

				File certZip = new File(targetDir, "cert.zip");
				try (FileOutputStream fos = new FileOutputStream(certZip)) {
					ByteStreams.copy(is, fos);
					net.lingala.zip4j.core.ZipFile zf = new net.lingala.zip4j.core.ZipFile(certZip);
					zf.extractAll(targetDir.getPath());
				} catch (ZipException ioe) {
					throw new CLIFatalException("Cannot write certs file.");
				}
			}

			stdout("Files unzipped and placed into {}", targetDir);
			String command = "cd " + targetDir.getPath() + " && source env.sh";
			stdout("Run the following command to change into swarm directory and set environment variables to use the docker certs:");
			stdout(command);
			stdout("After you can run \"docker node ls\" to test your connection to the swarm");
		}

		else {
			throw new CLIFatalException("Unable to make request. Status code " + response.response().code());
		}
	}

	@Override
	public void doRun() {

		try {
			if (!getConfigManager().getProperty("macgyver.token").isPresent()) {
				throw new CLIFatalException("Not Authenticated with MacGyver. Please use 'lc2 login' first.");
			}

			String securityToken = getConfigManager().getProperty("macgyver.token").get();
			getAndWriteCertsToHomeFolder(securityToken);
		} catch (IOException e) {
			throw new CLIFatalException(e);
		}
	}
}
