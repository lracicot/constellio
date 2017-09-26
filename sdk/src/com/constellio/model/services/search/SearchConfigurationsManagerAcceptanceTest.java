package com.constellio.model.services.search;

import com.constellio.app.modules.rm.RMTestRecords;
import com.constellio.app.modules.rm.services.RMSchemasRecordsServices;
import com.constellio.app.modules.rm.wrappers.Document;
import com.constellio.model.entities.enums.ParsingBehavior;
import com.constellio.model.entities.records.Content;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.records.wrappers.RecordWrapper;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.services.contents.ContentManager;
import com.constellio.model.services.contents.ContentVersionDataSummary;
import com.constellio.model.services.migrations.ConstellioEIMConfigs;
import com.constellio.model.services.records.RecordServices;
import com.constellio.model.services.records.RecordServicesException;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery;
import com.constellio.model.services.search.query.logical.condition.LogicalSearchCondition;
import com.constellio.sdk.tests.ConstellioTest;
import com.constellio.sdk.tests.setups.Users;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.fromAllSchemasIn;
import static org.assertj.core.api.Assertions.assertThat;

public class SearchConfigurationsManagerAcceptanceTest extends ConstellioTest {

    public SearchConfigurationsManager searchConfigurationsManager;
    public static final String SYNONYM_1 = "car";
    public static final String TWO_SYNONYMS = SYNONYM_1 +  ", ppppppppppppppppp";
    public static final String THREE_SYNONYMS = "auto, cccccccccccccc, dada";

    Users users = new Users();
    RecordServices recordServices;
    RMSchemasRecordsServices rm;
    RMTestRecords records = new RMTestRecords(zeCollection);
    SearchServices searchServices;

    @Before
    public void setUp() {
        prepareSystem(withZeCollection().withConstellioRMModule().withRMTest(records).withAllTest(users).withFoldersAndContainersOfEveryStatus());

        searchConfigurationsManager = getModelLayerFactory().getSearchConfigurationsManager();
        recordServices = getModelLayerFactory().newRecordServices();
        rm = new RMSchemasRecordsServices(zeCollection, getAppLayerFactory());
        searchServices = getModelLayerFactory().newSearchServices();
    }

    @Test
    public void getSetGetSynonymsOnServerThenOk() {
        searchConfigurationsManager.setSynonyms(new ArrayList<String>());
        searchConfigurationsManager.initialize();
        assertThat(searchConfigurationsManager.getSynonyms().size()).isEqualTo(1);
        assertThat(searchConfigurationsManager.getSynonyms().get(0)).isEqualTo("");
        searchConfigurationsManager.setSynonyms(Arrays.asList(TWO_SYNONYMS, THREE_SYNONYMS));
        searchConfigurationsManager.initialize();
        assertThat(searchConfigurationsManager.getSynonyms().get(0)).isEqualTo(TWO_SYNONYMS);
        assertThat(searchConfigurationsManager.getSynonyms().get(1)).isEqualTo(THREE_SYNONYMS);
    }

    @Test
    public void setTwoFileWithContentWithTwoSynonymsDisplayTheTwoDocumentThenOk() throws Exception {
        givenBackgroundThreadsEnabled();
        givenConfig(ConstellioEIMConfigs.DEFAULT_PARSING_BEHAVIOR, ParsingBehavior.SYNC_PARSING_FOR_ALL_CONTENTS);
        uploadARecord(rm.newDocument().setFolder(records.folder_A03), "car.docx");
        uploadARecord(rm.newDocument().setFolder(records.folder_C01),"p.docx");

        searchConfigurationsManager.setSynonyms(Arrays.asList(TWO_SYNONYMS));

        LogicalSearchCondition condition = fromAllSchemasIn(zeCollection).returnAll();
        LogicalSearchQuery query = new LogicalSearchQuery(condition).setFreeTextQuery(SYNONYM_1);


        List<Record> resultlist = searchServices.search(query);

        assertThat(resultlist.size()).isEqualTo(2);
    }


    private void uploadARecord(RecordWrapper recordWrapper, String newFile)
            throws RecordServicesException {
        User user = users.adminIn("zeCollection");

        ContentManager cm = getModelLayerFactory().getContentManager();
        ContentVersionDataSummary version = cm.upload(getTestResourceInputStream(newFile));
        Content content = cm.createMinor(user, newFile, version);
        recordWrapper.setTitle(newFile);

        ((Document) recordWrapper).setContent(content);

        recordServices.add(recordWrapper);

    }
}
