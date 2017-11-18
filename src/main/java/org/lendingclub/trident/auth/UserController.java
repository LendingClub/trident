package org.lendingclub.trident.auth;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.core.SecurityContext;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;

@Controller
public class UserController {

	
	@RequestMapping("/users/{id}")
	public ModelAndView users(@PathVariable("id") String id) {
		
		Map<String,Object> data = Maps.newHashMap();
		
		
		if (id!=null && id.equals("self")) {
			Map<String,Object> user = Maps.newHashMap();
			data.put("user", user);
			org.springframework.security.core.context.SecurityContext sc = SecurityContextHolder.getContext();		
			user.put("username",Strings.nullToEmpty(sc.getAuthentication().getName()));
			List<String> roles = sc.getAuthentication().getAuthorities().stream().map(a->a.getAuthority()).collect(Collectors.toList());
			user.put("roles", roles);
		}
		return new ModelAndView("user-details",data);
	}
}
