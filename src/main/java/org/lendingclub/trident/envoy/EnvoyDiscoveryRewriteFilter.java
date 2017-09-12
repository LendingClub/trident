package org.lendingclub.trident.envoy;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Envoy doesn't currently support path mapping of discovery URLs.  They are hard-coded to /v1/*.
 * This filter rewrites the incoming requests to a path that is consistent with Trident.
 * 
 * We can either patch Envoy to allow it to be configured, or perhaps, loop the requests through Envoy
 * itself to the path translation.
 * 
 * Note that this filter is registered ahead of the spring security filters so that it receives the request
 * without authn/authz policy being applied.  It is then applied once the path is translated.  This 
 * minimizes the special-casing necessary to make everything consistent.
 * 
 * @author rschoening
 *
 */
public class EnvoyDiscoveryRewriteFilter implements Filter {

	static String [] PATTERNS= new String[] { "/v1/registration/*","/v1/clusters/*","/v1/listeners/*","/v1/routes/*" };
	
	Logger logger = LoggerFactory.getLogger(EnvoyDiscoveryRewriteFilter.class);
	@Override
	public void destroy() {

	}

	public String[] getUrlPatterns() {
		return PATTERNS;
	}

	protected static boolean isEnvoyDiscoveryPath(String uri) {
		if (uri==null) {
			return false;
		}
		if (uri.startsWith("/v1/")) {
			for (String x: PATTERNS) {
				if (uri.startsWith(x.replace("*", ""))) {
					return true;
				}
			}
		}
		return false;
	}
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest httpServletRequest = (HttpServletRequest) request;
		String requestURI = httpServletRequest.getRequestURI();
		
		
		if (isEnvoyDiscoveryPath(requestURI)) {
			String modifiedPath = "/api/trident/envoy" + requestURI;
			logger.info("translated path: "+requestURI+ " ==> "+modifiedPath);
			httpServletRequest.getRequestDispatcher(modifiedPath).forward(request, response);
			return;
		}

		chain.doFilter(request, response);

	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {

	}

}
