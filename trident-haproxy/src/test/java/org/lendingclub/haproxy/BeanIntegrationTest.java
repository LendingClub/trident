package org.lendingclub.haproxy;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.lendingclub.haproxy.ConfigCompiler;
import org.lendingclub.haproxy.MonitorDaemon;
import org.lendingclub.haproxy.config.HAProxySpringConfig;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextHierarchy({
		@ContextConfiguration(classes = {HAProxyTestConfig.class})
})
public class BeanIntegrationTest {

	@Autowired
	org.springframework.context.ApplicationContext ctx;

	public void assertBean(Class c) {
		ctx.getBean(c);
	}

	public void assertBeanByClass(String clazz) {
		try {
			org.junit.Assert.assertTrue("bean can be resolved: " + clazz, ctx.getBean(Class.forName(clazz)) != null);
		} catch (ClassNotFoundException e) {
			org.junit.Assert.fail(e.getMessage());
		}
	}

	public void assertBeanNotFound(Class clazz) {
		try {
			Object bean = ctx.getBean(clazz);
			if (bean!=null) {
				Assertions.fail("must not be registered as a bean: "+clazz);

			}

		}
		catch (NoSuchBeanDefinitionException e) {
			//ignore
		}

	}

	@Test
	public void testIt() {
		assertBean(MonitorDaemon.class);
		assertBean(HAProxySpringConfig.class);
		assertBean(ConfigCompiler.class);
 	}

}
