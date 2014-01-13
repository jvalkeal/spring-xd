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
import java.util.Map.Entry;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;

import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.shell.event.ParseResult;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.xd.shell.util.Table;
import org.springframework.yarn.app.bootclient.YarnBootClientApplication;
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

	private static final String COMMAND_LIST = PREFIX + "list";

	private static final String COMMAND_INSTALL = PREFIX + "install";

	private static final String COMMAND_SUBMIT = PREFIX + "submit";

	private static final String COMMAND_KILL = PREFIX + "kill";

	private static final String COMMAND_UNINSTALL = PREFIX + "uninstall";

	private static final String HELP_LIST = "List XD instances on Yarn";

	private static final String HELP_LIST_VERBOSE = "verbose output";

	private static final String HELP_SUBMIT = "Submit new application instance";

	private static final String HELP_SUBMIT_COUNT = "container count - as how many xd instances should be created";

	private static final String HELP_INSTALL = "Install application bundle into hdfs";

	private static final String HELP_UNINSTALL = "Uninstall application bundle from hdfs";

	private static final String HELP_KILL = "Kill running instance on Yarn";

	private static final String HELP_KILL_ID = "application id - as shown by Yarn resource manager or shell list command";

	@Override
	protected boolean configurationChanged() throws Exception {
		return true;
	}

	@Override
	protected boolean propertiesChanged() throws Exception {
		return true;
	}

	// @CliAvailabilityIndicator({ COMMAND_LIST, COMMAND_SUBMIT, COMMAND_KILL })
	// public boolean available() {
	// // we have yarn if YarnConfiguration class can be found
	// return ClassUtils.isPresent("org.apache.hadoop.yarn.conf.YarnConfiguration", getClass().getClassLoader());
	// }

	@Override
	public ParseResult beforeInvocation(ParseResult invocationContext) {
		invocationContext = super.beforeInvocation(invocationContext);
		String defaultNameKey = (String) ReflectionUtils.getField(
				ReflectionUtils.findField(FileSystem.class, "FS_DEFAULT_NAME_KEY"), null);
		String fs = getConfiguration().get(defaultNameKey);
		String hdrm = getConfiguration().get("yarn.resourcemanager.address");

		if (StringUtils.hasText(fs) && StringUtils.hasText(hdrm)) {
			return invocationContext;
		}
		else {
			log.error("You must set fs URL and rm address before running yarn commands");
			throw new RuntimeException("You must set fs URL and rm address before running yarn commands");
		}
	}

	@CliCommand(value = COMMAND_INSTALL, help = HELP_INSTALL)
	public String install() {
		return "";
	}

	@CliCommand(value = COMMAND_UNINSTALL, help = HELP_UNINSTALL)
	public String uninstall() {
		return "";
	}

	/**
	 * Submits new Spring XD instance to Hadoop Yarn.
	 * 
	 * @return the Command message
	 * @throws Exception if error occurred
	 */
	@CliCommand(value = COMMAND_SUBMIT, help = HELP_SUBMIT)
	public String submit(
			@CliOption(key = "count", help = HELP_SUBMIT_COUNT, unspecifiedDefaultValue = "1") String count)
			throws Exception {

		int c = Integer.parseInt(count);
		if (c < 1 || c > 10) {
			throw new IllegalArgumentException("Illegal container count [" + c + "]");
		}

		ApplicationId applicationId = deployViaClientApplication(count);
		if (applicationId != null) {
			return "Submitted new XD instance with " + count + " containers, application id is "
					+ applicationId.toString();
		}
		throw new IllegalArgumentException("Failed to submit new application instance.");
	}

	/**
	 * Command listing application instances known to Yarn resource manager.
	 * 
	 * @param id the application to be killed
	 * @return the Command message
	 * @throws Exception if error occurred
	 */
	@CliCommand(value = COMMAND_KILL, help = HELP_KILL)
	public String kill(@CliOption(key = { "", "id" }, mandatory = true, help = HELP_KILL_ID) final String id)
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

		return "Killed app " + id;
	}

	/**
	 * Command listing known application instances known to Yarn resource manager.
	 * 
	 * @return The {@link Table} of known application instances.
	 * @throws Exception if error occurred
	 */
	@CliCommand(value = COMMAND_LIST, help = HELP_LIST)
	public Table list(
			@CliOption(key = "verbose", help = HELP_LIST_VERBOSE, unspecifiedDefaultValue = "false", specifiedDefaultValue = "true") boolean verbose)
			throws Exception {

		return new AppReportBuilder().add(Field.ID, Field.USER, Field.NAME, Field.QUEUE, Field.TYPE, Field.STARTTIME,
				Field.FINISHTIME, Field.STATE, Field.FINALSTATUS).sort(Field.ID).build(
				getYarnClient().listApplications());
	}

	private ApplicationId deployViaClientApplication(String count) {
		ArrayList<String> args = new ArrayList<String>();

		for (Entry<Object, Object> entry : getProperties().entrySet()) {
			args.add("--" + entry.getKey() + "=" + entry.getValue());
		}

		args.add("--spring.yarn.appmaster.containerCount=" + count);

		return new YarnBootClientApplication().deploy(args.toArray(new String[0]), getConfiguration());
	}

}
