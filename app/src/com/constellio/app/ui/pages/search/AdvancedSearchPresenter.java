package com.constellio.app.ui.pages.search;

import static com.constellio.app.ui.i18n.i18n.$;
import static com.constellio.model.entities.schemas.Schemas.IDENTIFIER;
import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.from;
import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.fromAllSchemasIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.constellio.app.entities.schemasDisplay.MetadataDisplayConfig;
import com.constellio.app.entities.schemasDisplay.enums.MetadataInputType;
import com.constellio.app.modules.rm.model.labelTemplate.LabelTemplate;
import com.constellio.app.modules.rm.model.labelTemplate.LabelTemplateManager;
import com.constellio.app.modules.rm.reports.builders.search.SearchResultReportBuilderFactory;
import com.constellio.app.modules.rm.services.RMSchemasRecordsServices;
import com.constellio.app.modules.rm.wrappers.Cart;
import com.constellio.app.modules.rm.wrappers.ContainerRecord;
import com.constellio.app.modules.rm.wrappers.Document;
import com.constellio.app.modules.rm.wrappers.Folder;
import com.constellio.app.ui.entities.MetadataVO;
import com.constellio.app.ui.framework.builders.MetadataToVOBuilder;
import com.constellio.app.ui.framework.reports.ReportBuilderFactory;
import com.constellio.app.ui.pages.search.criteria.ConditionBuilder;
import com.constellio.app.ui.pages.search.criteria.ConditionException;
import com.constellio.app.ui.pages.search.criteria.ConditionException.ConditionException_EmptyCondition;
import com.constellio.app.ui.pages.search.criteria.ConditionException.ConditionException_TooManyClosedParentheses;
import com.constellio.app.ui.pages.search.criteria.ConditionException.ConditionException_UnclosedParentheses;
import com.constellio.model.entities.batchprocess.BatchProcess;
import com.constellio.model.entities.batchprocess.BatchProcessAction;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.records.wrappers.SavedSearch;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.entities.schemas.Metadata;
import com.constellio.model.entities.schemas.MetadataSchema;
import com.constellio.model.entities.schemas.MetadataSchemaType;
import com.constellio.model.entities.schemas.MetadataValueType;
import com.constellio.model.entities.schemas.entries.DataEntryType;
import com.constellio.model.services.batch.actions.ChangeValueOfMetadataBatchProcessAction;
import com.constellio.model.services.batch.manager.BatchProcessesManager;
import com.constellio.model.services.records.RecordServicesException;
import com.constellio.model.services.reports.ReportServices;
import com.constellio.model.services.search.query.logical.condition.LogicalSearchCondition;

public class AdvancedSearchPresenter extends SearchPresenter<AdvancedSearchView> {
	private static final Logger LOGGER = LoggerFactory.getLogger(AdvancedSearchPresenter.class);

	String searchExpression;
	String schemaTypeCode;
	private int pageNumber;

	private transient LogicalSearchCondition condition;

	public AdvancedSearchPresenter(AdvancedSearchView view) {
		super(view);
	}

	@Override
	protected boolean hasPageAccess(String params, User user) {
		return true;
	}

	@Override
	public AdvancedSearchPresenter forRequestParameters(String params) {
		if (StringUtils.isNotBlank(params)) {
			String[] parts = params.split("/", 2);
			SavedSearch search = getSavedSearch(parts[1]);
			setSavedSearch(search);
		} else {
			searchExpression = StringUtils.stripToNull(view.getSearchExpression());
			resetFacetSelection();
			schemaTypeCode = view.getSchemaType();
			pageNumber = 1;
			saveTemporarySearch();
		}
		return this;
	}

	private void setSavedSearch(SavedSearch search) {
		searchExpression = search.getFreeTextSearch();
		facetSelections.putAll(search.getSelectedFacets());
		sortCriterion = search.getSortField();
		sortOrder = SortOrder.valueOf(search.getSortOrder().name());
		schemaTypeCode = search.getSchemaFilter();
		pageNumber = search.getPageNumber();

		view.setSchemaType(schemaTypeCode);
		view.setSearchExpression(searchExpression);
		view.setSearchCriteria(search.getAdvancedSearch());
	}

	@Override
	public int getPageNumber() {
		return pageNumber;
	}

	@Override
	public void setPageNumber(int pageNumber) {
		this.pageNumber = pageNumber;
	}

	@Override
	public boolean mustDisplayResults() {
		if (StringUtils.isBlank(schemaTypeCode)) {
			return false;
		}
		try {
			buildSearchCondition();
			return true;
		} catch (ConditionException_EmptyCondition e) {
			view.showErrorMessage($("AdvancedSearchView.emptyCondition"));
		} catch (ConditionException_TooManyClosedParentheses e) {
			view.showErrorMessage($("AdvancedSearchView.tooManyClosedParentheses"));
		} catch (ConditionException_UnclosedParentheses e) {
			view.showErrorMessage($("AdvancedSearchView.unclosedParentheses"));
		} catch (ConditionException e) {
			throw new RuntimeException("BUG: Uncaught ConditionException", e);
		}
		return false;
	}

	@Override
	public void suggestionSelected(String suggestion) {
		searchExpression = suggestion;
		view.setSearchExpression(suggestion);
		view.refreshSearchResultsAndFacets();
	}

	@Override
	public String getUserSearchExpression() {
		return searchExpression;
	}

	public void batchEditRequested(List<String> selectedRecordIds, String code, Object value) {
		Map<String, Object> changes = new HashMap<>();
		changes.put(code, value);
		BatchProcessAction action = new ChangeValueOfMetadataBatchProcessAction(changes);

		BatchProcessesManager manager = modelLayerFactory.getBatchProcessesManager();
		LogicalSearchCondition condition = fromAllSchemasIn(collection).where(IDENTIFIER).isIn(selectedRecordIds);
		BatchProcess process = manager.addBatchProcessInStandby(condition, action);
		manager.markAsPending(process);
	}

	@Override
	public List<MetadataVO> getMetadataAllowedInSort() {
		return getMetadataAllowedInSort(schemaTypeCode);
	}

	public List<MetadataVO> getMetadataAllowedInBatchEdit() {
		MetadataToVOBuilder builder = new MetadataToVOBuilder();

		List<MetadataVO> result = new ArrayList<>();
		for (Metadata metadata : types().getSchemaType(schemaTypeCode).getAllMetadatas().sortAscTitle()) {
			if (isBatchEditable(metadata)) {
				result.add(builder.build(metadata, view.getSessionContext()));
			}
		}
		return result;
	}

	@Override
	protected LogicalSearchCondition getSearchCondition() {
		if (condition == null) {
			try {
				buildSearchCondition();
			} catch (ConditionException e) {
				throw new RuntimeException("Unexpected exception (should be unreachable)", e);
			}
		}
		return condition;
	}

	void buildSearchCondition()
			throws ConditionException {
		MetadataSchemaType type = schemaType(schemaTypeCode);
		condition = (view.getSearchCriteria().isEmpty()) ?
				from(type).returnAll() :
				new ConditionBuilder(type).build(view.getSearchCriteria());
	}

	private boolean isBatchEditable(Metadata metadata) {
		return !metadata.isSystemReserved()
				&& !metadata.isUnmodifiable()
				&& metadata.isEnabled()
				&& !metadata.getType().isStructureOrContent()
				&& metadata.getDataEntry().getType() == DataEntryType.MANUAL
				&& isNotHidden(metadata)
				// XXX: Not supported in the backend
				&& metadata.getType() != MetadataValueType.ENUM
				;
	}

	private boolean isNotHidden(Metadata metadata) {
		MetadataDisplayConfig config = schemasDisplayManager().getMetadata(view.getCollection(), metadata.getCode());
		return config.getInputType() != MetadataInputType.HIDDEN;
	}

	public List<LabelTemplate> getTemplates() {
		LabelTemplateManager labelTemplateManager = appLayerFactory.getLabelTemplateManager();
		return labelTemplateManager.listTemplates(schemaTypeCode);
	}

	public Boolean computeStatistics() {
		return schemaTypeCode != null && schemaTypeCode.equals(Folder.SCHEMA_TYPE);
	}

	@Override
	public List<String> getSupportedReports() {
		List<String> supportedReports = super.getSupportedReports();
		ReportServices reportServices = new ReportServices(modelLayerFactory, collection);
		List<String> userReports = reportServices.getUserReportTitles(getCurrentUser(), view.getSchemaType());
		supportedReports.addAll(userReports);
		return supportedReports;
	}

	@Override
	public ReportBuilderFactory getReport(String reportTitle) {
		try {
			return super.getReport(reportTitle);
		} catch (UnknownReportRuntimeException e) {
			/**/
			return new SearchResultReportBuilderFactory(modelLayerFactory, view.getSelectedRecordIds(), view.getSchemaType(),
					collection, reportTitle, getCurrentUser(), getSearchQuery());
		}
	}

	public void addToCartRequested(List<String> recordIds) {
		// TODO: Create an extension for this
		RMSchemasRecordsServices rm = new RMSchemasRecordsServices(collection, modelLayerFactory);
		Cart cart = rm.getOrCreateUserCart(getCurrentUser());
		switch (schemaTypeCode) {
		case Folder.SCHEMA_TYPE:
			cart.addFolders(recordIds);
			break;
		case Document.SCHEMA_TYPE:
			cart.addDocuments(recordIds);
			break;
		case ContainerRecord.SCHEMA_TYPE:
			cart.addContainers(recordIds);
			break;
		}
		try {
			recordServices().add(cart);
			view.showMessage($("SearchView.addedToCart"));
		} catch (RecordServicesException e) {
			view.showErrorMessage($(e));
		}
	}

	@Override
	protected SavedSearch prepareSavedSearch(SavedSearch search) {
		return search.setSearchType(AdvancedSearchView.SEARCH_TYPE)
				.setSchemaFilter(schemaTypeCode)
				.setFreeTextSearch(searchExpression)
				.setAdvancedSearch(view.getSearchCriteria());
	}

	public Record getTemporarySearchRecord() {
		MetadataSchema schema = schema(SavedSearch.DEFAULT_SCHEMA);
		try {
			return searchServices().searchSingleResult(from(schema).where(schema.getMetadata(SavedSearch.USER))
					.isEqualTo(getCurrentUser())
					.andWhere(schema.getMetadata(SavedSearch.TEMPORARY)).isEqualTo(true)
					.andWhere(schema.getMetadata(SavedSearch.SEARCH_TYPE)).isEqualTo(AdvancedSearchView.SEARCH_TYPE));
		} catch (Exception e) {
			//TODO exception
			e.printStackTrace();
		}

		return null;
	}

	protected void saveTemporarySearch() {
		Record tmpSearchRecord = getTemporarySearchRecord();
		if (tmpSearchRecord == null) {
			tmpSearchRecord = recordServices().newRecordWithSchema(schema(SavedSearch.DEFAULT_SCHEMA));
		}

		SavedSearch search = new SavedSearch(tmpSearchRecord, types())
				.setTitle("temporaryAdvance")
				.setUser(getCurrentUser().getId())
				.setPublic(false)
				.setSortField(sortCriterion)
				.setSortOrder(SavedSearch.SortOrder.valueOf(sortOrder.name()))
				.setSelectedFacets(facetSelections.getNestedMap())
				.setTemporary(true)
				.setSearchType(AdvancedSearchView.SEARCH_TYPE)
				.setSchemaFilter(schemaTypeCode)
				.setFreeTextSearch(searchExpression)
				.setAdvancedSearch(view.getSearchCriteria())
				.setPageNumber(pageNumber);
		try {
			recordServices().update(search);
			view.navigate().to().advancedSearchReplay(search.getId());
		} catch (RecordServicesException e) {
			LOGGER.info("TEMPORARY SAVE ERROR", e);
		}
	}
}
