package org.lendingclub.trident.provision;

import java.util.List;
import java.util.Optional;

import org.lendingclub.trident.Trident;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public final class SwarmTemplateInterceptor implements SwarmNodeProvisionInterceptor {

	Logger logger = LoggerFactory.getLogger(SwarmTemplateInterceptor.class);
	
	SwarmTemplateManager swarmTemplateManager;
	
	SwarmTemplateInterceptor(SwarmTemplateManager m) {
		this.swarmTemplateManager = m;
	}
	@Override
	public SwarmNodeProvisionContext apply(SwarmNodeProvisionContext ctx) {
		
		Object template = ctx.getAttributes().get("templateName");
		if (template!=null && swarmTemplateManager!=null) {
		
			Optional<JsonNode> data = swarmTemplateManager.getTemplate(template.toString());
			if (data.isPresent()) {
				logger.info("loaded template: {}",template);
				data.get().fields().forEachRemaining(it->{
					JsonNode val = it.getValue();
					
					String stringVal = val.asText();
					if (val.isArray()) {
						List<String> tmp = Lists.newArrayList();
						val.forEach(x->{
							tmp.add(x.toString());
						});
						stringVal = Joiner.on(",").join(tmp);
					}
					ctx.getAttributes().put(it.getKey(), stringVal);
				});
			}
		}
		return ctx;
	}

	@Override
	public String apply(SwarmNodeProvisionContext ctx, String script) {
		return script;
	}

}
