package org.lendingclub.trident.util;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.google.common.base.Strings;

public class RequestValidator {
	
	public static List<String> validateParameters(HttpServletRequest request) {
		Enumeration<String> e = request.getParameterNames();
		List<String> emptyParams = new ArrayList<String>();
		while (e.hasMoreElements()) {
			String param = e.nextElement();
			String paramValue = Strings.nullToEmpty(request.getParameter(param)).trim();
			if (Strings.isNullOrEmpty(paramValue))
				emptyParams.add(param);
		}
		return emptyParams;
	}
	
	public static Map<String, String> getParametersMap(HttpServletRequest request) {
		Map<String, String> map = new HashMap<String, String>();
		Enumeration<String> e = request.getParameterNames();
		while (e.hasMoreElements()) {
			String param = e.nextElement();
			String paramValue = Strings.nullToEmpty(request.getParameter(param)).trim();
			map.put(param, paramValue);
		}	
		return map;
	}

}
