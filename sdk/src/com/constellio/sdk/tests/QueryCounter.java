package com.constellio.sdk.tests;

import java.util.concurrent.atomic.AtomicInteger;

import com.constellio.data.dao.services.factories.DataLayerFactory;
import com.constellio.data.extensions.AfterQueryParams;
import com.constellio.data.extensions.BigVaultServerExtension;

public class QueryCounter extends BigVaultServerExtension {

	private QueryCounterFilter filter;
	private AtomicInteger counter = new AtomicInteger();

	public QueryCounter(DataLayerFactory dataLayerFactory, final String name) {
		this.filter = new QueryCounterFilter() {
			@Override
			public boolean isCounted(AfterQueryParams params) {
				return params.getQueryName() != null && params.getQueryName().equals(name);
			}
		};
		dataLayerFactory.getExtensions().getSystemWideExtensions().bigVaultServerExtension.add(this);
	}

	public QueryCounter(DataLayerFactory dataLayerFactory, QueryCounterFilter filter) {
		this.filter = filter;
		dataLayerFactory.getExtensions().getSystemWideExtensions().bigVaultServerExtension.add(this);
	}

	@Override
	public void afterQuery(AfterQueryParams params) {
		if (filter.isCounted(params)) {
			counter.incrementAndGet();
		}
	}

	public int newQueryCalls() {
		int calls = counter.get();
		counter.set(0);
		return calls;
	}

	public void reset() {
		counter.set(0);
	}

	public interface QueryCounterFilter {

		boolean isCounted(AfterQueryParams params);
	}

	public static QueryCounterFilter ACCEPT_ALL = new QueryCounterFilter() {

		@Override
		public boolean isCounted(AfterQueryParams params) {
			return true;
		}
	};
}
