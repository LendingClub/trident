package org.lendingclub.haproxy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;

import com.google.common.base.Strings;
import io.macgyver.okrest3.OkRestClient;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = {"org.lendingclub.haproxy"})
public class MonitorDaemon {

	Supervisor supervisor = null;

	@Autowired ConfigCompiler configCompiler;

	@Autowired ApplicationContext applicationContext;

	public static OkRestClient okRestClient = new OkRestClient.Builder().build();

	@Value("${configDir:{null}}") String configDir;

	Logger logger = LoggerFactory.getLogger(MonitorDaemon.class);

	public static String getTsdNode() {
		String tsdNode = System.getenv("TSD_NODE");

		if (tsdNode == null || tsdNode.equals("")) {
			try {
				tsdNode = InetAddress.getLocalHost().getHostName();
			}
			catch (UnknownHostException ue) {
				tsdNode = "";
			}
		}
		return tsdNode;
	}

	@PostConstruct public void startMonitor() throws IOException, GitAPIException, ZipException {

		supervisor = new BasicSupervisor(this, configDir);

		startConfigCompileAndRestartLoop(supervisor, configCompiler);
	}

	public void startConfigCompileAndRestartLoop(Supervisor supervisor, ConfigCompiler configCompiler) {

		boolean fetchConfigRemotely = Files.notExists(Paths.get(ConfigCompiler.HAPROXY_CONFIG_TEMPLATE_PATH));

		Runnable configCompileAndRestart = new Runnable() {
			@Override public void run() {
				try {
					pullBootstrapConfigTemplate(fetchConfigRemotely);
					pullCertBundle();
				}
				catch (Exception e) {
					logger.info("error pulling remote config or cert", e);
				}
				configCompiler.run();
				supervisor.run();
			}
		};

		Executors.newSingleThreadScheduledExecutor()
				.scheduleAtFixedRate(configCompileAndRestart, 0, 10, TimeUnit.SECONDS);
	}

	public ApplicationContext getApplicationContext() {
		return applicationContext;
	}

	public void pullBootstrapConfigTemplate(boolean shouldFetchRemotely) throws IOException, ZipException {

		String tsdBaseUrl = System.getenv("TSD_BASE_URL");
		String tsdCluster = System.getenv("TSD_CLUSTER");
		String tsdEnv = System.getenv("TSD_ENV");
		String tsdSubenv = System.getenv("TSD_SUBENV");
		String tsdNode = getTsdNode();
		String tsdRegion = System.getenv("TSD_REGION");

		if(Strings.isNullOrEmpty(tsdRegion)) {
			tsdRegion = "local";
		}

		if (shouldFetchRemotely) {

			String configFetchURL =
					tsdBaseUrl + "/api/trident/haproxy/v1/config-bundle";

			logger.info("fetching from {}",
					ConfigCompiler.HAPROXY_CONFIG_TEMPLATE_PATH, configFetchURL);


			OkHttpClient client = new OkHttpClient();

			HttpUrl.Builder httpBuilder = HttpUrl.parse(configFetchURL).newBuilder();

			HttpUrl url = httpBuilder
					.addQueryParameter("serviceCluster", tsdCluster)
					.addQueryParameter("serviceNode", tsdNode)
					.addQueryParameter("environment", tsdEnv)
					.addQueryParameter("subEnvironment", tsdSubenv)
					.addQueryParameter("region", tsdRegion).build();

			Request request = new Request.Builder().url(url)
					.build();



			Response response = client.newCall(request).execute();

			if (!response.isSuccessful()) {
				throw new IOException("UNABLE TO DOWNLOAD BOOTSTRAP CONFIG TEMPLATE FILE " + response);
			}

			File configBundleFile = File.createTempFile("bootstrap", "config-bundle.zip");

			FileOutputStream fos = new FileOutputStream(configBundleFile);

			fos.write(response.body().bytes());

			fos.close();

			ZipFile zipFile = new ZipFile(configBundleFile.getAbsolutePath());

			zipFile.extractFile("config/haproxy.cfg.gsp", ConfigCompiler.HAPROXY_CONFIG_DIR);

		}
	}

	public void pullCertBundle() throws IOException, ZipException {

		String tsdBaseUrl = System.getenv("TSD_BASE_URL");
		String tsdCluster = System.getenv("TSD_CLUSTER");
		String tsdEnv = System.getenv("TSD_ENV");
		String tsdSubenv = System.getenv("TSD_SUBENV");
		String tsdNode = getTsdNode();

		String tsdRegion = System.getenv("TSD_REGION");

		if(Strings.isNullOrEmpty(tsdRegion)) {
			tsdRegion = "local";
		}

		String certFetchURL =
				tsdBaseUrl + "/api/trident/haproxy/v1/cert";

		HttpUrl.Builder httpBuilder = HttpUrl.parse(certFetchURL).newBuilder();

		HttpUrl fullUrl = httpBuilder
				.addQueryParameter("serviceCluster", tsdCluster)
				.addQueryParameter("serviceNode", tsdNode)
				.addQueryParameter("environment", tsdEnv)
				.addQueryParameter("subEnvironment", tsdSubenv)
				.addQueryParameter("region", tsdRegion).build();

		if (Files.notExists(Paths.get(ConfigCompiler.HAPROXY_CERT_PATH))) {

			logger.info("COULD NOT FIND ONBOARD CERT AT {} so fetching from {}",
					ConfigCompiler.HAPROXY_CERT_PATH, certFetchURL);

			OkHttpClient client = new OkHttpClient();
			Request request = new Request.Builder().url(fullUrl).build();

			Response response = client.newCall(request).execute();

			if (!response.isSuccessful()) {
				throw new IOException("UNABLE TO DOWNLOAD BOOTSTRAP CERT FROM FILE " + response);
			}

			File configBundleFile = File.createTempFile("bootstrap", "cert-bundle.zip");

			FileOutputStream fos = new FileOutputStream(configBundleFile);

			fos.write(response.body().bytes());

			fos.close();

			ZipFile zipFile = new ZipFile(configBundleFile.getAbsolutePath());

			zipFile.extractFile("config/cert.pem", ConfigCompiler.HAPROXY_CONFIG_DIR);

		}

	}
}
