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

package org.springframework.xd.yarn.shell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.records.ApplicationId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.shell.core.ExecutionProcessor;
import org.springframework.shell.event.ParseResult;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.xd.shell.hadoop.ConfigurationModifiedEvent;
import org.springframework.xd.shell.properties.ConfigurationPropertiesModifiedEvent;
import org.springframework.xd.shell.properties.SpringConfigurationProperties;
import org.springframework.yarn.boot.app.YarnBootClientInstallApplication;
import org.springframework.yarn.boot.app.YarnBootClientSubmitApplication;
import org.springframework.yarn.boot.support.SpringYarnBootUtils;
import org.springframework.yarn.client.YarnClient;
import org.springframework.yarn.client.YarnClientFactoryBean;

/**
 * Base support class for shell commands working with Spring Yarn and handling Spring Boot based applications on Yarn.
 * 
 * @author Janne Valkealahti
 * 
 */
public abstract class YarnCommandsSupport implements ApplicationEventPublisherAware,
		ApplicationListener<ApplicationEvent>, ExecutionProcessor {

	protected final static Log log = LogFactory.getLog(YarnCommandsSupport.class);

	/** Shared hadoop configuration instance for shell */
	@Autowired
	private Configuration configuration;

	/** Shared spring properties instance for shell */
	@Autowired
	@Qualifier("shellConfigurationProperties")
	private SpringConfigurationProperties configurationProperties;

	/** Flag indicating a changed configuration */
	private boolean configurationReinitialize = false;

	/** Flag indicating a changed properties */
	private boolean propertiesReinitialize = false;

	private ApplicationEventPublisher applicationEventPublisher;

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof ConfigurationModifiedEvent) {
			configurationReinitialize = true;
		}
		else if (event instanceof ConfigurationPropertiesModifiedEvent) {
			propertiesReinitialize = true;
		}
	}

	@Override
	public ParseResult beforeInvocation(ParseResult invocationContext) {
		// check whether the Hadoop configuration has changed
		if (configurationReinitialize) {
			try {
				configurationReinitialize = !configurationChanged();
			}
			catch (Exception ex) {
				logUpdateError(ex);
			}
		}
		// check whether the Spring properties has changed
		if (propertiesReinitialize) {
			try {
				propertiesReinitialize = !propertiesChanged();
			}
			catch (Exception ex) {
				logUpdateError(ex);
			}
		}
		return invocationContext;
	}

	@Override
	public void afterReturningInvocation(ParseResult invocationContext, Object result) {
	}

	@Override
	public void afterThrowingInvocation(ParseResult invocationContext, Throwable thrown) {
	}

	/**
	 * Gets the configuration.
	 * 
	 * @return the configuration
	 */
	public Configuration getConfiguration() {
		return configuration;
	}

	/**
	 * Gets the Spring configuration properties set in shell.
	 * 
	 * @return the spring configuration properties
	 */
	public SpringConfigurationProperties getConfigurationProperties() {
		return configurationProperties;
	}

	/**
	 * Install application into hdfs.
	 * 
	 * @param id the unique identifier
	 */
	protected void installApplication(String[] profiles, String appId, String applicationsBaseDir,
			Properties appProperties, boolean hadoopPropsConfig, String[] args) {
		Assert.state(StringUtils.hasText(appId), "Id must be set");

		if (appProperties == null) {
			appProperties = new Properties();
		}

		SpringYarnBootUtils.mergeHadoopPropertyIntoMap(configuration, "fs.defaultFS", "spring.yarn.fsUri",
				appProperties);
		SpringYarnBootUtils.mergeHadoopPropertyIntoMap(configuration, "yarn.resourcemanager.address",
				"spring.yarn.rmAddress", appProperties);
		SpringYarnBootUtils.mergeHadoopPropertyIntoMap(configuration,
				"yarn.resourcemanager.scheduler.address", "spring.yarn.schedulerAddress", appProperties);


		YarnBootClientInstallApplication app = new YarnBootClientInstallApplication();
		app.appId(appId)
				.applicationsBaseDir(applicationsBaseDir)
				.profiles(profiles)
				.appProperties(appProperties);

		if (hadoopPropsConfig) {
			app.configFile("application.properties", appProperties);
		}

		app.run(args);
	}

	/**
	 * Submit application into Yarn.
	 * 
	 * @param id the unique identifier
	 * @param count the count
	 * @return the application id
	 */
	protected ApplicationId submitApplication(String[] profiles, String appId) {

		Properties properties = null;

		String applicationsBaseDir = getConfigurationProperties().getProperties().getProperty(
				"spring.yarn.applicationsBaseDir");
		if (StringUtils.hasText(applicationsBaseDir)) {
			Path path = new Path(applicationsBaseDir, appId + "/application.properties");
			try {
				properties = readApplicationProperties(path, getConfiguration());
			}
			catch (IOException e) {
			}
		}
		SpringYarnBootUtils.mergeHadoopPropertyIntoMap(configuration, "fs.defaultFS", "spring.yarn.fsUri",
				properties);
		SpringYarnBootUtils.mergeHadoopPropertyIntoMap(configuration, "yarn.resourcemanager.address",
				"spring.yarn.rmAddress", properties);
		SpringYarnBootUtils.mergeHadoopPropertyIntoMap(configuration,
				"yarn.resourcemanager.scheduler.address", "spring.yarn.schedulerAddress", properties);


		ArrayList<String> args = new ArrayList<String>();

		for (Entry<Object, Object> entry : getConfigurationProperties().getMergedProperties(appId).entrySet()) {
			args.add("--" + entry.getKey() + "=" + entry.getValue());
		}

		YarnBootClientSubmitApplication app = new YarnBootClientSubmitApplication();
		app.appId(appId)
				.profiles(profiles)
				.appProperties(properties);

		return app.run(args.toArray(new String[0]));
		// return new YarnBootClientSubmitApplication().submit(profiles, properties, getConfiguration(),
		// args.toArray(new String[0]));
	}

	private Properties readApplicationProperties(Path path, Configuration configuration) throws IOException {
		FileSystem fs = null;
		FSDataInputStream in = null;
		Properties properties = new Properties();
		IOException ioe = null;
		try {
			fs = path.getFileSystem(configuration);
			if (fs.exists(path)) {
				in = fs.open(path);
				properties.load(in);
			}
		}
		catch (IOException e) {
			ioe = e;
		}
		finally {
			if (in != null) {
				try {
					in.close();
					in = null;
				}
				catch (IOException e) {
				}
			}
			fs = null;
		}
		if (ioe != null) {
			throw ioe;
		}
		return properties;
	}

	/**
	 * Called when configuration has been changed. Implementor needs to overwrite this method to process changed
	 * configuration.
	 * 
	 * @return true, if changed configuration is processed
	 * @throws Exception the exception
	 */
	protected abstract boolean configurationChanged() throws Exception;

	/**
	 * Called when properties has been changed. Implementor needs to overwrite this method to process changed
	 * properties.
	 * 
	 * @return true, if changed properties is processed
	 * @throws Exception the exception
	 */
	protected abstract boolean propertiesChanged() throws Exception;

	/**
	 * Log error.
	 * 
	 * @param e the exception
	 */
	protected void logUpdateError(Exception e) {
		log.error("Hadoop configuration changed but updating [" + failedComponentName() + "] failed", e);
	}

	/**
	 * Return a failed component name. Defaults to class name of an implementor.
	 * 
	 * @return the component name
	 */
	protected String failedComponentName() {
		return getClass().getName();
	}

	/**
	 * Builds a new {@link YarnClient} for generic client usage where it would be over the top to work via boot
	 * environment.
	 * 
	 * @return the {@link YarnClient}
	 * @throws Exception if error occurred
	 */
	protected YarnClient getYarnClient() throws Exception {
		YarnClientFactoryBean factory = new YarnClientFactoryBean();
		factory.setConfiguration(getConfiguration());
		factory.afterPropertiesSet();
		return factory.getObject();
	}

	protected void publishConfigurationPropertiesChange() {
		applicationEventPublisher.publishEvent(new ConfigurationPropertiesModifiedEvent(configurationProperties));
	}

}
