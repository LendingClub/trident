package org.lendingclub.trident.mustache;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Locale;

import org.slf4j.Logger;
import org.springframework.beans.propertyeditors.LocaleEditor;
import org.springframework.boot.autoconfigure.mustache.web.MustacheView;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.UrlBasedViewResolver;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.Template;

public class TridentMustacheViewResolver extends UrlBasedViewResolver {

	Logger logger = org.slf4j.LoggerFactory.getLogger(TridentMustacheViewResolver.class);
	private Compiler compiler = Mustache.compiler();

	private String charset;

	TridentMustacheTemplateLoader templateLoader;

	public TridentMustacheViewResolver() {
		setViewClass(requiredViewClass());

	}

	@Override
	protected Class<?> requiredViewClass() {
		return MustacheView.class;
	}

	/**
	 * Set the compiler.
	 * 
	 * @param compiler
	 *            the compiler
	 */
	public void setCompiler(Compiler compiler) {
		this.compiler = compiler;

	}

	/**
	 * Set the charset.
	 * 
	 * @param charset
	 *            the charset
	 */
	public void setCharset(String charset) {
		this.charset = charset;
	}

	@Override
	protected View loadView(String viewName, Locale locale) throws Exception {
		Resource resource = resolveResource(viewName, locale);
		if (resource == null) {
			return null;
		}
		MustacheView mustacheView = (MustacheView) super.loadView(viewName, locale);
		mustacheView.setTemplate(createTemplate(resource));
		return mustacheView;
	}

	protected Resource resolveResource(String viewName, Locale locale) {

		Resource r = null;
		try {
			r = resolveFromLocale(
					new java.io.File("./src/main/resources/templates").getCanonicalFile().toURI().toURL().toString()
							+ viewName,
					getLocale(locale));
		
		} catch (IOException e) {
			logger.info("could not resolve template: "+e.toString());
		}
		if (r == null) {
			r = resolveFromLocale("classpath:/templates/" + viewName, getLocale(locale));
		}
		
		
		logger.debug("resolved view {} => {}",viewName,r);
		return r;
	}

	protected Resource resolveFromLocale(String viewName, String locale) {
		String name = viewName + locale + getSuffix();

		Resource resource = getApplicationContext().getResource(name);

		if (resource == null || !resource.exists()) {
			if (locale.isEmpty()) {
				
				resource = null;
			} else {
				int index = locale.lastIndexOf("_");
				resource = resolveFromLocale(viewName, locale.substring(0, index));
			}
		}
		
		return resource;
	}

	private String getLocale(Locale locale) {
		if (locale == null) {
			return "";
		}
		LocaleEditor localeEditor = new LocaleEditor();
		localeEditor.setValue(locale);
		return "_" + localeEditor.getAsText();
	}

	private Template createTemplate(Resource resource) throws IOException {

		Reader reader = getReader(resource);

		try {
			Template t = this.compiler.compile(reader);

			return t;
		} finally {
			reader.close();
		}
	}

	private Reader getReader(Resource resource) throws IOException {
		if (this.charset != null) {
			return new InputStreamReader(resource.getInputStream(), this.charset);
		}
		return new InputStreamReader(resource.getInputStream());
	}

	public TridentMustacheTemplateLoader getTemplateLoader() {
		return templateLoader;
	}

	public void setTemplateLoader(TridentMustacheTemplateLoader tl) {
		this.templateLoader = tl;
	}
}
