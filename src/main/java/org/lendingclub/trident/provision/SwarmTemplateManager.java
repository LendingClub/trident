package org.lendingclub.trident.provision;

import java.util.List;
import java.util.Optional;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.util.TridentStartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class SwarmTemplateManager implements TridentStartupListener {

	Logger logger = LoggerFactory.getLogger(SwarmTemplateManager.class);

	@Autowired
	NeoRxClient neo4j;

	
	public void removeTemplate(String name) {
		neo4j.execCypher("match (a:DockerSwarmTemplate {templateName:{name}}) detach delete a","name",name);
	}
	public void saveTemplate(String name, ObjectNode data) {
		data.put("templateName", name);
		data.remove("name");
		neo4j.execCypher("merge (x:DockerSwarmTemplate {templateName:{name}}) set x+={props}","name",name,"props",data);
	}
	
	public Optional<JsonNode> getTemplate(String name) {
		return Optional.ofNullable(neo4j.execCypher("match (a:DockerSwarmTemplate {templateName:{name}}) return a","name",name).blockingFirst(null));	
	}
	
	
	public List<JsonNode> findTemplates() {
		return neo4j.execCypher("match (x:DockerSwarmTemplate) return x order by x.templateName").toList().blockingGet();
	}
	@Override
	public void onStart(ApplicationContext context) {

		try {
			neo4j.execCypher("CREATE  CONSTRAINT ON (a:DockerSwarmTemplate) assert a.templateName IS UNIQUE ");
		} catch (Exception e) {
			logger.warn("could not create constraint", e);
		}
		try {
			neo4j.execCypher("DROP  CONSTRAINT ON (a:DockerSwarmTemplate) assert a.templateName IS UNIQUE ");
		} catch (Exception e) {
			logger.warn("could not drop constraint", e);
		}
	
		try {
			neo4j.execCypher("match (a:DockerSwarmTemplate) where exists(a.name) and (not exists(a.templateName)) set a.templateName=a.name ");
			neo4j.execCypher("match (a:DockerSwarmTemplate) where a.name=a.templateName remove a.name ");
		} catch (Exception e) {
			logger.warn("could not create constraint", e);
		}
	}
}
