package com.constellio.app.modules.rm.ui.pages.folder;

import static com.constellio.app.modules.rm.constants.RMPermissionsTo.MANAGE_FOLDER_AUTHORIZATIONS;
import static com.constellio.app.modules.rm.constants.RMPermissionsTo.VIEW_FOLDER_AUTHORIZATIONS;
import static com.constellio.app.modules.tasks.model.wrappers.Task.STARRED_BY_USERS;
import static com.constellio.app.ui.i18n.i18n.$;
import static com.constellio.model.entities.security.global.AuthorizationDeleteRequest.authorizationDeleteRequest;
import static com.constellio.model.entities.security.global.AuthorizationModificationRequest.modifyAuthorizationOnRecord;
import static com.constellio.model.services.contents.ContentFactory.isFilename;
import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.from;
import static java.util.Arrays.asList;
import static org.apache.commons.lang.StringUtils.isNotBlank;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.constellio.app.api.extensions.params.DocumentFolderBreadCrumbParams;
import com.constellio.app.modules.rm.ConstellioRMModule;
import com.constellio.app.modules.rm.RMConfigs;
import com.constellio.app.modules.rm.constants.RMPermissionsTo;
import com.constellio.app.modules.rm.extensions.api.RMModuleExtensions;
import com.constellio.app.modules.rm.model.enums.DefaultTabInFolderDisplay;
import com.constellio.app.modules.rm.model.labelTemplate.LabelTemplate;
import com.constellio.app.modules.rm.navigation.RMNavigationConfiguration;
import com.constellio.app.modules.rm.navigation.RMViews;
import com.constellio.app.modules.rm.services.EmailParsingServices;
import com.constellio.app.modules.rm.services.RMSchemasRecordsServices;
import com.constellio.app.modules.rm.services.actions.FolderRecordActionsServices;
import com.constellio.app.modules.rm.services.borrowingServices.BorrowingServices;
import com.constellio.app.modules.rm.services.decommissioning.SearchType;
import com.constellio.app.modules.rm.services.events.RMEventsSearchServices;
import com.constellio.app.modules.rm.ui.builders.DocumentToVOBuilder;
import com.constellio.app.modules.rm.ui.builders.FolderToVOBuilder;
import com.constellio.app.modules.rm.ui.components.breadcrumb.FolderDocumentContainerBreadcrumbTrail;
import com.constellio.app.modules.rm.ui.components.breadcrumb.FolderDocumentContainerPresenterParam;
import com.constellio.app.modules.rm.ui.components.content.ConstellioAgentClickHandler;
import com.constellio.app.modules.rm.ui.entities.FolderVO;
import com.constellio.app.modules.rm.ui.pages.decommissioning.DecommissioningBuilderViewImpl;
import com.constellio.app.modules.rm.ui.pages.decommissioning.breadcrumb.DecommissionBreadcrumbTrail;
import com.constellio.app.modules.rm.ui.util.ConstellioAgentUtils;
import com.constellio.app.modules.rm.util.RMNavigationUtils;
import com.constellio.app.modules.rm.wrappers.Cart;
import com.constellio.app.modules.rm.wrappers.ContainerRecord;
import com.constellio.app.modules.rm.wrappers.Document;
import com.constellio.app.modules.rm.wrappers.ExternalLink;
import com.constellio.app.modules.rm.wrappers.Folder;
import com.constellio.app.modules.rm.wrappers.RMTask;
import com.constellio.app.modules.tasks.model.wrappers.BetaWorkflow;
import com.constellio.app.modules.tasks.model.wrappers.Task;
import com.constellio.app.modules.tasks.navigation.TaskViews;
import com.constellio.app.modules.tasks.services.BetaWorkflowServices;
import com.constellio.app.modules.tasks.services.TasksSchemasRecordsServices;
import com.constellio.app.services.factories.ConstellioFactories;
import com.constellio.app.ui.application.Navigation;
import com.constellio.app.ui.entities.AuthorizationVO;
import com.constellio.app.ui.entities.ContentVersionVO;
import com.constellio.app.ui.entities.ContentVersionVO.InputStreamProvider;
import com.constellio.app.ui.entities.FacetVO;
import com.constellio.app.ui.entities.MetadataSchemaVO;
import com.constellio.app.ui.entities.MetadataVO;
import com.constellio.app.ui.entities.RecordVO;
import com.constellio.app.ui.entities.RecordVO.VIEW_MODE;
import com.constellio.app.ui.framework.builders.AuthorizationToVOBuilder;
import com.constellio.app.ui.framework.builders.EventToVOBuilder;
import com.constellio.app.ui.framework.builders.MetadataSchemaToVOBuilder;
import com.constellio.app.ui.framework.builders.MetadataToVOBuilder;
import com.constellio.app.ui.framework.builders.RecordToVOBuilder;
import com.constellio.app.ui.framework.components.ComponentState;
import com.constellio.app.ui.framework.components.RMSelectionPanelReportPresenter;
import com.constellio.app.ui.framework.components.breadcrumb.BaseBreadcrumbTrail;
import com.constellio.app.ui.framework.data.RecordVODataProvider;
import com.constellio.app.ui.i18n.i18n;
import com.constellio.app.ui.pages.base.BaseView;
import com.constellio.app.ui.pages.base.SchemaPresenterUtils;
import com.constellio.app.ui.pages.base.SingleSchemaBasePresenter;
import com.constellio.app.ui.pages.search.SearchPresenter.SortOrder;
import com.constellio.app.ui.pages.search.SearchPresenterService;
import com.constellio.app.ui.params.ParamUtils;
import com.constellio.data.dao.services.bigVault.SearchResponseIterator;
import com.constellio.data.io.services.facades.IOServices;
import com.constellio.data.utils.KeySetMap;
import com.constellio.data.utils.dev.Toggle;
import com.constellio.model.entities.CorePermissions;
import com.constellio.model.entities.records.Content;
import com.constellio.model.entities.records.ContentVersion;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.records.Transaction;
import com.constellio.model.entities.records.wrappers.Authorization;
import com.constellio.model.entities.records.wrappers.Facet;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.entities.records.wrappers.structure.FacetType;
import com.constellio.model.entities.schemas.Metadata;
import com.constellio.model.entities.schemas.MetadataSchema;
import com.constellio.model.entities.schemas.MetadataSchemaType;
import com.constellio.model.entities.schemas.Schemas;
import com.constellio.model.entities.security.global.AuthorizationModificationRequest;
import com.constellio.model.extensions.ModelLayerCollectionExtensions;
import com.constellio.model.services.configs.SystemConfigurationsManager;
import com.constellio.model.services.contents.ContentManager;
import com.constellio.model.services.contents.ContentManager.UploadOptions;
import com.constellio.model.services.contents.ContentVersionDataSummary;
import com.constellio.model.services.contents.icap.IcapException;
import com.constellio.model.services.factories.ModelLayerFactory;
import com.constellio.model.services.logging.SearchEventServices;
import com.constellio.model.services.migrations.ConstellioEIMConfigs;
import com.constellio.model.services.records.RecordServices;
import com.constellio.model.services.records.RecordServicesException;
import com.constellio.model.services.records.RecordServicesException.ValidationException;
import com.constellio.model.services.records.RecordServicesRuntimeException;
import com.constellio.model.services.records.SchemasRecordsServices;
import com.constellio.model.services.schemas.MetadataSchemasManager;
import com.constellio.model.services.schemas.SchemaUtils;
import com.constellio.model.services.search.SearchServices;
import com.constellio.model.services.search.StatusFilter;
import com.constellio.model.services.search.query.ReturnedMetadatasFilter;
import com.constellio.model.services.search.query.logical.FunctionLogicalSearchQuerySort;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery;
import com.constellio.model.services.search.query.logical.LogicalSearchQueryFacetFilters;
import com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators;
import com.constellio.model.services.search.query.logical.LogicalSearchQuerySort;
import com.constellio.model.services.search.query.logical.QueryExecutionMethod;
import com.constellio.model.services.search.query.logical.condition.LogicalSearchCondition;
import com.constellio.model.services.security.AuthorizationsServices;
import com.constellio.model.services.thesaurus.ThesaurusManager;
import com.constellio.model.services.thesaurus.ThesaurusService;
import com.constellio.model.utils.Lazy;

public class DisplayFolderPresenter extends SingleSchemaBasePresenter<DisplayFolderView> {

	private static final int WAIT_ONE_SECOND = 1;
	private static final long NUMBER_OF_FOLDERS_IN_CART_LIMIT = 1000;
	private static Logger LOGGER = LoggerFactory.getLogger(DisplayFolderPresenter.class);

	RecordVODataProvider folderContentDataProvider;
	//	private RecordVODataProvider subFoldersDataProvider;
	//	private RecordVODataProvider documentsDataProvider;
	private RecordVODataProvider tasksDataProvider;
	private RecordVODataProvider eventsDataProvider;
	private MetadataSchemaToVOBuilder schemaVOBuilder = new MetadataSchemaToVOBuilder();
	private FolderToVOBuilder folderVOBuilder;
	private DocumentToVOBuilder documentVOBuilder;
	//	private ExternalLinkToVOBuilder externalLinkVOBuilder;
	private List<String> documentTitles = new ArrayList<>();

	private FolderVO summaryFolderVO;
	private Lazy<FolderVO> lazyFullFolderVO;

	private MetadataSchemaVO tasksSchemaVO;

	private transient RMConfigs rmConfigs;
	private transient RMSchemasRecordsServices rmSchemasRecordsServices;
	private transient BorrowingServices borrowingServices;
	private transient MetadataSchemasManager metadataSchemasManager;
	private transient RecordServices recordServices;
	private transient ModelLayerCollectionExtensions extensions;
	private transient RMModuleExtensions rmModuleExtensions;
	private transient ConstellioEIMConfigs eimConfigs;
	private String taxonomyCode;
	private User user;
	transient SearchPresenterService service;
	private SchemaPresenterUtils presenterUtilsForDocument;
	private FolderRecordActionsServices folderRecordActionsServices;

	protected RecordToVOBuilder voBuilder = new RecordToVOBuilder();

	private Set<String> selectedRecordIds = new HashSet<>();
	private Set<String> allRecordIds;

	Boolean allItemsSelected = false;

	Boolean allItemsDeselected = false;

	private boolean nestedView;

	private boolean applyButtonFacetEnabled = false;

	private boolean inWindow;

	private Map<String, String> params = null;

	KeySetMap<String, String> facetSelections = new KeySetMap<>();
	Map<String, Boolean> facetStatus = new HashMap<>();
	String sortCriterion;
	SortOrder sortOrder = SortOrder.ASCENDING;

	private RecordVO returnRecordVO;
	private Integer returnIndex;

	private AuthorizationsServices authorizationsServices;

	public DisplayFolderPresenter(DisplayFolderView view, RecordVO recordVO, boolean nestedView, boolean inWindow) {
		super(view, Folder.DEFAULT_SCHEMA);
		this.nestedView = nestedView;
		this.inWindow = inWindow;
		presenterUtilsForDocument = new SchemaPresenterUtils(Document.DEFAULT_SCHEMA, view.getConstellioFactories(), view.getSessionContext());
		authorizationsServices = new AuthorizationsServices(appLayerFactory.getModelLayerFactory());
		initTransientObjects();
		if (recordVO != null) {
			this.taxonomyCode = recordVO.getId();
			forParams(recordVO.getId());
		}
	}

	private void readObject(java.io.ObjectInputStream stream)
			throws IOException, ClassNotFoundException {
		stream.defaultReadObject();
		initTransientObjects();
	}

	private void initTransientObjects() {
		rmSchemasRecordsServices = new RMSchemasRecordsServices(collection, appLayerFactory);
		borrowingServices = new BorrowingServices(collection, modelLayerFactory);
		folderVOBuilder = new FolderToVOBuilder();
		documentVOBuilder = new DocumentToVOBuilder(modelLayerFactory);
		metadataSchemasManager = modelLayerFactory.getMetadataSchemasManager();
		recordServices = modelLayerFactory.newRecordServices();
		extensions = modelLayerFactory.getExtensions().forCollection(collection);
		rmModuleExtensions = appLayerFactory.getExtensions().forCollection(collection).forModule(ConstellioRMModule.ID);
		rmConfigs = new RMConfigs(modelLayerFactory.getSystemConfigurationsManager());
		eimConfigs = new ConstellioEIMConfigs(modelLayerFactory.getSystemConfigurationsManager());
		applyButtonFacetEnabled = getCurrentUser().isApplyFacetsEnabled();
		user = appLayerFactory.getModelLayerFactory().newUserServices().getUserInCollection(view.getSessionContext().getCurrentUser().getUsername(), collection);
		List<MetadataSchemaType> types = Arrays.asList(getFoldersSchemaType(), getDocumentsSchemaType());
		service = new SearchPresenterService(collection, user, modelLayerFactory, types);
		folderRecordActionsServices = new FolderRecordActionsServices(collection, appLayerFactory);
	}

	public RecordVODataProvider getFolderContentDataProvider() {
		return folderContentDataProvider;
	}

	public User getUser() {
		return user;
	}

	protected void setTaxonomyCode(String taxonomyCode) {
		this.taxonomyCode = taxonomyCode;
	}

	public RecordVO getRecordVOForDisplay() {
		return voBuilder.build(recordServices.realtimeGetRecordById(summaryFolderVO.getId()), VIEW_MODE.DISPLAY, view.getSessionContext());
	}

	@Override
	protected boolean hasPageAccess(String params, User user) {
		return true;
	}

	public void forParams(String params) {
		String id;

		Map<String, String> lParamsAsMap = ParamUtils.getParamsMap(params);
		if (lParamsAsMap.size() > 0) {
			this.params = ParamUtils.getParamsMap(params);
			id = this.params.get("id");
		} else {
			id = params;
			this.params = new HashMap<>();
			this.params.put("id", id);
		}

		view.getSessionContext().addVisited(id);

		String taxonomyCode = view.getUIContext().getAttribute(FolderDocumentContainerBreadcrumbTrail.TAXONOMY_CODE);
		this.setTaxonomyCode(taxonomyCode);
		view.setTaxonomyCode(taxonomyCode);

		Record summaryRecord;
		try {
			summaryRecord = modelLayerFactory.newRecordServices().realtimeGetRecordSummaryById(id);

		} catch (RecordServicesRuntimeException.NoSuchRecordWithId ignored) {
			summaryRecord = getRecord(id);
		}
		this.summaryFolderVO = folderVOBuilder.build(summaryRecord, VIEW_MODE.TABLE, view.getSessionContext());
		this.lazyFullFolderVO = new Lazy<FolderVO>() {
			@Override
			protected FolderVO load() {
				Record record = getRecord(id);
				return folderVOBuilder.build(record, VIEW_MODE.DISPLAY, view.getSessionContext());
			}
		};

		setSchemaCode(summaryRecord.getSchemaCode());
		view.setSummaryRecord(summaryFolderVO);

		MetadataSchemaVO folderSchemaVO = schemaVOBuilder.build(defaultSchema(), VIEW_MODE.TABLE, view.getSessionContext());
		MetadataSchema documentSchema = getDocumentsSchema();
		MetadataSchemaVO documentSchemaVO = schemaVOBuilder.build(documentSchema, VIEW_MODE.TABLE, view.getSessionContext());
		MetadataSchema externalLinkSchema = getExternalLinksSchema();
		MetadataSchemaVO externalLinkSchemaVO = schemaVOBuilder.build(externalLinkSchema, VIEW_MODE.TABLE, view.getSessionContext());

		Map<String, RecordToVOBuilder> voBuilders = new HashMap<>();
		voBuilders.put(folderSchemaVO.getCode(), folderVOBuilder);
		voBuilders.put(documentSchemaVO.getCode(), documentVOBuilder);
		voBuilders.put(externalLinkSchemaVO.getCode(), new RecordToVOBuilder());
		folderContentDataProvider = new RecordVODataProvider(Arrays.asList(folderSchemaVO, documentSchemaVO, externalLinkSchemaVO), voBuilders, modelLayerFactory, view.getSessionContext()) {
			@Override
			public LogicalSearchQuery getQuery() {
				return getFolderContentQuery(summaryFolderVO.getId(), false);
			}

			@Override
			public boolean isSearchCache() {
				return eimConfigs.isOnlySummaryMetadatasDisplayedInTables();
			}
		};

		tasksSchemaVO = schemaVOBuilder
				.build(getTasksSchema(), VIEW_MODE.TABLE, Arrays.asList(STARRED_BY_USERS), view.getSessionContext(), true);
		tasksDataProvider = new RecordVODataProvider(
				tasksSchemaVO, folderVOBuilder, modelLayerFactory, view.getSessionContext()) {
			@Override
			public LogicalSearchQuery getQuery() {
				LogicalSearchQuery query = getTasksQuery();

				if (searchServices().hasResults(query)) {
					addStarredSortToQuery(query);
					query.sortDesc(Schemas.MODIFIED_ON);
					return query;
				} else {
					return LogicalSearchQuery.returningNoResults();
				}
			}

			@Override
			protected void clearSort(LogicalSearchQuery query) {
				super.clearSort(query);
				addStarredSortToQuery(query);
			}
		};

		view.setFolderContent(folderContentDataProvider);
		view.setTasks(tasksDataProvider);
		
		if (folderContentDataProvider.size() < 15) {
			// Ugly workaround to avoid long grey zone under table
			view.setAllContentItemsVisible(true);
		}

		if (hasCurrentUserPermissionToViewEvents()) {
			eventsDataProvider = getEventsDataProvider();
			view.setEvents(eventsDataProvider);
		}
	}

	public Map<String, String> getParams() {
		return params;
	}

	public String getFolderId() {
		return summaryFolderVO.getId();
	}

	LogicalSearchQuery getDocumentsQuery() {

		RMSchemasRecordsServices rm = new RMSchemasRecordsServices(collection, appLayerFactory);
		Folder folder = rm.getFolderSummary(summaryFolderVO.getId());
		List<String> referencedDocuments = new ArrayList<>();
		for (Metadata folderMetadata : folder.getSchema().getMetadatas().onlyReferencesToType(Document.SCHEMA_TYPE)) {
			referencedDocuments.addAll(folder.getWrappedRecord().<String>getValues(folderMetadata));
		}

		LogicalSearchCondition condition = from(rm.document.schemaType())
				.where(rm.document.folder()).is(folder)
				.orWhere(rm.document.schema().getMetadata("linkedTo")).is(folder);

		if (!referencedDocuments.isEmpty()) {
			condition = condition.orWhere(Schemas.IDENTIFIER).isIn(referencedDocuments);
		}

		LogicalSearchQuery query = new LogicalSearchQuery(condition);
		query.filteredWithUser(getCurrentUser());
		query.filteredByStatus(StatusFilter.ACTIVES);
		query.sortAsc(Schemas.TITLE);
		return query;
	}

	private LogicalSearchQuery getFolderContentQuery(String folderId, boolean includeContentInHierarchy) {
		RMSchemasRecordsServices rm = new RMSchemasRecordsServices(collection, appLayerFactory);
		Folder folder = rm.getFolderSummary(folderId);

		List<String> referencedRecordIds = new ArrayList<>();
		for (Metadata folderMetadata : folder.getSchema().getMetadatas().onlyReferencesToType(Document.SCHEMA_TYPE)) {
			referencedRecordIds.addAll(folder.getWrappedRecord().<String>getValues(folderMetadata));
		}

		referencedRecordIds.addAll(folder.getExternalLinks());

		MetadataSchemaType folderSchemaType = getFoldersSchemaType();
		MetadataSchemaType documentSchemaType = getDocumentsSchemaType();
		MetadataSchemaType externalLinkSchemaType = getExternalLinksSchemaType();

		LogicalSearchQuery query = new LogicalSearchQuery();

		LogicalSearchCondition condition;
		if (includeContentInHierarchy) {
			condition = from(folderSchemaType, documentSchemaType, externalLinkSchemaType)
					.where(Schemas.PATH_PARTS).isContaining(asList(folder.getId()));
		} else {
			condition = from(folderSchemaType, documentSchemaType, externalLinkSchemaType)
					.where(rm.folder.parentFolder()).is(folder)
					.orWhere(rm.document.folder()).is(folder)
					.orWhere(rm.document.schema().getMetadata(Document.LINKED_TO)).is(folder);
		}

		if (!referencedRecordIds.isEmpty()) {
			condition = condition.orWhere(Schemas.IDENTIFIER).isIn(referencedRecordIds).andWhere(rm.externalLink.importedOn()).isNull();
		}
		query.setCondition(condition);

		service.configureQueryToComputeFacets(query);

		SchemasRecordsServices schemas = new SchemasRecordsServices(collection, modelLayerFactory);
		LogicalSearchQueryFacetFilters filters = query.getFacetFilters();
		filters.clear();
		for (Entry<String, Set<String>> selection : facetSelections.getMapEntries()) {
			try {
				Facet facet = schemas.getFacet(selection.getKey());
				if (!selection.getValue().isEmpty()) {
					if (facet.getFacetType() == FacetType.FIELD) {
						filters.selectedFieldFacetValues(facet.getFieldDataStoreCode(), selection.getValue());
					} else if (facet.getFacetType() == FacetType.QUERY) {
						filters.selectedQueryFacetValues(facet.getId(), selection.getValue());
					}
				}
			} catch (RecordServicesRuntimeException.NoSuchRecordWithId id) {
				LOGGER.warn("Facet '" + id + "' has been deleted");
			}
		}

		query.filteredWithUser(getCurrentUser());
		query.filteredByStatus(StatusFilter.ACTIVES);
		// Folder, Document

		if (eimConfigs.isOnlySummaryMetadatasDisplayedInTables()) {
			//The real sort will be done on shemaType
			query.sortDesc(Schemas.SCHEMA);
		} else {
			//Folders with schema_s:folder_default and with a value in folderType_s will be given the sort value 1
			//If a folder have both condition, it will still be given the sort value 1 because of the min function
			//Other records will be given the sort value 0,
			String sortReturningFoldersFirst = "min(sum(termfreq('schema_s', 'folder_default'),if(exists(folderType_s),1,0)),1)";
			query.sortOn(new FunctionLogicalSearchQuerySort(sortReturningFoldersFirst, false)).sortAsc(Schemas.TITLE);
		}


		addSortCriteriaForFolderContentQuery(query);

		if (eimConfigs.isOnlySummaryMetadatasDisplayedInTables()) {
			LogicalSearchQuery folderCacheableQuery = new LogicalSearchQuery(from(folderSchemaType)
					.where(rm.folder.parentFolder()).is(folder))
					.filteredWithUser(getCurrentUser())
					.filteredByStatus(StatusFilter.ACTIVES)
					.setReturnedMetadatas(ReturnedMetadatasFilter.onlySummaryFields());
			LogicalSearchQuery documentCacheableQuery = new LogicalSearchQuery(from(documentSchemaType)
					.where(rm.document.folder()).is(folder))
					.filteredWithUser(getCurrentUser())
					.filteredByStatus(StatusFilter.ACTIVES)
					.setReturnedMetadatas(ReturnedMetadatasFilter.onlySummaryFields());
			LogicalSearchQuery linkedDocumentsCacheableQuery = new LogicalSearchQuery(from(documentSchemaType)
					.where(rm.document.schema().getMetadata("linkedTo")).is(folder))
					.filteredWithUser(getCurrentUser())
					.filteredByStatus(StatusFilter.ACTIVES)
					.setReturnedMetadatas(ReturnedMetadatasFilter.onlySummaryFields());
			LogicalSearchQuery externalLinksCacheableQuery = new LogicalSearchQuery(from(externalLinkSchemaType)
					.where(Schemas.IDENTIFIER).isIn(referencedRecordIds).andWhere(rm.externalLink.importedOn()).isNull())
					.filteredByStatus(StatusFilter.ACTIVES)
					.setReturnedMetadatas(ReturnedMetadatasFilter.onlySummaryFields());

			addSortCriteriaForFolderContentQuery(folderCacheableQuery);
			addSortCriteriaForFolderContentQuery(documentCacheableQuery);
			addSortCriteriaForFolderContentQuery(linkedDocumentsCacheableQuery);
			addSortCriteriaForFolderContentQuery(externalLinksCacheableQuery);

			query.setCacheableQueries(asList(folderCacheableQuery, documentCacheableQuery, linkedDocumentsCacheableQuery, externalLinksCacheableQuery));
		}

		return query;
	}

	private void addSortCriteriaForFolderContentQuery(LogicalSearchQuery query) {
		if (sortCriterion == null) {
			query.setSkipSortingOverRecordSize(50);
			if (sortOrder == SortOrder.ASCENDING) {
				query.sortAsc(Schemas.TITLE);
			} else {
				query.sortDesc(Schemas.TITLE);
			}
		} else {
			query.setSkipSortingOverRecordSize(-1);
			Metadata metadata = getMetadata(sortCriterion);
			if (sortOrder == SortOrder.ASCENDING) {
				query.sortAsc(metadata);
			} else {
				query.sortDesc(metadata);
			}
		}
	}

	private LogicalSearchQuery getTasksQuery() {
		TasksSchemasRecordsServices tasks = new TasksSchemasRecordsServices(collection, appLayerFactory);
		Metadata taskFolderMetadata = tasks.userTask.schema().getMetadata(RMTask.LINKED_FOLDERS);
		LogicalSearchQuery query = new LogicalSearchQuery();
		query.setCondition(from(tasks.userTask.schemaType()).where(taskFolderMetadata).is(summaryFolderVO.getId()));
		query.filteredByStatus(StatusFilter.ACTIVES);
		query.filteredWithUser(getCurrentUser());

		return query;
	}

	public void selectInitialTabForUser() {
		SystemConfigurationsManager systemConfigurationsManager = modelLayerFactory.getSystemConfigurationsManager();
		RMConfigs rmConfigs = new RMConfigs(systemConfigurationsManager);

		String userDefaultTabInFolderDisplayCode = getCurrentUser().getDefaultTabInFolderDisplay();
		String configDefaultTabInFolderDisplayCode = rmConfigs.getDefaultTabInFolderDisplay();
		String defaultTabInFolderDisplayCode = StringUtils.isNotBlank(userDefaultTabInFolderDisplayCode) ?
											   userDefaultTabInFolderDisplayCode :
											   configDefaultTabInFolderDisplayCode;
		if (isNotBlank(defaultTabInFolderDisplayCode)) {
			if (DefaultTabInFolderDisplay.METADATA.getCode().equals(defaultTabInFolderDisplayCode)) {
				view.selectMetadataTab();
			} else if (DefaultTabInFolderDisplay.CONTENT.getCode().equals(defaultTabInFolderDisplayCode)) {
				view.selectFolderContentTab();
			}
		}
	}

	public BaseBreadcrumbTrail getBreadCrumbTrail() {
		return getBreadCrumbTrail(getParams(), view, getFolderId(), taxonomyCode, false);
	}

	public static BaseBreadcrumbTrail getBreadCrumbTrail(Map<String, String> params, BaseView view, String recordId,
														 String taxonomyCode, boolean forceBaseItemEnabled) {
		String saveSearchDecommissioningId = null;
		String searchTypeAsString = null;
		String favoritesId = null;

		if (params != null) {
			if (params.get("decommissioningSearchId") != null) {
				saveSearchDecommissioningId = params.get("decommissioningSearchId");
				view.getUIContext()
						.setAttribute(DecommissioningBuilderViewImpl.SAVE_SEARCH_DECOMMISSIONING, saveSearchDecommissioningId);
			}

			if (params.get("decommissioningType") != null) {
				searchTypeAsString = params.get("decommissioningType");
				view.getUIContext().setAttribute(DecommissioningBuilderViewImpl.DECOMMISSIONING_BUILDER_TYPE, searchTypeAsString);
			}
			favoritesId = params.get(RMViews.FAV_GROUP_ID_KEY);
		}

		SearchType searchType = null;
		if (searchTypeAsString != null) {
			searchType = SearchType.valueOf((searchTypeAsString));
		}
		BaseBreadcrumbTrail breadcrumbTrail;

		RMModuleExtensions rmModuleExtensions = view.getConstellioFactories().getAppLayerFactory().getExtensions()
				.forCollection(view.getCollection()).forModule(ConstellioRMModule.ID);
		breadcrumbTrail = rmModuleExtensions
				.getBreadCrumbtrail(new DocumentFolderBreadCrumbParams(recordId, params, view));

		if (breadcrumbTrail != null) {
			return breadcrumbTrail;
		} else if (favoritesId != null) {
			return new FolderDocumentContainerBreadcrumbTrail(new FolderDocumentContainerPresenterParam(recordId, null, null, favoritesId, view, forceBaseItemEnabled));
		} else if (saveSearchDecommissioningId == null) {
			String containerId = null;
			if (params != null && params instanceof Map) {
				containerId = params.get("containerId");
			}
			return new FolderDocumentContainerBreadcrumbTrail(new FolderDocumentContainerPresenterParam(recordId, taxonomyCode, containerId,
					null, view, forceBaseItemEnabled));
		} else {
			return new DecommissionBreadcrumbTrail($("DecommissioningBuilderView.viewTitle." + searchType.name()),
					searchType, saveSearchDecommissioningId, recordId, view, true);
		}
	}

	public int getFolderContentCount() {
		return folderContentDataProvider.size();
	}

	public int getTaskCount() {
		LogicalSearchQuery query = new LogicalSearchQuery(tasksDataProvider.getQuery());
		query.setQueryExecutionMethod(QueryExecutionMethod.USE_CACHE);
		return (int) searchServices().getResultsCount(query);
	}

	public RecordVODataProvider getWorkflows() {
		MetadataSchemaVO schemaVO = new MetadataSchemaToVOBuilder().build(
				schema(BetaWorkflow.DEFAULT_SCHEMA), VIEW_MODE.TABLE, view.getSessionContext());

		return new RecordVODataProvider(schemaVO, new RecordToVOBuilder(), modelLayerFactory, view.getSessionContext()) {
			@Override
			public LogicalSearchQuery getQuery() {
				return new BetaWorkflowServices(view.getCollection(), appLayerFactory).getWorkflowsQuery();
			}
		};
	}

	public void workflowStartRequested(RecordVO record) {
		Map<String, List<String>> parameters = new HashMap<>();
		parameters.put(RMTask.LINKED_FOLDERS, asList(summaryFolderVO.getId()));
		BetaWorkflow workflow = new TasksSchemasRecordsServices(view.getCollection(), appLayerFactory)
				.getBetaWorkflow(record.getId());
		new BetaWorkflowServices(view.getCollection(), appLayerFactory).start(workflow, getCurrentUser(), parameters);
	}

	@Override
	protected boolean hasRestrictedRecordAccess(String params, User user, Record restrictedRecord) {
		return user.hasReadAccess().on(restrictedRecord);
	}

	@Override
	protected List<String> getRestrictedRecordIds(String params) {
		return asList(summaryFolderVO.getId());
	}

	private void disableMenuItems(Folder folder) {
		if (!folder.isLogicallyDeletedStatus()) {
			User user = getCurrentUser();
			view.setDisplayButtonState(getDisplayButtonState(user, folder));
			view.setEditButtonState(getEditButtonState(user, folder));
			view.setAddSubfolderButtonState(getAddSubFolderButtonState(user, folder));
			view.setAddDocumentButtonState(getAddDocumentButtonState(user, folder));
			view.setBorrowedMessage(getBorrowMessageState(folder));
		}
	}

	String getBorrowMessageState(Folder folder) {
		String borrowedMessage = null;
		if (folder.getBorrowed() != null && folder.getBorrowed()) {
			String borrowUserEntered = folder.getBorrowUserEntered();
			if (borrowUserEntered != null) {
				String userTitle = rmSchemasRecordsServices.getUser(borrowUserEntered).getTitle();
				LocalDateTime borrowDateTime = folder.getBorrowDate();
				LocalDate borrowDate = borrowDateTime != null ? borrowDateTime.toLocalDate() : null;
				borrowedMessage = $("DisplayFolderView.borrowedFolder", userTitle, borrowDate);
			} else {
				borrowedMessage = $("DisplayFolderView.borrowedByNullUserFolder");
			}
		} else if (folder.getContainer() != null) {
			try {
				ContainerRecord containerRecord = rmSchemasRecordsServices.getContainerRecord(folder.getContainer());
				boolean borrowed = Boolean.TRUE.equals(containerRecord.getBorrowed());
				String borrower = containerRecord.getBorrower();
				if (borrowed && borrower != null) {
					String userTitle = rmSchemasRecordsServices.getUser(borrower).getTitle();
					LocalDate borrowDate = containerRecord.getBorrowDate();
					borrowedMessage = $("DisplayFolderView.borrowedContainer", userTitle, borrowDate);
				} else if (borrowed) {
					borrowedMessage = $("DisplayFolderView.borrowedByNullUserContainer");
				}
			} catch (Exception e) {
				LOGGER.error("Could not find linked container");
			}
		}
		return borrowedMessage;
	}

	ComponentState getDisplayButtonState(User user, Folder folder) {
		if (view.isInWindow()) {
			return ComponentState.INVISIBLE;
		} else {
			return ComponentState.visibleIf(nestedView && user.hasReadAccess().on(folder));
		}
	}

	ComponentState getEditButtonState(User user, Folder folder) {
		return ComponentState.visibleIf(folderRecordActionsServices.isEditActionPossible(folder.getWrappedRecord(), user));
	}

	ComponentState getAddDocumentButtonState(User user, Folder folder) {
		return ComponentState.visibleIf(folderRecordActionsServices.isAddDocumentActionPossible(folder.getWrappedRecord(), user));
	}

	ComponentState getAddSubFolderButtonState(User user, Folder folder) {
		return ComponentState.visibleIf(folderRecordActionsServices.isAddSubFolderActionPossible(folder.getWrappedRecord(), user));
	}

	private MetadataSchemaType getFoldersSchemaType() {
		return schemaType(Folder.SCHEMA_TYPE);
	}

	private MetadataSchemaType getDocumentsSchemaType() {
		return schemaType(Document.SCHEMA_TYPE);
	}

	private MetadataSchema getFoldersSchema() {
		return schema(Folder.DEFAULT_SCHEMA);
	}

	private MetadataSchema getDocumentsSchema() {
		return schema(Document.DEFAULT_SCHEMA);
	}

	private MetadataSchema getExternalLinksSchema() {
		return schema(ExternalLink.DEFAULT_SCHEMA);
	}

	private MetadataSchema getTasksSchema() {
		return schema(Task.DEFAULT_SCHEMA);
	}

	private MetadataSchemaType getExternalLinksSchemaType() {
		return schemaType(ExternalLink.SCHEMA_TYPE);
	}

	public void viewAssembled() {
		RMSchemasRecordsServices schemas = new RMSchemasRecordsServices(collection, appLayerFactory);
		Folder folder = schemas.getFolder(summaryFolderVO.getId());
		disableMenuItems(folder);
		view.setDragRowsEnabled(isVisibleSubFolder());

		modelLayerFactory.newLoggingServices().logRecordView(folder.getWrappedRecord(), getCurrentUser());

		view.setFolderContent(folderContentDataProvider);
		view.setTasks(tasksDataProvider);
		view.setEvents(eventsDataProvider);

		selectInitialTabForUser();
	}

	boolean isVisibleSubFolder() {
		SearchServices searchServices = searchServices();
		Record record = summaryFolderVO.getRecord();
		MetadataSchemaType foldersSchemaType = getFoldersSchemaType();
		MetadataSchema foldersSchema = getFoldersSchema();
		Metadata parentFolderMetadata = foldersSchema.getMetadata(Folder.PARENT_FOLDER);
		LogicalSearchQuery query = new LogicalSearchQuery();
		query.setCondition(from(foldersSchemaType).where(parentFolderMetadata).is(record));
		query.filteredWithUser(getCurrentUser());
		query.filteredByStatus(StatusFilter.ACTIVES);
		return searchServices.hasResults(query);
	}

	public void updateTaskStarred(boolean isStarred, String taskId, RecordVODataProvider dataProvider) {
		TasksSchemasRecordsServices taskSchemas = new TasksSchemasRecordsServices(collection, appLayerFactory);
		Task task = taskSchemas.getTask(taskId);
		if (isStarred) {
			task.addStarredBy(getCurrentUser().getId());
		} else {
			task.removeStarredBy(getCurrentUser().getId());
		}
		try {
			recordServices().update(task);
		} catch (RecordServicesException e) {
			e.printStackTrace();
		}
		dataProvider.fireDataRefreshEvent();
	}

	private Navigation navigate() {
		return view.navigate();
	}

	public void backButtonClicked() {
		navigate().to().previousView();
	}

	public void addDocumentButtonClicked() {
		navigate().to(RMViews.class).addDocument(summaryFolderVO.getId());
	}

	public void addSubfolderButtonClicked() {
		navigate().to(RMViews.class).addFolder(summaryFolderVO.getId());
	}

	public void navigateToSelf() {
		navigateToFolder(this.summaryFolderVO.getId());
	}

	public void displayFolderButtonClicked() {
		navigateToSelf();
	}

	public void editFolderButtonClicked() {
		RMNavigationUtils.navigateToEditFolder(summaryFolderVO.getId(), params, appLayerFactory, collection);
	}

	public void documentClicked(RecordVO recordVO) {
		ContentVersionVO contentVersionVO = recordVO.get(Document.CONTENT);
		if (contentVersionVO == null) {
			navigateToDocument(recordVO);
			return;
		}
		String agentURL = ConstellioAgentUtils.getAgentURL(recordVO, contentVersionVO);
		if (agentURL != null) {
			//			view.openAgentURL(agentURL);
			new ConstellioAgentClickHandler().handleClick(agentURL, recordVO, contentVersionVO, params);
		} else {
			navigateToDocument(recordVO);
		}
	}

	protected void navigateToDocument(RecordVO recordVO) {
		RMNavigationUtils.navigateToDisplayDocument(recordVO.getId(), params, appLayerFactory,
				collection);
	}

	protected void navigateToFolder(String folderId) {
		RMNavigationUtils.navigateToDisplayFolder(folderId, params, appLayerFactory, collection);
	}

	public void taskClicked(RecordVO taskVO) {
		navigate().to(TaskViews.class).displayTask(taskVO.getId());
	}

	private RMSchemasRecordsServices rmSchemasRecordsServices() {
		return new RMSchemasRecordsServices(getCurrentUser().getCollection(), appLayerFactory);
	}

	private List<String> getAllDocumentTitles() {
		if (documentTitles != null) {
			return documentTitles;
		} else {
			//TODO replace with SearchServices.stream in Constellio 9.0
			documentTitles = new ArrayList<>();
			RMSchemasRecordsServices rm = new RMSchemasRecordsServices(collection, appLayerFactory);
			LogicalSearchQuery query = new LogicalSearchQuery()
					.setCondition(from(rm.document.schemaType()).where(rm.document.folder()).is(summaryFolderVO.getId()))
					.filteredByStatus(StatusFilter.ACTIVES)
					.setReturnedMetadatas(ReturnedMetadatasFilter.onlyMetadatas(Schemas.TITLE));

			List<Record> documents = modelLayerFactory.newSearchServices().search(query);
			for (Record document : documents) {
				documentTitles.add(document.getId());
			}
			return documentTitles;
		}
	}

	private SearchResponseIterator<Record> getExistingDocumentInCurrentFolder(String fileName) {

		MetadataSchemaType documentsSchemaType = getDocumentsSchemaType();
		MetadataSchema documentsSchema = getDocumentsSchema();

		Metadata folderMetadata = documentsSchema.getMetadata(Document.FOLDER);
		LogicalSearchQuery query = new LogicalSearchQuery();
		LogicalSearchCondition queryCondition = from(documentsSchemaType).where(folderMetadata).is(summaryFolderVO.getId())
				.andWhere(Schemas.LOGICALLY_DELETED_STATUS).isFalseOrNull().andWhere(rmSchemasRecordsServices.documentContent()).is(isFilename(fileName));
		query.setCondition(queryCondition);

		SearchServices searchServices = modelLayerFactory.newSearchServices();

		SearchResponseIterator<Record> speQueryResponse = searchServices.recordsIterator(query, 100);

		return speQueryResponse;
	}

	private Record currentFullFolder() {
		return recordServices.getDocumentById(getLazyFullFolderVO().getId());
	}

	public void contentVersionUploaded(ContentVersionVO uploadedContentVO) {
		view.selectFolderContentTab();
		String fileName = uploadedContentVO.getFileName();
		SearchResponseIterator<Record> existingDocument = getExistingDocumentInCurrentFolder(fileName);
		if (existingDocument.getNumFound() == 0 && !extensions.isModifyBlocked(currentFullFolder(), getCurrentUser())) {
			try {
				if (Boolean.TRUE.equals(uploadedContentVO.hasFoundDuplicate())) {
					RMSchemasRecordsServices rm = new RMSchemasRecordsServices(collection, appLayerFactory);
					LogicalSearchQuery duplicateDocumentsQuery = new LogicalSearchQuery()
							.setCondition(LogicalSearchQueryOperators.from(rm.documentSchemaType())
									.where(rm.document.contentHashes()).isEqualTo(uploadedContentVO.getDuplicatedHash())
							).filteredByStatus(StatusFilter.ACTIVES)
							.setReturnedMetadatas(ReturnedMetadatasFilter.onlyMetadatas(Schemas.IDENTIFIER, Schemas.TITLE))
							.setNumberOfRows(100).filteredWithUser(getCurrentUser());
					List<Document> duplicateDocuments = rm.searchDocuments(duplicateDocumentsQuery);
					if (duplicateDocuments.size() > 0) {
						StringBuilder message = new StringBuilder(
								$("ContentManager.hasFoundDuplicateWithConfirmation", StringUtils.defaultIfBlank(fileName, "")));
						message.append("<br>");
						for (Document document : duplicateDocuments) {
							message.append("<br>-");
							message.append(document.getTitle());
							message.append(": ");
							message.append(generateDisplayLink(document));
						}
						view.showClickableMessage(message.toString());
					}
				}
				uploadedContentVO.setMajorVersion(true);
				Document document;
				if (rmSchemasRecordsServices().isEmail(fileName)) {
					InputStreamProvider inputStreamProvider = uploadedContentVO.getInputStreamProvider();
					InputStream in = inputStreamProvider.getInputStream(DisplayFolderPresenter.class + ".contentVersionUploaded");
					document = new EmailParsingServices(rmSchemasRecordsServices).newEmail(fileName, in);
				} else {
					document = rmSchemasRecordsServices.newDocument();
				}
				document.setFolder(summaryFolderVO.getId());
				document.setTitle(fileName);
				InputStream inputStream = null;
				ContentVersionDataSummary contentVersionDataSummary;
				try {
					inputStream = uploadedContentVO.getInputStreamProvider().getInputStream("SchemaPresenterUtils-VersionInputStream");
					UploadOptions options = new UploadOptions().setFileName(fileName);
					ContentManager.ContentVersionDataSummaryResponse uploadResponse = uploadContent(inputStream, options);
					contentVersionDataSummary = uploadResponse.getContentVersionDataSummary();
					document.setContent(appLayerFactory.getModelLayerFactory().getContentManager().createMajor(getCurrentUser(), fileName, contentVersionDataSummary));
					Transaction transaction = new Transaction();
					transaction.add(document);
					transaction.setUser(getCurrentUser());
					appLayerFactory.getModelLayerFactory().newRecordServices().executeWithoutImpactHandling(transaction);
					documentTitles.add(document.getTitle());
				} finally {
					IOServices ioServices = modelLayerFactory.getIOServicesFactory().newIOServices();
					ioServices.closeQuietly(inputStream);
				}
			} catch (final IcapException e) {
				view.showErrorMessage(e.getMessage());
			} catch (ValidationException e) {
				List<String> errorMessages = i18n.asListOfMessages(e.getErrors().getValidationErrors());
				for (String msg : errorMessages) {
					view.showErrorMessage(msg);
				}
				LOGGER.error(e.getMessage(), e);
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			} finally {
				view.clearUploadField();
			}
		} else if (existingDocument.getNumFound() > 0) {
			StringBuilder message = new StringBuilder();

			while (existingDocument.hasNext()) {
				Record currentRecord = existingDocument.next();
				Content content = currentRecord.get(rmSchemasRecordsServices.document.content());
				ContentVersion contentVersion = content.getCurrentVersion();
				if (contentVersion.getHash() != null && !uploadedContentVO.getHash().equals(contentVersion.getHash())) {
					if (!hasWritePermission(currentRecord)) {
						message.append($("displayFolderView.noWritePermission", currentRecord) + "</br>");
					} else if (isCheckedOutByOtherUser(currentRecord)) {
						message.append($("displayFolderView.checkoutByAnOtherUser", currentRecord) + "</br>");
					} else {
						view.showVersionUpdateWindow(voBuilder.build(currentRecord,
								VIEW_MODE.DISPLAY, view.getSessionContext()), uploadedContentVO);
					}
				} else {
					message.append($("displayfolderview.unchangeFile", currentRecord.getTitle()) + "</br>");
				}
			}
			if (message.length() > 0) {
				view.showErrorMessage(message.toString());
			}
		}
		folderContentDataProvider.fireDataRefreshEvent();
		view.refreshFolderContentTab();
	}

	private boolean hasWritePermission(Record record) {
		User currentUser = presenterUtilsForDocument.getCurrentUser();
		return currentUser.hasWriteAccess().on(record);
	}

	private boolean isCheckedOutByOtherUser(Record recordVO) {
		Content content = recordVO.get(rmSchemasRecordsServices.document.content());
		if (recordVO.getTypeCode().equals(Document.SCHEMA_TYPE) && content != null) {
			User currentUser = presenterUtilsForDocument.getCurrentUser();
			String checkOutUserId = content.getCheckoutUserId();
			return checkOutUserId != null && !checkOutUserId.equals(currentUser.getId());
		} else {
			return false;
		}
	}

	public List<LabelTemplate> getDefaultTemplates() {
		return appLayerFactory.getLabelTemplateManager().listTemplates(Folder.SCHEMA_TYPE);
	}

	public RecordVODataProvider getEventsDataProvider() {
		final MetadataSchemaVO eventSchemaVO = schemaVOBuilder
				.build(rmSchemasRecordsServices.eventSchema(), VIEW_MODE.TABLE, view.getSessionContext());
		return new RecordVODataProvider(eventSchemaVO, new EventToVOBuilder(), modelLayerFactory, view.getSessionContext()) {
			@Override
			public LogicalSearchQuery getQuery() {
				RMEventsSearchServices rmEventsSearchServices = new RMEventsSearchServices(modelLayerFactory, collection);
				return rmEventsSearchServices.newFindEventByRecordIDQuery(getCurrentUser(), summaryFolderVO.getId());
			}
		};
	}

	void sharesTabSelected() {
		view.selectSharesTab();
	}

	protected boolean hasCurrentUserPermissionToViewEvents() {
		Folder folder = rmSchemasRecordsServices.getFolderSummary(summaryFolderVO.getId());
		return getCurrentUser().has(CorePermissions.VIEW_EVENTS).on(folder);
	}

	void metadataTabSelected() {
		view.selectMetadataTab();
	}

	void folderContentTabSelected() {
		view.selectFolderContentTab();
	}

	void tasksTabSelected() {
		if (tasksDataProvider == null) {
			tasksDataProvider = new RecordVODataProvider(
					tasksSchemaVO, folderVOBuilder, modelLayerFactory, view.getSessionContext()) {
				@Override
				public LogicalSearchQuery getQuery() {
					LogicalSearchQuery query = getTasksQuery();
					addStarredSortToQuery(query);
					query.sortDesc(Schemas.MODIFIED_ON);
					return query;
				}

				@Override
				public boolean isSearchCache() {
					return eimConfigs.isOnlySummaryMetadatasDisplayedInTables();
				}

				@Override
				protected void clearSort(LogicalSearchQuery query) {
					super.clearSort(query);
					addStarredSortToQuery(query);
				}
			};
			view.setTasks(tasksDataProvider);
		}

		view.selectTasksTab();
	}

	void eventsTabSelected() {
		view.selectEventsTab();
	}

	public boolean hasCurrentUserPermissionToUseCartGroup() {
		return getCurrentUser().has(RMPermissionsTo.USE_GROUP_CART).globally();
	}

	public boolean isSelected(RecordVO recordVO) {
		return allItemsSelected || selectedRecordIds.contains(recordVO.getId());
	}

	public boolean isFacetApplyButtonEnabled() {
		return getCurrentUser().isApplyFacetsEnabled();
	}

	public void recordSelectionChanged(RecordVO recordVO, Boolean selected) {
		String recordId = recordVO.getId();
		if (allItemsSelected && !selected) {
			allItemsSelected = false;

			for (String currentRecordId : getOrFetchAllRecordIds()) {
				if (!selectedRecordIds.contains(currentRecordId)) {
					selectedRecordIds.add(currentRecordId);
					allRecordIds.add(currentRecordId);
				}
			}
		} else if (selected) {
			if (allItemsDeselected) {
				allItemsDeselected = false;
			}
			selectedRecordIds.add(recordId);
			if (selectedRecordIds.size() == getOrFetchAllRecordIds().size()) {
				allItemsSelected = true;
			}
		} else {
			selectedRecordIds.remove(recordId);
			if (!allItemsSelected && selectedRecordIds.isEmpty()) {
				allItemsDeselected = true;
			}
		}
	}

	private Set<String> getOrFetchAllRecordIds() {
		if (allRecordIds == null) {
			List<String> allRecordIdsList = searchServices().searchRecordIds(getFolderContentQuery(summaryFolderVO.getId(), false));
			allRecordIds = new HashSet<>(allRecordIdsList);
		}

		return allRecordIds;
	}

	boolean isAllItemsSelected() {
		return allItemsSelected;
	}

	boolean isAllItemsDeselected() {
		return allItemsDeselected;
	}

	void selectAllClicked() {
		allItemsSelected = true;
		allItemsDeselected = false;
	}

	void deselectAllClicked() {
		allItemsSelected = false;
		allItemsDeselected = true;
		selectedRecordIds.clear();
	}

	String generateDisplayLink(Document document) {
		String constellioUrl = eimConfigs.getConstellioUrl();
		String displayURL = RMNavigationConfiguration.DISPLAY_DOCUMENT;
		String url = constellioUrl + "#!" + displayURL + "/" + document.getId();
		return "<a href=\"" + url + "\">" + url + "</a>";
	}

	public boolean isLogicallyDeleted() {
		return Boolean.TRUE
				.equals(summaryFolderVO.getMetadataValue(summaryFolderVO.getMetadata(Schemas.LOGICALLY_DELETED_STATUS.getLocalCode()))
						.getValue());
	}

	private void addStarredSortToQuery(LogicalSearchQuery query) {
		Metadata metadata = types().getSchema(Task.DEFAULT_SCHEMA).getMetadata(STARRED_BY_USERS);
		LogicalSearchQuerySort sortField = new FunctionLogicalSearchQuerySort(
				"termfreq(" + metadata.getDataStoreCode() + ",\'" + getCurrentUser().getId() + "\')", false);
		query.sortFirstOn(sortField);
	}

	public RMSelectionPanelReportPresenter buildReportPresenter() {
		return new RMSelectionPanelReportPresenter(appLayerFactory, collection, getCurrentUser()) {
			@Override
			public String getSelectedSchemaType() {
				return Folder.SCHEMA_TYPE;
			}

			@Override
			public List<String> getSelectedRecordIds() {
				return asList(summaryFolderVO.getId());
			}
		};
	}

	public void uploadWindowClosed() {
		//documentsDataProvider.fireDataRefreshEvent();
		folderContentDataProvider.fireDataRefreshEvent();
		view.refreshFolderContentTab();
	}

	public void facetValueSelected(String facetId, String facetValue) {
		facetSelections.get(facetId).add(facetValue);
		folderContentDataProvider.fireDataRefreshEvent();
		view.refreshFolderContentAndFacets();
	}

	public void facetValuesChanged(KeySetMap<String, String> facets) {
		facetSelections.clear();
		facetSelections.addAll(facets);
		folderContentDataProvider.fireDataRefreshEvent();
		view.refreshFolderContentAndFacets();
	}

	public void facetValueDeselected(String facetId, String facetValue) {
		facetSelections.get(facetId).remove(facetValue);
		folderContentDataProvider.fireDataRefreshEvent();
		view.refreshFolderContentAndFacets();
	}

	public void facetDeselected(String facetId) {
		facetSelections.get(facetId).clear();
		folderContentDataProvider.fireDataRefreshEvent();
		view.refreshFolderContentAndFacets();
	}

	public void facetOpened(String facetId) {
		facetStatus.put(facetId, true);
	}

	public void facetClosed(String facetId) {
		facetStatus.put(facetId, false);
	}

	public KeySetMap<String, String> getFacetSelections() {
		return facetSelections;
	}

	public void setFacetSelections(Map<String, Set<String>> facetSelections) {
		this.facetSelections.putAll(facetSelections);
	}

	public void sortCriterionSelected(String sortCriterion, SortOrder sortOrder) {
		this.sortCriterion = sortCriterion;
		this.sortOrder = sortOrder;
		folderContentDataProvider.fireDataRefreshEvent();
		view.refreshFolderContent();
	}

	public List<FacetVO> getFacets(RecordVODataProvider dataProvider) {
		//Call #1
		if (dataProvider == null /* || dataProvider.getFieldFacetValues() == null */) {
			return service.getFacets(getFolderContentQuery(summaryFolderVO.getId(), false), facetStatus, getCurrentLocale());
		} else {
			return service.buildFacetVOs(dataProvider.getFieldFacetValues(), dataProvider.getQueryFacetsValues(),
					facetStatus, getCurrentLocale());
		}
	}

	public String getSortCriterion() {
		return sortCriterion;
	}

	public SortOrder getSortOrder() {
		return sortOrder;
	}

	protected List<MetadataVO> getMetadataAllowedInSort(String schemaTypeCode) {
		MetadataSchemaType schemaType = schemaType(schemaTypeCode);
		return getMetadataAllowedInSort(schemaType);
	}

	protected List<MetadataVO> getMetadataAllowedInSort(MetadataSchemaType schemaType) {
		MetadataToVOBuilder builder = new MetadataToVOBuilder();

		List<MetadataVO> result = new ArrayList<>();
		for (Metadata metadata : schemaType.getAllMetadatas()) {
			if (metadata.isSortable()) {
				result.add(builder.build(metadata, view.getSessionContext()));
			}
		}
		return result;
	}

	public List<MetadataVO> getMetadataAllowedInSort() {
		List<MetadataSchemaType> schemaTypes = new ArrayList<>();
		schemaTypes.add(rmSchemasRecordsServices.folderSchemaType());
		schemaTypes.add(rmSchemasRecordsServices.documentSchemaType());
		return getCommonMetadataAllowedInSort(schemaTypes);
	}

	private List<MetadataVO> getCommonMetadataAllowedInSort(List<MetadataSchemaType> schemaTypes) {
		List<MetadataVO> result = new ArrayList<>();
		Set<String> resultCodes = new HashSet<>();
		for (MetadataSchemaType metadataSchemaType : schemaTypes) {
			for (MetadataVO metadata : getMetadataAllowedInSort(metadataSchemaType)) {
				if (resultCodes.add(metadata.getLocalCode())) {
					result.add(metadata);
				}
			}
		}
		return result;
	}

	public String getSortCriterionValueAmong(List<MetadataVO> sortableMetadata) {
		if (this.sortCriterion == null) {
			return null;
		}
		if (!this.sortCriterion.startsWith("global_")) {
			return this.sortCriterion;
		} else {
			String localCode = new SchemaUtils().getLocalCodeFromMetadataCode(this.sortCriterion);
			for (MetadataVO metadata : sortableMetadata) {
				if (metadata.getLocalCode().equals(localCode)) {
					return metadata.getCode();
				}
			}
		}
		return this.sortCriterion;
	}


	public List<Cart> getOwnedCarts() {
		return rmSchemasRecordsServices().wrapCarts(searchServices().search(new LogicalSearchQuery(from(rmSchemasRecordsServices().cartSchema()).where(rmSchemasRecordsServices().cart.owner())
				.isEqualTo(getCurrentUser().getId())).sortAsc(Schemas.TITLE)));
	}

	public MetadataSchemaVO getSchema() {
		return new MetadataSchemaToVOBuilder().build(schema(Cart.DEFAULT_SCHEMA), RecordVO.VIEW_MODE.TABLE, view.getSessionContext());
	}

	public void itemClicked(RecordVO recordVO, Integer index) {
		this.returnIndex = index;
		this.returnRecordVO = recordVO;
	}

	public Integer getReturnIndex() {
		return returnIndex;
	}

	public RecordVO getReturnRecordVO() {
		return returnRecordVO;
	}

	public FolderVO getSummaryFolderVO() {
		return summaryFolderVO;
	}

	public FolderVO getLazyFullFolderVO() {
		return lazyFullFolderVO.get();
	}


	protected boolean isOwnAuthorization(Authorization authorization) {
		return authorization.getTarget().equals(getFolderId());
	}

	private boolean isSharedByCurrentUser(Authorization authorization) {
		return getCurrentUser().getId().equals(authorization.getSharedBy());
	}

	private List<Authorization> getAllAuthorizations() {
		Record record = presenterService().getRecord(getFolderId());
		return authorizationsServices.getRecordAuthorizations(record);
	}

	private AuthorizationToVOBuilder newAuthorizationToVOBuilder() {
		return new AuthorizationToVOBuilder(modelLayerFactory);
	}


	public List<AuthorizationVO> getSharedAuthorizations() {
		AuthorizationToVOBuilder builder = newAuthorizationToVOBuilder();

		List<AuthorizationVO> results = new ArrayList<>();
		for (Authorization authorization : getAllAuthorizations()) {
			if (isOwnAuthorization(authorization) && authorization.getSharedBy() != null &&
				(isSharedByCurrentUser(authorization) || user.hasAny(RMPermissionsTo.MANAGE_SHARE, VIEW_FOLDER_AUTHORIZATIONS, MANAGE_FOLDER_AUTHORIZATIONS).on(summaryFolderVO.getRecord()))) {
				results.add(builder.build(authorization));
			}
		}
		return results;
	}


	public void onAutorizationModified(AuthorizationVO authorizationVO) {
		AuthorizationModificationRequest request = toAuthorizationModificationRequest(authorizationVO);
		authorizationsServices.execute(request);
		sharesTabSelected();
	}

	private AuthorizationModificationRequest toAuthorizationModificationRequest(AuthorizationVO authorizationVO) {
		String authId = authorizationVO.getAuthId();

		AuthorizationModificationRequest request = modifyAuthorizationOnRecord(authId, collection, getFolderId());
		request = request.withNewAccessAndRoles(authorizationVO.getAccessRoles());
		request = request.withNewStartDate(authorizationVO.getStartDate());
		request = request.withNewEndDate(authorizationVO.getEndDate());

		List<String> principals = new ArrayList<>();
		principals.addAll(authorizationVO.getUsers());
		principals.addAll(authorizationVO.getGroups());
		request = request.withNewPrincipalIds(principals);
		request = request.setExecutedBy(getCurrentUser());

		return request;

	}

	public void deleteAutorizationButtonClicked(AuthorizationVO authorizationVO) {
		Authorization authorization = authorizationsServices.getAuthorization(
				view.getCollection(), authorizationVO.getAuthId());
		authorizationsServices.execute(authorizationDeleteRequest(authorization).setExecutedBy(getCurrentUser()));
		view.removeAuthorization(authorizationVO);
	}


	public void changeFolderContentDataProvider(String searchValue, Boolean includeTree) {
		final String value = searchValue.endsWith("*") ? searchValue : searchValue + "*";
		MetadataSchemaVO foldersSchemaVO = schemaVOBuilder.build(defaultSchema(), VIEW_MODE.TABLE, view.getSessionContext());
		MetadataSchema documentsSchema = getDocumentsSchema();
		MetadataSchemaVO documentsSchemaVO = schemaVOBuilder.build(documentsSchema, VIEW_MODE.TABLE, view.getSessionContext());
		Map<String, RecordToVOBuilder> voBuilders = new HashMap<>();
		voBuilders.put(foldersSchemaVO.getCode(), folderVOBuilder);
		voBuilders.put(documentsSchemaVO.getCode(), documentVOBuilder);
		folderContentDataProvider = new RecordVODataProvider(Arrays.asList(foldersSchemaVO, documentsSchemaVO), voBuilders, modelLayerFactory, view.getSessionContext()) {
			@Override
			public LogicalSearchQuery getQuery() {
				String userSearchExpression = filterSolrOperators(value);
				if (!StringUtils.isBlank(value)) {
					LogicalSearchQuery logicalSearchQuery;
					logicalSearchQuery = getFolderContentQuery(summaryFolderVO.getId(), includeTree).setFreeTextQuery(userSearchExpression);
					if (!"*".equals(value)) {
						logicalSearchQuery.setHighlighting(true);
					}
					return logicalSearchQuery;
				} else {
					return getFolderContentQuery(summaryFolderVO.getId(), false);
				}
			}
		};
		view.setFolderContent(folderContentDataProvider);
		folderContentTabSelected();
	}

	public void clearSearch() {
		MetadataSchemaVO foldersSchemaVO = schemaVOBuilder.build(defaultSchema(), VIEW_MODE.TABLE, view.getSessionContext());
		MetadataSchema documentsSchema = getDocumentsSchema();
		MetadataSchemaVO documentsSchemaVO = schemaVOBuilder.build(documentsSchema, VIEW_MODE.TABLE, view.getSessionContext());
		Map<String, RecordToVOBuilder> voBuilders = new HashMap<>();
		voBuilders.put(foldersSchemaVO.getCode(), folderVOBuilder);
		voBuilders.put(documentsSchemaVO.getCode(), documentVOBuilder);
		folderContentDataProvider = new RecordVODataProvider(Arrays.asList(foldersSchemaVO, documentsSchemaVO), voBuilders, modelLayerFactory, view.getSessionContext()) {
			@Override
			public LogicalSearchQuery getQuery() {
				return getFolderContentQuery(summaryFolderVO.getId(), false);
			}
		};
		view.setFolderContent(folderContentDataProvider);
		folderContentTabSelected();
	}

	protected String filterSolrOperators(String expression) {
		String userSearchExpression = expression;

		if (StringUtils.isNotBlank(userSearchExpression) && userSearchExpression.startsWith("\"") && userSearchExpression.endsWith("\"")) {
			userSearchExpression = ClientUtils.escapeQueryChars(userSearchExpression);
			userSearchExpression = "\"" + userSearchExpression + "\"";
		}

		return userSearchExpression;
	}

	public int getAutocompleteBufferSize() {
		ConstellioFactories constellioFactories = ConstellioFactories.getInstance();
		ModelLayerFactory modelLayerFactory = constellioFactories.getModelLayerFactory();
		return modelLayerFactory.getSystemConfigs().getAutocompleteSize();
	}

	public List<String> getAutocompleteSuggestions(String text) {
		List<String> suggestions = new ArrayList<>();
		if (Toggle.ADVANCED_SEARCH_CONFIGS.isEnabled()) {
			int minInputLength = 3;
			int maxResults = 10;
			String[] excludedRequests = new String[0];
			String collection = view.getCollection();

			SearchEventServices searchEventServices = new SearchEventServices(collection, modelLayerFactory);
			ThesaurusManager thesaurusManager = modelLayerFactory.getThesaurusManager();
			ThesaurusService thesaurusService = thesaurusManager.get(collection);

			List<String> statsSuggestions = searchEventServices
					.getMostPopularQueriesAutocomplete(text, maxResults, excludedRequests);
			suggestions.addAll(statsSuggestions);
			if (thesaurusService != null && statsSuggestions.size() < maxResults) {
				int thesaurusMaxResults = maxResults - statsSuggestions.size();
				List<String> thesaurusSuggestions = thesaurusService
						.suggestSimpleSearch(text, view.getSessionContext().getCurrentLocale(), minInputLength,
								thesaurusMaxResults, true, searchEventServices);
				suggestions.addAll(thesaurusSuggestions);
			}
		}
		return suggestions;
	}

	void recordsDroppedOn(List<RecordVO> droppedRecordVOs, RecordVO targetFolderRecordVO) {
		if (!droppedRecordVOs.isEmpty()) {
			Transaction transaction = new Transaction(getCurrentUser());

			for (RecordVO droppedRecordVO : droppedRecordVOs) {
				if (!getCurrentUser().hasWriteAccess().on(droppedRecordVO.getRecord()) ||
					!getCurrentUser().hasWriteAccess().on(targetFolderRecordVO.getRecord())) {
					return;
				}

				if (droppedRecordVO.getRecord().isOfSchemaType(Folder.SCHEMA_TYPE)) {
					Folder folder = rmSchemasRecordsServices.getFolder(droppedRecordVO.getId());
					folder.setParentFolder(targetFolderRecordVO.getId());
					transaction.update(folder.getWrappedRecord());
				} else if (droppedRecordVO.getRecord().isOfSchemaType(Document.SCHEMA_TYPE)) {
					Document document = rmSchemasRecordsServices.getDocument(droppedRecordVO.getId());
					document.setFolder(targetFolderRecordVO.getId());
					transaction.update(document.getWrappedRecord());
				} else if (droppedRecordVO.getRecord().isOfSchemaType(ExternalLink.SCHEMA_TYPE)) {
					Folder currentFolder = rmSchemasRecordsServices.wrapFolder(getLazyFullFolderVO().getRecord());
					currentFolder.removeExternalLink(droppedRecordVO.getId());

					Folder targetFolder = rmSchemasRecordsServices.getFolder(targetFolderRecordVO.getId());
					targetFolder.addExternalLink(droppedRecordVO.getId());
					transaction.addAll(currentFolder, targetFolder);
				}
			}
			try {
				recordServices().execute(transaction);
				folderContentDataProvider.fireDataRefreshEvent();
				view.refreshFolderContentTab();
				view.closeViewerPanel();
			} catch (Exception e) {
				LOGGER.warn("Error while dropping record(s) " + droppedRecordVOs + " on folder " + targetFolderRecordVO.getId(), e);
				view.showErrorMessage(e.getMessage());
			}
		}
	}
}
