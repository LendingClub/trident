package org.lendingclub.haproxy;

import org.springframework.stereotype.Component;

/**
 * Created by hasingh on 5/26/17.
 */

@Component
public interface EndpointResolver {

	public String getEndpointFor(String appId);
}
