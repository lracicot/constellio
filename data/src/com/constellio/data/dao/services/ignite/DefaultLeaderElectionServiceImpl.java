package com.constellio.data.dao.services.ignite;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCluster;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.internal.util.typedef.internal.CU;
import org.apache.ignite.internal.util.typedef.internal.U;

import com.constellio.data.dao.services.cache.ConstellioCacheManager;
import com.constellio.data.dao.services.cache.ignite.ConstellioIgniteCacheManager;
import com.constellio.data.dao.services.factories.DataLayerFactory;

public class DefaultLeaderElectionServiceImpl implements LeaderElectionService {

	DataLayerFactory dataLayerFactory;

	public DefaultLeaderElectionServiceImpl(DataLayerFactory dataLayerFactory) {
		this.dataLayerFactory = dataLayerFactory;
	}

	@Override
	public boolean isCurrentNodeLeader() {
		ConstellioCacheManager constellioCacheManager = dataLayerFactory.getSettingsCacheManager();

		if (constellioCacheManager instanceof ConstellioIgniteCacheManager) {
			return isCurrentNodeLeader(((ConstellioIgniteCacheManager) constellioCacheManager).getClient());
		} else {
			return true;
		}

	}

	public boolean isCurrentNodeLeader(Ignite ignite) {
		IgniteCluster cluster = ignite.cluster();

		Collection<ClusterNode> clients = new ArrayList<>();

		Collection<ClusterNode> all = cluster.nodes();
		for (ClusterNode clusterNode : all) {
			if (CU.clientNode(clusterNode)) {
				clients.add(clusterNode);
			}
		}

		ClusterNode oldest = U.oldest(clients, null);

		ClusterNode localNode = cluster.localNode();
		return localNode.id().equals(oldest.id());
	}

}