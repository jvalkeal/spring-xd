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

package org.springframework.xd.shell.hadoop;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;

import org.springframework.shell.core.ExecutionProcessor;
import org.springframework.shell.core.annotation.CliAvailabilityIndicator;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.shell.event.ParseResult;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.xd.shell.util.Table;
import org.springframework.xd.shell.util.TableHeader;
import org.springframework.xd.shell.util.TableRow;
import org.springframework.xd.yarn.app.xd.client.XdClientApplication;
import org.springframework.yarn.client.YarnClient;
import org.springframework.yarn.client.YarnClientFactoryBean;

/**
 * Shell integration providing control commands for Spring XD on Hadoop Yarn.
 * <p>
 * XD on Yarn command set follows logic of:
 * <ul>
 * <li>Submit new XD application instance</li>
 * <li>Query existing instances</li>
 * <li>Kill running instances</li>
 * </ul>
 * 
 * @author Janne Valkealahti
 * 
 */
@Component
public class YarnCommands extends ConfigurationAware implements ExecutionProcessor {

	private static final String PREFIX = "yarn ";

	private static final String COMMAND_LIST = PREFIX + "list";

	private static final String COMMAND_SUBMIT = PREFIX + "submit";

	private static final String COMMAND_KILL = PREFIX + "kill";

	private static final String HELP_LIST = "List XD instances on Yarn";

	private static final String HELP_LIST_VERBOSE = "verbose output";

	private static final String HELP_SUBMIT = "Submit new XD instance to Yarn";

	private static final String HELP_SUBMIT_COUNT = "container count - as how many xd instances should be created";

	private static final String HELP_SUBMIT_REDISHOST = "Redis host - if used, default localhost";

	private static final String HELP_SUBMIT_REDISPORT = "Redis port - if used, default 6379";

	private static final String HELP_KILL = "Kill running XD instance on Yarn";

	private static final String HELP_KILL_ID = "application id - as shown by Yarn resource manager or shell list command";

	@Override
	protected boolean configurationChanged() throws Exception {
		return true;
	}

	@CliAvailabilityIndicator({ COMMAND_LIST, COMMAND_SUBMIT, COMMAND_KILL })
	public boolean available() {
		// we have yarn if YarnConfiguration class can be found
		return ClassUtils.isPresent("org.apache.hadoop.yarn.conf.YarnConfiguration", getClass().getClassLoader());
	}

	@Override
	public ParseResult beforeInvocation(ParseResult invocationContext) {
		invocationContext = super.beforeInvocation(invocationContext);
		String defaultNameKey = (String) ReflectionUtils.getField(
				ReflectionUtils.findField(FileSystem.class, "FS_DEFAULT_NAME_KEY"), null);
		String fs = getHadoopConfiguration().get(defaultNameKey);
		String hdrm = getHadoopConfiguration().get("yarn.resourcemanager.address");

		if (StringUtils.hasText(fs) && StringUtils.hasText(hdrm)) {
			return invocationContext;
		}
		else {
			LOG.severe("You must set fs URL and rm address before running yarn commands");
			throw new RuntimeException("You must set fs URL and rm address before running yarn commands");
		}
	}

	/**
	 * Submits new Spring XD instance to Hadoop Yarn.
	 * 
	 * @return the Command message
	 * @throws Exception if error occurred
	 */
	@CliCommand(value = COMMAND_SUBMIT, help = HELP_SUBMIT)
	public String submit(
			@CliOption(key = "count", help = HELP_SUBMIT_COUNT, unspecifiedDefaultValue = "1") String count,
			@CliOption(key = "redisHost", help = HELP_SUBMIT_REDISHOST, specifiedDefaultValue = "localhost") String redisHost,
			@CliOption(key = "redisPort", help = HELP_SUBMIT_REDISPORT, specifiedDefaultValue = "6379") String redisPort)
			throws Exception {

		int c = Integer.parseInt(count);
		if (c < 1 || c > 10) {
			throw new IllegalArgumentException("Illegal container count [" + c + "]");
		}

		ApplicationId applicationId = deployViaXdClientApplication(count, redisHost, redisPort);
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
		Table table = new Table();
		table.addHeader(1, new TableHeader("Id"))
				.addHeader(2, new TableHeader("User"))
				.addHeader(3, new TableHeader("Name"))
				.addHeader(4, new TableHeader("Queue"))
				.addHeader(5, new TableHeader("Type"))
				.addHeader(6, new TableHeader("StartTime"))
				.addHeader(7, new TableHeader("FinishTime"))
				.addHeader(8, new TableHeader("State"))
				.addHeader(9, new TableHeader("FinalStatus"))
				.addHeader(10, new TableHeader("XD Admin"));

		for (ApplicationReport a : getYarnClient().listApplications()) {

			String xdAdminUrl = "";
			if (a.getYarnApplicationState() == YarnApplicationState.RUNNING) {
				xdAdminUrl = a.getOriginalTrackingUrl();
			}

			YarnApplicationState applicationState = a.getYarnApplicationState();
			if (verbose
					|| (applicationState != YarnApplicationState.FAILED
							&& applicationState != YarnApplicationState.FINISHED
							&& applicationState != YarnApplicationState.KILLED)) {
				final TableRow row = new TableRow();
				row.addValue(1, a.getApplicationId().toString())
						.addValue(2, a.getUser())
						.addValue(3, a.getName())
						.addValue(4, a.getQueue())
						.addValue(5, a.getApplicationType())
						.addValue(6, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(
								new Date(a.getStartTime())))
						.addValue(7, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(
								new Date(a.getFinishTime())))
						.addValue(8, a.getYarnApplicationState().toString())
						.addValue(9, a.getFinalApplicationStatus().toString())
						.addValue(10, xdAdminUrl);
				table.getRows().add(row);
			}

		}
		return table;
	}

	/**
	 * Builds a new {@link YarnClient}.
	 * 
	 * @return the {@link YarnClient}
	 * @throws Exception if error occurred
	 */
	private YarnClient getYarnClient() throws Exception {
		YarnClientFactoryBean factory = new YarnClientFactoryBean();
		factory.setConfiguration(getHadoopConfiguration());
		factory.afterPropertiesSet();
		return factory.getObject();
	}

	private ApplicationId deployViaXdClientApplication(String count, String redisHost, String redisPort) {
		ArrayList<String> args = new ArrayList<String>();

		args.add("--spring.yarn.appmaster.containerCount=" + count);
		if (StringUtils.hasText(redisHost)) {
			args.add("--spring.redis.host=" + redisHost);
		}
		if (StringUtils.hasText(redisPort)) {
			// bail out with error if not valid port
			int port = Integer.parseInt(redisPort);
			if (port < 1 || port > 65535) {
				throw new RuntimeException("Port " + port + " not valid");
			}
			args.add("--spring.redis.port=" + redisPort);
		}

		return new XdClientApplication().deploy(args.toArray(new String[0]), getHadoopConfiguration());
	}

}
