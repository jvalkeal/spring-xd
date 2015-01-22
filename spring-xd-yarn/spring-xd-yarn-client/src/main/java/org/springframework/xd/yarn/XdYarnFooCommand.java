/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.xd.yarn;

import joptsimple.OptionSet;

import org.springframework.boot.actuate.autoconfigure.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.EndpointMBeanExportAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.EmbeddedServletContainerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.WebMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.yarn.boot.SpringApplicationCallback;
import org.springframework.yarn.boot.SpringApplicationTemplate;
import org.springframework.yarn.boot.app.AbstractClientApplication;
import org.springframework.yarn.boot.cli.AbstractApplicationCommand;

public class XdYarnFooCommand extends AbstractApplicationCommand {

	protected XdYarnFooCommand() {
		super("foo", "bardesc", new FooOptionHandler());
	}

	public static class FooOptionHandler extends ApplicationOptionHandler<String> {

		@Override
		protected void runApplication(OptionSet options) throws Exception {
			handleApplicationRun(new FooApplication());
		}

	}

	@Configuration
	@EnableConfigurationProperties({ ZkProperties.class })
	@EnableAutoConfiguration(exclude = { EmbeddedServletContainerAutoConfiguration.class,
		WebMvcAutoConfiguration.class,
		JmxAutoConfiguration.class, BatchAutoConfiguration.class, JmxAutoConfiguration.class,
		EndpointMBeanExportAutoConfiguration.class, EndpointAutoConfiguration.class })
	public static class FooApplication extends AbstractClientApplication<String, FooApplication> {

		@Override
		public String run(String... args) {
			SpringApplicationBuilder builder = new SpringApplicationBuilder();
			builder.web(false);
			builder.sources(FooApplication.class);
			SpringApplicationTemplate template = new SpringApplicationTemplate(builder);
			return template.execute(new SpringApplicationCallback<String>() {

				@Override
				public String runWithSpringApplication(ApplicationContext context) throws Exception {
					ZkProperties zkProperties = context.getBean(ZkProperties.class);
					return "Hello zk.namespace=" + zkProperties.getNamespace();
				}

			}, args);
		}

		@Override
		protected FooApplication getThis() {
			return this;
		}

	}

	@ConfigurationProperties(value = "zk")
	public static class ZkProperties {

		private String namespace;

		public void setNamespace(String namespace) {
			this.namespace = namespace;
		}

		public String getNamespace() {
			return namespace;
		}

	}

}
