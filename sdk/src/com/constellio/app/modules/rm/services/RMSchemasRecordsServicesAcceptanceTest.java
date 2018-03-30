package com.constellio.app.modules.rm.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.Test;

import com.constellio.app.modules.rm.RMTestRecords;
import com.constellio.app.modules.rm.wrappers.Category;
import com.constellio.app.modules.rm.wrappers.Folder;
import com.constellio.model.entities.schemas.Schemas;
import com.constellio.model.services.records.RecordServices;
import com.constellio.model.services.schemas.MetadataSchemaTypesAlteration;
import com.constellio.model.services.schemas.builders.MetadataSchemaTypesBuilder;
import com.constellio.sdk.tests.ConstellioTest;
import com.constellio.sdk.tests.annotations.InDevelopmentTest;
import com.constellio.sdk.tests.setups.Users;

public class RMSchemasRecordsServicesAcceptanceTest extends ConstellioTest {

	RMTestRecords records = new RMTestRecords(zeCollection);
	Users users = new Users();

	RMSchemasRecordsServices rm;

	@Test
	public void validateLinkedSchemaUtilsMethods()
			throws Exception {

		prepareSystem(withZeCollection().withConstellioRMModule().withRMTest(records).withFoldersAndContainersOfEveryStatus()
				.withAllTest(users));

		rm = new RMSchemasRecordsServices(zeCollection, getAppLayerFactory());

		assertThat(rm.getLinkedSchemaOf(rm.getFolderTypeWithCode("meetingFolder"))).isEqualTo("folder_meetingFolder");
		assertThat(rm.getLinkedSchemaOf(rm.getFolderTypeWithCode("employe"))).isEqualTo("folder_employe");
		assertThat(rm.getLinkedSchemaOf(rm.getFolderTypeWithCode("other"))).isEqualTo("folder_default");

		Folder folder = records.getFolder_A05();
		assertThat(rm.getLinkedSchemaOf(folder)).isEqualTo("folder_default");

		folder.setType(rm.getFolderTypeWithCode("meetingFolder"));
		assertThat(rm.getLinkedSchemaOf(folder)).isEqualTo("folder_meetingFolder");

		folder.setType(rm.getFolderTypeWithCode("employe"));
		assertThat(rm.getLinkedSchemaOf(folder)).isEqualTo("folder_employe");

		folder.setType(rm.getFolderTypeWithCode("other"));
		assertThat(rm.getLinkedSchemaOf(folder)).isEqualTo("folder_default");

	}

	@Test
	public void givenMultilingualMetadatasThenLocalisedValueObtainedUsingSchemasRecordServices()
			throws Exception {

		prepareSystem(withZeCollection().withConstellioRMModule().withRMTest(records).withFoldersAndContainersOfEveryStatus()
				.withAllTest(users));

		RecordServices recordServices = getModelLayerFactory().newRecordServices();
		rm = new RMSchemasRecordsServices(zeCollection, getAppLayerFactory());

		getModelLayerFactory().getMetadataSchemasManager().modify(zeCollection, new MetadataSchemaTypesAlteration() {
			@Override
			public void alter(MetadataSchemaTypesBuilder types) {
				types.getSchemaType(Category.SCHEMA_TYPE).getDefaultSchema().get(Category.TITLE).setMultiLingual(true);
			}
		});

		assertThat(recordServices.getDocumentById(records.categoryId_X).get(Schemas.TITLE)).isEqualTo("Xe category");
		assertThat(recordServices.getDocumentById(records.categoryId_X).get(Schemas.TITLE, Locale.CANADA_FRENCH))
				.isEqualTo("{fr} Xe category");
		assertThat(recordServices.getDocumentById(records.categoryId_X).get(Schemas.TITLE, Locale.ENGLISH))
				.isEqualTo("{en} Xe category");

		RMSchemasRecordsServices defaultRM = new RMSchemasRecordsServices(zeCollection, getAppLayerFactory());
		RMSchemasRecordsServices frenchRM = new RMSchemasRecordsServices(zeCollection, getAppLayerFactory(),
				Locale.CANADA_FRENCH);
		RMSchemasRecordsServices englishRM = new RMSchemasRecordsServices(zeCollection, getAppLayerFactory(), Locale.ENGLISH);

		assertThat(defaultRM.getCategory(records.categoryId_X).getCode()).isEqualTo("X");
		assertThat(frenchRM.getCategory(records.categoryId_X).getCode()).isEqualTo("X");
		assertThat(englishRM.getCategory(records.categoryId_X).getCode()).isEqualTo("X");

		assertThat(defaultRM.getCategory(records.categoryId_X).getTitle()).isEqualTo("{fr} Xe category");
		assertThat(frenchRM.getCategory(records.categoryId_X).getTitle()).isEqualTo("{fr} Xe category");
		assertThat(englishRM.getCategory(records.categoryId_X).getTitle()).isEqualTo("{en} Xe category");

	}

	@Test
	@InDevelopmentTest
	public void testRmUserFolder() {
		assertThat(rm.newUserFolder()).isNotNull();
		assertThat(rm.newUserFolderWithId("test1").getId()).isEqualTo("test1");
	}
}
