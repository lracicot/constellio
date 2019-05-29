package com.constellio.model.services.records.cache2;

import com.constellio.data.dao.dto.records.RecordDTO;
import com.constellio.data.dao.dto.records.SolrRecordDTO;
import com.constellio.data.utils.ThreadList;
import com.constellio.model.entities.schemas.RecordCacheType;
import com.constellio.model.services.collections.CollectionsListManager;
import com.constellio.model.services.records.RecordServices;
import com.constellio.model.services.records.reindexing.ReindexingServices;
import com.constellio.model.services.schemas.MetadataSchemaTypesAlteration;
import com.constellio.model.services.schemas.MetadataSchemasManager;
import com.constellio.model.services.schemas.builders.MetadataSchemaTypesBuilder;
import com.constellio.sdk.tests.ConstellioTest;
import com.constellio.sdk.tests.schemas.TestsSchemasSetup;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.constellio.sdk.tests.schemas.TestsSchemasSetup.whichIsEssentialInSummary;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;

public class RecordsCachesDataStoreAcceptanceTest extends ConstellioTest {

	RecordsCachesDataStore dataStore;

	TestsSchemasSetup setup = new TestsSchemasSetup(zeCollection);
	TestsSchemasSetup.ZeSchemaMetadatas zeSchema = setup.new ZeSchemaMetadatas();
	TestsSchemasSetup.AnotherSchemaMetadatas anotherSchema = setup.new AnotherSchemaMetadatas();

	MetadataSchemasManager schemasManager;
	RecordServices recordServices;
	ReindexingServices reindexingServices;

	@Before
	public void setUp() throws Exception {
		schemasManager = getModelLayerFactory().getMetadataSchemasManager();
		recordServices = getModelLayerFactory().newRecordServices();
		reindexingServices = getModelLayerFactory().newReindexingServices();

		SummaryCacheSingletons.dataStore = new FileSystemRecordsValuesCacheDataStore(new File(newTempFolder(), "test.db"));
	}


	@After
	public void tearDown() throws Exception {
		SummaryCacheSingletons.dataStore.close();
	}


	MetadataSchemasManager metadataSchemasManager;
	CollectionsListManager collectionsListManager;

	byte zeCollectionId;
	byte anotherCollectionId;
	int zeCollectionIndex;
	int anotherCollectionIndex;
	short zeCollectionType1Id;
	short zeCollectionType2Id;
	short zeSchemaDefaultId;

	String[] collections = new String[]{"zeCollection", "anotherCollection"};
	String[] types = new String[]{"type1", "type2"};

	private void initTestVariables() {
		collectionsListManager = getModelLayerFactory().getCollectionsListManager();
		zeCollectionId = collectionsListManager.getCollectionId(collections[0]);
		anotherCollectionId = collectionsListManager.getCollectionId(collections[1]);

		metadataSchemasManager = getModelLayerFactory().getMetadataSchemasManager();

		zeCollectionType1Id = metadataSchemasManager.getSchemaTypes(zeCollectionId).getSchemaType(zeSchema.typeCode()).getId();
		zeCollectionType2Id = metadataSchemasManager.getSchemaTypes(zeCollectionId).getSchemaType(anotherSchema.typeCode()).getId();
		zeCollectionIndex = zeCollectionId - Byte.MIN_VALUE;
		anotherCollectionIndex = anotherCollectionId - Byte.MIN_VALUE;
		dataStore = new RecordsCachesDataStore(getModelLayerFactory());
		zeSchemaDefaultId = zeSchema.instance().getId();
	}

	@Test
	public void whenPreloadingCacheWithSpacedIdsThenNoSpaceReservedForIdsBetween() throws Exception {
		defineSchemasManager().using(setup.withABooleanMetadata(whichIsEssentialInSummary));

		getModelLayerFactory().getMetadataSchemasManager().modify(zeCollection, new MetadataSchemaTypesAlteration() {
			@Override
			public void alter(MetadataSchemaTypesBuilder types) {
				types.getSchemaType(zeSchema.typeCode()).setRecordCacheType(RecordCacheType.SUMMARY_CACHED_WITH_VOLATILE);
			}
		});
		initTestVariables();

		ByteArrayRecordDTO dto1, dto2, dto3, dto6, dto7, dto8;
		dto1 = create(new SolrRecordDTO(zeroPadded(1), 12L, fields("zeCollection", zeSchema.code()), true));
		dto3 = create(new SolrRecordDTO(zeroPadded(3), 23L, fields("zeCollection", zeSchema.code()), true));
		dto6 = create(new SolrRecordDTO(zeroPadded(6), 34L, fields("zeCollection", zeSchema.code()), true));
		dto8 = create(new SolrRecordDTO(zeroPadded(8), 45L, fields("zeCollection", zeSchema.code()), true));

		dataStore.insertWithoutReservingSpaceForPreviousIds(dto1);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto3);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto6);
		dataStore.insert(dto8);

		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 3, 6, 7, 8);

		assertThat(dataStore.intIdsDataStore.versions.stream().collect(toList()))
				.containsExactly(12L, 23L, 34L, 0L, 45L);

		assertThat(dataStore.intIdsDataStore.schema.stream().collect(toList()))
				.containsExactly(zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, (short) 0, zeSchemaDefaultId);

		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsExactly(dto1, dto3, dto6, dto8);


	}


	@Test
	public void givenRecordAddedAfterPreloadingTheAddedReservingSpaceAllowingToAddTheRecordsWithoutReallocate()
			throws Exception {
		defineSchemasManager().using(setup.withABooleanMetadata(whichIsEssentialInSummary));

		getModelLayerFactory().getMetadataSchemasManager().modify(zeCollection, new MetadataSchemaTypesAlteration() {
			@Override
			public void alter(MetadataSchemaTypesBuilder types) {
				types.getSchemaType(zeSchema.typeCode()).setRecordCacheType(RecordCacheType.SUMMARY_CACHED_WITHOUT_VOLATILE);
			}
		});
		initTestVariables();

		ByteArrayRecordDTO dto1, dto2, dto3, dto6, dto7, dto8, dto10, dto12;
		dto1 = create(new SolrRecordDTO(zeroPadded(1), 12L, fields("zeCollection", zeSchema.code()), true));
		dto3 = create(new SolrRecordDTO(zeroPadded(3), 23L, fields("zeCollection", zeSchema.code()), true));
		dto6 = create(new SolrRecordDTO(zeroPadded(6), 34L, fields("zeCollection", zeSchema.code()), true));
		dto7 = create(new SolrRecordDTO(zeroPadded(7), 56L, fields("zeCollection", zeSchema.code()), true));
		dto8 = create(new SolrRecordDTO(zeroPadded(8), 67L, fields("zeCollection", zeSchema.code()), true));
		dto10 = create(new SolrRecordDTO(zeroPadded(10), 111L, fields("zeCollection", zeSchema.code()), true));
		dto12 = create(new SolrRecordDTO(zeroPadded(12), 222L, fields("zeCollection", zeSchema.code()), true));

		dataStore.insertWithoutReservingSpaceForPreviousIds(dto1);
		//assertThat(dataStore.intIdsDataStore.typesIndexes[zeCollectionIndex][zeCollectionType1Id].size()).isEqualTo(1);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto3);
		//assertThat(dataStore.intIdsDataStore.typesIndexes[zeCollectionIndex][zeCollectionType1Id].size()).isEqualTo(2);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto6);
		//assertThat(dataStore.intIdsDataStore.typesIndexes[zeCollectionIndex][zeCollectionType1Id].size()).isEqualTo(3);


		assertThat(dataStore.intIdsDataStore.collection.stream().collect(toList()))
				.containsExactly(zeCollectionId, zeCollectionId, zeCollectionId);

		dataStore.insert(dto8);
		//assertThat(dataStore.intIdsDataStore.typesIndexes[zeCollectionIndex][zeCollectionType1Id].size()).isEqualTo(4);

		assertThat(dataStore.intIdsDataStore.collection.stream().collect(toList()))
				.containsExactly(zeCollectionId, zeCollectionId, zeCollectionId, (byte) 0, zeCollectionId);

		dataStore.insert(dto7);
		//assertThat(dataStore.intIdsDataStore.typesIndexes[zeCollectionIndex][zeCollectionType1Id].size()).isEqualTo(5);

		assertThat(dataStore.intIdsDataStore.collection.stream().collect(toList()))
				.containsExactly(zeCollectionId, zeCollectionId, zeCollectionId, zeCollectionId, zeCollectionId);

		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 3, 6, 7, 8);

		assertThat(dataStore.intIdsDataStore.versions.stream().collect(toList()))
				.containsExactly(12L, 23L, 34L, 56L, 67L);

		assertThat(dataStore.intIdsDataStore.type.stream().collect(toList()))
				.containsExactly(zeCollectionType1Id, zeCollectionType1Id, zeCollectionType1Id, zeCollectionType1Id, zeCollectionType1Id);

		assertThat(dataStore.intIdsDataStore.collection.stream().collect(toList()))
				.containsExactly(zeCollectionId, zeCollectionId, zeCollectionId, zeCollectionId, zeCollectionId);

		assertThat(dataStore.intIdsDataStore.schema.stream().collect(toList()))
				.containsExactly(zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId);

		//short zeCollectionIndex = (short) (zeCollectionId - Byte.MIN_VALUE);
		//assertThat(dataStore.intIdsDataStore.typesIndexes[zeCollectionIndex][zeCollectionType1Id].size()).isEqualTo(5);
		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsOnly(dto1, dto3, dto6, dto7, dto8);

		dataStore.insert(dto10);

		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 3, 6, 7, 8, 9, 10);

		assertThat(dataStore.intIdsDataStore.versions.stream().collect(toList()))
				.containsExactly(12L, 23L, 34L, 56L, 67L, 0L, 111L);

		assertThat(dataStore.intIdsDataStore.schema.stream().collect(toList()))
				.containsExactly(zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, (short) 0, zeSchemaDefaultId);

		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsOnly(dto1, dto3, dto6, dto7, dto8, dto10);

		dataStore.insertWithoutReservingSpaceForPreviousIds(dto12);

		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 3, 6, 7, 8, 9, 10, 12);

		assertThat(dataStore.intIdsDataStore.versions.stream().collect(toList()))
				.containsExactly(12L, 23L, 34L, 56L, 67L, 0L, 111L, 222L);

		assertThat(dataStore.intIdsDataStore.schema.stream().collect(toList()))
				.containsExactly(zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, (short) 0, zeSchemaDefaultId, zeSchemaDefaultId);

		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsOnly(dto1, dto3, dto6, dto7, dto8, dto10, dto12);

	}


	@Test
	public void whenSavingSolrDTOThenPersistedInHeapWithoutConversion()
			throws Exception {
		defineSchemasManager().using(setup.withABooleanMetadata(whichIsEssentialInSummary));

		getModelLayerFactory().getMetadataSchemasManager().modify(zeCollection, new MetadataSchemaTypesAlteration() {
			@Override
			public void alter(MetadataSchemaTypesBuilder types) {
				types.getSchemaType(zeSchema.typeCode()).setRecordCacheType(RecordCacheType.FULLY_CACHED);
			}
		});
		initTestVariables();

		SolrRecordDTO dto1, dto3, dto6, dto7, dto8, dto9, dto12, dto14;
		dto1 = new SolrRecordDTO(zeroPadded(1), 12L, fields("zeCollection", zeSchema.code()), false);
		dto3 = new SolrRecordDTO(zeroPadded(3), 23L, fields("zeCollection", zeSchema.code()), false);
		dto6 = new SolrRecordDTO(zeroPadded(6), 34L, fields("zeCollection", zeSchema.code()), false);
		dto7 = new SolrRecordDTO(zeroPadded(7), 56L, fields("zeCollection", zeSchema.code()), false);
		dto8 = new SolrRecordDTO(zeroPadded(8), 67L, fields("zeCollection", zeSchema.code()), false);
		dto9 = new SolrRecordDTO(zeroPadded(9), 45L, fields("zeCollection", zeSchema.code()), false);
		dto12 = new SolrRecordDTO(zeroPadded(12), 111L, fields("zeCollection", zeSchema.code()), false);
		dto14 = new SolrRecordDTO(zeroPadded(14), 222L, fields("zeCollection", zeSchema.code()), false);

		dataStore.insertWithoutReservingSpaceForPreviousIds(dto1);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto3);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto6);
		dataStore.insert(dto9);
		dataStore.insert(dto7);

		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 3, 6, 7, 8, 9);

		assertThat(dataStore.intIdsDataStore.versions.stream().collect(toList()))
				.containsExactly(12L, 23L, 34L, 56L, 0L, 45L);

		assertThat(dataStore.intIdsDataStore.schema.stream().collect(toList()))
				.containsExactly(zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, (short) 0, zeSchemaDefaultId);

		assertThat(dataStore.stream().collect(toList()))
				.containsOnly(dto1, dto3, dto6, dto7, dto9);

		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsOnly(dto1, dto3, dto6, dto7, dto9);

		dataStore.insert(dto8);

		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsOnly(dto1, dto3, dto6, dto7, dto8, dto9);

		dataStore.insert(dto12);

		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 3, 6, 7, 8, 9, 10, 11, 12);

		assertThat(dataStore.intIdsDataStore.versions.stream().collect(toList()))
				.containsExactly(12L, 23L, 34L, 56L, 67L, 45L, 0L, 0L, 111L);

		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsOnly(dto1, dto3, dto6, dto7, dto8, dto9, dto12);


		dataStore.insertWithoutReservingSpaceForPreviousIds(dto14);

		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 3, 6, 7, 8, 9, 10, 11, 12, 14);

		assertThat(dataStore.intIdsDataStore.versions.stream().collect(toList()))
				.containsExactly(12L, 23L, 34L, 56L, 67L, 45L, 0L, 0L, 111L, 222L);

		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsOnly(dto1, dto3, dto6, dto7, dto8, dto9, dto12, dto14);
	}


	@Test
	public void whenAddingAFullyCachedRecordInASpaceThatWasNotReservedThenRecreateArraysAddingNewReservedIndex()
			throws Exception {
		defineSchemasManager().using(setup.withABooleanMetadata(whichIsEssentialInSummary));

		getModelLayerFactory().getMetadataSchemasManager().modify(zeCollection, new MetadataSchemaTypesAlteration() {
			@Override
			public void alter(MetadataSchemaTypesBuilder types) {
				types.getSchemaType(zeSchema.typeCode()).setRecordCacheType(RecordCacheType.FULLY_CACHED);
			}
		});
		initTestVariables();

		SolrRecordDTO dto1, dto2, dto3, dto6, dto7, dto8;
		dto1 = new SolrRecordDTO(zeroPadded(1), 12L, fields("zeCollection", zeSchema.code()), false);
		dto2 = new SolrRecordDTO(zeroPadded(2), 89L, fields("zeCollection", zeSchema.code()), false);
		dto3 = new SolrRecordDTO(zeroPadded(3), 23L, fields("zeCollection", zeSchema.code()), false);
		dto6 = new SolrRecordDTO(zeroPadded(6), 34L, fields("zeCollection", zeSchema.code()), false);
		dto7 = new SolrRecordDTO(zeroPadded(7), 11L, fields("zeCollection", zeSchema.code()), false);
		dto8 = new SolrRecordDTO(zeroPadded(8), 67L, fields("zeCollection", zeSchema.code()), false);

		dataStore.insertWithoutReservingSpaceForPreviousIds(dto1);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto3);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto6);

		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 3, 6);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto8);

		//There is no space for record 2, which is a normal case
		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 3, 6, 8);

		assertThat(dataStore.intIdsDataStore.versions.stream().collect(toList()))
				.containsExactly(12L, 23L, 34L, 67L);

		assertThat(dataStore.intIdsDataStore.schema.stream().collect(toList()))
				.containsExactly(zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId);

		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsOnly(dto1, dto3, dto6, dto8);

		dataStore.insertWithoutReservingSpaceForPreviousIds(dto2);

		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 2, 3, 6, 8);

		assertThat(dataStore.intIdsDataStore.versions.stream().collect(toList()))
				.containsExactly(12L, 89L, 23L, 34L, 67L);

		assertThat(dataStore.intIdsDataStore.schema.stream().collect(toList()))
				.containsExactly(zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId);

		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsOnly(dto1, dto2, dto3, dto6, dto8);

		dataStore.insertWithoutReservingSpaceForPreviousIds(dto7);

		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 2, 3, 6, 7, 8);

		assertThat(dataStore.intIdsDataStore.versions.stream().collect(toList()))
				.containsExactly(12L, 89L, 23L, 34L, 11L, 67L);

		assertThat(dataStore.intIdsDataStore.schema.stream().collect(toList()))
				.containsExactly(zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId);

		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsOnly(dto1, dto2, dto3, dto6, dto7, dto8);
	}


	@Test
	public void whenAddingASummaryCachedRecordInASpaceThatWasNotReservedThenRecreateArraysAddingNewReservedIndex()
			throws Exception {
		//TODO : Boss de la fin
		defineSchemasManager().using(setup.withABooleanMetadata(whichIsEssentialInSummary));


		getModelLayerFactory().getMetadataSchemasManager().modify(zeCollection, new MetadataSchemaTypesAlteration() {
			@Override
			public void alter(MetadataSchemaTypesBuilder types) {
				types.getSchemaType(zeSchema.typeCode()).setRecordCacheType(RecordCacheType.SUMMARY_CACHED_WITHOUT_VOLATILE);
			}
		});
		initTestVariables();

		ByteArrayRecordDTO dto1, dto2, dto3, dto6, dto8;
		dto1 = create(new SolrRecordDTO(zeroPadded(1), 12L, fields("zeCollection", zeSchema.code()), true));
		dto3 = create(new SolrRecordDTO(zeroPadded(3), 23L, fields("zeCollection", zeSchema.code()), true));
		dto6 = create(new SolrRecordDTO(zeroPadded(6), 34L, fields("zeCollection", zeSchema.code()), true));
		dto8 = create(new SolrRecordDTO(zeroPadded(8), 45L, fields("zeCollection", zeSchema.code()), true));

		dataStore.insertWithoutReservingSpaceForPreviousIds(dto1);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto3);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto6);
		dataStore.insert(dto8);

		//There is no space for record 2, which is a normal case
		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 3, 6, 7, 8);

		//No space was reserved for id 2, recreating all arrays with a space for 2
		dto2 = create(new SolrRecordDTO(zeroPadded(2), 56L, fields("zeCollection", zeSchema.code(), "booleanMetadata_s", false), true));
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto2);

		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 2, 3, 6, 7, 8);

		assertThat(dataStore.intIdsDataStore.versions.stream().collect(toList()))
				.containsExactly(12L, 56L, 23L, 34L, 0L, 45L);

		assertThat(dataStore.intIdsDataStore.schema.stream().collect(toList()))
				.containsExactly(zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, zeSchemaDefaultId, (short) 0, zeSchemaDefaultId);

		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsOnly(dto1, dto2, dto3, dto6, dto8);

	}

	@Test
	public void whenInvalidatingSchemaTypeThenInvalidateAllRecordsUsingPredicate()
			throws Exception {
		defineSchemasManager().using(setup.withABooleanMetadata(whichIsEssentialInSummary));


		getModelLayerFactory().getMetadataSchemasManager().modify(zeCollection, new MetadataSchemaTypesAlteration() {
			@Override
			public void alter(MetadataSchemaTypesBuilder types) {
				types.getSchemaType(zeSchema.typeCode()).setRecordCacheType(RecordCacheType.SUMMARY_CACHED_WITHOUT_VOLATILE);
			}
		});
		initTestVariables();

		ByteArrayRecordDTO dto1, dto2, dto3, dto6, dto8;
		dto1 = create(new SolrRecordDTO(zeroPadded(1), 12L, fields("zeCollection", zeSchema.code()), true));
		dto2 = create(new SolrRecordDTO(zeroPadded(2), 56L, fields("zeCollection", zeSchema.code()), true));
		dto3 = create(new SolrRecordDTO(zeroPadded(3), 23L, fields("zeCollection", zeSchema.code()), true));
		dto6 = create(new SolrRecordDTO(zeroPadded(6), 34L, fields("zeCollection", zeSchema.code()), true));
		dto8 = create(new SolrRecordDTO(zeroPadded(8), 45L, fields("zeCollection", zeSchema.code()), true));

		dataStore.insertWithoutReservingSpaceForPreviousIds(dto1);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto2);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto3);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto6);
		dataStore.insert(dto8);

		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 2, 3, 6, 7, 8);
		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsExactly(dto1, dto2, dto3, dto6, dto8);

		assertThat(intArrayToList(dataStore.intIdsDataStore.typesIndexes[zeCollectionIndex][zeCollectionType1Id]))
				.containsExactly(0, 1, 2, 3, 5);

		//Invalidating other collection and types : Nothing happens
		dataStore.invalidate(anotherCollectionId, zeCollectionType1Id, (r) -> r.getVersion() % 2 == 0);
		assertThat(intArrayToList(dataStore.intIdsDataStore.typesIndexes[zeCollectionIndex][zeCollectionType1Id]))
				.containsExactly(0, 1, 2, 3, 5);

		dataStore.invalidate(zeCollectionId, zeCollectionType2Id, (r) -> r.getVersion() % 2 == 0);
		assertThat(intArrayToList(dataStore.intIdsDataStore.typesIndexes[zeCollectionIndex][zeCollectionType1Id]))
				.containsExactly(0, 1, 2, 3, 5);

		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsExactly(dto1, dto2, dto3, dto6, dto8);


		dataStore.invalidate(zeCollectionId, zeCollectionType1Id, (r) -> r.getVersion() % 2 == 0);

		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 2, 3, 6, 7, 8);

		assertThat(intArrayToList(dataStore.intIdsDataStore.typesIndexes[zeCollectionIndex][zeCollectionType1Id]))
				.containsExactly(2, 5);

		assertThat(dataStore.intIdsDataStore.versions.stream().collect(toList()))
				.containsExactly(0L, 0L, 23L, 0L, 0L, 45L);

		assertThat(dataStore.intIdsDataStore.schema.stream().collect(toList()))
				.containsExactly((short) 0, (short) 0, zeSchemaDefaultId, (short) 0, (short) 0, zeSchemaDefaultId);

		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsExactly(dto3, dto8);

	}

	private List<Integer> intArrayToList(IntArrayList intArrayList) {
		List<Integer> list = new ArrayList<>();
		intArrayList.forEach((v) -> list.add(v));
		return list;
	}


	@Test
	public void whenInvalidatingCollectionThenInvalidateAllRecordsUsingPredicate()
			throws Exception {
		defineSchemasManager().using(setup.withABooleanMetadata(whichIsEssentialInSummary));


		getModelLayerFactory().getMetadataSchemasManager().modify(zeCollection, new MetadataSchemaTypesAlteration() {
			@Override
			public void alter(MetadataSchemaTypesBuilder types) {
				types.getSchemaType(zeSchema.typeCode()).setRecordCacheType(RecordCacheType.SUMMARY_CACHED_WITHOUT_VOLATILE);
			}
		});
		initTestVariables();

		ByteArrayRecordDTO dto1, dto2, dto3, dto6, dto8;
		dto1 = create(new SolrRecordDTO(zeroPadded(1), 12L, fields("zeCollection", zeSchema.code()), true));
		dto2 = create(new SolrRecordDTO(zeroPadded(2), 56L, fields("zeCollection", zeSchema.code()), true));
		dto3 = create(new SolrRecordDTO(zeroPadded(3), 23L, fields("zeCollection", zeSchema.code()), true));
		dto6 = create(new SolrRecordDTO(zeroPadded(6), 34L, fields("zeCollection", zeSchema.code()), true));
		dto8 = create(new SolrRecordDTO(zeroPadded(8), 45L, fields("zeCollection", zeSchema.code()), true));

		dataStore.insertWithoutReservingSpaceForPreviousIds(dto1);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto2);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto3);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto6);
		dataStore.insert(dto8);

		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 2, 3, 6, 7, 8);
		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsExactly(dto1, dto2, dto3, dto6, dto8);

		//Invalidating other collection and types : Nothing happens
		dataStore.invalidate(anotherCollectionId, (r) -> r.getVersion() % 2 == 0);

		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsExactly(dto1, dto2, dto3, dto6, dto8);


		dataStore.invalidate(zeCollectionId, (r) -> r.getVersion() % 2 == 0);

		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 2, 3, 6, 7, 8);

		assertThat(dataStore.intIdsDataStore.versions.stream().collect(toList()))
				.containsExactly(0L, 0L, 23L, 0L, 0L, 45L);

		assertThat(dataStore.intIdsDataStore.schema.stream().collect(toList()))
				.containsExactly((short) 0, (short) 0, zeSchemaDefaultId, (short) 0, (short) 0, zeSchemaDefaultId);

		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsExactly(dto3, dto8);

	}


	@Test
	public void whenInvalidatingAllCollectionsThenInvalidateAllRecordsUsingPredicate()
			throws Exception {
		defineSchemasManager().using(setup.withABooleanMetadata(whichIsEssentialInSummary));


		getModelLayerFactory().getMetadataSchemasManager().modify(zeCollection, new MetadataSchemaTypesAlteration() {
			@Override
			public void alter(MetadataSchemaTypesBuilder types) {
				types.getSchemaType(zeSchema.typeCode()).setRecordCacheType(RecordCacheType.SUMMARY_CACHED_WITHOUT_VOLATILE);
			}
		});
		initTestVariables();

		ByteArrayRecordDTO dto1, dto2, dto3, dto6, dto8;
		dto1 = create(new SolrRecordDTO(zeroPadded(1), 12L, fields("zeCollection", zeSchema.code()), true));
		dto2 = create(new SolrRecordDTO(zeroPadded(2), 56L, fields("zeCollection", zeSchema.code()), true));
		dto3 = create(new SolrRecordDTO(zeroPadded(3), 23L, fields("zeCollection", zeSchema.code()), true));
		dto6 = create(new SolrRecordDTO(zeroPadded(6), 34L, fields("zeCollection", zeSchema.code()), true));
		dto8 = create(new SolrRecordDTO(zeroPadded(8), 45L, fields("zeCollection", zeSchema.code()), true));

		dataStore.insertWithoutReservingSpaceForPreviousIds(dto1);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto2);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto3);
		dataStore.insertWithoutReservingSpaceForPreviousIds(dto6);
		dataStore.insert(dto8);

		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 2, 3, 6, 7, 8);

		assertThat(dataStore.stream().collect(toList()))
				.extracting("id")
				.containsOnly(zeroPadded(1), zeroPadded(2), zeroPadded(3), zeroPadded(6), zeroPadded(8));


		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.extracting("id")
				.containsOnly(zeroPadded(1), zeroPadded(2), zeroPadded(3), zeroPadded(6), zeroPadded(8));

		assertThat(dataStore.stream(zeCollectionId).collect(toList()))
				.extracting("id")
				.containsOnly(zeroPadded(1), zeroPadded(2), zeroPadded(3), zeroPadded(6), zeroPadded(8));


		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsExactly(dto1, dto2, dto3, dto6, dto8);


		dataStore.invalidate((r) -> r.getVersion() % 2 == 0);

		assertThat(dataStore.intIdsDataStore.ids.stream().collect(toList()))
				.containsExactly(1, 2, 3, 6, 7, 8);

		assertThat(dataStore.intIdsDataStore.versions.stream().collect(toList()))
				.containsExactly(0L, 0L, 23L, 0L, 0L, 45L);

		assertThat(dataStore.intIdsDataStore.schema.stream().collect(toList()))
				.containsExactly((short) 0, (short) 0, zeSchemaDefaultId, (short) 0, (short) 0, zeSchemaDefaultId);

		assertThat(dataStore.stream(zeCollectionId, zeCollectionType1Id).collect(toList()))
				.containsExactly(dto3, dto8);


	}

	@Test
	public void givenRecordsInMultipleCollectionsSchemaTypesAndDifferentIdPatternsThenAllSavedAndRetrievableWithStreams()
			throws Exception {

		givenCollection("collection1");
		givenCollection("collection2");

		getModelLayerFactory().getMetadataSchemasManager().modify("collection1", new MetadataSchemaTypesAlteration() {
			@Override
			public void alter(MetadataSchemaTypesBuilder types) {
				types.createNewSchemaType("type1").setRecordCacheType(RecordCacheType.SUMMARY_CACHED_WITHOUT_VOLATILE);
				types.createNewSchemaType("type2").setRecordCacheType(RecordCacheType.SUMMARY_CACHED_WITHOUT_VOLATILE);
			}
		});

		getModelLayerFactory().getMetadataSchemasManager().modify("collection2", new MetadataSchemaTypesAlteration() {
			@Override
			public void alter(MetadataSchemaTypesBuilder types) {
				types.createNewSchemaType("type1").setRecordCacheType(RecordCacheType.SUMMARY_CACHED_WITHOUT_VOLATILE);
				types.createNewSchemaType("type2").setRecordCacheType(RecordCacheType.SUMMARY_CACHED_WITHOUT_VOLATILE);
			}
		});

		CollectionsListManager collectionsListManager = getModelLayerFactory().getCollectionsListManager();
		MetadataSchemasManager schemasManager = getModelLayerFactory().getMetadataSchemasManager();
		dataStore = new RecordsCachesDataStore(getModelLayerFactory());

		byte collection1Id = collectionsListManager.getCollectionId("collection1");
		byte collection2Id = collectionsListManager.getCollectionId("collection2");
		short collection1Type1 = schemasManager.getSchemaTypes(collection1Id).getSchemaType("type1").getId();
		short collection1Type2 = schemasManager.getSchemaTypes(collection1Id).getSchemaType("type2").getId();
		short collection2Type1 = schemasManager.getSchemaTypes(collection2Id).getSchemaType("type1").getId();
		short collection2Type2 = schemasManager.getSchemaTypes(collection2Id).getSchemaType("type2").getId();

		List<RecordDTO> collection1Type1Records = new ArrayList<>();
		List<RecordDTO> collection1Type2Records = new ArrayList<>();
		List<RecordDTO> collection2Type1Records = new ArrayList<>();
		List<RecordDTO> collection2Type2Records = new ArrayList<>();

		BiFunction<String, String, RecordDTO> dtoCreator = new BiFunction<String, String, RecordDTO>() {
			int id = 1000;

			@Override
			public RecordDTO apply(String collection, String schema) {
				long version = id + 10000;

				String strId;
				if (id % 7 == 0) {
					strId = "mouhahahaha" + id;
				} else {
					strId = zeroPadded(id);
				}
				id++;

				RecordDTO dto = create(new SolrRecordDTO(strId, version, fields(collection, schema), true));
				dataStore.insert(dto);
				return dto;
			}
		};

		range(0, 1000).forEach((i -> collection1Type1Records.add(dtoCreator.apply("collection1", "type1_default"))));
		range(0, 1000).forEach((i -> collection1Type2Records.add(dtoCreator.apply("collection1", "type2_default"))));
		range(0, 1000).forEach((i -> collection2Type1Records.add(dtoCreator.apply("collection2", "type1_default"))));
		range(0, 1000).forEach((i -> collection2Type2Records.add(dtoCreator.apply("collection2", "type2_default"))));


		assertThat(dataStore.stream().collect(toList()))
				.containsAll(collection1Type1Records)
				.containsAll(collection1Type2Records)
				.containsAll(collection2Type1Records)
				.containsAll(collection2Type2Records)
				.hasSize(4000);

		assertThat(dataStore.stream(collection1Id).collect(toList()))
				.containsAll(collection1Type1Records)
				.containsAll(collection1Type2Records)
				.hasSize(2000);

		assertThat(dataStore.stream(collection2Id).collect(toList()))
				.containsAll(collection2Type1Records)
				.containsAll(collection2Type2Records)
				.hasSize(2000);

		assertThat(dataStore.stream(collection1Id, collection1Type1).collect(toList()))
				.containsAll(collection1Type1Records)
				.hasSize(1000);

		assertThat(dataStore.stream(collection1Id, collection1Type2).collect(toList()))
				.containsAll(collection1Type2Records)
				.hasSize(1000);

		assertThat(dataStore.stream(collection2Id, collection2Type1).collect(toList()))
				.containsAll(collection2Type1Records)
				.hasSize(1000);

		assertThat(dataStore.stream(collection2Id, collection2Type2).collect(toList()))
				.containsAll(collection2Type2Records)
				.hasSize(1000);

		for (List<RecordDTO> list : Arrays.asList(collection1Type1Records, collection1Type2Records,
				collection2Type1Records, collection2Type2Records)) {
			for (RecordDTO recordDTO : list) {
				assertThat(dataStore.get(recordDTO.getId())).isEqualTo(recordDTO);
			}
		}

	}


	@Test
	public void givenHighCacheConcurrencyThenNoExceptionAndStreamsNeverReturnNulls()
			throws Exception {

		givenCollection("collection1");

		getModelLayerFactory().getMetadataSchemasManager().modify("collection1", new MetadataSchemaTypesAlteration() {
			@Override
			public void alter(MetadataSchemaTypesBuilder types) {
				types.createNewSchemaType("type1").setRecordCacheType(RecordCacheType.SUMMARY_CACHED_WITHOUT_VOLATILE);
			}
		});

		CollectionsListManager collectionsListManager = getModelLayerFactory().getCollectionsListManager();
		MetadataSchemasManager schemasManager = getModelLayerFactory().getMetadataSchemasManager();
		dataStore = new RecordsCachesDataStore(getModelLayerFactory());

		byte collection1Id = collectionsListManager.getCollectionId("collection1");
		short collection1Type1 = schemasManager.getSchemaTypes(collection1Id).getSchemaType("type1").getId();

		Random random = new Random();
		List<String> ids = new ArrayList<>();

		notAUnitItest = true;
		Function<Boolean, RecordDTO> dtoCreator = new Function<Boolean, RecordDTO>() {
			int id = 1000;

			@Override
			public RecordDTO apply(Boolean remove) {
				long version = id + 10000;

				String strId;
				if (id % 7 == 0) {
					strId = "mouhahahaha" + id;
				} else {
					strId = zeroPadded(id);
				}
				id++;


				RecordDTO dto = create(new SolrRecordDTO(strId, version, fields("collection1", "type1_default"), true));
				dataStore.insert(dto);

				synchronized (ids) {
					ids.add(strId);

					if (remove) {
						String idToRemove = ids.remove(random.nextInt(ids.size() - 20));
						dataStore.remove(dto);
					}
				}

				return dto;
			}
		};

		//Adding 1000 records
		range(0, 1000).forEach((i -> ids.add(dtoCreator.apply(false).getId())));

		AtomicBoolean running = new AtomicBoolean(true);
		AtomicInteger finishedWithoutErrors = new AtomicInteger(0);


		ThreadList threadList = new ThreadList();

		int creatingThreads = 50;
		int creatingDeletingThreads = 10;
		int streamingThreads = 100;

		for (int i = 0; i < creatingThreads; i++) {
			threadList.add(new Thread(() -> {
				while (running.get()) {
					dtoCreator.apply(false);
				}
				finishedWithoutErrors.incrementAndGet();
			}));
		}

		for (int i = 0; i < creatingDeletingThreads; i++) {
			threadList.add(new Thread(() -> {
				while (running.get()) {
					dtoCreator.apply(true);
				}
				finishedWithoutErrors.incrementAndGet();
			}));
		}

		for (int i = 0; i < streamingThreads; i++) {
			threadList.add(new Thread(() -> {
				while (running.get()) {
					int returnedCount = 0;
					int minimumExpected = 0;
					synchronized (ids) {
						minimumExpected = ids.size();
					}
					for (RecordDTO recordDTO : dataStore.stream().collect(toList())) {
						if (recordDTO == null) {
							throw new RuntimeException("Null returned when streaming");
						}
						returnedCount++;
					}

				}
				finishedWithoutErrors.incrementAndGet();
			}));
		}

		threadList.startAll();

		while (ids.size() < 1_000_000) {
			System.out.println(ids.size());
			Thread.sleep(100);
		}
		running.set(false);
		threadList.joinAll();

		System.out.println("Datastore size : " + ids.size());
		assertThat(finishedWithoutErrors.get()).isEqualTo(creatingThreads + creatingDeletingThreads + streamingThreads);

	}

	@Test
	public void whenInsertingRecordsWithIndexedMetadataValuesThenRecordsFindableUsingMetadata() {
	}

	private ByteArrayRecordDTO create(SolrRecordDTO solrRecordDTO) {
		return ByteArrayRecordDTO.create(getModelLayerFactory(), solrRecordDTO);
	}

	private String zeroPadded(int i) {
		return StringUtils.leftPad("" + i, 11, '0');
	}

	private Map<String, Object> fields(String collection, String schema, Object... extraFields) {
		Map<String, Object> fields = new HashMap<>();
		fields.put("collection_s", collection);
		fields.put("schema_s", schema);


		for (int i = 0; i < extraFields.length; i += 2) {
			fields.put((String) extraFields[i], extraFields[i + 1]);
		}

		return fields;
	}
}
