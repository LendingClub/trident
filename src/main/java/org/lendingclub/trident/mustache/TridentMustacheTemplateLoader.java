package org.lendingclub.trident.mustache;

import java.io.InputStreamReader;
import java.io.Reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import com.samskivert.mustache.Mustache.TemplateLoader;

public class TridentMustacheTemplateLoader implements TemplateLoader, ResourceLoaderAware {

	Logger logger = LoggerFactory.getLogger(TridentMustacheTemplateLoader.class);
	
	TridentMustacheViewResolver resolver=null;
	
	private String charSet = "UTF-8";


	public TridentMustacheTemplateLoader() {
	}

	/**
	 * Set the charset.
	 * 
	 * @param charSet
	 *            the charset
	 */
	public void setCharset(String charSet) {
		this.charSet = charSet;
	}


	@Override
	public Reader getTemplate(String name) throws Exception {
		
		logger.debug("getTemplate("+name+")");
		// We have to set the resolver in a lazy way due to some bizarre 3-way mutual dependency 
		// between Mustache compiler, Mustache Template Loader and Spring View Resolver.
		if (resolver==null) {
			logger.warn("TridentMustacheTemplateResolver not set...this could be a programming error");
			return null;
		}
		
		Resource r = resolver.resolveResource(name,null);
		

		if (r==null) {
			return null;
		}
		
		return new InputStreamReader(r.getInputStream());

		
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		// should be ok to be no-op
		
	}
	
	public void setTridentMustacheViewResolver(TridentMustacheViewResolver r) {
		this.resolver = r;
	}
}
