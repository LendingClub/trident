package org.lendingclub.trident.auth;

import javax.servlet.http.HttpServletRequest;

import org.apache.catalina.core.ApplicationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import com.google.common.base.Function;
import com.google.common.base.Supplier;

@Controller
@Order(-200)
public class TridentLoginController {

	Logger logger = LoggerFactory.getLogger(TridentLoginController.class);
	
	public static final String DEFAULT_FORM_LOGIN_TEMPLATE="form-login";
	@Autowired
	org.springframework.context.ApplicationContext ctx;

	java.util.function.Function<HttpServletRequest, String> templateChooser = new java.util.function.Function<HttpServletRequest,String>() {
	
		@Override
		public String apply(HttpServletRequest input) {
			return DEFAULT_FORM_LOGIN_TEMPLATE;
		}
	};

	public void setLoginTemplateSelector(java.util.function.Function<HttpServletRequest,String> chooser) {
		this.templateChooser = chooser;
	}
	@RequestMapping("/login")
	public ModelAndView login(HttpServletRequest request) {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {

			ModelAndView m = new ModelAndView("redirect:/home");
			return m;
		}

		ModelAndView m = new ModelAndView(templateChooser.apply(request));
		return m;

	}

}
