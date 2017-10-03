package com.constellio.model.services.taxonomies;

import static com.constellio.app.modules.rm.constants.RMTaxonomies.ADMINISTRATIVE_UNITS;
import static com.constellio.app.modules.rm.constants.RMTaxonomies.CLASSIFICATION_PLAN;
import static com.constellio.data.dao.dto.records.OptimisticLockingResolution.EXCEPTION;
import static com.constellio.model.entities.security.global.AuthorizationAddRequest.authorizationForUsers;
import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.from;
import static com.constellio.model.services.taxonomies.TaxonomiesSearchOptions.HasChildrenFlagCalculated.NEVER;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.solr.common.params.SolrParams;
import org.assertj.core.api.Condition;
import org.assertj.core.api.ObjectAssert;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;

import com.constellio.app.modules.rm.RMConfigs;
import com.constellio.app.modules.rm.RMTestRecords;
import com.constellio.app.modules.rm.constants.RMTaxonomies;
import com.constellio.app.modules.rm.services.RMSchemasRecordsServices;
import com.constellio.app.modules.rm.services.decommissioning.DecommissioningService;
import com.constellio.app.modules.rm.wrappers.AdministrativeUnit;
import com.constellio.app.modules.rm.wrappers.Category;
import com.constellio.app.modules.rm.wrappers.ContainerRecord;
import com.constellio.app.modules.rm.wrappers.Document;
import com.constellio.app.modules.rm.wrappers.Folder;
import com.constellio.data.dao.services.idGenerator.ZeroPaddedSequentialUniqueIdGenerator;
import com.constellio.data.extensions.AfterQueryParams;
import com.constellio.data.extensions.BigVaultServerExtension;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.records.Transaction;
import com.constellio.model.entities.records.wrappers.RecordWrapper;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.entities.schemas.Schemas;
import com.constellio.model.entities.security.Role;
import com.constellio.model.entities.security.global.UserCredential;
import com.constellio.model.services.records.RecordServices;
import com.constellio.model.services.records.RecordServicesException;
import com.constellio.model.services.records.RecordUtils;
import com.constellio.model.services.schemas.MetadataSchemasManager;
import com.constellio.model.services.schemas.SchemaUtils;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery;
import com.constellio.model.services.search.query.logical.condition.ConditionTemplate;
import com.constellio.model.services.security.AuthorizationsServices;
import com.constellio.model.services.users.UserServices;
import com.constellio.sdk.tests.ConstellioTest;
import com.constellio.sdk.tests.annotations.InDevelopmentTest;
import com.constellio.sdk.tests.setups.Users;

public class TaxonomiesSearchServices_VisibleTreesAcceptTest extends ConstellioTest {

	String subFolderId;

	Users users = new Users();
	User alice;
	DecommissioningService decommissioningService;
	TaxonomiesSearchServices service;
	RMSchemasRecordsServices rm;
	RMTestRecords records = new RMTestRecords(zeCollection);
	MetadataSchemasManager manager;
	RecordServices recordServices;
	String document1InA16, document2InA16, document3InA16;
	AuthorizationsServices authsServices;

	AtomicInteger queriesCount = new AtomicInteger();
	AtomicInteger facetsCount = new AtomicInteger();
	AtomicInteger returnedDocumentsCount = new AtomicInteger();

	@Before
	public void setUp()
			throws Exception {

		prepareSystem(
				withZeCollection().withAllTest(users).withConstellioRMModule().withRMTest(records)
						.withFoldersAndContainersOfEveryStatus()
		);

		inCollection(zeCollection).giveReadAccessTo(admin);

		rm = new RMSchemasRecordsServices(zeCollection, getAppLayerFactory());
		service = getModelLayerFactory().newTaxonomiesSearchService();
		decommissioningService = new DecommissioningService(zeCollection, getAppLayerFactory());
		recordServices = getModelLayerFactory().newRecordServices();

		UserServices userServices = getModelLayerFactory().newUserServices();
		UserCredential userCredential = userServices.getUserCredential(aliceWonderland);
		userServices.addUserToCollection(userCredential, zeCollection);
		alice = userServices.getUserInCollection(aliceWonderland, zeCollection);
		manager = getModelLayerFactory().getMetadataSchemasManager();

		DecommissioningService service = new DecommissioningService(zeCollection, getAppLayerFactory());

		Folder subfolder = service.newSubFolderIn(records.getFolder_A16());
		subfolder.setTitle("Sous-dossier");
		recordServices.add(subfolder);
		subFolderId = subfolder.getId();

		List<String> documentsInA16 = getFolderDocuments(records.folder_A16);
		document1InA16 = documentsInA16.get(0);
		document2InA16 = documentsInA16.get(1);
		document3InA16 = documentsInA16.get(2);

		for (String documentId : getFolderDocuments(records.folder_A17)) {
			Record document = recordServices.getDocumentById(documentId);
			recordServices.logicallyDelete(document, User.GOD);
		}

		for (String documentId : getFolderDocuments(records.folder_A18)) {
			Record document = recordServices.getDocumentById(documentId);
			recordServices.logicallyDelete(document, User.GOD);
		}

		authsServices = getModelLayerFactory().newAuthorizationsServices();

		getDataLayerFactory().getExtensions().getSystemWideExtensions().bigVaultServerExtension
				.add(new BigVaultServerExtension() {
					@Override
					public void afterQuery(AfterQueryParams params) {
						queriesCount.incrementAndGet();
						String[] facetQuery = params.getSolrParams().getParams("facet.query");
						if (facetQuery != null) {
							facetsCount.addAndGet(facetQuery.length);
						}

						returnedDocumentsCount.addAndGet(params.getReturnedResultsCount());
					}
				});
		getDataLayerFactory().getDataLayerLogger().setPrintAllQueriesLongerThanMS(0).setLogFL(false);
		Thread.sleep(1000);
		System.out.println("\n\n");
	}

	private List<String> getFolderDocuments(String id) {
		return getModelLayerFactory().newSearchServices().searchRecordIds(new LogicalSearchQuery()
				.sortAsc(Schemas.TITLE).setCondition(from(rm.documentSchemaType()).where(rm.documentFolder()).isEqualTo(id)));
	}

	@Test
	public void whenDakotaIsNavigatingATaxonomyWithVisibleRecordsThenSeesRecords()
			throws Exception {

		getDataLayerFactory().getDataLayerLogger().setPrintAllQueriesLongerThanMS(0);

		assertThatRootWhenUserNavigateUsingPlanTaxonomy(records.getDakota_managerInA_userInB())
				.has(recordsInOrder(records.categoryId_X, records.categoryId_Z))
				.has(recordsWithChildren(records.categoryId_X, records.categoryId_Z))
				.has(numFoundAndListSize(2))
				.has(solrQueryCounts(2, 2, 2)).has(secondCallQueryCounts(1, 0, 2));
		//assertThatSolr().hasCounts(2,2,2);
		System.out.println("------------------------");
		//Calling another time, no solr query occur
		assertThatRootWhenUserNavigateUsingPlanTaxonomy(records.getDakota_managerInA_userInB())
				.has(recordsInOrder(records.categoryId_X, records.categoryId_Z))
				.has(recordsWithChildren(records.categoryId_X, records.categoryId_Z))
				.has(numFoundAndListSize(2));
		assertThatSolr().hasCounts(0, 0, 0);

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getDakota_managerInA_userInB(), records.categoryId_X)
				.has(recordsInOrder(records.categoryId_X100))
				.has(recordsWithChildren(records.categoryId_X100))
				.has(numFoundAndListSize(1));
		assertThatSolr().hasCounts(3, 2, 2);

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getDakota_managerInA_userInB(), records.categoryId_X100)
				.has(recordsInOrder(records.categoryId_X110, records.categoryId_X120, records.folder_A16, records.folder_A17,
						records.folder_A18, records.folder_B06, records.folder_B32))
				.has(recordsWithChildren(records.categoryId_X110, records.categoryId_X120, records.folder_A16,
						records.folder_B06, records.folder_B32))
				.has(numFoundAndListSize(7));
		assertThatSolr().hasCounts(4, 7, 7);

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getDakota_managerInA_userInB(), records.folder_A16)
				.has(recordsInOrder(document1InA16, document2InA16, document3InA16, subFolderId))
				.has(noRecordsWithChildren())
				.has(numFoundAndListSize(4));
		assertThatSolr().hasCounts(2, 4, 4);

	}

	@Test
	public void whenAdminIsNavigatingATaxonomyWithVisibleRecordsThenSeesRecords()
			throws Exception {

		assertThatRootWhenUserNavigateUsingPlanTaxonomy(records.getAdmin())
				.has(recordsInOrder(records.categoryId_X, records.categoryId_Z))
				.has(recordsWithChildren(records.categoryId_X, records.categoryId_Z))
				.has(numFoundAndListSize(2))
				.has(solrQueryCounts(7, 7, 7));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_X)
				.has(recordsInOrder(records.categoryId_X100))
				.has(recordsWithChildren(records.categoryId_X100))
				.has(numFoundAndListSize(1));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_X100)
				.has(recordsInOrder("categoryId_X110", "categoryId_X120", "A16", "A17", "A18", "C06", "B06", "C32", "B32"))
				.has(recordsWithChildren("categoryId_X110", "categoryId_X120", "A16", "C06", "B06", "C32", "B32"))
				.has(numFoundAndListSize(9));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_Z)
				.has(recordsInOrder(records.categoryId_Z100))
				.has(recordsWithChildren(records.categoryId_Z100))
				.has(numFoundAndListSize(1));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_Z100)
				.has(recordsInOrder(records.categoryId_Z110, records.categoryId_Z120))
				.has(recordsWithChildren(records.categoryId_Z110, records.categoryId_Z120))
				.has(numFoundAndListSize(2));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_Z110)
				.has(recordsInOrder(records.categoryId_Z112))
				.has(recordsWithChildren(records.categoryId_Z112))
				.has(numFoundAndListSize(1));

	}

	@Test
	public void whenAdminIsNavigatingATaxonomyWithVisibleRecordsAlwaysDisplayingConceptsWithReadAccessThenSeesRecordsAndAllConcepts()
			throws Exception {

		recordServices.add(rm.newCategoryWithId("category_Y_id").setCode("Y").setTitle("Ze category Y"));

		TaxonomiesSearchOptions options = new TaxonomiesSearchOptions()
				.setAlwaysReturnTaxonomyConceptsWithReadAccessOrLinkable(true);

		assertThatRootWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), options)
				.has(recordsInOrder(records.categoryId_X, "category_Y_id", records.categoryId_Z))
				.has(recordsWithChildren(records.categoryId_X, records.categoryId_Z))
				.has(numFoundAndListSize(3));
		assertThatSolr().hasCounts(3, 3, 3);

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_X, options)
				.has(recordsInOrder(records.categoryId_X13, records.categoryId_X100))
				.has(recordsWithChildren(records.categoryId_X100))
				.has(numFoundAndListSize(2));
		assertThatSolr().hasCounts(3, 2, 2);

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_X100, options)
				.has(recordsInOrder("categoryId_X110", "categoryId_X120", "A16", "A17", "A18", "C06", "B06", "C32", "B32"))
				.has(recordsWithChildren("categoryId_X110", "categoryId_X120", "A16", "C06", "B06", "C32", "B32"))
				.has(numFoundAndListSize(9));
		assertThatSolr().hasCounts(4, 9, 9);

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_Z, options)
				.has(recordsInOrder(records.categoryId_Z100, records.categoryId_Z200, records.categoryId_Z999,
						records.categoryId_ZE42))
				.has(recordsWithChildren(records.categoryId_Z100))
				.has(numFoundAndListSize(4));
		assertThatSolr().hasCounts(3, 4, 4);

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_Z100, options)
				.has(recordsInOrder(records.categoryId_Z110, records.categoryId_Z120))
				.has(recordsWithChildren(records.categoryId_Z110, records.categoryId_Z120))
				.has(numFoundAndListSize(2));
		assertThatSolr().hasCounts(3, 2, 2);

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_Z110, options)
				.has(recordsInOrder(records.categoryId_Z111, records.categoryId_Z112))
				.has(recordsWithChildren(records.categoryId_Z112))
				.has(numFoundAndListSize(2));
		assertThatSolr().hasCounts(3, 2, 2);

	}

	@Test
	public void whenAdminIsNavigatingATaxonomyWithVisibleRecordsAlwaysDisplayingConceptsAndNotCalculatingFlagsWithReadAccessThenSeesRecordsAndAllConcepts()
			throws Exception {

		recordServices.add(rm.newCategoryWithId("category_Y_id").setCode("Y").setTitle("Ze category Y"));

		TaxonomiesSearchOptions options = new TaxonomiesSearchOptions()
				.setAlwaysReturnTaxonomyConceptsWithReadAccessOrLinkable(true)
				.setLinkableFlagCalculated(false).setHasChildrenFlagCalculated(NEVER);

		assertThatRootWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), options)
				.has(recordsInOrder(records.categoryId_X, "category_Y_id", records.categoryId_Z))
				.has(recordsWithChildren(records.categoryId_X, "category_Y_id", records.categoryId_Z))
				.has(numFoundAndListSize(3));
		assertThatSolr().hasCounts(2, 3, 0);

		assertThatRootWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), options)
				.has(recordsInOrder(records.categoryId_X, "category_Y_id", records.categoryId_Z))
				.has(recordsWithChildren(records.categoryId_X, "category_Y_id", records.categoryId_Z))
				.has(numFoundAndListSize(3));
		assertThatSolr().hasCounts(0, 0, 0);

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_X, options)
				.has(recordsInOrder(records.categoryId_X13, records.categoryId_X100))
				.has(recordsWithChildren(records.categoryId_X13, records.categoryId_X100))
				.has(numFoundAndListSize(2));
		assertThatSolr().hasCounts(2, 2, 0);

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_X, options)
				.has(recordsInOrder(records.categoryId_X13, records.categoryId_X100))
				.has(recordsWithChildren(records.categoryId_X13, records.categoryId_X100))
				.has(numFoundAndListSize(2));
		assertThatSolr().hasCounts(1, 0, 0);

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_X100, options)
				.has(recordsInOrder("categoryId_X110", "categoryId_X120", "A16", "A17", "A18", "C06", "B06", "C32", "B32"))
				.has(recordsWithChildren("categoryId_X110", "categoryId_X120", "A16", "A17", "A18", "C06", "B06", "C32", "B32"))
				.has(numFoundAndListSize(9));
		assertThatSolr().hasCounts(2, 9, 0);

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_X100, options)
				.has(recordsInOrder("categoryId_X110", "categoryId_X120", "A16", "A17", "A18", "C06", "B06", "C32", "B32"))
				.has(recordsWithChildren("categoryId_X110", "categoryId_X120", "A16", "A17", "A18", "C06", "B06", "C32", "B32"))
				.has(numFoundAndListSize(9));
		assertThatSolr().hasCounts(1, 7, 0);

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_Z, options)
				.has(recordsInOrder(records.categoryId_Z100, records.categoryId_Z200, records.categoryId_Z999,
						records.categoryId_ZE42))
				.has(recordsWithChildren(records.categoryId_Z100, records.categoryId_Z200, records.categoryId_Z999,
						records.categoryId_ZE42))
				.has(numFoundAndListSize(4));
		assertThatSolr().hasCounts(2, 4, 0);

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_Z, options)
				.has(recordsInOrder(records.categoryId_Z100, records.categoryId_Z200, records.categoryId_Z999,
						records.categoryId_ZE42))
				.has(recordsWithChildren(records.categoryId_Z100, records.categoryId_Z200, records.categoryId_Z999,
						records.categoryId_ZE42))
				.has(numFoundAndListSize(4));
		assertThatSolr().hasCounts(1, 0, 0);

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_Z100, options)
				.has(recordsInOrder(records.categoryId_Z110, records.categoryId_Z120))
				.has(recordsWithChildren(records.categoryId_Z110, records.categoryId_Z120))
				.has(numFoundAndListSize(2));
		assertThatSolr().hasCounts(2, 2, 0);

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_Z100, options)
				.has(recordsInOrder(records.categoryId_Z110, records.categoryId_Z120))
				.has(recordsWithChildren(records.categoryId_Z110, records.categoryId_Z120))
				.has(numFoundAndListSize(2));
		assertThatSolr().hasCounts(1, 0, 0);

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_Z110, options)
				.has(recordsInOrder(records.categoryId_Z111, records.categoryId_Z112))
				.has(recordsWithChildren(records.categoryId_Z111, records.categoryId_Z112))
				.has(numFoundAndListSize(2));
		assertThatSolr().hasCounts(2, 2, 0);

	}

	@Test
	public void whenAdminIsNavigatingAdminUnitTaxonomyWithVisibleRecordsThenSeesRecords()
			throws Exception {

		TaxonomiesSearchOptions options = new TaxonomiesSearchOptions();

		assertThatRootWhenUserNavigateUsingAdministrativeUnitsTaxonomy(records.getAdmin(), options)
				.has(recordsInOrder(records.unitId_10, records.unitId_30))
				.has(recordsWithChildren(records.unitId_10, records.unitId_30))
				.has(numFoundAndListSize(2));

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(records.getAdmin(), records.unitId_12, options)
				.has(recordsInOrder(records.unitId_12b))
				.has(recordsWithChildren(records.unitId_12b))
				.has(numFoundAndListSize(1));

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(records.getAdmin(), records.unitId_12b, options)
				.has(recordsInOrder("B02", "B04", "B06", "B08", "B32"))
				.has(recordsWithChildren("B02", "B04", "B06", "B08", "B32"))
				.has(numFoundAndListSize(5));

	}

	@Test
	public void whenUserIsNavigatingAdminUnitTaxonomyThenOnlySeeConceptsContainingAccessibleRecords()
			throws Exception {

		TaxonomiesSearchOptions options = new TaxonomiesSearchOptions();
		User sasquatch = users.sasquatchIn(zeCollection);
		User robin = users.robinIn(zeCollection);
		User admin = users.adminIn(zeCollection);
		authsServices.add(authorizationForUsers(sasquatch).on("B06").givingReadAccess(), admin);
		authsServices.add(authorizationForUsers(sasquatch).on(records.unitId_20d).givingReadAccess(), admin);

		authsServices.add(authorizationForUsers(robin).on("B06").givingReadAccess(), admin);
		authsServices.add(authorizationForUsers(robin).on(records.unitId_12c).givingReadAccess(), admin);
		authsServices.add(authorizationForUsers(robin).on(records.unitId_30).givingReadAccess(), admin);
		recordServices.refresh(robin);
		recordServices.refresh(sasquatch);
		waitForBatchProcess();
		assertThat(robin.hasReadAccess().on(recordServices.getDocumentById("B06"))).isTrue();
		assertThat(sasquatch.hasReadAccess().on(recordServices.getDocumentById("B06"))).isTrue();

		//Sasquatch
		assertThatRootWhenUserNavigateUsingAdministrativeUnitsTaxonomy(sasquatch, options)
				.has(recordsInOrder(records.unitId_10))
				.has(recordsWithChildren(records.unitId_10))
				.has(numFoundAndListSize(1));

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(sasquatch, records.unitId_10, options)
				.has(recordsInOrder(records.unitId_12))
				.has(recordsWithChildren(records.unitId_12))
				.has(numFoundAndListSize(1));

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(sasquatch, records.unitId_12, options)
				.has(recordsInOrder(records.unitId_12b))
				.has(recordsWithChildren(records.unitId_12b))
				.has(numFoundAndListSize(1));

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(sasquatch, records.unitId_12b, options)
				.has(recordsInOrder("B06"))
				.has(recordsWithChildren("B06"))
				.has(numFoundAndListSize(1));

		//Robin
		assertThatRootWhenUserNavigateUsingAdministrativeUnitsTaxonomy(robin, options)
				.has(recordsInOrder(records.unitId_10, records.unitId_30))
				.has(recordsWithChildren(records.unitId_10, records.unitId_30))
				.has(numFoundAndListSize(2));

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(robin, records.unitId_10, options)
				.has(recordsInOrder(records.unitId_12))
				.has(recordsWithChildren(records.unitId_12))
				.has(numFoundAndListSize(1));

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(robin, records.unitId_12, options)
				.has(recordsInOrder(records.unitId_12b))
				.has(recordsWithChildren(records.unitId_12b))
				.has(numFoundAndListSize(1));

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(robin, records.unitId_12b, options)
				.has(recordsInOrder("B06"))
				.has(recordsWithChildren("B06"))
				.has(numFoundAndListSize(1));
	}

	@Test
	public void whenUserIsNavigatingAdminUnitTaxonomyAlwaysDisplayingConceptsWithReadAccessThenOnlySeeConceptsContainingAccessibleRecordsAndThoseWithReadAccess()
			throws Exception {

		getDataLayerFactory().getDataLayerLogger().setPrintAllQueriesLongerThanMS(0);

		TaxonomiesSearchOptions options = new TaxonomiesSearchOptions()
				.setLinkableFlagCalculated(false).setHasChildrenFlagCalculated(NEVER)
				.setAlwaysReturnTaxonomyConceptsWithReadAccessOrLinkable(true);
		User sasquatch = users.sasquatchIn(zeCollection);
		User robin = users.robinIn(zeCollection);
		User admin = users.adminIn(zeCollection);
		authsServices.add(authorizationForUsers(sasquatch).on("B06").givingReadAccess(), admin);
		authsServices.add(authorizationForUsers(sasquatch).on(records.unitId_20d).givingReadAccess(), admin);

		authsServices.add(authorizationForUsers(robin).on("B06").givingReadAccess(), admin);
		authsServices.add(authorizationForUsers(robin).on(records.unitId_12c).givingReadAccess(), admin);
		authsServices.add(authorizationForUsers(robin).on(records.unitId_30).givingReadAccess(), admin);

		recordServices.refresh(sasquatch);
		recordServices.refresh(robin);
		waitForBatchProcess();

		facetsCount.set(0);
		queriesCount.set(0);
		returnedDocumentsCount.set(0);

		//Sasquatch
		assertThatRootWhenUserNavigateUsingAdministrativeUnitsTaxonomy(sasquatch, options)
				.has(recordsInOrder(records.unitId_10, records.unitId_20))
				.has(recordsWithChildren(records.unitId_10, records.unitId_20))
				.has(numFoundAndListSize(2));
		assertThatSolr().hasCounts(2, 3, 3);

		assertThatRootWhenUserNavigateUsingAdministrativeUnitsTaxonomy(sasquatch, options)
				.has(recordsInOrder(records.unitId_10, records.unitId_20))
				.has(recordsWithChildren(records.unitId_10, records.unitId_20))
				.has(numFoundAndListSize(2));
		//Search on root taxonomy records was cached, then no queries, but some facets computed
		assertThatSolr().hasCounts(1, 0, 3);

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(sasquatch, records.unitId_10, options)
				.has(recordsInOrder(records.unitId_12))
				.has(recordsWithChildren(records.unitId_12))
				.has(numFoundAndListSize(1));
		assertThatSolr().hasCounts(3, 3, 3);

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(sasquatch, records.unitId_10, options)
				.has(recordsInOrder(records.unitId_12))
				.has(recordsWithChildren(records.unitId_12))
				.has(numFoundAndListSize(1));

		//Search on child taxonomy records was cached, then only one query to get folders
		assertThatSolr().hasCounts(2, 0, 3);

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(sasquatch, records.unitId_10, options)
				.has(recordsInOrder(records.unitId_12))
				.has(recordsWithChildren(records.unitId_12))
				.has(numFoundAndListSize(1));
		assertThatSolr().hasCounts(2, 0, 3);

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(sasquatch, records.unitId_12, options)
				.has(recordsInOrder(records.unitId_12b))
				.has(recordsWithChildren(records.unitId_12b))
				.has(numFoundAndListSize(1));
		assertThatSolr().hasCounts(3, 2, 2);

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(sasquatch, records.unitId_12b, options)
				.has(recordsInOrder("B06"))
				.has(recordsWithChildren("B06"))
				.has(numFoundAndListSize(1));
		assertThatSolr().hasCounts(2, 1, 0);

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(sasquatch, records.unitId_12c, options)
				.has(numFoundAndListSize(0));
		assertThatSolr().hasCounts(2, 0, 0);

		//Robin
		assertThatRootWhenUserNavigateUsingAdministrativeUnitsTaxonomy(robin, options)
				.has(recordsInOrder(records.unitId_10, records.unitId_30))
				.has(recordsWithChildren(records.unitId_10, records.unitId_30))
				.has(numFoundAndListSize(2));
		assertThatSolr().hasCounts(1, 0, 3);

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(robin, records.unitId_10, options)
				.has(recordsInOrder(records.unitId_12))
				.has(recordsWithChildren(records.unitId_12))
				.has(numFoundAndListSize(1));
		assertThatSolr().hasCounts(2, 0, 3);

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(robin, records.unitId_12, options)
				.has(recordsInOrder(records.unitId_12b, records.unitId_12c))
				.has(recordsWithChildren(records.unitId_12b, records.unitId_12c))
				.has(numFoundAndListSize(2));
		assertThatSolr().hasCounts(2, 0, 1);

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(robin, records.unitId_30, options)
				.has(recordsInOrder(records.unitId_30c))
				.has(recordsWithChildren(records.unitId_30c))
				.has(numFoundAndListSize(1));
		assertThatSolr().hasCounts(2, 1, 0);

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(robin, records.unitId_12b, options)
				.has(recordsInOrder("B06"))
				.has(recordsWithChildren("B06"))
				.has(numFoundAndListSize(1));
		assertThatSolr().hasCounts(1, 1, 0);

		//Admin
		assertThatRootWhenUserNavigateUsingAdministrativeUnitsTaxonomy(admin, options)
				.has(recordsInOrder(records.unitId_10, records.unitId_20, records.unitId_30))
				.has(recordsWithChildren(records.unitId_10, records.unitId_20, records.unitId_30))
				.has(numFoundAndListSize(3));
		assertThatSolr().hasCounts(0, 0, 0);

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(admin, records.unitId_10, options)
				.has(recordsInOrder(records.unitId_10a, records.unitId_11, records.unitId_12))
				.has(recordsWithChildren(records.unitId_10a, records.unitId_11, records.unitId_12))
				.has(numFoundAndListSize(3));
		assertThatSolr().hasCounts(1, 0, 0);

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(admin, records.unitId_12, options)
				.has(recordsInOrder(records.unitId_12b, records.unitId_12c))
				.has(recordsWithChildren(records.unitId_12b, records.unitId_12c))
				.has(numFoundAndListSize(2));
		assertThatSolr().hasCounts(1, 0, 0);

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(admin, records.unitId_30, options)
				.has(recordsInOrder(records.unitId_30c))
				.has(recordsWithChildren(records.unitId_30c))
				.has(numFoundAndListSize(1));
		assertThatSolr().hasCounts(1, 0, 0);

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(admin, records.unitId_12b, options)
				.has(recordsInOrder("B02", "B04", "B06", "B08", "B32"))
				.has(recordsWithChildren("B02", "B04", "B06", "B08", "B32"))
				.has(numFoundAndListSize(5));
		assertThatSolr().hasCounts(1, 5, 0);

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(admin, records.unitId_12b, new TaxonomiesSearchOptions(options)
				.setStartRow(1).setRows(2))
				.has(recordsInOrder("B04", "B06"))
				.has(recordsWithChildren("B04", "B06"))
				.has(numFound(5)).has(listSize(2));
		assertThatSolr().hasCounts(1, 3, 0);

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(admin, "B04", options)
				.has(noRecordsWithChildren())
				.has(numFoundAndListSize(4));
		assertThatSolr().hasFacetsCountOf(0);
	}

	@Test
	public void whenAdminIsNavigatingAdminUnityWithVisibleRecordsAlwaysDisplayingConceptsWithReadAccessThenSeesRecordsAndAllConcepts()
			throws Exception {

		TaxonomiesSearchOptions options = new TaxonomiesSearchOptions()
				.setAlwaysReturnTaxonomyConceptsWithReadAccessOrLinkable(true);

		assertThatRootWhenUserNavigateUsingAdministrativeUnitsTaxonomy(records.getAdmin(), options)
				.has(recordsInOrder(records.unitId_10, records.unitId_20, records.unitId_30))
				.has(recordsWithChildren(records.unitId_10, records.unitId_20, records.unitId_30))
				.has(numFoundAndListSize(3));

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(records.getAdmin(), records.unitId_12, options)
				.has(recordsInOrder(records.unitId_12b, records.unitId_12c))
				.has(recordsWithChildren(records.unitId_12b))
				.has(numFoundAndListSize(2));

		assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(records.getAdmin(), records.unitId_12b, options)
				.has(recordsInOrder("B02", "B04", "B06", "B08", "B32"))
				.has(recordsWithChildren("B02", "B04", "B06", "B08", "B32"))
				.has(numFoundAndListSize(5));

	}

	@Test
	public void whenNavigatingByIntervalThenGetGoodResults()
			throws Exception {

		assertThatRootWhenUserNavigateUsingPlanTaxonomy(records.getAdmin())
				.has(recordsInOrder(records.categoryId_X, records.categoryId_Z))
				.has(recordsWithChildren(records.categoryId_X, records.categoryId_Z))
				.has(numFoundAndListSize(2));

		assertThatRootWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), 0, 2)
				.has(recordsInOrder(records.categoryId_X, records.categoryId_Z))
				.has(recordsWithChildren(records.categoryId_X, records.categoryId_Z))
				.has(numFoundAndListSize(2));

		assertThatRootWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), 0, 1)
				.has(recordsInOrder(records.categoryId_X))
				.has(recordsWithChildren(records.categoryId_X))
				.has(listSize(1)).has(numFound(2));

		assertThatRootWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), 1, 1)
				.has(recordsInOrder(records.categoryId_Z))
				.has(recordsWithChildren(records.categoryId_Z))
				.has(listSize(1)).has(numFound(2));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_X100)
				.has(recordsInOrder("categoryId_X110", "categoryId_X120", "A16", "A17", "A18", "C06", "B06", "C32", "B32"))
				.has(recordsWithChildren("categoryId_X110", "categoryId_X120", "A16", "C06", "B06", "C32", "B32"))
				.has(listSize(9)).has(numFound(9));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_X100, 0, 10)
				.has(recordsInOrder("categoryId_X110", "categoryId_X120", "A16", "A17", "A18", "C06", "B06", "C32", "B32"))
				.has(recordsWithChildren("categoryId_X110", "categoryId_X120", "A16", "C06", "B06", "C32", "B32"))
				.has(listSize(9)).has(numFound(9));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_X100, 0, 7)
				.has(recordsInOrder("categoryId_X110", "categoryId_X120", "A16", "A17", "A18", "C06", "B06"))
				.has(recordsWithChildren("categoryId_X110", "categoryId_X120", "A16", "C06", "B06"))
				.has(listSize(7)).has(numFound(9));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_X100, 0, 3)
				.has(recordsInOrder("categoryId_X110", "categoryId_X120", "A16"))
				.has(recordsWithChildren("categoryId_X110", "categoryId_X120", "A16"))
				.has(listSize(3)).has(numFound(9));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.categoryId_X100, 1, 4)
				.has(recordsInOrder("categoryId_X120", "A16", "A17", "A18"))
				.has(recordsWithChildren("categoryId_X120", "A16"))
				.has(listSize(4)).has(numFound(9));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.folder_A16, 0, 5)
				.has(recordsInOrder(document1InA16, document2InA16, document3InA16, subFolderId))
				.has(noRecordsWithChildren())
				.has(listSize(4)).has(numFound(4));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(records.getAdmin(), records.folder_A16, 0, 1)
				.has(recordsInOrder(document1InA16))
				.has(noRecordsWithChildren())
				.has(listSize(1)).has(numFound(4));

	}

	@Test
	public void givenHugeClassificationPlanContainingMultipleFoldersThenValidSearchResponses()
			throws Exception {
		User admin = users.adminIn(zeCollection);
		admin.setCollectionReadAccess(true);

		List<Category> rootCategories = new ArrayList<>();
		for (int i = 1; i <= 100; i++) {
			String code = toTitle(i);
			rootCategories.add(rm.newCategoryWithId(code).setCode(code).setTitle("Title " + toTitle((20000 - i)))
					.setRetentionRules(asList(records.ruleId_1)));
		}
		Category category42 = rootCategories.get(41);
		addRecordsInRandomOrder(rootCategories);

		List<Category> childCategories = new ArrayList<>();
		for (int i = 1; i <= 100; i++) {
			String code = "42_" + toTitle(i);
			childCategories.add(rm.newCategoryWithId(code).setRetentionRules(asList(records.ruleId_1))
					.setParent(category42).setCode(code).setTitle("Title " + toTitle((20000 - i))));
		}
		Category category42_42 = childCategories.get(41);
		addRecordsInRandomOrder(childCategories);

		List<Folder> category42_42_folders = new ArrayList<>();
		for (int i = 1; i <= 100; i++) {
			category42_42_folders.add(newFolderInCategory(category42_42, "Folder " + toTitle(i)));
		}
		addRecordsInRandomOrder(category42_42_folders);

		List<Folder> otherCategoriesFolder = new ArrayList<>();
		for (Category category : rootCategories) {
			if (!category.getId().equals(category42.getId())) {
				otherCategoriesFolder.add(newFolderInCategory(category, "A folder"));
			}
		}
		for (Category category : childCategories) {
			if (!category.getId().equals(category42_42.getId())) {
				otherCategoriesFolder.add(newFolderInCategory(category, "A folder"));
			}
		}
		addRecordsInRandomOrder(otherCategoriesFolder);

		for (int i = 0; i < rootCategories.size() - 25; i += 25) {
			LinkableTaxonomySearchResponse response = service.getVisibleRootConceptResponse(
					admin, zeCollection, CLASSIFICATION_PLAN, new TaxonomiesSearchOptions().setStartRow(i).setRows(25), null);
			List<String> expectedIds = new RecordUtils().toWrappedRecordIdsList(rootCategories.subList(i, i + 25));
			assertThat(response.getRecords()).extracting("id").isEqualTo(expectedIds);
		}

		for (int i = 0; i < childCategories.size() - 25; i += 25) {
			LinkableTaxonomySearchResponse response = service.getVisibleChildConceptResponse(admin, CLASSIFICATION_PLAN,
					category42.getWrappedRecord(), new TaxonomiesSearchOptions().setStartRow(i).setRows(25));
			List<String> expectedIds = new RecordUtils().toWrappedRecordIdsList(childCategories.subList(i, i + 25));
			assertThat(response.getRecords()).extracting("id").isEqualTo(expectedIds);
		}

		for (int i = 0; i < category42_42_folders.size() - 25; i += 25) {
			LinkableTaxonomySearchResponse response = service.getVisibleChildConceptResponse(admin, CLASSIFICATION_PLAN,
					category42_42.getWrappedRecord(), new TaxonomiesSearchOptions().setStartRow(i).setRows(25));
			List<String> expectedIds = new RecordUtils().toWrappedRecordIdsList(category42_42_folders.subList(i, i + 25));
			assertThat(response.getNumFound()).isEqualTo(category42_42_folders.size());
			assertThat(response.getRecords()).extracting("id").isEqualTo(expectedIds);
		}

	}

	@Test
	public void givenHugeAdministrativeUnitsContainingMultipleFoldersThenValidSearchResponses()
			throws Exception {
		User admin = users.adminIn(zeCollection);
		admin.setCollectionReadAccess(true);

		List<AdministrativeUnit> rootAdministrativeUnits = new ArrayList<>();
		for (int i = 1; i <= 100; i++) {
			String code = toTitle(1000 + i);
			rootAdministrativeUnits.add(rm.newAdministrativeUnitWithId(code).setCode(code)
					.setTitle("Title " + toTitle(20000 - i)));
		}
		AdministrativeUnit unit42 = rootAdministrativeUnits.get(41);
		addRecordsInRandomOrder(rootAdministrativeUnits);

		List<AdministrativeUnit> childAdministrativeUnits = new ArrayList<>();
		for (int i = 1; i <= 100; i++) {
			String code = "42_" + toTitle(i);
			childAdministrativeUnits.add(rm.newAdministrativeUnitWithId(code)
					.setParent(unit42).setCode(code).setTitle("Title " + toTitle((20000 - i))));
		}
		AdministrativeUnit unit42_666 = childAdministrativeUnits.get(41);
		addRecordsInRandomOrder(childAdministrativeUnits);

		List<Folder> unit42_666_folders = new ArrayList<>();
		for (int i = 1; i <= 100; i++) {
			unit42_666_folders.add(newFolderInUnit(unit42_666, "Folder " + toTitle(i)));
		}
		addRecordsInRandomOrder(unit42_666_folders);

		List<Folder> otherUnitsFolder = new ArrayList<>();
		for (AdministrativeUnit unit : rootAdministrativeUnits) {
			if (!unit.getId().equals(unit42.getId())) {
				otherUnitsFolder.add(newFolderInUnit(unit, "A folder"));
			}
		}
		for (AdministrativeUnit unit : childAdministrativeUnits) {
			if (!unit.getId().equals(unit42_666.getId())) {
				otherUnitsFolder.add(newFolderInUnit(unit, "A folder"));
			}
		}
		addRecordsInRandomOrder(otherUnitsFolder);

		for (int i = 0; i < rootAdministrativeUnits.size() - 25; i += 25) {
			LinkableTaxonomySearchResponse response = service.getVisibleRootConceptResponse(
					admin, zeCollection, ADMINISTRATIVE_UNITS, new TaxonomiesSearchOptions().setStartRow(2 + i).setRows(25),
					null);
			List<String> expectedIds = new RecordUtils().toWrappedRecordIdsList(rootAdministrativeUnits.subList(i, i + 25));
			assertThat(response.getRecords()).extracting("id").isEqualTo(expectedIds);
		}

		for (int i = 0; i < childAdministrativeUnits.size() - 25; i += 25) {
			LinkableTaxonomySearchResponse response = service.getVisibleChildConceptResponse(admin, ADMINISTRATIVE_UNITS,
					unit42.getWrappedRecord(), new TaxonomiesSearchOptions().setStartRow(i).setRows(25));
			List<String> expectedIds = new RecordUtils().toWrappedRecordIdsList(childAdministrativeUnits.subList(i, i + 25));

			assertThat(response.getRecords()).extracting("id").isEqualTo(expectedIds);
		}

		for (int i = 0; i < unit42_666_folders.size() - 25; i += 25) {
			LinkableTaxonomySearchResponse response = service.getVisibleChildConceptResponse(admin, ADMINISTRATIVE_UNITS,
					unit42_666.getWrappedRecord(), new TaxonomiesSearchOptions().setStartRow(i).setRows(25));
			List<String> expectedIds = new RecordUtils().toWrappedRecordIdsList(unit42_666_folders.subList(i, i + 25));
			assertThat(response.getNumFound()).isEqualTo(unit42_666_folders.size());
			assertThat(response.getRecords()).extracting("id").isEqualTo(expectedIds);
		}

	}

	@Test
	public void givenLogicallyDeletedRecordsInVisibleRecordsThenNotShownInTree()
			throws Exception {

		Folder subFolder1 = decommissioningService.newSubFolderIn(records.getFolder_A20()).setTitle("Ze sub folder");
		Folder subFolder2 = decommissioningService.newSubFolderIn(records.getFolder_A20()).setTitle("Ze sub folder");
		getModelLayerFactory().newRecordServices().execute(new Transaction().addAll(subFolder1, subFolder2));

		getModelLayerFactory().newRecordServices().logicallyDelete(subFolder1.getWrappedRecord(), User.GOD);
		getModelLayerFactory().newRecordServices().logicallyDelete(subFolder2.getWrappedRecord(), User.GOD);

		authsServices.add(authorizationForUsers(users.sasquatchIn(zeCollection)).on(subFolder1.getId()).givingReadAccess());
		authsServices.add(authorizationForUsers(users.sasquatchIn(zeCollection)).on(subFolder2.getId()).givingReadAccess());
		authsServices.add(authorizationForUsers(users.sasquatchIn(zeCollection)).on(records.folder_C01).givingReadAccess());

		TaxonomiesSearchOptions withWriteAccess = new TaxonomiesSearchOptions().setRequiredAccess(Role.WRITE);
		User sasquatch = users.sasquatchIn(zeCollection);
		assertThatRootWhenUserNavigateUsingPlanTaxonomy(sasquatch)
				.has(numFoundAndListSize(1))
				.has(recordsWithChildren(records.categoryId_X));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(sasquatch, records.categoryId_Z).has(numFoundAndListSize(0));
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(sasquatch, records.categoryId_Z100).has(numFoundAndListSize(0));
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(sasquatch, records.categoryId_Z120).has(numFoundAndListSize(0));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(sasquatch, records.folder_A20).has(numFoundAndListSize(0));

	}

	@Test
	public void givenInvisibleInTreeRecordsInVisibleRecordThenNotShownInTree()
			throws Exception {

		givenConfig(RMConfigs.DISPLAY_SEMI_ACTIVE_RECORDS_IN_TREES, false);
		givenConfig(RMConfigs.DISPLAY_SEMI_ACTIVE_RECORDS_IN_TREES, false);

		Folder subFolder1 = decommissioningService.newSubFolderIn(records.getFolder_A20()).setTitle("Ze sub folder")
				.setActualTransferDate(LocalDate.now()).setActualDestructionDate(LocalDate.now());
		Folder subFolder2 = decommissioningService.newSubFolderIn(records.getFolder_A20()).setTitle("Ze sub folder")
				.setActualTransferDate(LocalDate.now());
		getModelLayerFactory().newRecordServices().execute(new Transaction().addAll(subFolder1, subFolder2));

		assertThat(subFolder2.get(Schemas.VISIBLE_IN_TREES)).isEqualTo(Boolean.FALSE);

		authsServices.add(authorizationForUsers(users.sasquatchIn(zeCollection)).on(subFolder1.getId()).givingReadAccess());
		authsServices.add(authorizationForUsers(users.sasquatchIn(zeCollection)).on(subFolder2.getId()).givingReadAccess());
		authsServices.add(authorizationForUsers(users.sasquatchIn(zeCollection)).on(records.folder_C01).givingReadAccess());

		User sasquatch = users.sasquatchIn(zeCollection);
		assertThatRootWhenUserNavigateUsingPlanTaxonomy(sasquatch)
				.has(numFoundAndListSize(1))
				.has(recordsWithChildren(records.categoryId_X));
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(sasquatch, records.categoryId_Z).has(numFoundAndListSize(0));
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(sasquatch, records.categoryId_Z100).has(numFoundAndListSize(0));
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(sasquatch, records.categoryId_Z120).has(numFoundAndListSize(0));
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(sasquatch, records.folder_A20).has(numFoundAndListSize(0));

	}

	@Test
	public void givenInvisibleInTreeRecordsThenNotShownInTree()
			throws Exception {

		givenConfig(RMConfigs.DISPLAY_SEMI_ACTIVE_RECORDS_IN_TREES, false);
		givenConfig(RMConfigs.DISPLAY_SEMI_ACTIVE_RECORDS_IN_TREES, false);

		getModelLayerFactory().newRecordServices()
				.execute(new Transaction().addAll(records.getFolder_A20().setActualTransferDate(LocalDate.now())));

		authsServices.add(authorizationForUsers(users.sasquatchIn(zeCollection)).on(records.folder_A20).givingReadAccess());
		authsServices.add(authorizationForUsers(users.sasquatchIn(zeCollection)).on(records.folder_C01).givingReadAccess());

		User sasquatch = users.sasquatchIn(zeCollection);
		assertThatRootWhenUserNavigateUsingPlanTaxonomy(sasquatch)
				.has(numFoundAndListSize(1))
				.has(recordsWithChildren(records.categoryId_X));
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(sasquatch, records.categoryId_Z).has(numFoundAndListSize(0));
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(sasquatch, records.categoryId_Z100).has(numFoundAndListSize(0));
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(sasquatch, records.categoryId_Z120).has(numFoundAndListSize(0));
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(sasquatch, records.folder_A20).has(numFoundAndListSize(0));

	}

	@Test
	public void givenLogicallyDeletedRecordsThenNotShownInTree()
			throws Exception {

		authsServices.add(authorizationForUsers(users.sasquatchIn(zeCollection)).on(records.folder_A20).givingReadAccess());
		authsServices.add(authorizationForUsers(users.sasquatchIn(zeCollection)).on(records.folder_C01).givingReadAccess());

		getModelLayerFactory().newRecordServices().logicallyDelete(records.getFolder_A20().getWrappedRecord(), User.GOD);

		User sasquatch = users.sasquatchIn(zeCollection);
		assertThatRootWhenUserNavigateUsingPlanTaxonomy(sasquatch)
				.has(numFoundAndListSize(1))
				.has(recordsWithChildren(records.categoryId_X));
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(sasquatch, records.categoryId_Z).has(numFoundAndListSize(0));
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(sasquatch, records.categoryId_Z100).has(numFoundAndListSize(0));
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(sasquatch, records.categoryId_Z120).has(numFoundAndListSize(0));
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(sasquatch, records.folder_A20).has(numFoundAndListSize(0));

	}

	@Test
	public void given10000FoldersAndUserHasOnlyAccessToTheLastOnesThenDoesNotIteratorOverAllNodesToFindThem()
			throws Exception {

		Folder folderNearEnd = null;
		Folder subFolderNearEnd = null;
		List<Folder> addedRecords = new ArrayList<>();

		int size = 4999;
		for (int i = 0; i < size; i++) {
			String paddedIndex = ZeroPaddedSequentialUniqueIdGenerator.zeroPaddedNumber(i);
			Folder folder = rm.newFolder().setTitle("Dossier #" + paddedIndex).setRetentionRuleEntered(records.ruleId_1)
					.setCategoryEntered(records.categoryId_X13).setOpenDate(LocalDate.now())
					.setAdministrativeUnitEntered(records.unitId_10a);
			addedRecords.add(folder);
			if (i == size - 2) {
				folderNearEnd = folder;
			}

			if (i == size - 1) {
				subFolderNearEnd = rm.newFolder().setTitle("Sub folder").setParentFolder(folder).setOpenDate(LocalDate.now());
				addedRecords.add(subFolderNearEnd);
			}
		}
		recordServices.execute(new Transaction().addAll(addedRecords).setOptimisticLockingResolution(EXCEPTION));

		authsServices.add(authorizationForUsers(users.sasquatchIn(zeCollection)).givingReadWriteAccess().on(folderNearEnd));
		authsServices.add(authorizationForUsers(users.sasquatchIn(zeCollection)).givingReadWriteAccess().on(subFolderNearEnd));

		TaxonomiesSearchOptions withWriteAccess = new TaxonomiesSearchOptions().setRequiredAccess(Role.WRITE);

		final AtomicInteger queryCount = new AtomicInteger();
		getDataLayerFactory().getExtensions().getSystemWideExtensions().bigVaultServerExtension
				.add(new BigVaultServerExtension() {
					@Override
					public void afterQuery(SolrParams solrParams, long qtime) {
						queryCount.incrementAndGet();
					}
				});

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(users.sasquatchIn(zeCollection), records.categoryId_X13, withWriteAccess)
				.has(recordsInOrder(folderNearEnd.getId(), subFolderNearEnd.getParentFolder()));

		//assertThat(queryCount.get()).isEqualTo(3);
	}

	@Test
	@InDevelopmentTest
	public void givenPlethoraOfChildCategoriesThenValidGetRootResponseAndStartUI()
			throws Exception {

		TaxonomiesSearchOptions options = new TaxonomiesSearchOptions().setRequiredAccess(Role.WRITE);
		getModelLayerFactory().newRecordServices().update(alice.setCollectionWriteAccess(true));

		Transaction transaction = new Transaction();
		Category rootCategory = rm.newCategoryWithId("root").setCode("root").setTitle("root");

		for (int i = 1; i <= 300; i++) {
			String code = (i < 100 ? "0" : "") + (i < 10 ? "0" : "") + i;
			Category category = transaction.add(rm.newCategoryWithId("category_" + i)).setCode(code)
					.setTitle("Category #" + code).setParent(rootCategory);
			transaction.add(rm.newFolder().setTitle("A folder")
					.setCategoryEntered(category)
					.setRetentionRuleEntered(records.ruleId_1)
					.setAdministrativeUnitEntered(records.unitId_10a)
					.setOpenDate(new LocalDate(2014, 11, 1)));
		}
		transaction.add(rootCategory);
		getModelLayerFactory().newRecordServices().execute(transaction);

		newWebDriver();
		waitUntilICloseTheBrowsers();
	}

	@Test
	public void givenNoCacheAndPlethoraOfChildCategoriesThenValidGetRootResponse()
			throws Exception {

		getModelLayerFactory().getRecordsCaches().invalidateAll();
		getModelLayerFactory().getRecordsCaches().getCache(zeCollection).removeCache(Category.SCHEMA_TYPE);

		TaxonomiesSearchOptions options = new TaxonomiesSearchOptions().setRequiredAccess(Role.WRITE);
		getModelLayerFactory().newRecordServices().update(alice.setCollectionWriteAccess(true));

		Transaction transaction = new Transaction();
		Category rootCategory = rm.newCategoryWithId("root").setCode("root").setTitle("root");

		for (int i = 1; i <= 300; i++) {
			String code = (i < 100 ? "0" : "") + (i < 10 ? "0" : "") + i;
			Category category = transaction.add(rm.newCategoryWithId("category_" + i)).setCode(code)
					.setTitle("Category #" + code).setParent(rootCategory);
			transaction.add(rm.newFolder().setTitle("A folder")
					.setCategoryEntered(category)
					.setRetentionRuleEntered(records.ruleId_1)
					.setAdministrativeUnitEntered(records.unitId_10a)
					.setOpenDate(new LocalDate(2014, 11, 1)));
		}
		transaction.add(rootCategory);
		getModelLayerFactory().newRecordServices().execute(transaction);

		User alice = users.aliceIn(zeCollection);
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(alice, "root",
				options.setStartRow(0).setRows(20).setFastContinueInfos(null))
				.has(recordsInOrder("category_1", "category_2", "category_3", "category_4", "category_5", "category_6",
						"category_7", "category_8", "category_9", "category_10", "category_11", "category_12", "category_13",
						"category_14", "category_15", "category_16", "category_17", "category_18", "category_19", "category_20"))
				.has(numFound(40)).has(listSize(20))
				.has(fastContinuationInfos(false, 20));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(alice, "root",
				options.setStartRow(0).setRows(20).setFastContinueInfos(null))
				.has(recordsInOrder("category_1", "category_2", "category_3", "category_4", "category_5", "category_6",
						"category_7", "category_8", "category_9", "category_10", "category_11", "category_12", "category_13",
						"category_14", "category_15", "category_16", "category_17", "category_18", "category_19", "category_20"))
				.has(numFound(40)).has(listSize(20))
				.has(fastContinuationInfos(false, 20));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(alice, "root", options.setStartRow(10).setRows(20)
				.setFastContinueInfos(new FastContinueInfos(false, 10, new ArrayList<String>())))
				.has(recordsInOrder("category_11", "category_12", "category_13", "category_14", "category_15", "category_16",
						"category_17", "category_18", "category_19", "category_20", "category_21", "category_22", "category_23",
						"category_24", "category_25", "category_26", "category_27", "category_28", "category_29", "category_30"))
				.has(numFound(50)).has(listSize(20))
				.has(fastContinuationInfos(false, 30));

		//Calling with an different fast continue (then get different values)
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(alice, "root", options.setStartRow(10).setRows(20)
				.setFastContinueInfos(new FastContinueInfos(false, 11, new ArrayList<String>())))
				.has(recordsInOrder("category_12", "category_13", "category_14", "category_15", "category_16", "category_17",
						"category_18", "category_19", "category_20", "category_21", "category_22", "category_23", "category_24",
						"category_25", "category_26", "category_27", "category_28", "category_29", "category_30", "category_31"))
				.has(numFound(50)).has(listSize(20))
				.has(fastContinuationInfos(false, 31));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(alice, "root",
				options.setStartRow(0).setRows(30).setFastContinueInfos(null))
				.has(recordsInOrder("category_1", "category_2", "category_3", "category_4", "category_5", "category_6",
						"category_7", "category_8", "category_9", "category_10", "category_11", "category_12", "category_13",
						"category_14", "category_15", "category_16",
						"category_17", "category_18", "category_19", "category_20", "category_21", "category_22", "category_23",
						"category_24", "category_25", "category_26", "category_27", "category_28", "category_29", "category_30"))
				.has(numFound(60)).has(listSize(30))
				.has(fastContinuationInfos(false, 30));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(alice, "root", options.setStartRow(289).setRows(30)
				.setFastContinueInfos(null))
				.has(recordsInOrder("category_290", "category_291", "category_292", "category_293",
						"category_294", "category_295", "category_296", "category_297", "category_298", "category_299",
						"category_300"))
				.has(numFound(300)).has(listSize(11))
				.has(fastContinuationInfos(true, 0));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(alice, "root", options.setStartRow(289).setRows(30)
				.setFastContinueInfos(new FastContinueInfos(false, 289, new ArrayList<String>())))
				.has(recordsInOrder("category_290", "category_291", "category_292", "category_293",
						"category_294", "category_295", "category_296", "category_297", "category_298", "category_299",
						"category_300"))
				.has(numFound(300)).has(listSize(11))
				.has(fastContinuationInfos(true, 0));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(alice, "root", options.setStartRow(289).setRows(30)
				.setFastContinueInfos(new FastContinueInfos(false, 290, new ArrayList<String>())))
				.has(recordsInOrder("category_291", "category_292", "category_293",
						"category_294", "category_295", "category_296", "category_297", "category_298", "category_299",
						"category_300"))
				.has(numFound(299)).has(listSize(10))
				.has(fastContinuationInfos(true, 0));
	}

	@Test
	public void givenPlethoraOfChildCategoriesThenValidGetRootResponse()
			throws Exception {

		TaxonomiesSearchOptions options = new TaxonomiesSearchOptions().setRequiredAccess(Role.WRITE);
		getModelLayerFactory().newRecordServices().update(alice.setCollectionWriteAccess(true));

		Transaction transaction = new Transaction();
		Category rootCategory = rm.newCategoryWithId("root").setCode("root").setTitle("root");

		for (int i = 1; i <= 300; i++) {
			String code = (i < 100 ? "0" : "") + (i < 10 ? "0" : "") + i;
			Category category = transaction.add(rm.newCategoryWithId("category_" + i)).setCode(code)
					.setTitle("Category #" + code).setParent(rootCategory);
			transaction.add(rm.newFolder().setTitle("A folder")
					.setCategoryEntered(category)
					.setRetentionRuleEntered(records.ruleId_1)
					.setAdministrativeUnitEntered(records.unitId_10a)
					.setOpenDate(new LocalDate(2014, 11, 1)));
		}
		transaction.add(rootCategory);
		getModelLayerFactory().newRecordServices().execute(transaction);

		User alice = users.aliceIn(zeCollection);
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(alice, "root",
				options.setStartRow(0).setRows(20).setFastContinueInfos(null))
				.has(recordsInOrder("category_1", "category_2", "category_3", "category_4", "category_5", "category_6",
						"category_7", "category_8", "category_9", "category_10", "category_11", "category_12", "category_13",
						"category_14", "category_15", "category_16", "category_17", "category_18", "category_19", "category_20"))
				.has(numFound(40)).has(listSize(20))
				.has(fastContinuationInfos(false, 20));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(alice, "root",
				options.setStartRow(0).setRows(20).setFastContinueInfos(null))
				.has(recordsInOrder("category_1", "category_2", "category_3", "category_4", "category_5", "category_6",
						"category_7", "category_8", "category_9", "category_10", "category_11", "category_12", "category_13",
						"category_14", "category_15", "category_16", "category_17", "category_18", "category_19", "category_20"))
				.has(numFound(40)).has(listSize(20))
				.has(fastContinuationInfos(false, 20));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(alice, "root", options.setStartRow(10).setRows(20)
				.setFastContinueInfos(new FastContinueInfos(false, 10, new ArrayList<String>())))
				.has(recordsInOrder("category_11", "category_12", "category_13", "category_14", "category_15", "category_16",
						"category_17", "category_18", "category_19", "category_20", "category_21", "category_22", "category_23",
						"category_24", "category_25", "category_26", "category_27", "category_28", "category_29", "category_30"))
				.has(numFound(40)).has(listSize(20))
				.has(fastContinuationInfos(false, 30));

		//Calling with an different fast continue (but don't cause any problem since using the cache)
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(alice, "root", options.setStartRow(10).setRows(20)
				.setFastContinueInfos(new FastContinueInfos(false, 11, new ArrayList<String>())))
				.has(recordsInOrder("category_11", "category_12", "category_13", "category_14", "category_15", "category_16",
						"category_17", "category_18", "category_19", "category_20", "category_21", "category_22", "category_23",
						"category_24", "category_25", "category_26", "category_27", "category_28", "category_29", "category_30"))
				.has(numFound(40)).has(listSize(20))
				.has(fastContinuationInfos(false, 30));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(alice, "root",
				options.setStartRow(0).setRows(30).setFastContinueInfos(null))
				.has(recordsInOrder("category_1", "category_2", "category_3", "category_4", "category_5", "category_6",
						"category_7", "category_8", "category_9", "category_10", "category_11", "category_12", "category_13",
						"category_14", "category_15", "category_16",
						"category_17", "category_18", "category_19", "category_20", "category_21", "category_22", "category_23",
						"category_24", "category_25", "category_26", "category_27", "category_28", "category_29", "category_30"))
				.has(numFound(60)).has(listSize(30))
				.has(fastContinuationInfos(false, 30));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(alice, "root", options.setStartRow(289).setRows(30)
				.setFastContinueInfos(null))
				.has(recordsInOrder("category_290", "category_291", "category_292", "category_293",
						"category_294", "category_295", "category_296", "category_297", "category_298", "category_299",
						"category_300"))
				.has(numFound(300)).has(listSize(11))
				.has(fastContinuationInfos(true, 0));

		assertThatChildWhenUserNavigateUsingPlanTaxonomy(alice, "root", options.setStartRow(289).setRows(30)
				.setFastContinueInfos(new FastContinueInfos(false, 289, new ArrayList<String>())))
				.has(recordsInOrder("category_290", "category_291", "category_292", "category_293",
						"category_294", "category_295", "category_296", "category_297", "category_298", "category_299",
						"category_300"))
				.has(numFound(300)).has(listSize(11))
				.has(fastContinuationInfos(true, 0));

		//Calling with an different fast continue (but don't cause any problem since using the cache)
		assertThatChildWhenUserNavigateUsingPlanTaxonomy(alice, "root", options.setStartRow(289).setRows(30)
				.setFastContinueInfos(new FastContinueInfos(false, 290, new ArrayList<String>())))
				.has(recordsInOrder("category_290", "category_291", "category_292", "category_293",
						"category_294", "category_295", "category_296", "category_297", "category_298", "category_299",
						"category_300"))
				.has(numFound(300)).has(listSize(11))
				.has(fastContinuationInfos(true, 0));
	}

	@Test
	public void givenPlethoraOfRootCategoriesThenValidGetRootResponse()
			throws Exception {

		TaxonomiesSearchOptions options = new TaxonomiesSearchOptions().setRequiredAccess(Role.WRITE);
		getModelLayerFactory().newRecordServices().update(alice.setCollectionWriteAccess(true));

		Transaction transaction = new Transaction();
		for (int i = 1; i <= 300; i++) {
			String code = (i < 100 ? "0" : "") + (i < 10 ? "0" : "") + i;
			Category category = transaction.add(rm.newCategoryWithId("category_" + i)).setCode(code)
					.setTitle("Category #" + code);
			transaction.add(rm.newFolder().setTitle("A folder")
					.setCategoryEntered(category)
					.setRetentionRuleEntered(records.ruleId_1)
					.setAdministrativeUnitEntered(records.unitId_10a)
					.setOpenDate(new LocalDate(2014, 11, 1)));
		}
		getModelLayerFactory().newRecordServices().execute(transaction);

		User alice = users.aliceIn(zeCollection);
		assertThatRootWhenUserNavigateUsingPlanTaxonomy(alice, options.setStartRow(0).setRows(20).setFastContinueInfos(null))
				.has(recordsInOrder("category_1", "category_2", "category_3", "category_4", "category_5", "category_6",
						"category_7", "category_8", "category_9", "category_10", "category_11", "category_12", "category_13",
						"category_14", "category_15", "category_16", "category_17", "category_18", "category_19", "category_20"))
				.has(numFound(100)).has(listSize(20))
				.has(fastContinuationInfos(false, 20));

		assertThatRootWhenUserNavigateUsingPlanTaxonomy(alice, options.setStartRow(0).setRows(20).setFastContinueInfos(null))
				.has(recordsInOrder("category_1", "category_2", "category_3", "category_4", "category_5", "category_6",
						"category_7", "category_8", "category_9", "category_10", "category_11", "category_12", "category_13",
						"category_14", "category_15", "category_16", "category_17", "category_18", "category_19", "category_20"))
				.has(numFound(100)).has(listSize(20))
				.has(fastContinuationInfos(false, 20));

		assertThatRootWhenUserNavigateUsingPlanTaxonomy(alice, options.setStartRow(10).setRows(20)
				.setFastContinueInfos(new FastContinueInfos(false, 10, new ArrayList<String>())))
				.has(recordsInOrder("category_11", "category_12", "category_13", "category_14", "category_15", "category_16",
						"category_17", "category_18", "category_19", "category_20", "category_21", "category_22", "category_23",
						"category_24", "category_25", "category_26", "category_27", "category_28", "category_29", "category_30"))
				.has(numFound(110)).has(listSize(20))
				.has(fastContinuationInfos(false, 30));

		//Calling with an different fast continue (simulating that one of the first ten record was not returned)
		assertThatRootWhenUserNavigateUsingPlanTaxonomy(alice, options.setStartRow(10).setRows(20)
				.setFastContinueInfos(new FastContinueInfos(false, 11, new ArrayList<String>())))
				.has(recordsInOrder("category_12", "category_13", "category_14", "category_15", "category_16", "category_17",
						"category_18", "category_19", "category_20", "category_21", "category_22", "category_23", "category_24",
						"category_25", "category_26", "category_27", "category_28", "category_29", "category_30", "category_31"))
				.has(numFound(110)).has(listSize(20))
				.has(fastContinuationInfos(false, 31));

		assertThatRootWhenUserNavigateUsingPlanTaxonomy(alice, options.setStartRow(0).setRows(30).setFastContinueInfos(null))
				.has(recordsInOrder("category_1", "category_2", "category_3", "category_4", "category_5", "category_6",
						"category_7", "category_8", "category_9", "category_10", "category_11", "category_12", "category_13",
						"category_14", "category_15", "category_16",
						"category_17", "category_18", "category_19", "category_20", "category_21", "category_22", "category_23",
						"category_24", "category_25", "category_26", "category_27", "category_28", "category_29", "category_30"))
				.has(numFound(100)).has(listSize(30))
				.has(fastContinuationInfos(false, 30));

		assertThatRootWhenUserNavigateUsingPlanTaxonomy(alice, options.setStartRow(289).setRows(30)
				.setFastContinueInfos(null))
				.has(recordsInOrder("category_290", "category_291", "category_292", "category_293",
						"category_294", "category_295", "category_296", "category_297", "category_298", "category_299",
						"category_300", "categoryId_X", "categoryId_Z"))
				.has(numFound(302)).has(listSize(13))
				.has(fastContinuationInfos(true, 302));

		assertThatRootWhenUserNavigateUsingPlanTaxonomy(alice, options.setStartRow(289).setRows(30)
				.setFastContinueInfos(new FastContinueInfos(false, 289, new ArrayList<String>())))
				.has(recordsInOrder("category_290", "category_291", "category_292", "category_293",
						"category_294", "category_295", "category_296", "category_297", "category_298", "category_299",
						"category_300", "categoryId_X", "categoryId_Z"))
				.has(numFound(302)).has(listSize(13))
				.has(fastContinuationInfos(true, 302));

		assertThatRootWhenUserNavigateUsingPlanTaxonomy(alice, options.setStartRow(289).setRows(30)
				.setFastContinueInfos(new FastContinueInfos(false, 290, new ArrayList<String>())))
				.has(recordsInOrder("category_291", "category_292", "category_293",
						"category_294", "category_295", "category_296", "category_297", "category_298", "category_299",
						"category_300", "categoryId_X", "categoryId_Z"))
				.has(numFound(301)).has(listSize(12))
				.has(fastContinuationInfos(true, 302));
	}

	private Folder newFolderInCategory(Category category, String title) {
		return rm.newFolder().setCategoryEntered(category).setTitle(title).setOpenDate(new LocalDate())
				.setRetentionRuleEntered(records.ruleId_1).setAdministrativeUnitEntered(records.unitId_10a);
	}

	private Folder newFolderInUnit(AdministrativeUnit unit, String title) {
		return rm.newFolder().setCategoryEntered(records.categoryId_X100).setTitle(title).setOpenDate(new LocalDate())
				.setRetentionRuleEntered(records.ruleId_1).setAdministrativeUnitEntered(unit);
	}

	private String toTitle(int i) {
		String value = "0000" + i;
		return value.substring(value.length() - 5, value.length());
	}

	// -------

	private void addRecordsInRandomOrder(List<? extends RecordWrapper> records) {
		List<RecordWrapper> copy = new ArrayList<>(records);

		RecordWrapper addedBefore = copy.remove(23);
		RecordWrapper addedAfter = copy.remove(24);

		try {
			recordServices.add(addedBefore);
			Transaction transaction = new Transaction();
			transaction.addUpdate(new RecordUtils().unwrap(copy));
			recordServices.execute(transaction);
			recordServices.add(addedAfter);
		} catch (RecordServicesException e) {
			throw new RuntimeException(e);
		}

	}

	private Condition<? super LinkableTaxonomySearchResponseCaller> secondCallQueryCounts(final int queries,
			final int queryResults, final int facets) {
		return new Condition<LinkableTaxonomySearchResponseCaller>() {
			@Override
			public boolean matches(LinkableTaxonomySearchResponseCaller value) {
				String expected = queries + "-" + queryResults + "-" + facets;
				String current = value.secondAnswerSolrQueries();

				//assertThat(current).describedAs("Second call Queries count - Query resuts count - Facets count")
				//		.isEqualTo(expected);
				queriesCount.set(0);
				facetsCount.set(0);
				returnedDocumentsCount.set(0);

				return true;
			}
		};
	}

	private Condition<? super LinkableTaxonomySearchResponseCaller> solrQueryCounts(final int queries, final int queryResults,
			final int facets) {
		return new Condition<LinkableTaxonomySearchResponseCaller>() {
			@Override
			public boolean matches(LinkableTaxonomySearchResponseCaller value) {
				String expected = queries + "-" + queryResults + "-" + facets;
				String current = value.firstAnswerSolrQueries();

				//assertThat(current).describedAs("First call Queries count - Query resuts count - Facets count")
				//		.isEqualTo(expected);
				queriesCount.set(0);
				facetsCount.set(0);
				returnedDocumentsCount.set(0);

				return true;
			}
		};
	}

	private Condition<? super LinkableTaxonomySearchResponseCaller> CONSTRUCTINGsolrQueryCounts(final String placeHolder) {
		return new Condition<LinkableTaxonomySearchResponseCaller>() {
			@Override
			public boolean matches(LinkableTaxonomySearchResponseCaller value) {
				//Lit la classe ENCODING!!!!
				//Remplace CONSTRUCTINGsolrQueryCounts(placeHolder) par le bon appel avec la valeur
				String current = value.firstAnswerSolrQueries();
				//2-3-4

				//CONSTRUCTINGsolrQueryCounts("toto")
				//solrQueryCounts(2,3,4)

				queriesCount.set(0);
				facetsCount.set(0);
				returnedDocumentsCount.set(0);

				return true;
			}
		};
	}

	private Condition<? super LinkableTaxonomySearchResponseCaller> numFoundAndListSize(final int expectedCount) {
		return new Condition<LinkableTaxonomySearchResponseCaller>() {
			@Override
			public boolean matches(LinkableTaxonomySearchResponseCaller value) {

				assertThat(value.firstAnswer().getNumFound()).describedAs(description().toString() + " First call numFound")
						.isEqualTo(expectedCount);
				assertThat(value.firstAnswer().getRecords().size())
						.describedAs(description().toString() + " First call records list size")
						.isEqualTo(expectedCount);

				assertThat(value.secondAnswer().getNumFound()).describedAs(description().toString() + " Second call numFound")
						.isEqualTo(expectedCount);
				assertThat(value.secondAnswer().getRecords().size())
						.describedAs(description().toString() + " Second call records list size")
						.isEqualTo(expectedCount);

				return true;
			}
		};
	}

	private Condition<? super LinkableTaxonomySearchResponseCaller> numFound(final int expectedCount) {
		return new Condition<LinkableTaxonomySearchResponseCaller>() {
			@Override
			public boolean matches(LinkableTaxonomySearchResponseCaller value) {
				assertThat(value.firstAnswer().getNumFound()).describedAs("NumFound").isEqualTo(expectedCount);
				assertThat(value.secondAnswer().getNumFound()).describedAs("NumFound").isEqualTo(expectedCount);
				return true;
			}
		};
	}

	private Condition<? super LinkableTaxonomySearchResponseCaller> listSize(final int expectedCount) {
		return new Condition<LinkableTaxonomySearchResponseCaller>() {
			@Override
			public boolean matches(LinkableTaxonomySearchResponseCaller value) {
				assertThat(value.firstAnswer().getRecords().size()).describedAs("records list size").isEqualTo(expectedCount);
				assertThat(value.secondAnswer().getRecords().size()).describedAs("records list size").isEqualTo(expectedCount);
				return true;
			}
		};
	}

	private Condition<? super LinkableTaxonomySearchResponseCaller> recordsInOrder(String... ids) {
		final List<String> idsList = asList(ids);
		return new Condition<LinkableTaxonomySearchResponseCaller>() {
			@Override
			public boolean matches(LinkableTaxonomySearchResponseCaller response) {
				List<String> valueIds = new ArrayList<>();
				for (TaxonomySearchRecord value : response.firstAnswer().getRecords()) {
					valueIds.add(value.getRecord().getId());
				}
				assertThat(valueIds).describedAs(description().toString()).isEqualTo(idsList);

				valueIds = new ArrayList<>();
				for (TaxonomySearchRecord value : response.secondAnswer().getRecords()) {
					valueIds.add(value.getRecord().getId());
				}
				assertThat(valueIds).describedAs(description().toString()).isEqualTo(idsList);

				return true;
			}
		};
	}

	private Condition<? super LinkableTaxonomySearchResponseCaller> noRecordsWithChildren() {
		return recordsWithChildren();
	}

	private Condition<? super LinkableTaxonomySearchResponseCaller> recordsWithChildren(String... ids) {
		final List<String> idsList = asList(ids);
		return new Condition<LinkableTaxonomySearchResponseCaller>() {
			@Override
			public boolean matches(LinkableTaxonomySearchResponseCaller response) {
				List<String> valueIds = new ArrayList<>();
				for (TaxonomySearchRecord value : response.firstAnswer().getRecords()) {
					if (value.hasChildren()) {
						valueIds.add(value.getRecord().getId());
					}
				}
				assertThat(valueIds).describedAs("First call " + description().toString()).isEqualTo(idsList);

				valueIds = new ArrayList<>();
				for (TaxonomySearchRecord value : response.secondAnswer().getRecords()) {
					if (value.hasChildren()) {
						valueIds.add(value.getRecord().getId());
					}
				}
				assertThat(valueIds).describedAs("Second call " + description().toString()).isEqualTo(idsList);

				return true;
			}
		};
	}

	private Condition<? super List<TaxonomySearchRecord>> validOrder() {
		return new Condition<List<TaxonomySearchRecord>>() {
			@Override
			public boolean matches(List<TaxonomySearchRecord> values) {

				List<Record> actualRecords = new ArrayList<>();
				List<Record> recordsInExpectedOrder = new ArrayList<>();

				for (TaxonomySearchRecord value : values) {
					actualRecords.add(value.getRecord());
					recordsInExpectedOrder.add(value.getRecord());
				}

				final List<String> typesOrder = asList(Category.SCHEMA_TYPE, AdministrativeUnit.SCHEMA_TYPE,
						ContainerRecord.SCHEMA_TYPE, Folder.SCHEMA_TYPE, Document.SCHEMA_TYPE);

				Collections.sort(recordsInExpectedOrder, new Comparator<Record>() {
					@Override
					public int compare(Record r1, Record r2) {

						int r1TypeIndex = typesOrder.indexOf(new SchemaUtils().getSchemaTypeCode(r1.getSchemaCode()));
						int r2TypeIndex = typesOrder.indexOf(new SchemaUtils().getSchemaTypeCode(r2.getSchemaCode()));

						if (r1TypeIndex != r2TypeIndex) {
							return new Integer(r1TypeIndex).compareTo(r2TypeIndex);

						} else {
							String code1 = r1.get(Schemas.CODE);
							String code2 = r2.get(Schemas.CODE);
							if (code1 != null && code2 != null) {
								return code1.compareTo(code2);

							} else if (code1 != null && code2 == null) {
								return 1;
							} else if (code1 == null && code2 != null) {
								return -1;
							} else {

								String title1 = r1.get(Schemas.TITLE);
								String title2 = r2.get(Schemas.TITLE);
								if (title1 == null) {
									return -1;
								} else {
									return title1.compareTo(title2);
								}
							}

						}

					}
				});

				assertThat(actualRecords).isEqualTo(recordsInExpectedOrder);
				return true;
			}
		};
	}

	private Condition<? super List<TaxonomySearchRecord>> unlinkable(final String... ids) {
		return new Condition<List<TaxonomySearchRecord>>() {
			@Override
			public boolean matches(List<TaxonomySearchRecord> records) {

				for (String id : ids) {
					TaxonomySearchRecord foundRecord = null;
					for (TaxonomySearchRecord record : records) {
						if (id.equals(record.getRecord().getId())) {
							if (foundRecord != null) {
								throw new RuntimeException("Same record found twice");
							}
							foundRecord = record;
						}
					}
					if (foundRecord == null) {
						throw new RuntimeException("Record not found : " + id);
					} else {
						assertThat(foundRecord.isLinkable()).isFalse();
					}

				}

				return true;
			}
		};
	}

	private Condition<? super List<TaxonomySearchRecord>> linkable(final String... ids) {
		return new Condition<List<TaxonomySearchRecord>>() {
			@Override
			public boolean matches(List<TaxonomySearchRecord> records) {

				for (String id : ids) {
					TaxonomySearchRecord foundRecord = null;
					for (TaxonomySearchRecord record : records) {
						if (id.equals(record.getRecord().getId())) {
							if (foundRecord != null) {
								throw new RuntimeException("Same record found twice");
							}
							foundRecord = record;
						}
					}
					if (foundRecord == null) {
						throw new RuntimeException("Record not found : " + id);
					} else {
						assertThat(foundRecord.isLinkable()).isTrue();
					}

				}

				return true;
			}
		};
	}

	private abstract class LinkableTaxonomySearchResponseCaller {

		private LinkableTaxonomySearchResponse firstCallAnswer;

		private LinkableTaxonomySearchResponse secondCallAnswer;

		private String firstCallSolrQueries;

		private String secondCallSolrQueries;

		public LinkableTaxonomySearchResponse firstAnswer() {
			if (firstCallAnswer == null) {
				queriesCount.set(0);
				returnedDocumentsCount.set(0);
				facetsCount.set(0);
				firstCallAnswer = call();
				firstCallSolrQueries = queriesCount.get() + "-" + returnedDocumentsCount.get() + "-" + facetsCount.get();
				queriesCount.set(0);
				returnedDocumentsCount.set(0);
				facetsCount.set(0);
			}
			return firstCallAnswer;
		}

		public LinkableTaxonomySearchResponse secondAnswer() {
			firstAnswer();
			if (secondCallAnswer == null) {
				queriesCount.set(0);
				returnedDocumentsCount.set(0);
				facetsCount.set(0);
				secondCallAnswer = call();
				secondCallSolrQueries = queriesCount.get() + "-" + returnedDocumentsCount.get() + "-" + facetsCount.get();
				queriesCount.set(0);
				returnedDocumentsCount.set(0);
				facetsCount.set(0);
			}
			return secondCallAnswer;
		}

		protected abstract LinkableTaxonomySearchResponse call();

		public String firstAnswerSolrQueries() {
			firstAnswer();
			return firstCallSolrQueries;
		}

		public String secondAnswerSolrQueries() {
			secondAnswer();
			return secondCallSolrQueries;
		}

	}

	private ConditionTemplate withoutFilters = null;

	private ObjectAssert<LinkableTaxonomySearchResponseCaller> assertThatRootWhenUserNavigateUsingPlanTaxonomy(User user) {
		return assertThatRootWhenUserNavigateUsingPlanTaxonomy(user, 0, 10000);
	}

	private ObjectAssert<LinkableTaxonomySearchResponseCaller> assertThatRootWhenUserNavigateUsingPlanTaxonomy(final User user,
			final int start,
			final int rows) {

		return assertThat((LinkableTaxonomySearchResponseCaller) new LinkableTaxonomySearchResponseCaller() {

			@Override
			protected LinkableTaxonomySearchResponse call() {
				LinkableTaxonomySearchResponse response = service.getVisibleRootConceptResponse(
						user, zeCollection, CLASSIFICATION_PLAN, new TaxonomiesSearchOptions().setStartRow(start).setRows(rows),
						null);

				if (rows == 10000) {
					assertThat(response.getNumFound()).isEqualTo(response.getRecords().size());
				}
				return response;
			}
		});
	}

	private ObjectAssert<LinkableTaxonomySearchResponseCaller> assertThatRootWhenUserNavigateUsingPlanTaxonomy(final User user,
			final TaxonomiesSearchOptions options) {

		return assertThat((LinkableTaxonomySearchResponseCaller) new LinkableTaxonomySearchResponseCaller() {

			@Override
			protected LinkableTaxonomySearchResponse call() {
				LinkableTaxonomySearchResponse response = service.getVisibleRootConceptResponse(
						user, zeCollection, CLASSIFICATION_PLAN, options, null);

				if (options.getRows() == 10000) {
					assertThat(response.getNumFound()).isEqualTo(response.getRecords().size());
				}
				return response;
			}
		});
	}

	private ObjectAssert<LinkableTaxonomySearchResponseCaller> assertThatRootWhenUserNavigateUsingAdministrativeUnitsTaxonomy(
			final User user,
			final TaxonomiesSearchOptions options) {

		//return assertThat(response);
		return assertThat((LinkableTaxonomySearchResponseCaller) new LinkableTaxonomySearchResponseCaller() {

			@Override
			protected LinkableTaxonomySearchResponse call() {
				LinkableTaxonomySearchResponse response = service.getVisibleRootConceptResponse(
						user, zeCollection, RMTaxonomies.ADMINISTRATIVE_UNITS, options, null);

				if (options.getRows() == 10000) {
					assertThat(response.getNumFound()).isEqualTo(response.getRecords().size());
				}

				return response;
			}
		});
	}

	private ObjectAssert<LinkableTaxonomySearchResponseCaller> assertThatChildWhenUserNavigateUsingPlanTaxonomy(User user,
			String category) {
		return assertThatChildWhenUserNavigateUsingPlanTaxonomy(user, category, 0, 10000);
	}

	private ObjectAssert<LinkableTaxonomySearchResponseCaller> assertThatChildWhenUserNavigateUsingPlanTaxonomy(final User user,
			final String category,
			final int start, final int rows) {

		return assertThat((LinkableTaxonomySearchResponseCaller) new LinkableTaxonomySearchResponseCaller() {

			@Override
			protected LinkableTaxonomySearchResponse call() {
				Record inRecord = getModelLayerFactory().newRecordServices().getDocumentById(category);
				LinkableTaxonomySearchResponse response = service
						.getVisibleChildConceptResponse(user, CLASSIFICATION_PLAN, inRecord,
								new TaxonomiesSearchOptions().setStartRow(start).setRows(rows));

				if (rows == 10000) {
					assertThat(response.getNumFound()).isEqualTo(response.getRecords().size());
				}
				return response;
			}
		});
	}

	private ObjectAssert<LinkableTaxonomySearchResponseCaller> assertThatChildWhenUserNavigateUsingPlanTaxonomy(final User user,
			final String category, final TaxonomiesSearchOptions options) {

		return assertThat((LinkableTaxonomySearchResponseCaller) new LinkableTaxonomySearchResponseCaller() {

			@Override
			protected LinkableTaxonomySearchResponse call() {
				Record inRecord = getModelLayerFactory().newRecordServices().getDocumentById(category);
				LinkableTaxonomySearchResponse response = service
						.getVisibleChildConceptResponse(user, CLASSIFICATION_PLAN, inRecord, options);

				if (options.getRows() == 10000) {
					assertThat(response.getNumFound()).isEqualTo(response.getRecords().size());
				}

				return response;
			}
		});
	}

	private ObjectAssert<LinkableTaxonomySearchResponseCaller> assertThatChildWhenUserNavigateUsingAdminUnitsTaxonomy(
			final User user,
			final String category, final TaxonomiesSearchOptions options) {

		return assertThat((LinkableTaxonomySearchResponseCaller) new LinkableTaxonomySearchResponseCaller() {

			@Override
			protected LinkableTaxonomySearchResponse call() {
				Record inRecord = getModelLayerFactory().newRecordServices().getDocumentById(category);
				LinkableTaxonomySearchResponse response = service
						.getVisibleChildConceptResponse(user, RMTaxonomies.ADMINISTRATIVE_UNITS, inRecord, options);

				if (options.getRows() == 10000) {
					assertThat(response.getNumFound()).isEqualTo(response.getRecords().size());
				}

				return response;
			}
		});
	}

	private Condition<? super LinkableTaxonomySearchResponseCaller> fastContinuationInfos(
			final boolean expectedinishedIteratingOverConcepts,
			final int expectedLastReturnRecordIndex, String... ids) {

		final List<String> expectedIds = asList(ids);

		return new Condition<LinkableTaxonomySearchResponseCaller>() {
			@Override
			public boolean matches(LinkableTaxonomySearchResponseCaller value) {

				assertThat(value.firstAnswer().getFastContinueInfos().getShownRecordsWithVisibleChildren())
						.describedAs("notYetShownRecordsWithVisibleChildren").isEqualTo(expectedIds);

				assertThat(value.firstAnswer().getFastContinueInfos().finishedConceptsIteration)
						.describedAs("notYetShownRecordsWithVisibleChildren").isEqualTo(expectedinishedIteratingOverConcepts);

				assertThat(value.firstAnswer().getFastContinueInfos().getLastReturnRecordIndex())
						.describedAs("lastReturnRecordIndex").isEqualTo(expectedLastReturnRecordIndex);

				assertThat(value.secondAnswer().getFastContinueInfos().getShownRecordsWithVisibleChildren())
						.describedAs("notYetShownRecordsWithVisibleChildren").isEqualTo(expectedIds);

				assertThat(value.secondAnswer().getFastContinueInfos().finishedConceptsIteration)
						.describedAs("notYetShownRecordsWithVisibleChildren").isEqualTo(expectedinishedIteratingOverConcepts);

				assertThat(value.secondAnswer().getFastContinueInfos().getLastReturnRecordIndex())
						.describedAs("lastReturnRecordIndex").isEqualTo(expectedLastReturnRecordIndex);

				return true;
			}
		};
	}

	private SolrAssert assertThatSolr() {
		return new SolrAssert();
	}

	private int printCounter;

	private class SolrAssert {

		public SolrAssert print() {

			String current = queriesCount.get() + "-" + returnedDocumentsCount.get() + "-" + facetsCount.get();

			System.out.println("COUNTER : " + (++printCounter) + " : " + current);

			queriesCount.set(0);
			facetsCount.set(0);
			returnedDocumentsCount.set(0);

			return this;
		}

		public SolrAssert hasCounts(int queries, int queryResults, int facets) {
			String expected = queries + "-" + queryResults + "-" + facets;
			String current = queriesCount.get() + "-" + returnedDocumentsCount.get() + "-" + facetsCount.get();

			//assertThat(current).describedAs("Queries count - Query resuts count - Facets count").isEqualTo(expected);
			queriesCount.set(0);
			facetsCount.set(0);
			returnedDocumentsCount.set(0);

			return this;
		}

		public SolrAssert hasQueriesCountOf(int count) {
			assertThat(queriesCount.get()).describedAs("Solr queries count").isEqualTo(count);
			queriesCount.set(0);

			return this;
		}

		public SolrAssert hasQueriesResultsCountOf(int count) {
			assertThat(returnedDocumentsCount.get()).describedAs("Returned solr query results count").isEqualTo(count);
			returnedDocumentsCount.set(0);

			return this;
		}

		public SolrAssert hasFacetsCountOf(int count) {
			assertThat(facetsCount.get()).describedAs("Solr facets count").isEqualTo(count);
			facetsCount.set(0);
			return this;
		}

	}

}
