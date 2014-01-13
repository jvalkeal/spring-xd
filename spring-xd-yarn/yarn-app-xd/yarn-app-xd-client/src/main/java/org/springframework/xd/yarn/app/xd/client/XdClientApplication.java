/*
 * Copyright 2013 the original author or authors.
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

package org.springframework.xd.yarn.app.xd.client;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.records.ApplicationId;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.EmbeddedServletContainerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.WebMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.yarn.boot.support.SpringYarnProperties;
import org.springframework.yarn.client.AbstractYarnClient;
import org.springframework.yarn.client.YarnClient;

/**
 * Spring Boot based application acting as a client deploying XD instances into Hadoop Yarn.
 * 
 * @author Janne Valkealahti
 */
@Configuration
@EnableAutoConfiguration(exclude = { EmbeddedServletContainerAutoConfiguration.class, WebMvcAutoConfiguration.class,
	JmxAutoConfiguration.class, BatchAutoConfiguration.class })
public class XdClientApplication {

	private static final Log log = LogFactory.getLog(XdClientApplication.class);

	/**
	 * Deploy new XD instance into hadoop.
	 * 
	 * @return the application id
	 */
	public ApplicationId deploy() {
		return deploy(new String[] {});
	}

	public ApplicationId deploy(org.apache.hadoop.conf.Configuration configuration) {
		return deploy(new String[] {}, configuration);
	}

	public ApplicationId deploy(String[] args, org.apache.hadoop.conf.Configuration configuration) {
		Assert.notNull(configuration, "Configuration must be set");
		String[] custom = new String[] {
			"--spring.yarn.fsUri=" + configuration.get("fs.defaultFS"),
			"--spring.yarn.rmAddress=" + configuration.get("yarn.resourcemanager.address"),
			"--spring.yarn.schedulerAddress=" + configuration.get("yarn.resourcemanager.scheduler.address")
		};
		return deploy(StringUtils.mergeStringArrays(args, custom));
	}

	/**
	 * Deploy new XD instance into hadoop.
	 * 
	 * @param args the args
	 * @return the application id
	 */
	public ApplicationId deploy(String[] args) {
		ApplicationId applicationId = null;
		ConfigurableApplicationContext context = null;
		Exception exception = null;

		try {
			context = new SpringApplicationBuilder(XdClientApplication.class)
					.web(false)
					.run(args);

			YarnClient client = context.getBean(YarnClient.class);

			// TODO: make application.properties create more generic
			SpringYarnProperties springYarnProperties = context.getBean(SpringYarnProperties.class);
			if (springYarnProperties != null && client instanceof AbstractYarnClient) {
				String applicationDir = springYarnProperties.getApplicationDir();
				FileSystem fs = FileSystem.get(((AbstractYarnClient) client).getConfiguration());
				FSDataOutputStream out = fs.create(new Path(applicationDir, "application.properties"));
				for (String arg : args) {
					System.out.println("XXX arg: " + arg);
					if (arg.startsWith("--") && arg.length() > 4) {
						out.writeBytes(arg.substring(2) + "\n");
					}
				}
				out.close();
			}

			applicationId = client.submitApplication();
		}
		catch (Exception e) {
			exception = e;
			log.debug("Error submitting new XD instance", e);
		}
		finally {
			if (context != null) {
				try {
					context.close();
				}
				catch (Exception e) {
					log.debug("Error closing context", e);
				}
			}
		}

		if (exception != null) {
			throw new RuntimeException("Failed to submit XD instance", exception);
		}

		return applicationId;
	}

	public static void main(String[] args) {
		new XdClientApplication().deploy(args);
	}

}
