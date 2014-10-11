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

package org.springframework.xd.dirt.container.store;

import java.util.ArrayList;
import java.util.List;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.xd.dirt.cluster.ContainerRuntime;
import org.springframework.xd.dirt.cluster.NoSuchContainerException;
import org.springframework.xd.dirt.zookeeper.Paths;
import org.springframework.xd.dirt.zookeeper.ZooKeeperConnection;
import org.springframework.xd.dirt.zookeeper.ZooKeeperUtils;

/**
 * ZooKeeper backed repository for runtime info about Admins.
 *
 * @author Janne Valkealahti
 */
public class ZooKeeperRuntimeRepository implements RuntimeRepository {

	/**
	 * ZooKeeper connection.
	 */
	private final ZooKeeperConnection zkConnection;

	/**
	 * Construct a {@code ZooKeeperRuntimeRepository}.
	 *
	 * @param zkConnection the ZooKeeper connection
	 */
	@Autowired
	public ZooKeeperRuntimeRepository(ZooKeeperConnection zkConnection) {
		this.zkConnection = zkConnection;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void save(ContainerRuntime entity) {
		CuratorFramework client = zkConnection.getClient();
		String path = Paths.build(Paths.ADMINS, entity.getName());

		try {
			client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL)
					.forPath(path, ZooKeeperUtils.mapToBytes(entity.getAttributes()));
		}
		catch (Exception e) {
			throw ZooKeeperUtils.wrapThrowable(e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void update(ContainerRuntime entity) {
		CuratorFramework client = zkConnection.getClient();
		String path = Paths.build(Paths.ADMINS, entity.getName());

		try {
			Stat stat = client.checkExists().forPath(path);
			if (stat == null) {
				throw new NoSuchContainerException("Could not find admin with id " + entity.getName());
			}
			client.setData().forPath(path, ZooKeeperUtils.mapToBytes(entity.getAttributes()));
		}
		catch (Exception e) {
			throw ZooKeeperUtils.wrapThrowable(e, e.getMessage());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean exists(String id) {
		try {
			return null != zkConnection.getClient().checkExists()
					.forPath(path(id));
		}
		catch (Exception e) {
			throw ZooKeeperUtils.wrapThrowable(e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<ContainerRuntime> findAll() {
		List<ContainerRuntime> results = new ArrayList<ContainerRuntime>();
		try {
			List<String> children = zkConnection.getClient().getChildren().forPath(Paths.build(Paths.ADMINS));
			for (String child : children) {
				byte[] data = zkConnection.getClient().getData().forPath(
						Paths.build(Paths.ADMINS, child));
				if (data != null && data.length > 0) {
					results.add(new ContainerRuntime(child, ZooKeeperUtils.bytesToMap(data)));
				}
			}

		}
		catch (Exception e) {
			throw ZooKeeperUtils.wrapThrowable(e);
		}
		return results;
	}

	/**
	 * Return the path for a container runtime.
	 *
	 * @param id container runtime id
	 * @return path for the container
	 * @see Paths#build
	 */
	private String path(String id) {
		return Paths.build(Paths.ADMINS, id);
	}

}
