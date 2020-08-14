package com.constellio.model.services.records.cache.cacheIndexConditions;

import com.constellio.data.dao.dto.records.RecordId;
import com.constellio.data.utils.LazyIterator;

import java.util.Iterator;

public class AllExceptSortedIdsStreamer implements SortedIdsStreamer {

	SortedIdsStreamer source;
	SortedIdsStreamer except;

	public AllExceptSortedIdsStreamer(
			SortedIdsStreamer source,
			SortedIdsStreamer except) {
		this.source = source;
		this.except = except;
	}

	@Override
	public Iterator<RecordId> iterator() {
		Iterator<RecordId> sourceIterator = source.iterator();
		RecordExistingIdsIteratorConsumer exceptIteratorConsumer = new RecordExistingIdsIteratorConsumer(except.iterator());
		return new LazyIterator<RecordId>() {
			@Override
			protected RecordId getNextOrNull() {

				while (sourceIterator.hasNext()) {
					RecordId recordId = sourceIterator.next();
					if (!exceptIteratorConsumer.exists(recordId)) {
						return recordId;
					}

				}
				return null;
			}
		};
	}
}
