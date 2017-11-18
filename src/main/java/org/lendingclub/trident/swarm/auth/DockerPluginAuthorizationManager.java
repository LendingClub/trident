package org.lendingclub.trident.swarm.auth;

import java.util.List;

import org.lendingclub.trident.util.JsonUtil;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

@Component
public class DockerPluginAuthorizationManager {

	List<DockerPluginAuthorizationVoter> voters = Lists.newCopyOnWriteArrayList();

	public JsonNode authorize(DockerPluginAuthorizationContext ctx) {

		if (voters != null) {
			for (DockerPluginAuthorizationVoter voter : voters) {
				voter.authorize(ctx);
			}
		}

		if (!ctx.isAuthorized().isPresent()) {
			// allow by default. It is more intuitive and easier to extend this
			// way. If the implementor wants to change this,
			// they can simply register a deny rule and then permit on a
			// case-basis.
			return JsonUtil.createObjectNode().put("Allow", true).put("Msg", "").put("Err", "");
		} else if (ctx.isAuthorized().get() == true) {
			return JsonUtil.createObjectNode().put("Allow", true).put("Msg", ctx.getReason().orElse("")).put("Err", "");
		} else {
			return JsonUtil.createObjectNode().put("Allow", false).put("Msg", ctx.getReason().orElse("")).put("Err",
					ctx.getReason().orElse(""));
		}
	}

	public List<DockerPluginAuthorizationVoter> getVoters() {
		return voters;
	}
}
