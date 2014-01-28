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

import java.util.ArrayList;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;

import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.shell.event.ParseResult;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.xd.shell.util.Table;
import org.springframework.yarn.boot.app.YarnBootClientInfoApplication;
import org.springframework.yarn.client.YarnClient;

/**
 * Shell integration providing control commands for Spring Boot based Spring Yarn Applications running on Hadoop Yarn.
 * 
 * @author Janne Valkealahti
 * 
 */
@Component
public class YarnAppCommands extends YarnCommandsSupport {

	private static final String PREFIX = "yarn app ";

	private static final String COMMAND_INSTALL = PREFIX + "install";

	private static final String COMMAND_SUBMIT = PREFIX + "submit";

	private static final String COMMAND_KILL = PREFIX + "kill";

	private static final String COMMAND_LIST_SUBMITTED = PREFIX + "list submitted";

	private static final String COMMAND_LIST_INSTALLED = PREFIX + "list installed";

	private static final String COMMAND_CONFIG_DIR = PREFIX + "config dir";

	private static final String OPTION_INSTALL_ID = "id";

	private static final String OPTION_INSTALL_FILES = "files";

	private static final String OPTION_INSTALL_OVERWRITE = "overwrite";

	private static final String OPTION_SUBMIT_ID = "id";

	private static final String OPTION_KILL_ID = "id";

	private static final String OPTION_LIST_SUBMITTED_VERBOSE = "verbose";

	private static final String OPTION_LIST_SUBMITTED_TYPE = "type";

	private static final String OPTION_CONFIG_DIR = "dir";

	private static final String HELP_COMMAND_INSTALL = "Install application bundle into hdfs";

	private static final String HELP_OPTION_INSTALL_ID = "Application unique id";

	private static final String HELP_OPTION_INSTALL_FILES = "Paths to application files";

	private static final String HELP_OPTION_INSTALL_OVERWRITE = "If existing application bundle can be overwritten";

	private static final String HELP_COMMAND_SUBMIT = "Submit new application instance";

	private static final String HELP_OPTION_SUBMIT_ID = "Application unique id";

	private static final String HELP_COMMAND_KILL = "Kill running instance on Yarn";

	private static final String HELP_OPTION_KILL_ID = "application id - as shown by Yarn resource manager or shell list command";

	private static final String HELP_COMMAND_LIST_SUBMITTED = "List application instances on Yarn";

	private static final String HELP_OPTION_LIST_SUBMITTED_VERBOSE = "verbose output";

	private static final String HELP_OPTION_LIST_SUBMITTED_TYPE = "application type";

	private static final String HELP_COMMAND_LIST_INSTALLED = "List installed applications from hdfs";

	private static final String HELP_COMMAND_CONFIG_DIR = "Sets a Spring Yarn applications base directory";

	@Override
	public ParseResult beforeInvocation(ParseResult invocationContext) {
		invocationContext = super.beforeInvocation(invocationContext);

		// we can always run command setting app base dir
		if (invocationContext.getMethod().getName().equals("configDir")) {
			return invocationContext;
		}

		// finds a default hdfs uri key name via reflection
		// to support old/new hadoop versions
		String defaultNameKey = (String) ReflectionUtils.getField(
				ReflectionUtils.findField(FileSystem.class, "FS_DEFAULT_NAME_KEY"), null);

		String fs = getConfiguration().get(defaultNameKey);
		String hdrm = getConfiguration().get("yarn.resourcemanager.address");
		String dir = getConfigurationProperties().getProperties().getProperty("spring.yarn.applicationsBaseDir");

		if (!StringUtils.hasText(fs) || !StringUtils.hasText(hdrm)) {
			log.error("You must set fs URL and rm address before running yarn commands");
			throw new RuntimeException("You must set fs URL and rm address before running yarn commands");
		}
		if (!StringUtils.hasText(dir)) {
			log.error("You must set applications base directory before running yarn commands");
			throw new RuntimeException("You must set applications base directory before running yarn commands");
		}

		return invocationContext;
	}

	@Override
	protected boolean configurationChanged() throws Exception {
		return true;
	}

	@Override
	protected boolean propertiesChanged() throws Exception {
		return true;
	}

	@CliCommand(value = COMMAND_CONFIG_DIR, help = HELP_COMMAND_CONFIG_DIR)
	public void configDir(@CliOption(key = OPTION_CONFIG_DIR, mandatory = true) String dir) {
		getConfigurationProperties().setProperty("spring.yarn.applicationsBaseDir", dir);
		publishConfigurationPropertiesChange();
	}

	/**
	 * Install a new application bundle into hdfs.
	 * 
	 * @param name the unique name identifier for the application bundle
	 * @return the status of an install operation
	 */
	@CliCommand(value = COMMAND_INSTALL, help = HELP_COMMAND_INSTALL)
	public String install(
			@CliOption(key = OPTION_INSTALL_ID, help = HELP_OPTION_INSTALL_ID, mandatory = true) String id,
			@CliOption(key = OPTION_INSTALL_FILES, help = HELP_OPTION_INSTALL_FILES) final String files,
			@CliOption(key = OPTION_INSTALL_OVERWRITE, help = HELP_OPTION_INSTALL_OVERWRITE, unspecifiedDefaultValue = "false", specifiedDefaultValue = "true") boolean overwrite) {
		Assert.state(StringUtils.hasText(id), "Id must be set");

		// TODO: check overwrite

		ArrayList<String> args = new ArrayList<String>();

		String[] paths = null;
		if (StringUtils.hasText(files)) {
			paths = files.split(",");
		}

		if (!ObjectUtils.isEmpty(paths)) {
			for (int i = 0; i < paths.length; i++) {
				args.add("--spring.yarn.client.files[" + i + "]=" + paths[i].trim());
			}
		}


		installApplication(new String[0], id,
				getConfigurationProperties().getProperties().getProperty("spring.yarn.applicationsBaseDir"),
				null, false, args.toArray(new String[0]));
		return "Successfully installed new application instance";
	}

	@CliCommand(value = COMMAND_SUBMIT, help = HELP_COMMAND_SUBMIT)
	public String submit(
			@CliOption(key = OPTION_SUBMIT_ID, help = HELP_OPTION_SUBMIT_ID, mandatory = true) String id)
			throws Exception {

		ApplicationId applicationId = submitApplication(new String[0], id);
		if (applicationId != null) {
			return "Submitted new application instance, application id is "
					+ applicationId.toString();
		}
		throw new IllegalArgumentException("Failed to submit new application instance.");
	}

	@CliCommand(value = COMMAND_KILL, help = HELP_COMMAND_KILL)
	public String kill(
			@CliOption(key = { "", OPTION_KILL_ID }, mandatory = true, help = HELP_OPTION_KILL_ID) final String id)
			throws Exception {
		ApplicationId applicationId = null;
		YarnClient yarnClient = getYarnClient();
		for (ApplicationReport a : yarnClient.listApplications()) {
			if (a.getApplicationId().toString().equals(id)) {
				applicationId = a.getApplicationId();
				break;
			}
		}
		if (applicationId != null) {
			yarnClient.killApplication(applicationId);
		}
		else {
			throw new IllegalArgumentException("Application id " + id + " not found.");
		}

		return "Succesfully send kill request for id " + id;
	}

	/**
	 * List submitted application instances known to Yarn resource manager.
	 * 
	 * @return The {@link Table} of known application instances.
	 * @throws Exception if error occurred
	 */
	@CliCommand(value = COMMAND_LIST_SUBMITTED, help = HELP_COMMAND_LIST_SUBMITTED)
	public String listSubmitted(
			@CliOption(key = OPTION_LIST_SUBMITTED_TYPE, help = HELP_OPTION_LIST_SUBMITTED_TYPE, unspecifiedDefaultValue = "BOOT") String type,
			@CliOption(key = OPTION_LIST_SUBMITTED_VERBOSE, help = HELP_OPTION_LIST_SUBMITTED_VERBOSE, unspecifiedDefaultValue = "false", specifiedDefaultValue = "true") boolean verbose)
			throws Exception {
		ArrayList<String> args = new ArrayList<String>();
		args.add("--spring.yarn.internal.YarnBootClientInfoApplication.operation=SUBMITTED");
		args.add("--spring.yarn.internal.YarnBootClientInfoApplication.verbose=" + verbose);
		args.add("--spring.yarn.internal.YarnBootClientInfoApplication.type=" + type);
		return new YarnBootClientInfoApplication().info("", null,
				getConfigurationProperties().getProperties(),
				getConfiguration(), args.toArray(new String[0]));
	}

	/**
	 * List installed applications available in hdfs.
	 * 
	 * @return The {@link Table} of installed applications.
	 * @throws Exception if error occurred
	 */
	@CliCommand(value = COMMAND_LIST_INSTALLED, help = HELP_COMMAND_LIST_INSTALLED)
	public String listInstalled() {
		ArrayList<String> args = new ArrayList<String>();
		args.add("--spring.yarn.internal.YarnBootClientInfoApplication.operation=INSTALLED");
		args.add("--spring.yarn.internal.YarnBootClientInfoApplication.type=BOOT");
		return new YarnBootClientInfoApplication().info("", null,
				getConfigurationProperties().getMergedProperties("foo"),
				getConfiguration(), args.toArray(new String[0]));
	}

}
