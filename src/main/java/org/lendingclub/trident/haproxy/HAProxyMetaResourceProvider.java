package org.lendingclub.trident.haproxy;

import com.google.common.base.Strings;
import org.lendingclub.trident.Trident;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;


public class HAProxyMetaResourceProvider extends HAProxyResourceProvider{

	Logger logger = LoggerFactory.getLogger(HAProxyMetaResourceProvider.class);

	@Override String execute(ResourceRequest resourceRequest) {

		String configFromGit = "";

		try {
			HAProxyGitResourceProvider haProxyGitResourceProvider =
					Trident.getApplicationContext().getBean(HAProxyGitResourceProvider.class);
			configFromGit = haProxyGitResourceProvider.execute(resourceRequest);
		}
		catch (Exception e) {
			logger.info("problem getting config from git ", e);
		}

		String configFromFileSystem = "";

		try {
			HAProxyFileSystemResourceProvider haProxyFileSystemResourceProvider =
					Trident.getApplicationContext().getBean(HAProxyFileSystemResourceProvider.class);
			configFromFileSystem = haProxyFileSystemResourceProvider.execute(resourceRequest);
		}
		catch (Exception e) {
			logger.info("problem getting config from filesystem ", e);
		}

		if(! Strings.isNullOrEmpty(configFromGit)) return configFromGit;

		if(! Strings.isNullOrEmpty(configFromFileSystem)) return configFromFileSystem;

		return "";
	}
}
