package com.constellio.data.io.services.facades;

import com.constellio.data.utils.ImpossibleRuntimeException;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.HashMap;
import java.util.Map;

public class OpenedResourcesWatcher {

	public static boolean enabled = true;

	public static boolean logStackTraces = false;

	public static String openingStackHeader = "Where the resource was opened";

	static Map<String, String> openedResourcesOpeningStack = new HashMap<String, String>();

	static Map<String, Object> openedResources = new HashMap<String, Object>();

	public static Map<String, Object> getOpenedResources() {
		return new HashMap<>(openedResources);
	}

	public static String getOpeningStackTraceOf(String resource) {
		if (logStackTraces) {
			String stack = openedResourcesOpeningStack.get(resource);
			return openingStackHeader + " - " + stack;
		} else {
			return openingStackHeader;
		}
	}

	public static synchronized <T> T onOpen(T resource) {
		if (openedResources.containsKey(resource.toString())) {
			throw new ImpossibleRuntimeException("Resource named " + resource.toString() + " is already opened");
		}
		openedResources.put(resource.toString(), resource);

		if (logStackTraces) {
			String stackTrace = ExceptionUtils.getStackTrace(new RuntimeException());
			openedResourcesOpeningStack.put(resource.toString(), stackTrace);
		}
		return resource;
	}

	public static synchronized void onClose(Object resource) {
		String key = resource.toString();
		openedResources.remove(key);
		if (logStackTraces) {
			openedResourcesOpeningStack.remove(key);
		}
	}

	public static void clear() {
		openedResources.clear();
		if (logStackTraces) {
			openedResourcesOpeningStack.clear();
		}
	}
}
