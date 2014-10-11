/*
 * Copyright 2013-2014 the original author or authors.
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

package org.springframework.xd.dirt.container.store;

import java.util.List;

import org.springframework.xd.dirt.cluster.Container;
import org.springframework.xd.dirt.cluster.ContainerRuntime;

/**
 * Repository for persisting {@link Container} entities for admins.
 *
 * @author Ilayaperumal Gopinathan
 * @author Janne Valkealahti
 */
public interface RuntimeRepository {

	/**
	 * Update the container attributes.
	 *
	 * @param container the container entity
	 */
	void update(ContainerRuntime container);

	void save(ContainerRuntime container);

	List<ContainerRuntime> findAll();

	boolean exists(String id);

}
