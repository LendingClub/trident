package org.lendingclub.trident.auth;

import java.util.Set;

public interface User {

	String getUsername();
	Set<String> getRoles();
}
