package org.lendingclub.trident.auth;

import org.lendingclub.trident.Trident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;


/**
 * AuthorizationUtil is a convenience class for authorizing actions for the current user.  Callers that need more granular control
 * should use AuthorizationManager.
 * 
 * @author rschoening
 *
 */
public class AuthorizationUtil {

	static AuthorizationManager authManager=null;
	public static synchronized AuthorizationManager getAuthorizationManager() {
		if (authManager!=null) {
			return authManager;
		}
		authManager = Trident.getApplicationContext().getBean(AuthorizationManager.class);
		return authManager;
	}
	/**
	 * Throws AuthorizationException if the action is not authorized.
	 * @param action
	 * @param data
	 */
	public static final void checkAuthorization(String action, JsonNode data) {
		AuthorizationManager.throwExceptionOnAuthorizationFailure(getAuthorizationManager().authorizeCurrentUser(action, data));
	}
	/**
	 * Throws AuthorizationException if the action is not authorized.
	 * @param action
	 * @param data
	 */
	public static final void checkAuthorization(String action) {
		AuthorizationManager.throwExceptionOnAuthorizationFailure(getAuthorizationManager().authorizeCurrentUser(action));
	}
	public static final AuthorizationResult authorize(String action, JsonNode n) {
		return getAuthorizationManager().authorizeCurrentUser(action,n);
	}
	public static final AuthorizationResult authorize(String action) {
		return getAuthorizationManager().authorizeCurrentUser(action,MissingNode.getInstance());
	}
	public static final boolean isAuthorized(String action) {
		return authorize(action).isAuthorized();
	}
	public static final boolean isAuthorized(String action, JsonNode n) {
		return authorize(action,n).isAuthorized();
	}
}
