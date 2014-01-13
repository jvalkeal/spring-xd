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

import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.ExecutionProcessor;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.shell.event.ParseResult;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Command setting spring boot properties.
 * 
 * @author Janne Valkealahti
 * 
 */
@Component
public class SpringConfigurationPropertiesCommands implements ApplicationEventPublisherAware, CommandMarker,
		ExecutionProcessor {

	private static final String PREFIX = "spring config ";

	private static final String COMMAND_SET = PREFIX + "set";

	private static final String COMMAND_LIST = PREFIX + "list";

	private static final String KEY_SET_PROPERTY = "property";

	private static final String HELP_SET_PROPERTY = "what to set, in the form <name=value>";

	private static final String HELP_SET = "Set spring property";

	private static final String HELP_LIST = "List spring properties";

	@Autowired
	@Qualifier("shellConfigurationProperties")
	private Properties properties;

	private ApplicationEventPublisher applicationEventPublisher;

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Override
	public ParseResult beforeInvocation(ParseResult invocationContext) {
		return invocationContext;
	}

	@Override
	public void afterReturningInvocation(ParseResult invocationContext, Object result) {
		String name = invocationContext.getMethod().getName();
		if (name.startsWith("load") || name.startsWith("set")) {
			publishChange();
		}
	}

	@Override
	public void afterThrowingInvocation(ParseResult invocationContext, Throwable thrown) {
	}

	@CliCommand(value = COMMAND_SET, help = HELP_SET)
	public void setProperty(
			@CliOption(key = { "", KEY_SET_PROPERTY }, mandatory = true, help = HELP_SET_PROPERTY) String property) {
		int i = property.indexOf("=");
		Assert.isTrue(i >= 0, "invalid format");
		String key = property.substring(0, i);
		Assert.hasText(key, "a valid name is required");
		String value = property.substring(i + 1);
		properties.setProperty(key, value);
	}

	@CliCommand(value = COMMAND_LIST, help = HELP_LIST)
	public String props() {
		StringBuilder buf = new StringBuilder();
		buf.append("Spring Boot Properties: ");
		buf.append(properties);
		return buf.toString();
	}

	private void publishChange() {
		applicationEventPublisher.publishEvent(new ConfigurationPropertiesModifiedEvent(properties));
	}

}
