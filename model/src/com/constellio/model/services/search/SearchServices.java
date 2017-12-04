package com.constellio.model.services.search;

import static com.constellio.model.services.records.RecordUtils.splitByCollection;
import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.from;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.HighlightParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.MoreLikeThisParams;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.params.StatsParams;

import com.constellio.data.dao.dto.records.FacetValue;
import com.constellio.data.dao.dto.records.QueryResponseDTO;
import com.constellio.data.dao.dto.records.RecordDTO;
import com.constellio.data.dao.services.bigVault.LazyResultsIterator;
import com.constellio.data.dao.services.bigVault.LazyResultsKeepingOrderIterator;
import com.constellio.data.dao.services.bigVault.SearchResponseIterator;
import com.constellio.data.dao.services.records.RecordDao;
import com.constellio.data.utils.BatchBuilderIterator;
import com.constellio.data.utils.BatchBuilderSearchResponseIterator;
import com.constellio.data.utils.dev.Toggle;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.schemas.Metadata;
import com.constellio.model.entities.schemas.MetadataSchemaType;
import com.constellio.model.entities.schemas.MetadataSchemaTypes;
import com.constellio.model.entities.schemas.Schemas;
import com.constellio.model.services.collections.CollectionsListManager;
import com.constellio.model.services.collections.CollectionsListManagerRuntimeException.CollectionsListManagerRuntimeException_NoSuchCollection;
import com.constellio.model.services.factories.ModelLayerFactory;
import com.constellio.model.services.migrations.ConstellioEIMConfigs;
import com.constellio.model.services.records.RecordServices;
import com.constellio.model.services.records.cache.RecordsCache;
import com.constellio.model.services.records.cache.RecordsCaches;
import com.constellio.model.services.schemas.MetadataSchemasManager;
import com.constellio.model.services.search.entities.SearchBoost;
import com.constellio.model.services.search.query.ReturnedMetadatasFilter;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery.UserFilter;
import com.constellio.model.services.search.query.logical.condition.LogicalSearchCondition;
import com.constellio.model.services.search.query.logical.condition.SolrQueryBuilderParams;
import com.constellio.model.services.security.SecurityTokenManager;

public class SearchServices {

	private static String[] STOP_WORDS_FR = { "au", "aux", "avec", "ce", "ces", "dans", "de", "des", "du", "elle", "en", "et",
			"eux", "il", "je", "la", "le", "leur", "lui", "ma", "mais", "me", "même", "mes", "moi", "mon", "ne", "nos", "notre",
			"nous", "on", "ou", "par", "pas", "pour", "qu", "que", "qui", "sa", "se", "ses", "son", "sur", "ta", "te", "tes",
			"toi", "ton", "tu", "un", "une", "vos", "votre", "vous", "c", "d", "j", "l", "à", "m", "n", "s", "t", "y", "été",
			"étée", "étées", "étés", "étant", "suis", "es", "est", "sommes", "êtes", "sont", "serai", "seras", "sera", "serons",
			"serez", "seront", "serais", "serait", "serions", "seriez", "seraient", "étais", "était", "étions", "étiez",
			"étaient", "fus", "fut", "fûmes", "fûtes", "furent", "sois", "soit", "soyons", "soyez", "soient", "fusse", "fusses",
			"fût", "fussions", "fussiez", "fussent", "ayant", "eu", "eue", "eues", "eus", "ai", "as", "avons", "avez", "ont",
			"aurai", "auras", "aura", "aurons", "aurez", "auront", "aurais", "aurait", "aurions", "auriez", "auraient", "avais",
			"avait", "avions", "aviez", "avaient", "eut", "eûmes", "eûtes", "eurent", "aie", "aies", "ait", "ayons", "ayez",
			"aient", "eusse", "eusses", "eût", "eussions", "eussiez", "eussent", "ceci", "cela", "celà", "cet", "cette", "ici",
			"ils", "les", "leurs", "quel", "quels", "quelle", "quelles", "sans", "soi" };

	RecordDao recordDao;
	RecordServices recordServices;
	SecurityTokenManager securityTokenManager;
	CollectionsListManager collectionsListManager;
	RecordsCaches recordsCaches;
	MetadataSchemasManager metadataSchemasManager;
	String mainDataLanguage;
	ConstellioEIMConfigs systemConfigs;

	public SearchServices(RecordDao recordDao, ModelLayerFactory modelLayerFactory) {
		this(recordDao, modelLayerFactory, modelLayerFactory.getRecordsCaches());
	}

	public SearchServices(ModelLayerFactory modelLayerFactory, RecordsCaches recordsCaches) {
		this(modelLayerFactory.getDataLayerFactory().newRecordDao(), modelLayerFactory, recordsCaches);
	}

	private SearchServices(RecordDao recordDao, ModelLayerFactory modelLayerFactory, RecordsCaches recordsCaches) {
		this.recordDao = recordDao;
		this.recordServices = modelLayerFactory.newRecordServices();
		this.securityTokenManager = modelLayerFactory.getSecurityTokenManager();
		this.collectionsListManager = modelLayerFactory.getCollectionsListManager();
		this.metadataSchemasManager = modelLayerFactory.getMetadataSchemasManager();
		mainDataLanguage = modelLayerFactory.getConfiguration().getMainDataLanguage();
		this.systemConfigs = modelLayerFactory.getSystemConfigs();
		this.recordsCaches = recordsCaches;
	}

	public SPEQueryResponse query(LogicalSearchQuery query) {
		ModifiableSolrParams params = addSolrModifiableParams(query);
		return buildResponse(params, query);
	}

	public List<Record> cachedSearch(LogicalSearchQuery query) {
		RecordsCache recordsCache = recordsCaches.getCache(query.getCondition().getCollection());
		List<Record> records = recordsCache.getQueryResults(query);
		if (records == null) {
			records = search(query);
			recordsCache.insertQueryResults(query, records);
		}
		return records;
	}

	public List<String> cachedSearchRecordIds(LogicalSearchQuery query) {
		RecordsCache recordsCache = recordsCaches.getCache(query.getCondition().getCollection());
		List<String> records = recordsCache.getQueryResultIds(query);
		if (records == null) {
			records = searchRecordIds(query);
			recordsCache.insertQueryResultIds(query, records);
		}
		return records;
	}

	public Map<Record, Map<Record, Double>> searchWithMoreLikeThis(LogicalSearchQuery query) {
		return query(query).getRecordsWithMoreLikeThis();
	}

	public List<Record> search(LogicalSearchQuery query) {
		return query(query).getRecords();
	}

	public Record searchSingleResult(LogicalSearchCondition condition) {
		SPEQueryResponse response = query(new LogicalSearchQuery(condition).setNumberOfRows(1));
		if (response.getNumFound() > 1) {
			SolrQueryBuilderParams params = new SolrQueryBuilderParams(false, "?");
			throw new SearchServicesRuntimeException.TooManyRecordsInSingleSearchResult(condition.getSolrQuery(params));
		}
		return response.getNumFound() == 1 ? response.getRecords().get(0) : null;
	}

	public Iterator<List<Record>> recordsBatchIterator(int batch, LogicalSearchQuery query) {
		Iterator<Record> recordsIterator = recordsIterator(query, batch);
		return new BatchBuilderIterator<>(recordsIterator, batch);
	}

	public Iterator<List<Record>> recordsBatchIterator(LogicalSearchQuery query) {
		return recordsBatchIterator(100, query);
	}

	public SearchResponseIterator<Record> recordsIterator(LogicalSearchCondition condition) {
		return recordsIterator(new LogicalSearchQuery(condition));
	}

	public SearchResponseIterator<Record> recordsIterator(LogicalSearchCondition condition, int batchSize) {
		return recordsIterator(new LogicalSearchQuery(condition), batchSize);
	}

	public SearchResponseIterator<Record> recordsIterator(LogicalSearchQuery query) {
		return recordsIterator(query, 100);
	}

	public SearchResponseIterator<Record> recordsIterator(LogicalSearchQuery query, int batchSize) {
		ModifiableSolrParams params = addSolrModifiableParams(query);
		final boolean fullyLoaded = query.getReturnedMetadatas().isFullyLoaded();
		return new LazyResultsIterator<Record>(recordDao, params, batchSize, true) {

			@Override
			public Record convert(RecordDTO recordDTO) {
				return recordServices.toRecord(recordDTO, fullyLoaded);
			}
		};
	}

	public Iterator<List<Record>> reverseRecordsBatchIterator(int batch, LogicalSearchQuery query) {
		Iterator<Record> recordsIterator = reverseRecordsIterator(query, batch);
		return new BatchBuilderIterator<>(recordsIterator, batch);
	}

	public Iterator<List<Record>> reverseRecordsBatchIterator(LogicalSearchQuery query) {
		return reverseRecordsBatchIterator(100, query);
	}

	public SearchResponseIterator<Record> reverseRecordsIterator(LogicalSearchCondition condition) {
		return reverseRecordsIterator(new LogicalSearchQuery(condition));
	}

	public SearchResponseIterator<Record> reverseRecordsIterator(LogicalSearchCondition condition, int batchSize) {
		return reverseRecordsIterator(new LogicalSearchQuery(condition), batchSize);
	}

	public SearchResponseIterator<Record> reverseRecordsIterator(LogicalSearchQuery query) {
		return reverseRecordsIterator(query, 100);
	}

	public SearchResponseIterator<Record> reverseRecordsIterator(LogicalSearchQuery query, int batchSize) {
		ModifiableSolrParams params = addSolrModifiableParams(query);
		final boolean fullyLoaded = query.getReturnedMetadatas().isFullyLoaded();
		return new LazyResultsIterator<Record>(recordDao, params, batchSize, false) {

			@Override
			public Record convert(RecordDTO recordDTO) {
				return recordServices.toRecord(recordDTO, fullyLoaded);
			}
		};
	}

	public SearchResponseIterator<Record> recordsIteratorKeepingOrder(LogicalSearchQuery query, int batchSize) {
		ModifiableSolrParams params = addSolrModifiableParams(query);
		final boolean fullyLoaded = query.getReturnedMetadatas().isFullyLoaded();
		return new LazyResultsKeepingOrderIterator<Record>(recordDao, params, batchSize) {

			@Override
			public Record convert(RecordDTO recordDTO) {
				return recordServices.toRecord(recordDTO, fullyLoaded);
			}
		};
	}

	public SearchResponseIterator<Record> recordsIteratorKeepingOrder(LogicalSearchQuery query, int batchSize, int skipping) {
		ModifiableSolrParams params = addSolrModifiableParams(query);
		final boolean fullyLoaded = query.getReturnedMetadatas().isFullyLoaded();
		return new LazyResultsKeepingOrderIterator<Record>(recordDao, params, batchSize, skipping) {

			@Override
			public Record convert(RecordDTO recordDTO) {
				return recordServices.toRecord(recordDTO, fullyLoaded);
			}
		};
	}

	public SearchResponseIterator<Record> cachedRecordsIteratorKeepingOrder(LogicalSearchQuery query, final int batchSize) {
		LogicalSearchQuery querCompatibleWithCache = new LogicalSearchQuery(query);
		querCompatibleWithCache.setStartRow(0);
		querCompatibleWithCache.setNumberOfRows(100000);
		querCompatibleWithCache.setReturnedMetadatas(ReturnedMetadatasFilter.all());

		//final List<Record> original = search(query);
		final List<Record> records = cachedSearch(querCompatibleWithCache);

		//		if (original.size() != records.size()) {
		//			System.out.println("different");
		//		}

		final Iterator<Record> nestedIterator = records.iterator();
		return new SearchResponseIterator<Record>() {
			@Override
			public long getNumFound() {
				return records.size();
			}

			@Override
			public SearchResponseIterator<List<Record>> inBatches() {
				final SearchResponseIterator iterator = this;
				return new BatchBuilderSearchResponseIterator<Record>(iterator, batchSize) {

					@Override
					public long getNumFound() {
						return iterator.getNumFound();
					}
				};
			}

			@Override
			public boolean hasNext() {
				return nestedIterator.hasNext();
			}

			@Override
			public Record next() {
				return nestedIterator.next();
			}

			@Override
			public void remove() {
				nestedIterator.remove();
			}
		};
	}

	public SearchResponseIterator<Record> cachedRecordsIteratorKeepingOrder(LogicalSearchQuery query, int batchSize,
			int skipping) {

		SearchResponseIterator<Record> iterator = cachedRecordsIteratorKeepingOrder(query, batchSize);

		for (int i = 0; i < skipping && iterator.hasNext(); i++) {
			iterator.next();
		}
		return iterator;
		//		ModifiableSolrParams params = addSolrModifiableParams(query);
		//		final boolean fullyLoaded = query.getReturnedMetadatas().isFullyLoaded();
		//		return new LazyResultsKeepingOrderIterator<Record>(recordDao, params, batchSize, skipping) {
		//
		//			@Override
		//			public Record convert(RecordDTO recordDTO) {
		//				return recordServices.toRecord(recordDTO, fullyLoaded);
		//			}
		//		};
	}

	public long getResultsCount(LogicalSearchCondition condition) {
		return getResultsCount(new LogicalSearchQuery(condition));
	}

	public long getResultsCount(LogicalSearchQuery query) {
		int oldNumberOfRows = query.getNumberOfRows();
		query.setNumberOfRows(0);
		ModifiableSolrParams params = addSolrModifiableParams(query);
		long result = recordDao.query(query.getName(), params).getNumFound();
		query.setNumberOfRows(oldNumberOfRows);
		return result;
	}

	public List<String> searchRecordIds(LogicalSearchCondition condition) {
		LogicalSearchQuery query = new LogicalSearchQuery(condition);
		return searchRecordIds(query);
	}

	public List<String> searchRecordIds(LogicalSearchQuery query) {
		query.setReturnedMetadatas(ReturnedMetadatasFilter.idVersionSchema());
		ModifiableSolrParams params = addSolrModifiableParams(query);

		List<String> ids = new ArrayList<>();
		for (Record record : buildResponse(params, query).getRecords()) {
			ids.add(record.getId());
		}
		return ids;
	}

	public Iterator<String> recordsIdsIterator(LogicalSearchQuery query) {
		ModifiableSolrParams params = addSolrModifiableParams(query);
		return new LazyResultsIterator<String>(recordDao, params, 10000, true) {

			@Override
			public String convert(RecordDTO recordDTO) {
				return recordDTO.getId();
			}
		};
	}

	public Iterator<String> reverseRecordsIdsIterator(LogicalSearchQuery query) {
		ModifiableSolrParams params = addSolrModifiableParams(query);
		return new LazyResultsIterator<String>(recordDao, params, 10000, false) {

			@Override
			public String convert(RecordDTO recordDTO) {
				return recordDTO.getId();
			}
		};
	}

	public boolean hasResults(LogicalSearchQuery query) {
		return getResultsCount(query) != 0;
	}

	public boolean hasResults(LogicalSearchCondition condition) {
		return getResultsCount(condition) != 0;
	}

	public String getLanguage(LogicalSearchQuery query) {
		if (query.getCondition().isCollectionSearch()) {
			return getLanguageCode(query.getCondition().getCollection());

		} else {
			return mainDataLanguage;
		}
	}

	public String getLanguageCode(String collection) {
		String language;
		try {
			language = collectionsListManager.getCollectionLanguages(collection).get(0);
		} catch (CollectionsListManagerRuntimeException_NoSuchCollection e) {
			language = mainDataLanguage;
		}
		return language;
	}

	public ModifiableSolrParams addSolrModifiableParams(LogicalSearchQuery query) {
		ModifiableSolrParams params = new ModifiableSolrParams();

		for (String filterQuery : query.getFilterQueries()) {
			params.add(CommonParams.FQ, filterQuery);
		}
		addUserFilter(params, query.getUserFilters());

		String language = getLanguage(query);
		params.add(CommonParams.FQ, "" + query.getQuery(language));

		params.add(CommonParams.QT, "/spell");
		params.add(ShardParams.SHARDS_QT, "/spell");

		if (query.getFreeTextQuery() != null) {
			String qf = getQfFor(query.getCondition().getCollection(), query.getFieldBoosts());
			params.add(DisMaxParams.QF, qf);
			params.add(DisMaxParams.PF, qf);
			if (systemConfigs.isReplaceSpacesInSimpleSearchForAnds()) {
				int mm = calcMM(query.getFreeTextQuery());
				params.add(DisMaxParams.MM, "" + mm);
			} else {
				params.add(DisMaxParams.MM, "1");
			}
			params.add("defType", "edismax");
			params.add(DisMaxParams.BQ, "\"" + query.getFreeTextQuery() + "\"");

			for (SearchBoost boost : query.getQueryBoosts()) {
				params.add(DisMaxParams.BQ, boost.getKey() + "^" + boost.getValue());
			}
		}

		String userCondition = "";
		if (query.getQueryCondition() != null) {
			userCondition = " AND " + query.getQueryCondition().getSolrQuery(new SolrQueryBuilderParams(false, "?"));
		}

		params.add(CommonParams.Q, String.format("%s%s", StringUtils.defaultString(query.getFreeTextQuery(), "*:*")
				, userCondition));

		params.add(CommonParams.ROWS, "" + query.getNumberOfRows());
		params.add(CommonParams.START, "" + query.getStartRow());

		if (!query.getFieldFacets().isEmpty() || !query.getQueryFacets().isEmpty()) {
			params.add(FacetParams.FACET, "true");
			params.add(FacetParams.FACET_SORT, FacetParams.FACET_SORT_COUNT);
		}
		if (!query.getFieldFacets().isEmpty()) {
			params.add(FacetParams.FACET_MINCOUNT, "1");
			for (String field : query.getFieldFacets()) {
				params.add(FacetParams.FACET_FIELD, "{!ex=" + field + "}" + field);
			}
			if (query.getFieldFacetLimit() != 0) {
				params.add(FacetParams.FACET_LIMIT, "" + query.getFieldFacetLimit());
			}
		}
		if (!query.getStatisticFields().isEmpty()) {
			params.set(StatsParams.STATS, "true");
			for (String field : query.getStatisticFields()) {
				params.add(StatsParams.STATS_FIELD, field);
			}
		}
		if (!query.getQueryFacets().isEmpty()) {
			for (Entry<String, Set<String>> facetQuery : query.getQueryFacets().getMapEntries()) {
				for (String aQuery : facetQuery.getValue()) {
					params.add(FacetParams.FACET_QUERY, "{!ex=f" + facetQuery.getKey() + "}" + aQuery);
				}
			}
		}

		String sort = query.getSort();
		if (!sort.isEmpty()) {
			params.add(CommonParams.SORT, sort);
		}

		if (query.getReturnedMetadatas() != null && query.getReturnedMetadatas().getAcceptedFields() != null) {
			List<String> fields = new ArrayList<>();
			fields.add("id");
			fields.add("schema_s");
			fields.add("_version_");
			fields.add("collection_s");
			fields.addAll(query.getReturnedMetadatas().getAcceptedFields());
			params.set(CommonParams.FL, StringUtils.join(fields.toArray(), ","));

		}

		if (query.isHighlighting()) {
			HashSet<String> highligthedMetadatas = new HashSet<>();
			MetadataSchemaTypes types = metadataSchemasManager.getSchemaTypes(query.getCondition().getCollection());
			for (Metadata metadata : types.getHighlightedMetadatas()) {
				highligthedMetadatas.add(metadata.getAnalyzedField(language).getDataStoreCode());
			}

			params.add(HighlightParams.HIGHLIGHT, "true");
			params.add(HighlightParams.FIELDS, StringUtils.join(highligthedMetadatas, " "));
			params.add(HighlightParams.SNIPPETS, "1");
			params.add(HighlightParams.FRAGSIZE, "140");
			params.add(HighlightParams.MERGE_CONTIGUOUS_FRAGMENTS, "true");
		}

		if (query.isSpellcheck()) {
			params.add("spellcheck", "on");
		}

		if (query.getOverridedQueryParams() != null) {
			for (Map.Entry<String, String[]> overridedQueryParam : query.getOverridedQueryParams().entrySet()) {
				params.remove(overridedQueryParam.getKey());
				if (overridedQueryParam.getValue() != null) {
					for (String value : overridedQueryParam.getValue()) {
						params.add(overridedQueryParam.getKey(), value);
					}
				}

			}
		}

		if (query.isMoreLikeThis() /*&& query.getMoreLikeThisFields().size() > 0*/) {
			params.add(MoreLikeThisParams.MLT, "true");
			params.add(MoreLikeThisParams.MIN_DOC_FREQ, "0");
			params.add(MoreLikeThisParams.MIN_TERM_FREQ, "0");
			List<String> moreLikeThisFields = query.getMoreLikeThisFields();
			if (moreLikeThisFields.isEmpty()) {
				moreLikeThisFields.addAll(Arrays.asList("content_txt_fr", "content_txt_en", "content_txt_ar"));
			}

			StringBuilder similarityFields = new StringBuilder();
			for (String aSimilarityField : moreLikeThisFields) {
				if (similarityFields.length() != 0)
					similarityFields.append(",");
				if (!aSimilarityField.contains("_txt_") && !aSimilarityField.contains("_t_")) {
					System.err.printf("The %s does not support term vector. It may cause performance issue.\n", aSimilarityField);
				}
				similarityFields.append(aSimilarityField);
			}

			params.add(MoreLikeThisParams.SIMILARITY_FIELDS, similarityFields.toString());
		}

		return params;
	}

	/**
	 * FIXME With solr 6+, use mm autorelax instead
	 * @param userQuery
	 * @return
	 */
	private int calcMM(String userQuery) {
		HashSet queryTerms = new HashSet(Arrays.asList(StringUtils.split(StringUtils.lowerCase(userQuery))));
		queryTerms.removeAll(Arrays.asList(STOP_WORDS_FR));
		return queryTerms.size();
	}

	private String getQfFor(String collection, List<SearchBoost> boosts) {
		StringBuilder sb = new StringBuilder();

		Set<String> fieldsWithBoosts = new HashSet<>();

		for (SearchBoost boost : boosts) {
			sb.append(boost.getKey());
			sb.append("^");
			sb.append(boost.getValue());
			sb.append(" ");
			fieldsWithBoosts.add(boost.getKey());
		}
		for (Metadata metadata : metadataSchemasManager.getSchemaTypes(collection).getHighlightedMetadatas()) {
			if (metadata.hasSameCode(Schemas.LEGACY_ID)) {
				sb.append(Schemas.LEGACY_ID.getDataStoreCode());
				sb.append("^20 ");
			}
			String analyzedField = metadata.getAnalyzedField(mainDataLanguage).getDataStoreCode();
			if (!fieldsWithBoosts.contains(analyzedField)) {
				sb.append(analyzedField + " ");
			}
		}

		String idAnalyzedField = Schemas.IDENTIFIER.getAnalyzedField(mainDataLanguage).getDataStoreCode();
		if (!fieldsWithBoosts.contains(idAnalyzedField)) {
			sb.append(idAnalyzedField + " ");
		}

		//		sb.append("search_txt_");
		//		sb.append(mainDataLanguage);
		return sb.toString();
	}

	private SPEQueryResponse buildResponse(ModifiableSolrParams params, LogicalSearchQuery query) {
		QueryResponseDTO queryResponseDTO = recordDao.query(query.getName(), params);
		List<RecordDTO> recordDTOs = queryResponseDTO.getResults();

		List<Record> records = recordServices.toRecords(recordDTOs, query.getReturnedMetadatas().isFullyLoaded());
		if (!records.isEmpty() && Toggle.PUTS_AFTER_SOLR_QUERY.isEnabled() && query.getReturnedMetadatas().isFullyLoaded()) {
			for (Map.Entry<String, List<Record>> entry : splitByCollection(records).entrySet()) {
				recordsCaches.insert(entry.getKey(), entry.getValue());
			}

		}
		Map<Record, Map<Record, Double>> moreLikeThisResult = getResultWithMoreLikeThis(
				queryResponseDTO.getResultsWithMoreLikeThis());

		Map<String, List<FacetValue>> fieldFacetValues = buildFacets(query.getFieldFacets(),
				queryResponseDTO.getFieldFacetValues());
		Map<String, Integer> queryFacetValues = withRemoveExclusions(queryResponseDTO.getQueryFacetValues());

		Map<String, Map<String, Object>> statisticsValues = buildStats(query.getStatisticFields(),
				queryResponseDTO.getFieldsStatistics());
		SPEQueryResponse response = new SPEQueryResponse(fieldFacetValues, statisticsValues, queryFacetValues,
				queryResponseDTO.getQtime(),
				queryResponseDTO.getNumFound(), records, queryResponseDTO.getHighlights(),
				queryResponseDTO.isCorrectlySpelt(), queryResponseDTO.getSpellCheckerSuggestions(), moreLikeThisResult);

		if (query.getResultsProjection() != null) {
			return query.getResultsProjection().project(query, response);
		} else {
			return response;
		}
	}

	private Map<Record, Map<Record, Double>> getResultWithMoreLikeThis(
			Map<RecordDTO, Map<RecordDTO, Double>> resultsWithMoreLikeThis) {
		Map<Record, Map<Record, Double>> results = new LinkedHashMap<>();
		for (Entry<RecordDTO, Map<RecordDTO, Double>> aDocWithMoreLikeThis : resultsWithMoreLikeThis.entrySet()) {
			Map<Record, Double> similarRecords = new LinkedHashMap<>();
			for (Entry<RecordDTO, Double> similarDocWithScore : aDocWithMoreLikeThis.getValue().entrySet()) {
				similarRecords.put(recordServices.toRecord(similarDocWithScore.getKey(), true), similarDocWithScore.getValue());
			}
			results.put(recordServices.toRecord(aDocWithMoreLikeThis.getKey(), true), similarRecords);
		}
		return results;
	}

	private Map<String, Integer> withRemoveExclusions(Map<String, Integer> queryFacetValues) {
		if (queryFacetValues == null) {
			return null;
		}
		Map<String, Integer> withRemovedExclusions = new HashMap<>();
		for (Map.Entry<String, Integer> queryEntry : queryFacetValues.entrySet()) {
			String query = queryEntry.getKey();
			query = query.substring(query.indexOf("}") + 1);
			withRemovedExclusions.put(query, queryEntry.getValue());
		}
		return withRemovedExclusions;
	}

	private Map<String, List<FacetValue>> buildFacets(
			List<String> fields, Map<String, List<FacetValue>> facetValues) {
		Map<String, List<FacetValue>> result = new HashMap<>();
		for (String field : fields) {
			List<FacetValue> values = facetValues.get(field);
			if (values != null) {
				result.put(field, values);
			}
		}
		return result;
	}

	private Map<String, Map<String, Object>> buildStats(
			List<String> fields, Map<String, Map<String, Object>> fieldStatsValues) {
		Map<String, Map<String, Object>> result = new HashMap<>();
		for (String field : fields) {
			Map<String, Object> values = fieldStatsValues.get(field);
			if (values != null) {
				result.put(field, values);
			}
		}
		return result;
	}

	private void addUserFilter(ModifiableSolrParams params, List<UserFilter> userFilters) {
		if (userFilters == null) {
			return;
		}

		for (UserFilter userFilter : userFilters) {
			params.add(CommonParams.FQ, userFilter.buildFQ(securityTokenManager));
		}
	}

	public List<Record> getAllRecords(MetadataSchemaType schemaType) {

		if (Toggle.GET_ALL_VALUES_USING_NEW_CACHE_METHOD.isEnabled()) {
			RecordsCache cache = recordsCaches.getCache(schemaType.getCollection());
			if (cache.isConfigured(schemaType)) {
				if (cache.isFullyLoaded(schemaType.getCode())) {
					return cache.getAllValues(schemaType.getCode());

				} else {
					List<Record> records = cachedSearch(new LogicalSearchQuery(from(schemaType).returnAll()));
					cache.insert(records);
					cache.markAsFullyLoaded(schemaType.getCode());
					List<Record> loadedFromMethod = cache.getAllValues(schemaType.getCode());
					System.out.println(records.size() == loadedFromMethod.size());
					return loadedFromMethod;
				}
			}
			throw new SearchServicesRuntimeException.GetAllValuesNotSupportedForSchemaType(schemaType.getCode());
		} else {
			return cachedSearch(new LogicalSearchQuery(from(schemaType).returnAll()));
		}
	}
}
