/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.xd.yarn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import org.springframework.util.StringUtils;
import org.springframework.yarn.boot.app.YarnInfoApplication;
import org.springframework.yarn.boot.app.YarnInstallApplication;
import org.springframework.yarn.boot.app.YarnKillApplication;
import org.springframework.yarn.boot.app.YarnSubmitApplication;

/**
 * Dispatcher for client-side commands.
 * 
 * @author Thomas Risberg
 * @author Janne Valkealahti
 */
public class ClientApp {

	@Option(name = "--operation", required = true)
	private Operation operation;

	private void doMain(String[] args) {
		CmdLineParser parser = new CmdLineParser(this);

		// we control arguments so it's safe to assume that
		// first two are --operation and xxx, rest
		// are passed to actual running application
		// and appArgs are parsed again.
		List<String> mainArgs = Arrays.asList(Arrays.copyOfRange(args, 0, Math.min(2, args.length)));
		List<String> appArgs = new ArrayList<String>(Arrays.asList(Arrays.copyOfRange(args, Math.min(2, args.length),
				args.length)));

		// get operation to know what to do
		try {
			parser.parseArgument(mainArgs);
		}
		catch (CmdLineException e) {
			// we should not get here
			System.err.println(e.getMessage());
			parser.printUsage(System.err);
		}

		// run actual app based on given operation
		try {
			if (Operation.INSTALL.equals(operation)) {
				InstallOptions options = new InstallOptions();
				parser = new CmdLineParser(options);
				parser.parseArgument(appArgs);
				if (options.help) {
					parser.printUsage(System.out);
				}
				else {
					doInstall(options, appArgs);
				}
			}
			else if (Operation.SUBMIT.equals(operation)) {
				SubmitOptions options = new SubmitOptions();
				parser = new CmdLineParser(options);
				parser.parseArgument(appArgs);
				if (options.help) {
					parser.printUsage(System.out);
				}
				else {
					doSubmit(options, appArgs);
				}
			}
			else if (Operation.KILL.equals(operation)) {
				KillOptions options = new KillOptions();
				parser = new CmdLineParser(options);
				parser.parseArgument(appArgs);
				if (options.help) {
					parser.printUsage(System.out);
				}
				else {
					doKill(options, appArgs);
				}
			}
			else if (Operation.LISTSUBMITTED.equals(operation)) {
				ListSubmittedOptions options = new ListSubmittedOptions();
				parser = new CmdLineParser(options);
				parser.parseArgument(appArgs);
				if (options.help) {
					parser.printUsage(System.out);
				}
				else {
					doListSubmitted(options, appArgs);
				}
			}
			else if (Operation.LISTINSTALLED.equals(operation)) {
				ListInstalledOptions options = new ListInstalledOptions();
				parser = new CmdLineParser(options);
				parser.parseArgument(appArgs);
				if (options.help) {
					parser.printUsage(System.out);
				}
				else {
					doListInstalled(appArgs);
				}
			}
			else {
				throw new IllegalArgumentException("Operation " + operation + " not valid");
			}
		}
		catch (CmdLineException e) {
			parser.printUsage(System.err);
			System.err.println();
			System.exit(1);
		}
		catch (Exception e) {
			Throwable rootCause = ExceptionUtils.getRootCause(e);
			if (rootCause != null && StringUtils.hasText(rootCause.getMessage())) {
				System.err.println("Command failed: " + rootCause.getMessage());
			}
			else {
				// something we didn't expect
				// so print out stack trace
				e.printStackTrace();
			}
			System.exit(1);
		}
	}

	private void doInstall(InstallOptions options, List<String> appArgs) {
		YarnInstallApplication app = new YarnInstallApplication();
		app.instanceId(options.instanceId);

		Properties instanceProperties = new Properties();
		instanceProperties.setProperty("spring.yarn.applicationId", options.instanceId);
		app.configFile("application.properties", instanceProperties);

		app.run(appArgs.toArray(new String[0]));
		System.out.println("New instance " + options.instanceId + " installed");
	}

	private void doSubmit(SubmitOptions options, List<String> appArgs) {
		YarnSubmitApplication app = new YarnSubmitApplication();
		app.instanceId(options.instanceId);
		ApplicationId applicationId = app.run(appArgs.toArray(new String[0]));
		System.out.println("New instance submitted with id " + applicationId);
	}

	private void doKill(KillOptions options, List<String> appArgs) {
		YarnKillApplication app = new YarnKillApplication();
		appArgs.add("--spring.yarn.internal.YarnKillApplication.applicationId=" + options.applicationId);
		String info = app.run(appArgs.toArray(new String[0]));
		System.out.println(info);
	}

	private void doListInstalled(List<String> appArgs) {
		YarnInfoApplication app = new YarnInfoApplication();
		appArgs.add("--spring.yarn.internal.YarnInfoApplication.operation=INSTALLED");
		String info = app.run(appArgs.toArray(new String[0]));
		System.out.println(info);
	}

	private void doListSubmitted(ListSubmittedOptions options, List<String> appArgs) {
		YarnInfoApplication app = new YarnInfoApplication();
		appArgs.add("--spring.yarn.internal.YarnInfoApplication.operation=SUBMITTED");
		if (options.verbose) {
			appArgs.add("--spring.yarn.internal.YarnInfoApplication.verbose=true");
		}
		if (StringUtils.hasText(options.type)) {
			appArgs.add("--spring.yarn.internal.YarnInfoApplication.type=" + options.type);
		}
		else {
			appArgs.add("--spring.yarn.internal.YarnInfoApplication.type=XD");
		}
		String info = app.run(appArgs.toArray(new String[0]));
		System.out.println(info);
	}

	public static void main(String[] args) {
		new ClientApp().doMain(args);
	}

	private static class InstallOptions {

		@Option(name = "-h", aliases = { "--help" }, usage = "Print this help")
		private boolean help;

		@Option(name = "-i", aliases = { "--instance-id" }, usage = "Instance id of the application, defaults to 'app'")
		private String instanceId = "app";
	}

	private static class SubmitOptions {

		@Option(name = "-h", aliases = { "--help" }, usage = "Print this help")
		private boolean help;

		@Option(name = "-i", aliases = { "--instance-id" }, usage = "Instance id of the application, defaults to 'app'")
		private String instanceId = "app";
	}

	private static class KillOptions {

		@Option(name = "-h", aliases = { "--help" }, usage = "Print this help")
		private boolean help;

		@Option(name = "-a", aliases = { "--application-id" }, usage = "Yarn Application id")
		private String applicationId;
	}

	private static class ListSubmittedOptions {

		@Option(name = "-h", aliases = { "--help" }, usage = "Print this help")
		private boolean help;

		@Option(name = "-v", aliases = { "--verbose" }, usage = "Verbose output")
		private boolean verbose;

		@Option(name = "-t", aliases = { "--type" }, usage = "Yarn application type")
		private String type;
	}

	private static class ListInstalledOptions {

		@Option(name = "-h", aliases = { "--help" }, usage = "Print this help")
		private boolean help;
	}

	private enum Operation {
		INSTALL,
		SUBMIT,
		KILL,
		LISTINSTALLED,
		LISTSUBMITTED
	}

}
