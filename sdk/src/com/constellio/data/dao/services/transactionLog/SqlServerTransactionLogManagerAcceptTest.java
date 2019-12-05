package com.constellio.data.dao.services.transactionLog;

import com.constellio.data.conf.PropertiesDataLayerConfiguration.InMemoryDataLayerConfiguration;
import com.constellio.data.conf.SecondTransactionLogType;
import com.constellio.data.dao.dto.records.RecordDTO;
import com.constellio.data.dao.dto.records.RecordsFlushing;
import com.constellio.data.dao.dto.sql.RecordTransactionSqlDTO;
import com.constellio.data.dao.services.bigVault.BigVaultRecordDao;
import com.constellio.data.dao.services.bigVault.solr.BigVaultServerTransaction;
import com.constellio.data.dao.services.records.RecordDao;
import com.constellio.data.dao.services.solr.ConstellioSolrInputDocument;
import com.constellio.data.dao.services.sql.SqlRecordDaoType;
import com.constellio.data.utils.LangUtils;
import com.constellio.data.utils.ThreadList;
import com.constellio.model.entities.records.Content;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.services.contents.ContentManager;
import com.constellio.model.services.contents.ContentVersionDataSummary;
import com.constellio.model.services.migrations.ConstellioEIMConfigs;
import com.constellio.model.services.records.RecordServices;
import com.constellio.model.services.records.RecordServicesException;
import com.constellio.model.services.records.reindexing.ReindexationMode;
import com.constellio.model.services.records.reindexing.ReindexationParams;
import com.constellio.model.services.records.reindexing.ReindexingServices;
import com.constellio.model.services.schemas.MetadataSchemaTypesAlteration;
import com.constellio.model.services.schemas.builders.MetadataSchemaTypesBuilder;
import com.constellio.model.services.search.SearchServices;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery;
import com.constellio.sdk.tests.ConstellioTest;
import com.constellio.sdk.tests.DataLayerConfigurationAlteration;
import com.constellio.sdk.tests.SolrSDKToolsServices;
import com.constellio.sdk.tests.SolrSDKToolsServices.VaultSnapshot;
import com.constellio.sdk.tests.TestRecord;
import com.constellio.sdk.tests.schemas.TestsSchemasSetup;
import com.constellio.sdk.tests.schemas.TestsSchemasSetup.ZeSchemaMetadatas;
import com.constellio.sdk.tests.setups.Users;
import org.apache.commons.io.IOUtils;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.assertj.core.api.Condition;
import org.joda.time.Duration;
import org.joda.time.LocalDateTime;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.constellio.model.services.records.reindexing.ReindexationMode.RECALCULATE_AND_REWRITE;
import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.from;
import static com.constellio.sdk.tests.schemas.TestsSchemasSetup.whichIsSearchable;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class SqlServerTransactionLogManagerAcceptTest extends ConstellioTest {

	private LocalDateTime shishOClock = new LocalDateTime();
	private LocalDateTime shishOClockPlus1Hour = shishOClock.plusHours(1);
	private LocalDateTime shishOClockPlus2Hour = shishOClock.plusHours(2);
	private LocalDateTime shishOClockPlus3Hour = shishOClock.plusHours(3);
	private LocalDateTime shishOClockPlus4Hour = shishOClock.plusHours(4);

	private static final Logger LOGGER = LoggerFactory.getLogger(KafkaTransactionLogManagerAcceptTest.class);

	Users users = new Users();

	TestsSchemasSetup schemas = new TestsSchemasSetup();
	ZeSchemaMetadatas zeSchema = schemas.new ZeSchemaMetadatas();

	SqlServerTransactionLogManager log;

	ReindexingServices reindexServices;
	RecordServices recordServices;

	private AtomicInteger index = new AtomicInteger(-1);
	private AtomicInteger titleIndex = new AtomicInteger(0);
	private List<String> recordTextValues = new ArrayList<>();

	@Before
	public void setUp()
			throws Exception {
		// givenHashingEncodingIs(BASE64_URL_ENCODED);
		givenBackgroundThreadsEnabled();
		//withSpiedServices(SecondTransactionLogManager.class);

		configure(new DataLayerConfigurationAlteration() {
			@Override
			public void alter(InMemoryDataLayerConfiguration configuration) {
				configuration.setSecondTransactionLogEnabled(true);
				configuration.setSecondTransactionLogMode(SecondTransactionLogType.SQL_SERVER);
				configuration.setSecondTransactionLogMergeFrequency(Duration.standardSeconds(5));
				configuration.setSecondTransactionLogBackupCount(3);
				configuration.setReplayTransactionStartVersion(0L);
				configuration.setMicrosoftSqlServerUrl("jdbc:sqlserver://localhost:1433");
				configuration.setMicrosoftSqlServerDatabase("constellio");
				configuration.setMicrosoftSqlServeruser("constellio");
				configuration.setMicrosoftSqlServerpassword("ncix123$");
				configuration.setMicrosoftSqlServerencrypt(false);
				configuration.setMicrosoftSqlServertrustServerCertificate(false);
				configuration.setMicrosoftSqlServerloginTimeout(15);
			}
		});

		givenCollection(zeCollection).withAllTestUsers();

		defineSchemasManager().using(schemas.withAStringMetadata().withAContentMetadata(whichIsSearchable));

		reindexServices = getModelLayerFactory().newReindexingServices();
		recordServices = getModelLayerFactory().newRecordServices();
		log = (SqlServerTransactionLogManager) getDataLayerFactory().getSecondTransactionLogManager();
	}

	// @LoadTest
	@Test
	public void whenMultipleThreadsAreAdding5000RecordsThenAllRecordsAreLogged()
			throws Exception {
		runAdding(2000000);
		//		assertThat(log.isLastFlushFailed()).isFalse();

		//log.destroyAndRebuildSolrCollection();
	}

	@Test
	public void givenSchemaTypeRecordsAreNotSavedInTransactionLogThenNotInTLog()
			throws Exception {

		getModelLayerFactory().getMetadataSchemasManager().modify(zeCollection, new MetadataSchemaTypesAlteration() {
			@Override
			public void alter(MetadataSchemaTypesBuilder types) {
				types.getSchemaType(zeSchema.typeCode()).setInTransactionLog(false);
			}
		});
		schemas.refresh();

		getModelLayerFactory().getSystemConfigurationsManager()
				.setValue(ConstellioEIMConfigs.WRITE_ZZRECORDS_IN_TLOG, false);

		User admin = getModelLayerFactory().newUserServices().getUserInCollection("admin", zeCollection);
		ContentManager contentManager = getModelLayerFactory().getContentManager();
		ContentVersionDataSummary data = contentManager
				.upload(getTestResourceInputStreamFactory("guide.pdf").create(SDK_STREAM));

		//Schema schema = getModelLayerFactory().getMetadataSchemasManager().getSchemaTypes(zeCollection).getSchema()
		Record record1 = recordServices.newRecordWithSchema(zeSchema.instance());
		record1.set(zeSchema.stringMetadata(), "Guide d'architecture");
		record1.set(zeSchema.contentMetadata(), contentManager.createMajor(admin, "guide.pdf", data));
		recordServices.add(record1);

		record1.set(zeSchema.stringMetadata(), "Guide d'architecture 2");
		recordServices.update(record1);

		recordServices.logicallyDelete(record1, User.GOD);
		recordServices.physicallyDelete(record1, User.GOD);

		log.regroupAndMove();
		log.destroyAndRebuildSolrCollection();

		assertThat(completeRecordsLOG().stream().anyMatch(x -> x.getRecordId().equals(record1.getId()))).isFalse();
	}

	@Test
	public void givenRecordWithParsedContentWithMultipleTypesOfLinebreakThenCanReplayWithoutProblems()
			throws Exception {

		User admin = getModelLayerFactory().newUserServices().getUserInCollection("admin", zeCollection);
		ContentManager contentManager = getModelLayerFactory().getContentManager();
		ContentVersionDataSummary data = contentManager
				.upload(getTestResourceInputStreamFactory("guide.pdf").create(SDK_STREAM));

		Record record1 = new TestRecord(zeSchema, "zeRecord");
		record1.set(zeSchema.stringMetadata(), "Guide d'architecture");
		record1.set(zeSchema.contentMetadata(), contentManager.createMajor(admin, "guide.pdf", data));
		recordServices.add(record1);

		log.regroupAndMove();
		log.destroyAndRebuildSolrCollection();

		Content content = recordServices.getDocumentById("zeRecord").get(zeSchema.contentMetadata());
		assertThat(content.getCurrentVersion().getHash()).isEqualTo("io25znMv7hM3k-m441kYKBEHbbE=");
	}

	@Test
	public void givenSequencesWhenReindexReplayLoggedThenSetToGoodValues()
			throws Exception {

		for (int i = 0; i < 6; i++) {
			getDataLayerFactory().getSequencesManager().next("zeSequence");
		}
		getDataLayerFactory().getSequencesManager().set("zeSequence", 10);
		for (int i = 0; i < 32; i++) {
			getDataLayerFactory().getSequencesManager().next("zeSequence");
		}
		getDataLayerFactory().getSequencesManager().set("anotherSequence", 666);

		getModelLayerFactory().newReindexingServices().reindexCollections(RECALCULATE_AND_REWRITE);

		log.regroupAndMove();
		log.destroyAndRebuildSolrCollection();

		assertThat(getDataLayerFactory().getSequencesManager().getLastSequenceValue("zeSequence")).isEqualTo(42);
		assertThat(getDataLayerFactory().getSequencesManager().getLastSequenceValue("anotherSequence")).isEqualTo(666);
	}

	@Test
	public void whenReindexingWithoutRewriteOrOnlyASpecificCollectionThenDoNotRewriteTLog()
			throws Exception {

		Record record1 = new TestRecord(zeSchema);
		record1.set(zeSchema.stringMetadata(), "Darth Vador");
		recordServices.add(record1);
		log.regroupAndMove();

		Record record2 = new TestRecord(zeSchema);
		record2.set(zeSchema.stringMetadata(), "Luke Skywalker");
		recordServices.add(record2);
		log.regroupAndMove();

		assertThat(completeTLOG()).is(onlyContainingValues("Darth Vador", "Luke Skywalker"));

		recordServices.update(record1.set(zeSchema.stringMetadata(), "Obi-Wan Kenobi"));
		recordServices.update(record2.set(zeSchema.stringMetadata(), "Yoda"));
		recordServices.update(record2.set(zeSchema.stringMetadata(), "Anakin Skywalker"));

		reindexServices.reindexCollection(zeCollection, new ReindexationParams(ReindexationMode.REWRITE));
		log.regroupAndMove();
		assertThat(completeTLOG()).is(containingAllValues());

		reindexServices.reindexCollection(zeCollection, new ReindexationParams(ReindexationMode.RECALCULATE));
		log.regroupAndMove();
		assertThat(completeTLOG()).is(containingAllValues());

		reindexServices.reindexCollection(zeCollection, new ReindexationParams(RECALCULATE_AND_REWRITE));
		log.regroupAndMove();
		assertThat(completeTLOG()).is(containingAllValues());

		reindexServices.reindexCollections(new ReindexationParams(ReindexationMode.RECALCULATE));
		log.regroupAndMove();

		assertThat(completeTLOG()).is(containingAllValues());
	}

	private List<String> allValues = asList("Darth Vador", "Luke Skywalker", "Obi-Wan Kenobi", "Yoda",
			"Anakin Skywalker");

	private Condition<? super String> containingAllValues() {
		final List<String> expectedValuesList = allValues;
		return new Condition<String>() {
			@Override
			public boolean matches(String value) {

				for (String aValue : allValues) {
					if (expectedValuesList.contains(aValue)) {
						assertThat(value).contains(aValue);
					} else {
						assertThat(value).doesNotContain(aValue);
					}
				}

				return true;
			}
		};
	}

	private Condition<? super String> onlyContainingValues(final String... expectedValues) {
		final List<String> expectedValuesList = asList(expectedValues);
		return new Condition<String>() {
			@Override
			public boolean matches(String value) {

				for (String aValue : allValues) {
					if (expectedValuesList.contains(aValue)) {
						assertThat(value).contains(aValue);
					} else {
						assertThat(value).doesNotContain(aValue);
					}
				}

				return true;
			}
		};
	}

	private void runAdding(final int nbRecordsToAdd)
			throws Exception {
		final ThreadList<Thread> threads = new ThreadList<>();
		for (int i = 1; i <= nbRecordsToAdd; i++) {
			recordTextValues.add("The Hobbit - Episode " + i + " of " + nbRecordsToAdd);
		}

		for (int i = 0; i < 10; i++) {

			threads.add(new Thread(String.valueOf(i)) {
				@Override
				public void run() {
					int arrayIndex;

					while ((arrayIndex = index.incrementAndGet()) < nbRecordsToAdd) {
						if ((arrayIndex + 1) % 500 == 0) {
							System.out.println((arrayIndex + 1) + " / " + nbRecordsToAdd + " (Thread numero : " + getName() + ")");
						}
						Record record = new TestRecord(zeSchema);

						String title = "The Hobbit - Episode " + (arrayIndex + 1) + " of " + nbRecordsToAdd;
						record.set(zeSchema.stringMetadata(), title);
						try {
							recordServices.add(record);
						} catch (RecordServicesException e) {
							throw new RuntimeException(e);
						}
					}
				}
			});
		}
		threads.startAll();
		threads.joinAll();

		int i = 0;
		while (log.getTableTransactionCount() != 0) {
			Thread.sleep(100);
			i++;
			if (i > 300) {
				fail("Never committed");
			}
		}

		List<String> stringMetadataLines = new ArrayList<>();
		List<RecordTransactionSqlDTO> transactionLogs = completeRecordsLOG();

		for (RecordTransactionSqlDTO record : transactionLogs) {
			InputStream logStream = getDataLayerFactory().getContentsDao().getContentInputStream(record.getRecordId(), SDK_STREAM);
			for (String line : IOUtils.readLines(logStream)) {
				stringMetadataLines.add(line);
			}
		}

		for (String value : recordTextValues) {
			assertThat(stringMetadataLines).contains(zeSchema.stringMetadata().getDataStoreCode() + "=" + value);
		}


		RecordDao recordDao = getDataLayerFactory().newRecordDao();
		SolrSDKToolsServices solrSDKTools = new SolrSDKToolsServices(recordDao);
		VaultSnapshot beforeRebuild = solrSDKTools.snapshot();

		alterSomeDocuments();

		log.destroyAndRebuildSolrCollection();

		VaultSnapshot afterRebuild = solrSDKTools.snapshot();
		solrSDKTools.ensureSameSnapshots("vault altered", beforeRebuild, afterRebuild);

		for (String text : recordTextValues) {
			assertThat(getRecordsByStringMetadata(text)).hasSize(1);
		}

	}

	private List<String> getRecordsByStringMetadata(String value) {
		SearchServices searchServices = getModelLayerFactory().newSearchServices();
		return searchServices.searchRecordIds(new LogicalSearchQuery()
				.setCondition(from(zeSchema.instance()).where(zeSchema.stringMetadata()).isEqualTo(value)));
	}

	private void alterSomeDocuments()
			throws Exception {

		BigVaultRecordDao recordDao = (BigVaultRecordDao) getDataLayerFactory().newRecordDao();

		String idOf42 = getRecordsByStringMetadata(recordTextValues.get(42)).get(0);
		String idOf66 = getRecordsByStringMetadata(recordTextValues.get(66)).get(0);
		String idOf72 = getRecordsByStringMetadata(recordTextValues.get(72)).get(0);

		ModifiableSolrParams params = new ModifiableSolrParams();
		params.set("q", "id:" + idOf42);

		RecordDTO recordDTO = recordDao.get(idOf66);

		SolrInputDocument documentUpdate = new ConstellioSolrInputDocument();
		documentUpdate.addField("id", idOf66);
		documentUpdate.addField("_version_", recordDTO.getVersion());
		documentUpdate.addField("stringMetadata_s", LangUtils.newMapWithEntry("set", "Mouhahahahaha"));

		recordDao.getBigVaultServer().addAll(new BigVaultServerTransaction(RecordsFlushing.NOW())
				.setUpdatedDocuments(asList(documentUpdate))
				.addDeletedQuery("id:" + idOf42));

	}

	private List<RecordTransactionSqlDTO> completeRecordsLOG()
			throws Exception {
		List<RecordTransactionSqlDTO> transactionLogs = getDataLayerFactory().getSqlRecordDao().getRecordDao(SqlRecordDaoType.RECORDS).getAll();

		return transactionLogs;
	}

	private String completeTLOG() throws SQLException {
		List<RecordTransactionSqlDTO> transactionLogs = getDataLayerFactory().getSqlRecordDao().getRecordDao(SqlRecordDaoType.RECORDS).getAll();

		return String.join("\n" , transactionLogs.stream().map(x-> x.getContent()).collect(Collectors.toList()));
	}


}

