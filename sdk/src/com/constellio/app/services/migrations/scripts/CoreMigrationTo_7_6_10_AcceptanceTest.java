package com.constellio.app.services.migrations.scripts;

import com.constellio.app.modules.rm.services.RMSchemasRecordsServices;
import com.constellio.data.dao.services.contents.ContentDao;
import com.constellio.model.services.contents.ContentManager;
import com.constellio.sdk.tests.ConstellioTest;
import org.junit.Test;

import static com.constellio.model.entities.schemas.Schemas.IDENTIFIER;
import static com.constellio.model.entities.schemas.Schemas.MARKED_FOR_PREVIEW_CONVERSION;
import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.ALL;
import static com.constellio.sdk.tests.TestUtils.assertThatRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class CoreMigrationTo_7_6_10_AcceptanceTest extends ConstellioTest {

	@Test
	public void whenMigratingTo7_6_10ThenContentNeedingReconversionAreFlagged() {
		givenTransactionLogIsEnabled();

		getCurrentTestSession().getFactoriesTestFeatures()
							   .givenSystemInState(getTestResourceFile("saveStateWithContentToConvert.zip")).withPasswordsReset()
							   .withFakeEncryptionServices();

		ContentDao contentDao = getDataLayerFactory().getContentsDao();
		assertThat(contentDao.getContentLength("DVWQVPBPGDRIYQWFTEEU2X6EO6KQ5EXR.preview")).isEqualTo(0);
		assertThat(contentDao.getContentLength("BJKNYGROLCWIFSE3DWRBR4DELZQBWBUT.preview")).isEqualTo(438516L);

		reindex();

		RMSchemasRecordsServices rm = new RMSchemasRecordsServices(zeCollection, getAppLayerFactory());
		assertThatRecords(rm.searchDocuments(ALL)).extractingMetadatas(IDENTIFIER, MARKED_FOR_PREVIEW_CONVERSION).containsOnly(
				tuple("00000000099", null),
				tuple("00000000105", null),
				tuple("00000000102", true)
		);

		ContentManager contentManager = getModelLayerFactory().getContentManager();
		contentManager.convertPendingContentForPreview();

		assertThat(contentDao.getContentLength("DVWQVPBPGDRIYQWFTEEU2X6EO6KQ5EXR.preview")).isGreaterThan(0L);
		assertThat(contentDao.getContentLength("BJKNYGROLCWIFSE3DWRBR4DELZQBWBUT.preview")).isEqualTo(438516L);

	}
}
