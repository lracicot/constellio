package com.constellio.model.entities.records.wrappers;

import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.schemas.MetadataSchemaTypes;

public class SearchEvent extends RecordWrapper {

	public static final String SCHEMA_TYPE = "searchEvent";
	public static final String DEFAULT_SCHEMA = SCHEMA_TYPE + "_default";
	public static final String USERNAME = "username";
	public static final String QUERY = "query";
	public static final String CLICK_COUNT = "clickCount";
	public static final String PAGE_NAVIGATION_COUNT = "pageNavigationCount";

	public SearchEvent(Record record, MetadataSchemaTypes types) {
		super(record, types, SCHEMA_TYPE + "_");
	}

	public String getUsername() {
		return get(USERNAME);
	}

	public SearchEvent setUsername(String username) {
		set(USERNAME, username);
		return this;
	}

	public String getQuery() {
		return get(QUERY);
	}

	public SearchEvent setQuery(String query) {
		set(QUERY, query);
		return this;
	}

	public int getClickCount() {
		return getPrimitiveInteger(CLICK_COUNT);
	}

	public SearchEvent setClickCount(int clickCount) {
		set(CLICK_COUNT, clickCount);
		return this;
	}

	public int getPageNavigationCount() {
		return getPrimitiveInteger(PAGE_NAVIGATION_COUNT);
	}

	public SearchEvent setPageNavigationCount(int pageNavigationCount) {
		set(PAGE_NAVIGATION_COUNT, pageNavigationCount);
		return this;
	}
}
