package org.lendingclub.trident.auth;

import java.util.Collection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class TridentRoles {

	public static final String TRIDENT_ADMIN = "TRIDENT_ADMIN";
	public static final String TRIDENT_USER= "TRIDENT_USER";
	
	public static final String TRIDENT_SWARM_ADMIN="TRIDENT_SWARM_ADMIN";

	public static final Collection<String> ADMIN_ROLES=ImmutableSet.of(TRIDENT_USER,TRIDENT_SWARM_ADMIN);
}
