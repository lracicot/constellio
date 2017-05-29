package com.constellio.data.dao.services.cache.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.constellio.data.conf.DataLayerConfiguration;
import com.constellio.data.dao.services.cache.ConstellioCacheManager;
import com.constellio.data.dao.services.cache.ConstellioCache;

public class ConstellioMapCacheManager implements ConstellioCacheManager {
	
	private Map<String, ConstellioCache> caches = new HashMap<>();

	public ConstellioMapCacheManager(DataLayerConfiguration dataLayerConfiguration) {
	}

	@Override
	public List<String> getCacheNames() {
		return Collections.unmodifiableList(new ArrayList<>(caches.keySet()));
	}

	@Override
	public synchronized ConstellioCache getCache(String name) {
		ConstellioCache cache = caches.get(name);
		if (cache == null) {
			cache = new ConstellioMapCache(name);
			caches.put(name, cache);
		}
		return cache;
	}

}
