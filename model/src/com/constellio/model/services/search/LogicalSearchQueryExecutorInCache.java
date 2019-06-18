package com.constellio.model.services.search;

import com.constellio.data.utils.LangUtils;
import com.constellio.data.utils.dev.Toggle;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.schemas.Metadata;
import com.constellio.model.entities.schemas.MetadataSchemaType;
import com.constellio.model.entities.schemas.RecordCacheType;
import com.constellio.model.services.records.cache.RecordsCaches;
import com.constellio.model.services.schemas.MetadataSchemasManager;
import com.constellio.model.services.search.query.logical.FieldLogicalSearchQuerySort;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery;
import com.constellio.model.services.search.query.logical.LogicalSearchQuerySort;
import com.constellio.model.services.search.query.logical.condition.LogicalSearchCondition;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class LogicalSearchQueryExecutorInCache {

	SearchServices searchServices;
	RecordsCaches recordsCaches;
	MetadataSchemasManager schemasManager;

	public LogicalSearchQueryExecutorInCache(SearchServices searchServices,
											 RecordsCaches recordsCaches,
											 MetadataSchemasManager schemasManager) {
		this.searchServices = searchServices;
		this.recordsCaches = recordsCaches;
		this.schemasManager = schemasManager;
	}

	public Stream<Record> stream(LogicalSearchQuery query) {
		MetadataSchemaType schemaType = getQueriedSchemaType(query.getCondition());
		Stream<Record> stream = recordsCaches.stream(schemaType).filter(query.getCondition());

		if (!query.getSortFields().isEmpty()) {
			return stream.sorted(newQuerySortFieldsComparator(query, schemaType));
		} else {
			return stream;
		}
	}

	@NotNull
	private Comparator<Record> newQuerySortFieldsComparator(LogicalSearchQuery query, MetadataSchemaType schemaType) {
		return (o1, o2) -> {
			for (LogicalSearchQuerySort sort : query.getSortFields()) {
				FieldLogicalSearchQuerySort fieldSort = (FieldLogicalSearchQuerySort) sort;
				Metadata metadata =
						schemaType.getDefaultSchema().getMetadataByDatastoreCode(fieldSort.getField().getDataStoreCode());
				int sortValue;
				if (sort.isAscending()) {
					sortValue = compareMetadatasValues(o1, o2, metadata);
				} else {
					sortValue = -1 * compareMetadatasValues(o1, o2, metadata);
				}

				if (sortValue != 0) {
					return sortValue;
				}
			}

			return 0;
		};
	}

	private int compareMetadatasValues(Record record1, Record record2, Metadata metadata) {
		Object value1 = record1.get(metadata);
		Object value2 = record2.get(metadata);

		if (value1 instanceof String) {
			value1 = metadata.getSortFieldNormalizer().normalize((String) value1);
		}

		if (value2 instanceof String) {
			value2 = metadata.getSortFieldNormalizer().normalize((String) value2);
		}

		return LangUtils.nullableNaturalCompare((Comparable) value1, (Comparable) value2);
	}

	public Stream<Record> stream(LogicalSearchCondition condition) {
		return stream(new LogicalSearchQuery(condition));
	}

	public boolean isQueryExecutableInCache(LogicalSearchQuery query) {
		if (recordsCaches == null || !recordsCaches.isInitialized() || !isConditionExecutableInCache(query.getCondition())) {
			return false;
		}

		return hasNoUnsupportedFeatureOrFilter(query);
	}

	public static boolean hasNoUnsupportedFeatureOrFilter(LogicalSearchQuery query) {
		return query.getFacetFilters().toSolrFilterQueries().isEmpty()
			   && hasNoSortOrOnlyFieldSorts(query)
			   && query.getFreeTextQuery() == null
			   && query.getFieldPivotFacets().isEmpty()
			   && query.getFieldPivotFacets().isEmpty()
			   && query.getFieldBoosts().isEmpty()
			   && query.getQueryBoosts().isEmpty()
			   && query.getStatisticFields().isEmpty()
			   && !query.isPreferAnalyzedFields()
			   && query.getResultsProjection() == null
			   && query.getFieldFacets().isEmpty()
			   && query.getFieldPivotFacets().isEmpty()
			   && query.getQueryFacets().isEmpty()
			   && (query.getUserFilters() == null || query.getUserFilters().isEmpty())
			   && !query.isHighlighting();
	}

	private static boolean hasNoSortOrOnlyFieldSorts(LogicalSearchQuery query) {
		for (LogicalSearchQuerySort sort : query.getSortFields()) {
			if (!(sort instanceof FieldLogicalSearchQuerySort)) {
				return false;
			}
		}

		return true;
	}

	public boolean isConditionExecutableInCache(LogicalSearchCondition condition) {

		if (recordsCaches == null || !recordsCaches.isInitialized()) {
			return false;
		}

		MetadataSchemaType schemaType = getQueriedSchemaType(condition);
		return schemaType != null && schemaType.getCacheType() == RecordCacheType.FULLY_CACHED
			   && Toggle.USE_CACHE_FOR_QUERY_EXECUTION.isEnabled() && condition.isSupportingMemoryExecution();

	}

	private MetadataSchemaType getQueriedSchemaType(LogicalSearchCondition condition) {
		List<String> schemaTypes = condition.getFilterSchemaTypesCodes();

		if (schemaTypes != null && schemaTypes.size() == 1 && condition.getCollection() != null) {

			MetadataSchemaType schemaType = schemasManager.getSchemaTypes(condition.getCollection())
					.getSchemaType(schemaTypes.get(0));

			return schemaType;

		} else {
			return null;
		}
	}
}
