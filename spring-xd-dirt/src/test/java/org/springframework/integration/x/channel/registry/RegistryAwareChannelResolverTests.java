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

package org.springframework.integration.x.channel.registry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import org.springframework.context.support.StaticApplicationContext;
import org.springframework.integration.Message;
import org.springframework.integration.MessageChannel;
import org.springframework.integration.MessagingException;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageHandler;
import org.springframework.integration.scheduling.PollerMetadata;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * @author Mark Fisher
 */
public class RegistryAwareChannelResolverTests {

	@Test
	public void test() {
		StaticApplicationContext context = new StaticApplicationContext();
		context.registerSingleton("channelResolver", RegistryAwareChannelResolver.class);
		context.registerSingleton("channelRegistry", LocalChannelRegistry.class);
		context.registerSingleton("other", DirectChannel.class);
		context.registerSingleton("taskScheduler", ThreadPoolTaskScheduler.class);
		context.refresh();
		RegistryAwareChannelResolver resolver = context.getBean(RegistryAwareChannelResolver.class);
		MessageChannel registered = resolver.resolveChannelName(":foo");
		LocalChannelRegistry registry = context.getBean(LocalChannelRegistry.class);
		PollerMetadata poller = new PollerMetadata();
		poller.setTrigger(new PeriodicTrigger(1000));
		registry.setPoller(poller);
		DirectChannel testChannel = new DirectChannel();
		final CountDownLatch latch = new CountDownLatch(1);
		final List<Message<?>> received = new ArrayList<Message<?>>();
		testChannel.subscribe(new MessageHandler() {

			@Override
			public void handleMessage(Message<?> message) throws MessagingException {
				received.add(message);
				latch.countDown();
			}
		});
		registry.createInbound("foo", testChannel, null, true);
		MessageChannel other = resolver.resolveChannelName("other");
		assertSame(context.getBean("other"), other);
		assertEquals(0, received.size());
		registered.send(MessageBuilder.withPayload("hello").build());
		try {
			assertTrue("latch timed out", latch.await(1, TimeUnit.SECONDS));
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			fail("interrupted while awaiting latch");
		}
		assertEquals("hello", received.get(0).getPayload());
		context.close();
	}

}
