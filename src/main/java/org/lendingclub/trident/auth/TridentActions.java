package org.lendingclub.trident.auth;

/**
 * Dictionary of actions to facilitate fine-grained authorization. 
 * We do not use Enum types for this because they are brittle and can't be extended.
 * Lowly String "constants" don't have this problem.
 * @author rschoening
 *
 */
public class TridentActions {

	public static final String CREATE_SWARM="CREATE_SWARM";
	public static final String MODIFY_SWARM="MODIFY_SWARM";
	public static final String DELETE_SWARM="DELETE_SWARM";
	
	public static final String CREATE_SWARM_TEMPLATE="CREATE_SWARM_TEMPLATE";
	public static final String MODIFY_SWARM_TEMPLATE="MODIFY_SWARM_TEMPLATE";
	public static final String DELETE_SWARM_TEMPLATE="DELETE_SWARM_TEMPLATE";
	
	public static final String CREATE_APP_CLUSTER="CREATE_APP_CLUSTER";
	public static final String MODIFY_APP_CLUSTER="MODIFY_APP_CLUSTER";
	public static final String DELETE_APP_CLUSTER="DELETE_APP_CLUSTER";
	
	public static final String CREATE_USER="CREATE_USER";
	public static final String MODIFY_USER="MODIFY_USER";
	public static final String DELETE_USER="DELETE_USER";
	
	
	public static final String CREATE_SCHEDULE="CREATE_SCHEDULE";
	public static final String MODIFY_SCHEDULE="MODIFY_SCHEDULE";
	public static final String DELETE_SCHEDULE="DELETE_SCHEDULE";
	
}
