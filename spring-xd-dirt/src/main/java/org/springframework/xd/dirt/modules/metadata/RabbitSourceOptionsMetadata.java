/*
 * Copyright 2013 the original author or authors.
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

package org.springframework.xd.dirt.modules.metadata;

import org.springframework.http.MediaType;
import org.springframework.xd.module.options.spi.ModuleOption;

/**
 * Describes options to the {@code rabbit} source module.
 * 
 * @author Eric Bottard
 */
public class RabbitSourceOptionsMetadata extends AbstractRabbitConnectionOptionsMetadata {

	private String queues;

	public String getQueues() {
		return queues;
	}


	@ModuleOption("the queue(s) from which messages will be received")
	public void setQueues(String queues) {
		this.queues = queues;
	}


	// Adding those back as we can't inherit from multiple classes.
	// Will go away when XD-1050 is done
	private MediaType outputType;

	public MediaType getOutputType() {
		return outputType;
	}


	@ModuleOption("how this module should emit messages it produces")
	public void setOutputType(MediaType outputType) {
		this.outputType = outputType;
	}


}
