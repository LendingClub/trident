package org.lendingclub.trident.swarm.aws;

import java.util.function.BiConsumer;

import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.fasterxml.jackson.databind.JsonNode;

public interface ManagerDnsRegistrationInterceptor extends BiConsumer<JsonNode, ChangeResourceRecordSetsRequest> {

}