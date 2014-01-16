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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationReport;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.shell.core.ExecutionProcessor;
import org.springframework.shell.event.ParseResult;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.xd.shell.hadoop.ConfigurationModifiedEvent;
import org.springframework.xd.shell.properties.ConfigurationPropertiesModifiedEvent;
import org.springframework.xd.shell.properties.SpringConfigurationProperties;
import org.springframework.xd.shell.util.Table;
import org.springframework.xd.shell.util.TableHeader;
import org.springframework.xd.shell.util.TableRow;
import org.springframework.yarn.app.bootclient.YarnBootClientInstallApplication;
import org.springframework.yarn.app.bootclient.YarnBootClientSubmitApplication;
import org.springframework.yarn.client.YarnClient;
import org.springframework.yarn.client.YarnClientFactoryBean;

/**
 * Base support class for shell commands working with Spring Yarn and handling Spring Boot based applications on Yarn.
 * 
 * @author Janne Valkealahti
 * 
 */
public abstract class YarnCommandsSupport implements ApplicationListener<ApplicationEvent>, ExecutionProcessor {

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
	protected void installApplication(String[] profiles, String id, String[] args) {
		Assert.state(StringUtils.hasText(id), "Id must be set");
		new YarnBootClientInstallApplication().install(id, profiles,
				getConfigurationProperties().getMergedProperties(id),
				getConfiguration(), args);
	}

	/**
	 * Submit application into Yarn.
	 * 
	 * @param id the unique identifier
	 * @param count the count
	 * @return the application id
	 */
	protected ApplicationId submitApplication(String[] profiles, String id) {
		ArrayList<String> args = new ArrayList<String>();

		for (Entry<Object, Object> entry : getConfigurationProperties().getMergedProperties(id).entrySet()) {
			args.add("--" + entry.getKey() + "=" + entry.getValue());
		}
		return new YarnBootClientSubmitApplication().submit(profiles, getConfiguration(), args.toArray(new String[0]));
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
	 * Builds a new {@link YarnClient} for generic application status queries.
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

	/**
	 * Utility class helping to build shell tables for yarn application reports.
	 */
	protected static class AppReportBuilder {

		private ArrayList<Field> fields = new ArrayList<Field>();

		private Field sort;

		public AppReportBuilder() {
		}

		public AppReportBuilder add(Field f) {
			fields.add(f);
			return this;
		}

		public AppReportBuilder add(Field... f) {
			for (Field ff : f) {
				fields.add(ff);
			}
			return this;
		}

		public AppReportBuilder sort(Field f) {
			sort = f;
			return this;
		}

		public Table build(List<ApplicationReport> reports) {
			if (sort != null) {
				Collections.sort(reports, new ApplicationReportComparator(sort));
			}
			Table table = new Table();
			addHeader(table);
			for (ApplicationReport report : reports) {
				addRow(table, report);
			}
			return table;
		}

		private void addHeader(Table table) {
			int index = 1;
			for (Field f : fields) {
				table.addHeader(index++, new TableHeader(f.getName()));
			}
		}

		private void addRow(Table table, ApplicationReport report) {
			final TableRow row = new TableRow();
			int index = 1;
			for (Field f : fields) {
				if (Field.ID == f) {
					row.addValue(index++, report.getApplicationId().toString());
				}
				else if (Field.USER == f) {
					row.addValue(index++, report.getUser());
				}
				else if (Field.NAME == f) {
					row.addValue(index++, report.getName());
				}
				else if (Field.QUEUE == f) {
					row.addValue(index++, report.getQueue());
				}
				else if (Field.TYPE == f) {
					row.addValue(index++, report.getApplicationType());
				}
				else if (Field.STARTTIME == f) {
					row.addValue(index++, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(
							new Date(report.getStartTime())));
				}
				else if (Field.FINISHTIME == f) {
					long time = report.getFinishTime();
					String value = time > 0 ? DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(
							new Date(time))
							: "N/A";
					row.addValue(index++, value);
				}
				else if (Field.STATE == f) {
					row.addValue(index++, report.getYarnApplicationState().toString());
				}
				else if (Field.FINALSTATUS == f) {
					row.addValue(index++, report.getFinalApplicationStatus().toString());
				}
				else if (Field.XDADMINURL == f) {
					String url = report.getYarnApplicationState() == YarnApplicationState.RUNNING ? report.getOriginalTrackingUrl()
							: "";
					row.addValue(index++, url);
				}
				else if (Field.TRACKURL == f) {
					row.addValue(index++, report.getTrackingUrl());
				}
			}
			table.getRows().add(row);
		}
	}

	/**
	 * Enums for ApplicationReport fields.
	 */
	protected static enum Field {
		ID("APPLICATION ID"),
		USER,
		NAME,
		QUEUE,
		TYPE,
		STARTTIME,
		FINISHTIME,
		STATE,
		FINALSTATUS,
		XDADMINURL("XD ADMIN URL"),
		TRACKURL("TRACKING URL");

		private String name;

		private Field() {
		}

		private Field(String name) {
			this.name = name;
		}

		protected String getName() {
			return StringUtils.hasText(name) ? name : this.toString();
		}
	}

	/**
	 * Sort comparator for ApplicationReport.
	 */
	private static class ApplicationReportComparator implements Comparator<ApplicationReport> {

		private final Field f;

		private ApplicationReportComparator(Field f) {
			this.f = f;
		}

		@Override
		public int compare(ApplicationReport l, ApplicationReport r) {
			if (Field.ID == f) {
				return -(l.getApplicationId().toString().compareTo(r.getApplicationId().toString()));
			}
			else if (Field.USER == f) {
				return l.getUser().compareTo(r.getUser());
			}
			else {
				return 0;
			}
		}

	}

}
