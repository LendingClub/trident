package org.lendingclub.trident.layout;

import java.util.Map;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lendingclub.trident.Trident;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.support.ControllerClassNameHandlerMapping;

import com.google.common.collect.Maps;


/**
 * This global interceptor will be invoked for all MVC invocations.  It allows us to ensure that
 * data is passed consistently to all views so that information needed by shared header/footer 
 * components is always present.
 * @author rschoening
 *
 */
public class TridentMVCHandlerInterceptor extends HandlerInterceptorAdapter {

	@Autowired
	NavigationManager navigationManager;
	
	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) throws Exception {

		super.postHandle(request, response, handler, modelAndView);

	
		Map<String, String> tridentInfo = Maps.newHashMap();
		tridentInfo.put("revision", Trident.getInstance().getVersion().getShortRevision());

		if (modelAndView != null) {
			
			
			// Add revision info
			modelAndView.addObject("tridentInfo", tridentInfo);
			
			// Add sidebar nav
			modelAndView.addObject("sidebar", navigationManager.getSidebarMenu());
		}
	}

}
