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

package org.springframework.xd.shell.command;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

import org.springframework.xd.shell.command.fixtures.FileSink;
import org.springframework.xd.shell.command.fixtures.HttpSource;


/**
 * Shell integration tests for various simple processor modules.
 * 
 * @author Eric Bottard
 */
public class ProcessorsTests extends AbstractStreamIntegrationTest {

	@Test
	public void splitterDoesNotSplitByDefault() throws Exception {
		HttpSource httpSource = newHttpSource();

		stream().create("splitter-test", "%s | splitter | counter --name=%s", httpSource, DEFAULT_METRIC_NAME);

		httpSource.ensureReady().postData("Hello World !");
		counter().verifyCounter("1");

	}

	@Test
	public void splitterDoesSplit() {
		HttpSource httpSource = newHttpSource();

		stream().create("splitter-test", "%s | splitter --expression=payload.split(' ') | counter --name=%s",
				httpSource, DEFAULT_METRIC_NAME);

		httpSource.ensureReady().postData("Hello World !");
		counter().verifyCounter("3");

	}

	@Test
	public void testAggregatorNormalRelease() throws IOException {
		HttpSource httpSource = newHttpSource();
		FileSink fileSink = newFileSink().binary(true);

		stream().create(
				"aggtest",
				"%s | aggregator --count=3 --aggregation=T(org.springframework.util.StringUtils).collectionToDelimitedString(#this.![payload],' ') | %s",
				httpSource, fileSink);

		httpSource.ensureReady().postData("Hello").postData("World").postData("!");

		String result = fileSink.getContents();
		assertEquals("Hello World !", result);

	}

	@Test
	public void testAggregatorEarlyRelease() throws IOException {
		HttpSource httpSource = newHttpSource();
		FileSink fileSink = newFileSink().binary(true);

		int timeout = 1000;

		stream().create(
				"aggtest",
				"%s | aggregator --count=100 --timeout=%d --aggregation=T(org.springframework.util.StringUtils).collectionToDelimitedString(#this.![payload],' ') | %s",
				httpSource, timeout, fileSink);

		httpSource.ensureReady().postData("Hello").postData("World").postData("!");

		// The reaper and the task scheduler are both configured with 'timeout'
		// so in the worst case, it can take 2*timeout to actually flush the msgs
		String result = fileSink.getContents((int) (2.1 * timeout));
		assertEquals("Hello World !", result);

	}
}
