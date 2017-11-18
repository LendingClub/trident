package org.lendingclub.trident.haproxy;

import com.google.common.base.Strings;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.lendingclub.trident.git.GitRepoManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

public class HAProxyGitResourceProvider extends HAProxyResourceProvider {

	@Autowired
	GitRepoManager gitRepoManager;

	Logger logger = LoggerFactory.getLogger(HAProxyGitResourceProvider.class);

	public String safelyGetResourceFromGit(String env, String subEnv, String serviceGroup, String resourceName, String region) {
		try {

			logger.info("trying to get config template from git for "
							+ "resourceName: {}, env: {}, subEnv: {}, serviceGroup: {}", resourceName, env,
					subEnv, serviceGroup);

			String config = gitRepoManager
					.getGitRepo(resourceName)
					.getString(env+"/"+subEnv,
							serviceGroup+"/haproxy.cfg.gsp");

			return config;
		}
		catch (Exception e) {

			logger.info("unable to fetch config template from git", e);

		}

		return "";
	}

	public String execute(ResourceRequest resourceRequest) {

		String userRequestedConfig =  safelyGetResourceFromGit(resourceRequest.env, resourceRequest.subEnv, resourceRequest.serviceGroup,
				resourceRequest.resourceName, resourceRequest.region);

		// fall back to subenvironment default if config for the user requested env cannot be found
		if(Strings.isNullOrEmpty(userRequestedConfig)) {
			userRequestedConfig = safelyGetResourceFromGit(resourceRequest.env, "default", resourceRequest.serviceGroup,
					resourceRequest.resourceName, resourceRequest.region);
		}

		return userRequestedConfig;

	}
}
