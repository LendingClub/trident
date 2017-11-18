package org.lendingclub.trident.auth;

public interface AuthorizationResult {

	boolean isAuthorized();
	String getMessage();
}
