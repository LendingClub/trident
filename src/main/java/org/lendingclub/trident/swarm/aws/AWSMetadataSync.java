package org.lendingclub.trident.swarm.aws;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.cluster.TridentClusterManager;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.CreateOrUpdateTagsRequest;
import com.amazonaws.services.autoscaling.model.Tag;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

public class AWSMetadataSync {

	Logger logger = LoggerFactory.getLogger(AWSMetadataSync.class);

	@Autowired
	NeoRxClient neo4j;

	@Autowired
	TridentClusterManager tridentClusterManager;

	@Autowired
	AWSAccountManager accountManager;

	private static final boolean PROPAGATE = true;
	private static final boolean DO_NOT_PROPAGATE = false;

	private static final String ASG_TAG_RESOURCE_TYPE="auto-scaling-group";
	public void writeTagsForAllSwarms() {
		neo4j.execCypher(
				"match (a:AwsAsg) where exists(a.aws_tag_tridentClusterId) and a.aws_tag_swarmNodeType='MANAGER' return a")
				.forEach(asg -> {
					String tridentClusterId = asg.path("aws_tag_tridentClusterId").asText();
					try {

						if (!Strings.isNullOrEmpty(tridentClusterId)) {
							writeTagsForSwarm(tridentClusterId);
						} else {
							logger.warn("{} has invalid tridentClusterIdTag",
									asg.path("aws_autoScalingGroupARN").asText());
						}
					} catch (RuntimeException e) {
						logger.warn("problem writing tags to {}", asg.path("aws_autoScalingGroupARN").asText());
					}
				});
	}

	public boolean writeTagsForSwarm(String id) {

		AtomicBoolean success = new AtomicBoolean(true);  // assume success until we encounter a failure
		// Let's scan the AwsAsg nodes in neo4j.
		neo4j.execCypher("match (a:AwsAsg {aws_tag_tridentClusterId:{id}, aws_tag_swarmNodeType:'MANAGER'})  return a",
				"id", id).forEach(it -> {
					String arn = it.path("aws_arn").asText();
					String asgName = it.path("aws_autoScalingGroupName").asText();
					if (Strings.isNullOrEmpty(asgName)) {
						logger.warn("aws_autoScalingGroupName is missing...cannot tag: {}",it);
						success.set(false);
					}
					else {
						try {
						
					
							List<Tag> list = createTagsForSwarm(id);
							
							// The ASG tagging API is kind of bizarre...the ASG name that we want to tag is placed on the tag, rather than the request.
							list.forEach(c->{
								c.withResourceId(asgName);
							});
							String account = it.path("aws_account").asText();
							String region = it.path("aws_region").asText();

							if (Strings.isNullOrEmpty(account) || Strings.isNullOrEmpty(region)) {
								logger.info(
										"cannot find account or region to update ASG: " + it.path("aws_arn").asText());

							} else {
								AmazonAutoScaling asgClient = accountManager
										.getClient(account, AmazonAutoScalingClientBuilder.class,region);
								asgClient.createOrUpdateTags(new CreateOrUpdateTagsRequest().withTags(list));
							
							}
						} catch (RuntimeException e) {
							logger.warn("problem writing tags to asg: " + arn, e);
							success.set(false);
						}
					}
				});

		return success.get();
	}

	protected List<Tag> createTagsForSwarm(String id) {
		return createTagsForSwarm(loadDockerSwarmNode(id));
	}

	protected JsonNode loadDockerSwarmNode(String id) {
		return neo4j.execCypher("match (s:DockerSwarm {tridentClusterId:{tridentClusterId}}) return s",
				"tridentClusterId", id).blockingFirst(MissingNode.getInstance());
	}

	protected List<Tag> createTagsForSwarm(JsonNode n) {
		List<Tag> tagList = Lists.newArrayList();

		addIfPresent(tagList, n, "tridentClusterId", PROPAGATE)
		.addIfPresent(tagList, n, "tridentClusterName", PROPAGATE)
		.addIfPresent(tagList, n, "tridentOwnerId", PROPAGATE)
				.addIfPresent(tagList, n, "desiredManagerDnsName", DO_NOT_PROPAGATE)
				.addIfPresent(tagList, n, "managerDnsName", DO_NOT_PROPAGATE)
				.addIfPresent(tagList, n, "managerSubjectAlternativeNames", DO_NOT_PROPAGATE)
				.addIfPresent(tagList, n, "description", DO_NOT_PROPAGATE);

		if (tridentClusterManager != null) {
			String ownerId = tridentClusterManager.getTridentInstallationId();
			if (!Strings.isNullOrEmpty(ownerId)) {
				Tag tag = new Tag().withKey("tridentOwnerId").withValue(ownerId).withPropagateAtLaunch(true).withResourceType(ASG_TAG_RESOURCE_TYPE);
				tagList.add(tag);
			}

		}
		return tagList;
	}

	protected AWSMetadataSync addIfPresent(List<Tag> list, JsonNode n, String key, boolean propagate) {
		String val = n.path(key).asText();
		if (Strings.isNullOrEmpty(val) || Strings.isNullOrEmpty(key)) {
			// do nothing
		} else {
			// note that we do NOT set the asg name here. The asg tag API is
			// kind of strange in that it is set on a per-tag vs per-request
			// basis, which

			Tag tag = new Tag().withPropagateAtLaunch(propagate).withKey(key).withValue(val)
					.withResourceType(ASG_TAG_RESOURCE_TYPE);
			list.add(tag);
		}
		return this;
	}

	protected void createMissingASGRelationships() {
		neo4j.execCypher(
				"match (a:AwsAsg) OPTIONAL MATCH (s:DockerSwarm)-[:PROVIDED_BY]->(a) where s=null and exists(a.aws_tag_tridentClusterId) return a")
				.forEach(it -> {
					String tridentId = it.path("aws_tag_tridentClusterId").asText();
					String arn = it.path("aws_autoScalingGroupARN").asText();
					if (!Strings.isNullOrEmpty(tridentId)) {

						neo4j.execCypher(
								"match (a:AwsAsg {aws_autoScalingGroupARN:{arn}}), (t:DockerSwarm {tridentClusterId:{id}}) merge (t)-[x:PROVIDED_BY]->(a)",
								"arn", arn, "id", tridentId);
					}

				});
	}

	public void createMissingDockerSwarmNodes() {
		// This cypher will find all tridend ASGs that do not have DockerSwarm
		// nodes. We will will then backfill
		String cypher = "match (a:AwsAsg)--(x:AwsEc2Instance) OPTIONAL MATCH (a)--(s:DockerSwarm)   "
				+ "where s=null and exists(a.aws_tag_tridentClusterId) and "
				+ "a.aws_tag_swarmNodeType='MANAGER' return " + "a.aws_tag_tridentClusterName as tridentClusterName, "
				+ "a.aws_arn as aws_arn, "
				+ "a.aws_tag_tridentClusterId as tridentClusterId, x.aws_privateIpAddress as ip,x.aws_account as account,x.aws_region as region, a.aws_autoScalingGroupName as asgName, a.aws_autoScalingGroupARN as asgARN";

		neo4j.execCypher(cypher).forEach(it -> {
			String id = it.path("tridentClusterId").asText().trim();
			String name = it.path("name").asText();
			String managerAddress = it.get("ip").asText() + ":2377";
			if ((!Strings.isNullOrEmpty(id)) && (!Strings.isNullOrEmpty(name))) {

				// Merge the node, but only set attributes on create so that we
				// don't accidentally overwrite something.
				JsonNode swarmNode = neo4j
						.execCypher(
								"merge (s:DockerSwarm {tridentClusterId:{id}}) on create set s.managerAddress={addr}, s.name={name} set s.updateTs=timestamp() return s",
								"id", it.get("tridentClusterId").asText(), "addr", managerAddress, "name", name)
						.blockingFirst(null);

				JsonNode asg = neo4j
						.execCypher("match (a:AwsAsg {aws_arn:{arn}}) return a", "arn", it.path("aws_arn").asText())
						.blockingFirst(MissingNode.getInstance());

				JsonNode swarmAttributes = copyASGTagsToSwarmAttributes(asg);

				ObjectNode filteredAttributes = JsonUtil.createObjectNode();
				// Now find all attributes that are already on the swarm node
				// and eliminate them. We don't want to overwrite. Just replace
				// missing data.
				swarmAttributes.fieldNames().forEachRemaining(fieldName -> {
					if (swarmNode.has(fieldName)) {
						// don't set a value if it is already set
					} else {
						filteredAttributes.set(fieldName, swarmAttributes.get(fieldName));
					}
				});
				filteredAttributes.remove("tridentClusterId"); // remove this
																// since it is
																// already
																// present
				neo4j.execCypher("match (s:DockerSwarm {tridentClusterId:{tridentClusterId}}) set s+={props}",
						"tridentClusterId", id, "props", filteredAttributes);
			}
		});

		createMissingASGRelationships();
	}

	void copyTagToSwarmAttribute(JsonNode asg, ObjectNode target, String attributeName) {
		String tagName = "aws_tag_" + attributeName;
		if (!asg.has(tagName)) {
			return;
		}
		String val = asg.path(tagName).asText();
		if (!Strings.isNullOrEmpty(val)) {
			target.put(attributeName, val);
		}
	}

	ObjectNode copyASGTagsToSwarmAttributes(JsonNode n) {
		ObjectNode target = JsonUtil.createObjectNode();

		copyTagToSwarmAttribute(n, target, "tridentOwnerId");
		copyTagToSwarmAttribute(n, target, "tridentClusterName");
		copyTagToSwarmAttribute(n, target, "desiredManagerDnsName");
		copyTagToSwarmAttribute(n, target, "managerSubjectAlternativeNames");
		copyTagToSwarmAttribute(n, target, "description");
		return target;

	}
}
