package com.constellio.model.services.search;

import com.constellio.data.utils.AccentApostropheCleaner;
import com.constellio.data.utils.LangUtils;
import com.constellio.data.utils.dev.Toggle;
import com.constellio.model.entities.Language;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.schemas.DataStoreField;
import com.constellio.model.entities.schemas.Metadata;
import com.constellio.model.entities.schemas.MetadataSchemaType;
import com.constellio.model.entities.schemas.RecordCacheType;
import com.constellio.model.entities.schemas.Schemas;
import com.constellio.model.extensions.ModelLayerSystemExtensions;
import com.constellio.model.services.records.cache.FindMultipleRecordByMetadata;
import com.constellio.model.services.records.cache.RecordsCache;
import com.constellio.model.services.records.cache.RecordsCaches;
import com.constellio.model.services.schemas.MetadataSchemasManager;
import com.constellio.model.services.search.query.logical.FieldLogicalSearchQuerySort;
import com.constellio.model.services.search.query.logical.LogicalOperator;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery.UserFilter;
import com.constellio.model.services.search.query.logical.LogicalSearchQuerySort;
import com.constellio.model.services.search.query.logical.LogicalSearchValueCondition;
import com.constellio.model.services.search.query.logical.QueryExecutionMethod;
import com.constellio.model.services.search.query.logical.condition.CompositeLogicalSearchCondition;
import com.constellio.model.services.search.query.logical.condition.DataStoreFieldLogicalSearchCondition;
import com.constellio.model.services.search.query.logical.condition.LogicalSearchCondition;
import com.constellio.model.services.search.query.logical.condition.SchemaFilters;
import com.constellio.model.services.search.query.logical.criteria.IsEqualCriterion;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.constellio.model.entities.records.LocalisedRecordMetadataRetrieval.PREFERRING;
import static com.constellio.model.entities.schemas.MetadataValueType.INTEGER;
import static com.constellio.model.entities.schemas.MetadataValueType.NUMBER;
import static com.constellio.model.entities.schemas.MetadataValueType.STRING;

public class LogicalSearchQueryExecutorInCache {

	private static Logger LOGGER = LoggerFactory.getLogger(LogicalSearchQueryExecutorInCache.class);

	SearchServices searchServices;
	RecordsCaches recordsCaches;
	MetadataSchemasManager schemasManager;
	ModelLayerSystemExtensions modelLayerExtensions;
	String mainDataLanguage;
	private int initialBaseStreamSize = -1;

	public LogicalSearchQueryExecutorInCache(SearchServices searchServices, RecordsCaches recordsCaches,
											 MetadataSchemasManager schemasManager,
											 ModelLayerSystemExtensions modelLayerExtensions,
											 String mainDataLanguage) {
		this.searchServices = searchServices;
		this.recordsCaches = recordsCaches;
		this.schemasManager = schemasManager;
		this.modelLayerExtensions = modelLayerExtensions;
		this.mainDataLanguage = mainDataLanguage;
	}

	public Stream<Record> stream(LogicalSearchQuery query) {
		MetadataSchemaType schemaType = getQueriedSchemaType(query.getCondition());

		final long startOfStreaming = new Date().getTime();

		Predicate<Record> filter = new Predicate<Record>() {
			@Override
			public boolean test(Record record) {

				boolean result = true;

				switch (query.getVisibilityStatusFilter()) {

					case HIDDENS:
						result = Boolean.TRUE.equals(record.get(Schemas.HIDDEN));
						break;
					case VISIBLES:
						result = !Boolean.TRUE.equals(record.get(Schemas.HIDDEN));
						break;
				}

				if (!result) {
					return false;
				}

				switch (query.getStatusFilter()) {

					case DELETED:
						result = Boolean.TRUE.equals(record.get(Schemas.LOGICALLY_DELETED_STATUS));
						break;
					case ACTIVES:
						result = !Boolean.TRUE.equals(record.get(Schemas.LOGICALLY_DELETED_STATUS));
						break;
				}

				return result;
			}
		}.and(query.getCondition());


		if (query.getCondition().getFilters() instanceof SchemaFilters) {
			SchemaFilters schemaFilters = (SchemaFilters) query.getCondition().getFilters();

			if (schemaFilters.getSchemaFilter() != null && schemaFilters.getSchemaTypeFilter() == null) {
				filter = new Predicate<Record>() {
					@Override
					public boolean test(Record record) {
						return schemaFilters.getSchemaFilter().getCode().equals(record.getSchemaCode());
					}
				}.and(filter);
			}
		}


		if (Toggle.VALIDATE_CACHE_EXECUTION_SERVICE_USING_SOLR.isEnabled()) {
			filter = filter.and(new Predicate<Record>() {
				@Override
				public boolean test(Record record) {
					LOGGER.info("Record returned by stream : " + record.getIdTitle());

					return true;
				}
			});
		}

		if (query.getUserFilters() != null && !query.getUserFilters().isEmpty()) {
			filter = ((Predicate<Record>) record -> isRecordAccessibleForUser(query.getUserFilters(), record)).and(filter);
		}

		boolean isBaseStreamDefined = false;
		List<Record> baseRecords = null;

		RecordsCache recordsCache = recordsCaches.getCache(schemaType.getCollection());

		if (recordsCache instanceof FindMultipleRecordByMetadata) {
			FindMultipleRecordByMetadata findMultipleRecordByMetadata = (FindMultipleRecordByMetadata) recordsCache;

			if (query.getCondition() instanceof DataStoreFieldLogicalSearchCondition) {
				DataStoreFieldLogicalSearchCondition dataStoreFieldLogicalSearchCondition = (DataStoreFieldLogicalSearchCondition) query.getCondition();
				LogicalSearchValueCondition logicalSearchValueCondition = dataStoreFieldLogicalSearchCondition.getValueCondition();
				if (logicalSearchValueCondition instanceof IsEqualCriterion) {
					IsEqualCriterion isEqualCriterion = (IsEqualCriterion) logicalSearchValueCondition;
					List<DataStoreField> dataStoreFields = dataStoreFieldLogicalSearchCondition.getDataStoreFields();

					if (dataStoreFields != null && dataStoreFields.size() == 1) {
						Object value = isEqualCriterion.getMemoryQueryValue();
						DataStoreField dataStoreField = dataStoreFields.get(0);
						if (canDataGetByMetadata(schemaType, value, dataStoreField)) {

							Metadata metadata = schemaType.getDefaultSchema().getMetadataByDatastoreCode(dataStoreField.getDataStoreCode());
							isBaseStreamDefined = true;
							baseRecords = findMultipleRecordByMetadata.getMultipleByMetadata(metadata, (String) value);
							initialBaseStreamSize = baseRecords.size();
						}
					}
				}
			} else if (query.getCondition() instanceof CompositeLogicalSearchCondition) {
				CompositeLogicalSearchCondition compositeLogicalSearchCondition = (CompositeLogicalSearchCondition) query.getCondition();

				LogicalOperator logicalOperator = compositeLogicalSearchCondition.getLogicalOperator();

				if (logicalOperator == LogicalOperator.AND) {
					for (LogicalSearchCondition logicalSearchCondition : compositeLogicalSearchCondition.getNestedSearchConditions()) {
						if (logicalSearchCondition instanceof DataStoreFieldLogicalSearchCondition
							&& ((DataStoreFieldLogicalSearchCondition) logicalSearchCondition).getValueCondition() instanceof IsEqualCriterion) {
							IsEqualCriterion isEqualCriterion = (IsEqualCriterion) ((DataStoreFieldLogicalSearchCondition) logicalSearchCondition).getValueCondition();
							DataStoreFieldLogicalSearchCondition dataStoreFieldLogicalSearchCondition = (DataStoreFieldLogicalSearchCondition) logicalSearchCondition;

							List<DataStoreField> dataStoreFields = dataStoreFieldLogicalSearchCondition.getDataStoreFields();

							if (dataStoreFields != null && dataStoreFields.size() == 1) {
								Object value = isEqualCriterion.getMemoryQueryValue();
								DataStoreField dataStoreField = dataStoreFields.get(0);

								if (canDataGetByMetadata(schemaType, value, dataStoreField)) {
									Metadata metadata = schemaType.getDefaultSchema().getMetadataByDatastoreCode(dataStoreField.getDataStoreCode());
									isBaseStreamDefined = true;
									baseRecords = findMultipleRecordByMetadata.getMultipleByMetadata(metadata, (String) value);
									initialBaseStreamSize = baseRecords.size();
									break;
								}
							}
						}
					}
				}
			}
		}

		Stream<Record> stream;

		if (!isBaseStreamDefined) {
			stream = recordsCaches.stream(schemaType).filter(filter)
					.sorted(newIdComparator()).onClose(() -> {
						long duration = new Date().getTime() - startOfStreaming;
						modelLayerExtensions.onQueryExecution(query, duration);
					});
		} else {
			stream = baseRecords.stream().filter(filter).sorted(newIdComparator());
		}

		if (!query.getSortFields().isEmpty()) {
			initialBaseStreamSize = -1;
			return stream.sorted(newQuerySortFieldsComparator(query, schemaType));
		} else {
			return stream;
		}
	}

	private static boolean isRecordAccessibleForUser(List<UserFilter> userFilterList, Record record) {
		for (UserFilter currentUserFilter : userFilterList) {
			if (!currentUserFilter.hasUserAccessToRecord(record)) {
				return false;
			}
		}

		return true;
	}

	private boolean canDataGetByMetadata(MetadataSchemaType schemaType, Object value, DataStoreField dataStoreField) {
		return value instanceof String
			   && (dataStoreField.isUniqueValue() || dataStoreField.isCacheIndex()
													 && schemaType.getCacheType() == RecordCacheType.FULLY_CACHED);
	}

	protected int getLastStreamInitialBaseRecordSize() {
		return initialBaseStreamSize;
	}

	@NotNull
	private Comparator<Record> newQuerySortFieldsComparator(LogicalSearchQuery query, MetadataSchemaType schemaType) {
		return (o1, o2) -> {

			String queryLanguage = query.getLanguage() == null ? mainDataLanguage : query.getLanguage();
			Locale locale = Language.withCode(queryLanguage).getLocale();
			for (LogicalSearchQuerySort sort : query.getSortFields()) {
				FieldLogicalSearchQuerySort fieldSort = (FieldLogicalSearchQuerySort) sort;
				Metadata metadata =
						schemaType.getDefaultSchema().getMetadataByDatastoreCode(fieldSort.getField().getDataStoreCode());
				if (metadata != null) {
					int sortValue;
					sortValue = compareMetadatasValues(o1, o2, metadata, locale, sort.isAscending());

					if (sortValue != 0) {
						return sortValue;
					}
				}
			}

			return 0;
		};
	}


	@NotNull
	private Comparator<Record> newIdComparator() {
		return (o1, o2) -> {
			return o1.getId().compareTo(o2.getId());
		};
	}

	private int compareMetadatasValues(Record record1, Record record2, Metadata metadata, Locale preferedLanguage,
									   boolean ascending) {
		Object value1 = record1.get(metadata, preferedLanguage, PREFERRING);
		Object value2 = record2.get(metadata, preferedLanguage, PREFERRING);

		if (metadata.getType() == INTEGER) {
			if (value1 == null) {
				value1 = 0;
			}
			if (value2 == null) {
				value2 = 0;
			}
		} else if (metadata.getType() == NUMBER) {
			if (value1 == null) {
				value1 = 0.0;
			}
			if (value2 == null) {
				value2 = 0.0;
			}
		}

		if (metadata.isSortable()) {
			if (value1 instanceof String && metadata.getSortFieldNormalizer() != null) {
				value1 = metadata.getSortFieldNormalizer().normalize((String) value1);
			}

			if (value2 instanceof String && metadata.getSortFieldNormalizer() != null) {
				value2 = metadata.getSortFieldNormalizer().normalize((String) value2);
			}
		} else {
			if (value1 instanceof String) {
				value1 = AccentApostropheCleaner.removeAccents(((String) value1).toLowerCase());
			}

			if (value2 instanceof String) {
				value2 = AccentApostropheCleaner.removeAccents(((String) value2).toLowerCase());
			}
		}

		int sort = LangUtils.nullableNaturalCompare((Comparable) value1, (Comparable) value2, ascending);
		return ascending ? sort : (-1 * sort);
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
		return query.getQueryExecutionMethod() != QueryExecutionMethod.USE_SOLR
			   && query.getFacetFilters().toSolrFilterQueries().isEmpty()
			   && hasNoUnsupportedSort(query)
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
			   && areAllFiltersExecutableInCache(query.getUserFilters())
			   && !query.isHighlighting();
	}

	private static boolean areAllFiltersExecutableInCache(List<UserFilter> userFilters) {
		if (userFilters == null || userFilters.isEmpty()) {
			return true;
		}

		for (UserFilter currentUserFilter : userFilters) {
			if (!currentUserFilter.isExecutableInCache()) {
				return false;
			}
		}

		return true;
	}

	private static boolean hasNoSort(LogicalSearchQuery query) {
		return query.getSortFields().isEmpty();
	}


	private static boolean hasNoUnsupportedSort(LogicalSearchQuery query) {
		for (LogicalSearchQuerySort sort : query.getSortFields()) {
			if (!(sort instanceof FieldLogicalSearchQuerySort)) {
				return false;
			} else {
				FieldLogicalSearchQuerySort fieldSort = (FieldLogicalSearchQuerySort) sort;
				return fieldSort.getField().getType() == STRING
					   || fieldSort.getField().getType() == NUMBER
					   || fieldSort.getField().getType() == INTEGER;
			}
		}

		return true;
	}

	public boolean isConditionExecutableInCache(LogicalSearchCondition condition) {

		if (recordsCaches == null || !recordsCaches.isInitialized()) {
			return false;
		}

		MetadataSchemaType schemaType = getQueriedSchemaType(condition);

		if (schemaType == null || !Toggle.USE_CACHE_FOR_QUERY_EXECUTION.isEnabled()) {
			return false;

		} else if (schemaType.getCacheType() == RecordCacheType.FULLY_CACHED) {
			return condition.isSupportingMemoryExecution(false);

		} else if (schemaType.getCacheType().hasPermanentCache()) {
			return false;//condition.isSupportingMemoryExecution(true);

		} else {
			return false;
		}


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
