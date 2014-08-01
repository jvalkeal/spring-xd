/*
 * Copyright 2014 the original author or authors.
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

package org.springframework.xd.yarn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.yarn.am.cluster.ContainerCluster;
import org.springframework.yarn.am.cluster.ManagedContainerClusterAppmaster;

/**
 * Custom application master handling XD containers grouping setting
 * for launch commands.
 *
 * @author Janne Valkealahti
 */
public class XdAppmaster extends ManagedContainerClusterAppmaster {

	@Override
	protected List<String> onContainerLaunchCommands(ContainerCluster cluster, List<String> orig) {
		ArrayList<String> list = new ArrayList<String>();
		Map<String, Object> extraProperties = cluster.getExtraProperties();
		for (String command : orig) {
			if (command.contains("xd.container.groups")) {
				if (extraProperties != null && extraProperties.containsKey("containerGroups")) {
					list.add("-Dxd.container.groups=" + cluster.getExtraProperties().get("containerGroups"));
				}
				else {
					list.add(command);
				}
			}
			else {
				list.add(command);
			}
		}
		return list;
	}

}
