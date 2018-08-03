package com.constellio.data.dao.services.cache.serialization;

import com.constellio.data.conf.DataLayerConfiguration;
import com.constellio.data.dao.services.cache.ConstellioCache;
import com.constellio.data.dao.services.cache.ConstellioCacheManager;
import com.constellio.data.dao.services.cache.ConstellioCacheManagerRuntimeException.ConstellioCacheManagerRuntimeException_CacheAlreadyExist;
import com.constellio.data.dao.services.cache.ConstellioCacheOptions;

import java.util.*;

public class SerializationCheckCacheManager implements ConstellioCacheManager {

	private Map<String, ConstellioCache> caches = new HashMap<>();

	public SerializationCheckCacheManager(DataLayerConfiguration dataLayerConfiguration) {
	}

	@Override
	public List<String> getCacheNames() {
		return Collections.unmodifiableList(new ArrayList<>(caches.keySet()));
	}

	@Override
	public synchronized ConstellioCache getCache(String name) {
		ConstellioCache cache = caches.get(name);
		if (cache == null) {
			cache = new SerializationCheckCache(name, new ConstellioCacheOptions());
			caches.put(name, cache);
		}
		return cache;
	}

	@Override
	public ConstellioCache createCache(String name, ConstellioCacheOptions options) {
		ConstellioCache cache = caches.get(name);
		if (cache == null) {
			cache = new SerializationCheckCache(name, options);
			caches.put(name, cache);
		} else {
			throw new ConstellioCacheManagerRuntimeException_CacheAlreadyExist(name);
		}
		return cache;
	}

	@Override
	public void initialize() {
	}

	@Override
	public void close() {
	}

	@Override
	public void clearAll() {
		for (ConstellioCache cache : caches.values()) {
			cache.clear();
		}
	}

}
