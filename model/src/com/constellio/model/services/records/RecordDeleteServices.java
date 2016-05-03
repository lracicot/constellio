package com.constellio.model.services.records;

import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.from;
import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.fromAllSchemasIn;
import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.startingWithText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.constellio.data.dao.dto.records.OptimisticLockingResolution;
import com.constellio.data.dao.dto.records.RecordDTO;
import com.constellio.data.dao.dto.records.RecordsFlushing;
import com.constellio.data.dao.dto.records.TransactionDTO;
import com.constellio.data.dao.services.bigVault.RecordDaoException.OptimisticLocking;
import com.constellio.data.dao.services.records.RecordDao;
import com.constellio.data.utils.Factory;
import com.constellio.model.entities.Taxonomy;
import com.constellio.model.entities.records.ActionExecutorInBatch;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.records.Transaction;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.entities.schemas.Metadata;
import com.constellio.model.entities.schemas.MetadataSchemaType;
import com.constellio.model.entities.schemas.MetadataSchemaTypes;
import com.constellio.model.entities.schemas.MetadataValueType;
import com.constellio.model.entities.schemas.Schemas;
import com.constellio.model.extensions.events.records.RecordLogicalDeletionValidationEvent;
import com.constellio.model.extensions.events.records.RecordPhysicalDeletionEvent;
import com.constellio.model.extensions.events.records.RecordPhysicalDeletionValidationEvent;
import com.constellio.model.services.contents.ContentManager;
import com.constellio.model.services.contents.ContentModificationsBuilder;
import com.constellio.model.services.extensions.ModelLayerExtensions;
import com.constellio.model.services.factories.ModelLayerFactory;
import com.constellio.model.services.records.RecordDeleteServicesRuntimeException.RecordDeleteServicesRuntimeException_CannotDeleteRecordWithUserFromOtherCollection;
import com.constellio.model.services.records.RecordDeleteServicesRuntimeException.RecordDeleteServicesRuntimeException_CannotTotallyDeleteSchemaType;
import com.constellio.model.services.records.RecordDeleteServicesRuntimeException.RecordDeleteServicesRuntimeException_RecordServicesErrorDuringOperation;
import com.constellio.model.services.records.RecordServicesRuntimeException.RecordIsNotAPrincipalConcept;
import com.constellio.model.services.records.RecordServicesRuntimeException.RecordServicesRuntimeException_CannotLogicallyDeleteRecord;
import com.constellio.model.services.records.RecordServicesRuntimeException.RecordServicesRuntimeException_CannotPhysicallyDeleteRecord;
import com.constellio.model.services.records.RecordServicesRuntimeException.RecordServicesRuntimeException_CannotRestoreRecord;
import com.constellio.model.services.schemas.MetadataSchemaTypesAlteration;
import com.constellio.model.services.schemas.MetadataSchemasManager;
import com.constellio.model.services.schemas.SchemaUtils;
import com.constellio.model.services.schemas.builders.MetadataBuilder;
import com.constellio.model.services.schemas.builders.MetadataSchemaBuilder;
import com.constellio.model.services.schemas.builders.MetadataSchemaTypeBuilder;
import com.constellio.model.services.schemas.builders.MetadataSchemaTypesBuilder;
import com.constellio.model.services.search.SearchServices;
import com.constellio.model.services.search.StatusFilter;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery;
import com.constellio.model.services.search.query.logical.condition.LogicalSearchCondition;
import com.constellio.model.services.security.AuthorizationsServices;
import com.constellio.model.services.taxonomies.TaxonomiesManager;

public class RecordDeleteServices {

	private final Logger LOGGER = LoggerFactory.getLogger(RecordDeleteServices.class);

	private final RecordDao recordDao;

	private final SearchServices searchServices;

	private final RecordServices recordServices;

	private final AuthorizationsServices authorizationsServices;

	private final TaxonomiesManager taxonomiesManager;

	private final MetadataSchemasManager metadataSchemasManager;

	private final ContentManager contentManager;

	private final ModelLayerExtensions extensions;

	public RecordDeleteServices(RecordDao recordDao, ModelLayerFactory modelLayerFactory) {
		this.recordDao = recordDao;
		this.searchServices = modelLayerFactory.newSearchServices();
		this.recordServices = modelLayerFactory.newRecordServices();
		this.authorizationsServices = modelLayerFactory.newAuthorizationsServices();
		this.taxonomiesManager = modelLayerFactory.getTaxonomiesManager();
		this.metadataSchemasManager = modelLayerFactory.getMetadataSchemasManager();
		this.contentManager = modelLayerFactory.getContentManager();
		this.extensions = modelLayerFactory.getExtensions();
	}

	public boolean isRestorable(Record record, User user) {
		ensureSameCollection(user, record);

		String parentId = record.getParentId();
		boolean parentActiveOrNull;
		if (parentId != null) {
			Record parent = recordServices.getDocumentById(parentId);
			parentActiveOrNull = Boolean.TRUE != parent.get(Schemas.LOGICALLY_DELETED_STATUS);
		} else {
			parentActiveOrNull = true;
		}

		String typeCode = new SchemaUtils().getSchemaTypeCode(record.getSchemaCode());
		MetadataSchemaType schemaType = metadataSchemasManager.getSchemaTypes(record.getCollection()).getSchemaType(typeCode);
		return parentActiveOrNull && (!schemaType.hasSecurity() || user == User.GOD ||
				authorizationsServices.hasRestaurationPermissionOnHierarchy(user, record));
	}

	public void restore(Record record, User user) {
		if (!isRestorable(record, user)) {
			throw new RecordServicesRuntimeException_CannotRestoreRecord(record.getId());
		}

		Transaction transaction = new Transaction();

		for (Record hierarchyRecord : getAllRecordsInHierarchy(record)) {
			hierarchyRecord.set(Schemas.LOGICALLY_DELETED_STATUS, false);
			transaction.add(hierarchyRecord);
		}
		if (!transaction.getRecords().contains(record)) {
			record.set(Schemas.LOGICALLY_DELETED_STATUS, false);
			transaction.add(record);
		}
		try {
			recordServices.execute(transaction);
		} catch (RecordServicesException e) {
			throw new RecordDeleteServicesRuntimeException_RecordServicesErrorDuringOperation("restore", e);
		}

	}

	public boolean isLogicallyThenPhysicallyDeletable(Record record, User user) {
		return isLogicallyThenPhysicallyDeletable(record, user, new RecordDeleteOptions());
	}

	public boolean isLogicallyThenPhysicallyDeletable(Record record, User user, RecordDeleteOptions options) {
		return isPhysicallyDeletableNoMatterTheStatus(record, user, options);
	}

	public boolean isPhysicallyDeletable(Record record, User user) {
		return isPhysicallyDeletable(record, user, new RecordDeleteOptions());
	}

	public boolean isPhysicallyDeletable(Record record, User user, RecordDeleteOptions options) {
		ensureSameCollection(user, record);

		String typeCode = new SchemaUtils().getSchemaTypeCode(record.getSchemaCode());
		MetadataSchemaType schemaType = metadataSchemasManager.getSchemaTypes(record.getCollection()).getSchemaType(typeCode);

		boolean correctStatus = Boolean.TRUE == record.get(Schemas.LOGICALLY_DELETED_STATUS);
		boolean noActiveRecords = containsNoActiveRecords(record);
		boolean hasPermissions =
				!schemaType.hasSecurity() || authorizationsServices.hasRestaurationPermissionOnHierarchy(user, record);
		if (!correctStatus) {
			LOGGER.info("Not physically deletable : Record is not logically deleted");
			return false;

		} else if (!noActiveRecords) {
			LOGGER.info("Not physically deletable : There is active records in the hierarchy");
			return false;

		} else if (!hasPermissions) {
			LOGGER.info("Not physically deletable : No sufficient permissions on hierarchy");
			return false;

		} else {
			return isPhysicallyDeletableNoMatterTheStatus(record, user, options);
		}

	}

	private boolean isPhysicallyDeletableNoMatterTheStatus(Record record, User user, RecordDeleteOptions options) {
		ensureSameCollection(user, record);

		String typeCode = new SchemaUtils().getSchemaTypeCode(record.getSchemaCode());
		MetadataSchemaType schemaType = metadataSchemasManager.getSchemaTypes(record.getCollection()).getSchemaType(typeCode);

		boolean hasPermissions =
				!schemaType.hasSecurity() || authorizationsServices.hasDeletePermissionOnHierarchyNoMatterTheStatus(user, record);
		boolean referencesUnhandled = isReferencedByOtherRecords(record) && !options.isReferencesToNull();

		if (!hasPermissions) {
			LOGGER.info("Not physically deletable : No sufficient permissions on hierarchy");
		}

		if (referencesUnhandled) {
			LOGGER.info("Not physically deletable : A record in the hierarchy is referenced outside of the hierarchy");
		}

		boolean physicallyDeletable = hasPermissions && !referencesUnhandled;

		if (physicallyDeletable) {
			RecordPhysicalDeletionValidationEvent event = new RecordPhysicalDeletionValidationEvent(record, user);
			physicallyDeletable = extensions.forCollectionOf(record).isPhysicallyDeletable(event);
		}

		return physicallyDeletable;
	}

	public void physicallyDeleteNoMatterTheStatus(Record record, User user, RecordDeleteOptions options) {
		if (record.get(Schemas.LOGICALLY_DELETED_STATUS)) {
			physicallyDelete(record, user, options);

		} else {
			logicallyDelete(record, user);
			try {
				physicallyDelete(record, user, options);
			} catch (RecordServicesRuntimeException e) {
				restore(record, user);
				throw e;
			}
		}
	}

	public void physicallyDelete(Record record, User user) {
		physicallyDelete(record, user, new RecordDeleteOptions());
	}

	public void physicallyDelete(final Record record, User user, RecordDeleteOptions options) {
		if (!isPhysicallyDeletable(record, user, options)) {
			throw new RecordServicesRuntimeException_CannotPhysicallyDeleteRecord(record.getId());
		}

		List<Record> records = getAllRecordsInHierarchy(record);

		MetadataSchemaTypes types = metadataSchemasManager.getSchemaTypes(record.getCollection());
		if (options.isReferencesToNull()) {

			//Collections.sort(records, sortByLevelFromLeafToRoot());

			for (final Record recordInHierarchy : records) {
				String type = new SchemaUtils().getSchemaTypeCode(recordInHierarchy.getSchemaCode());
				final List<Metadata> metadatas = types.getAllMetadatas().onlyReferencesToType(type).onlyNonParentReferences()
						.onlyManuals();
				if (!metadatas.isEmpty()) {
					try {
						new ActionExecutorInBatch(searchServices, "Remove references to '" + recordInHierarchy.getId() + "'",
								1000) {

							@Override
							public void doActionOnBatch(List<Record> recordsWithRef)
									throws Exception {

								Transaction transaction = new Transaction();

								for (Record recordWithRef : recordsWithRef) {
									String recordWithRefType = new SchemaUtils().getSchemaTypeCode(recordWithRef.getSchemaCode());
									for (Metadata metadata : metadatas) {
										String metadataType = new SchemaUtils().getSchemaTypeCode(metadata);
										if (recordWithRefType.equals(metadataType)) {
											if (metadata.isMultivalue()) {
												List<String> values = new ArrayList<>(recordWithRef.<String>getList(metadata));
												int sizeBefore = values.size();
												values.removeAll(Collections.singletonList(recordInHierarchy.getId()));
												if (sizeBefore != values.size()) {
													recordWithRef.set(metadata, values);
												}
											} else {
												String value = recordWithRef.get(metadata);
												if (recordInHierarchy.getId().equals(value)) {
													recordWithRef.set(metadata, null);
												}
											}
										}
									}
									transaction.add(recordWithRef);
								}

								recordServices.execute(transaction);
							}
						}.execute(fromAllSchemasIn(record.getCollection()).whereAny(metadatas).isEqualTo(recordInHierarchy));
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			}
		}

		deleteContents(records);
		List<RecordDTO> recordsDTO = newRecordUtils().toRecordDTOList(records);

		try {
			recordDao.execute(
					new TransactionDTO(RecordsFlushing.NOW).withDeletedRecords(recordsDTO));
		} catch (OptimisticLocking optimisticLocking) {
			throw new RecordServicesRuntimeException_CannotPhysicallyDeleteRecord(record.getId(), optimisticLocking);
		}

		for (Record hierarchyRecord : records) {
			RecordPhysicalDeletionEvent event = new RecordPhysicalDeletionEvent(hierarchyRecord);
			extensions.forCollectionOf(record).callRecordPhysicallyDeleted(event);
		}
	}
	//
	//	private Comparator<? super Record> sortByLevelFromLeafToRoot() {
	//		return new Comparator<Record>() {
	//			@Override
	//			public int compare(Record o1, Record o2) {
	//				 String path1 = o1.get(Schemas.PRINCIPAL_PATH);
	//
	//
	//				String path2 = o1.get(Schemas.PRINCIPAL_PATH);
	//				return 0;
	//			}
	//		};
	//	}

	void deleteContents(List<Record> records) {
		String collection = records.get(0).getCollection();
		for (String potentiallyDeletableHash : newContentModificationsBuilder(collection).buildForDeletedRecords(records)) {
			contentManager.silentlyMarkForDeletionIfNotReferenced(potentiallyDeletableHash);
		}
	}

	ContentModificationsBuilder newContentModificationsBuilder(String collection) {
		MetadataSchemaTypes types = metadataSchemasManager.getSchemaTypes(collection);
		return new ContentModificationsBuilder(types);
	}

	public boolean isLogicallyDeletable(final Record record, User user) {
		ensureSameCollection(user, record);

		String typeCode = new SchemaUtils().getSchemaTypeCode(record.getSchemaCode());
		MetadataSchemaType schemaType = metadataSchemasManager.getSchemaTypes(record.getCollection()).getSchemaType(typeCode);

		boolean logicallyDeletable =
				!schemaType.hasSecurity() || authorizationsServices.hasDeletePermissionOnHierarchy(user, record);

		if (logicallyDeletable) {
			Factory<Boolean> referenced = new Factory<Boolean>() {
				@Override
				public Boolean get() {
					return !recordDao.getReferencedRecordsInHierarchy(record.getId()).isEmpty();
				}
			};
			RecordLogicalDeletionValidationEvent event = new RecordLogicalDeletionValidationEvent(record, user, referenced);
			logicallyDeletable = extensions.forCollectionOf(record).isLogicallyDeletable(event);
		}

		return logicallyDeletable;
	}

	private void removedDefaultValues(String collection, List<Record> records) {
		List<String> defaultValuesIds = metadataSchemasManager.getSchemaTypes(collection).getReferenceDefaultValues();
		final List<String> defaultValuesIdsToRemove = new ArrayList<>();
		for (Record record : records) {
			if (defaultValuesIds.contains(record.getId())) {
				defaultValuesIdsToRemove.add(record.getId());
			}
		}

		if (!defaultValuesIdsToRemove.isEmpty()) {
			metadataSchemasManager.modify(collection, new MetadataSchemaTypesAlteration() {
				@Override
				public void alter(MetadataSchemaTypesBuilder types) {
					for (MetadataSchemaTypeBuilder typeBuilder : types.getTypes()) {
						for (MetadataSchemaBuilder schemaBuilder : typeBuilder.getAllSchemas()) {
							for (MetadataBuilder metadataBuilder : schemaBuilder.getMetadatas()) {
								Object defaultValue = metadataBuilder.getDefaultValue();
								if (metadataBuilder.getType() == MetadataValueType.REFERENCE && defaultValue != null) {
									if (defaultValue instanceof String && defaultValuesIdsToRemove.contains(defaultValue)) {
										metadataBuilder.setDefaultValue(null);
									} else if (defaultValue instanceof List) {
										List<String> withoutRemovedDefaultValues = null;
										for (Object item : (List) defaultValue) {
											if (defaultValuesIdsToRemove.contains(item)) {
												if (withoutRemovedDefaultValues == null) {
													withoutRemovedDefaultValues = new ArrayList<String>((List) defaultValue);
												}
												withoutRemovedDefaultValues.remove(item);
											}
										}
										if (withoutRemovedDefaultValues != null) {
											if (withoutRemovedDefaultValues.isEmpty()) {
												withoutRemovedDefaultValues = null;
											}
											metadataBuilder.setDefaultValue(withoutRemovedDefaultValues);
										}
									}
								}
							}
						}
					}
				}
			});
		}
	}

	public void logicallyDelete(Record record, User user) {
		if (!isLogicallyDeletable(record, user)) {
			throw new RecordServicesRuntimeException_CannotLogicallyDeleteRecord(record.getId());
		}

		Transaction transaction = new Transaction().setSkippingRequiredValuesValidation(true);

		List<Record> hierarchyRecords = new ArrayList<>(getAllRecordsInHierarchy(record));
		if (!new RecordUtils().toIdList(hierarchyRecords).contains(record.getId())) {
			hierarchyRecords.add(record);
		}
		removedDefaultValues(record.getCollection(), hierarchyRecords);
		for (Record hierarchyRecord : hierarchyRecords) {
			hierarchyRecord.set(Schemas.LOGICALLY_DELETED_STATUS, true);
			transaction.add(hierarchyRecord);
		}
		//		if (!transaction.getRecords().contains(record)) {
		//			record.set(Schemas.LOGICALLY_DELETED_STATUS, true);
		//			transaction.add(record);
		//		}
		transaction.setUser(user);
		try {
			recordServices.execute(transaction);
		} catch (RecordServicesException e) {
			throw new RecordDeleteServicesRuntimeException_RecordServicesErrorDuringOperation("logicallyDelete", e);
		}
	}

	public boolean isPrincipalConceptLogicallyDeletableIncludingContent(Record principalConcept, User user) {
		return authorizationsServices
				.hasDeletePermissionOnPrincipalConceptHierarchy(user, principalConcept, true, metadataSchemasManager);
	}

	public void logicallyDeletePrincipalConceptIncludingRecords(Record principalConcept, User user) {
		if (!isPrincipalConceptLogicallyDeletableIncludingContent(principalConcept, user)) {
			throw new RecordServicesRuntimeException_CannotLogicallyDeleteRecord(principalConcept.getId());
		}

		Transaction transaction = new Transaction();
		transaction.setOptimisticLockingResolution(OptimisticLockingResolution.EXCEPTION);
		principalConcept.set(Schemas.LOGICALLY_DELETED_STATUS, true);
		transaction.add(principalConcept);

		for (Record hierarchyRecord : getAllRecordsInHierarchy(principalConcept)) {
			hierarchyRecord.set(Schemas.LOGICALLY_DELETED_STATUS, true);
			transaction.add(hierarchyRecord);
		}
		try {
			recordServices.execute(transaction);
			recordServices.refresh(principalConcept);
		} catch (RecordServicesException e) {
			throw new RecordDeleteServicesRuntimeException_RecordServicesErrorDuringOperation(
					"logicallyDeletePrincipalConceptIncludingRecords", e);
		}
	}

	public boolean isPrincipalConceptLogicallyDeletableExcludingContent(Record principalConcept, User user) {
		return authorizationsServices
				.hasDeletePermissionOnPrincipalConceptHierarchy(user, principalConcept, false, metadataSchemasManager);
	}

	public void logicallyDeletePrincipalConceptExcludingRecords(Record principalConcept, User user) {
		Taxonomy principalTaxonomy = taxonomiesManager.getPrincipalTaxonomy(principalConcept.getCollection());
		String schemaType = new SchemaUtils().getSchemaTypeCode(principalConcept.getSchemaCode());
		if (!principalTaxonomy.getSchemaTypes().contains(schemaType)) {
			throw new RecordIsNotAPrincipalConcept(principalConcept.getId());
		}

		if (!isPrincipalConceptLogicallyDeletableExcludingContent(principalConcept, user)) {
			throw new RecordServicesRuntimeException_CannotLogicallyDeleteRecord(principalConcept.getId());
		}

		Transaction transaction = new Transaction();
		principalConcept.set(Schemas.LOGICALLY_DELETED_STATUS, true);
		transaction.add(principalConcept);

		for (Record hierarchyRecord : getAllPrincipalConceptsRecordsInHierarchy(principalConcept, principalTaxonomy)) {
			hierarchyRecord.set(Schemas.LOGICALLY_DELETED_STATUS, true);
			transaction.add(hierarchyRecord);
		}
		try {
			recordServices.execute(transaction);
		} catch (RecordServicesException e) {
			throw new RecordDeleteServicesRuntimeException_RecordServicesErrorDuringOperation(
					"logicallyDeletePrincipalConceptExcludingRecords", e);
		}

	}

	List<Record> getAllRecordsInHierarchy(Record record) {
		if (record.getList(Schemas.PATH).isEmpty()) {
			return Arrays.asList(record);

		} else {
			LogicalSearchQuery query = new LogicalSearchQuery();
			List<String> paths = record.getList(Schemas.PATH);
			query.setCondition(fromAllSchemasIn(record.getCollection()).where(Schemas.PATH).isStartingWithText(paths.get(0)));
			return searchServices.search(query);
		}
	}

	List<Record> getAllPrincipalConceptsRecordsInHierarchy(Record principalConcept, Taxonomy principalTaxonomy) {
		List<Record> records = new ArrayList<>();
		for (String schemaTypeCode : principalTaxonomy.getSchemaTypes()) {
			MetadataSchemaType schemaType = metadataSchemasManager.getSchemaTypes(principalConcept.getCollection())
					.getSchemaType(schemaTypeCode);

			List<String> paths = principalConcept.getList(Schemas.PATH);
			LogicalSearchQuery query = new LogicalSearchQuery();
			query.setCondition(from(schemaType).where(Schemas.PATH).isStartingWithText(paths.get(0)));
			records.addAll(searchServices.search(query));
		}
		return records;
	}

	boolean containsNoActiveRecords(Record record) {
		LogicalSearchQuery query = new LogicalSearchQuery().filteredByStatus(StatusFilter.ACTIVES);
		query.setCondition(fromAllSchemasIn(record.getCollection()).where(Schemas.PATH).isContainingText(record.getId()));
		boolean result = !searchServices.hasResults(query);
		return result;
	}

	public RecordUtils newRecordUtils() {
		return new RecordUtils();
	}

	public List<Record> getVisibleRecordsWithReferenceToRecordInHierarchy(Record record, User user) {
		//1 - Find all hierarchy records (including the given record) that are referenced (using the counter index)
		List<Record> returnedRecords = new ArrayList<>();
		List<String> recordsWithReferences = getRecordsInHierarchyWithDependency(record);
		if (!recordsWithReferences.isEmpty()) {

			for (MetadataSchemaType type : metadataSchemasManager.getSchemaTypes(record.getCollection()).getSchemaTypes()) {
				List<Metadata> references = type.getAllNonParentReferences();
				if (!references.isEmpty()) {
					List<Record> recordsInType = getRecordsInTypeWithReferenceTo(user, recordsWithReferences, type, references);
					returnedRecords.addAll(recordsInType);
				}
			}
		}

		return Collections.unmodifiableList(returnedRecords);
	}

	List<Record> getRecordsInTypeWithReferenceTo(User user, List<String> recordsWithReferences, MetadataSchemaType type,
			List<Metadata> references) {

		LogicalSearchQuery query = new LogicalSearchQuery().filteredWithUser(user);
		query.setCondition(from(type).whereAny(references).isIn(recordsWithReferences));
		return searchServices.search(query);
	}

	public List<String> getRecordsInHierarchyWithDependency(Record record) {
		return recordDao.getReferencedRecordsInHierarchy(record.getId());
	}

	public boolean isReferencedByOtherRecords(Record record) {
		List<String> references = recordDao.getReferencedRecordsInHierarchy(record.getId());
		//		List<Record> hierarchyRecords = recordServices.getRecordsById(record.getCollection(), references);
		if (references.isEmpty()) {
			return false;
		}
		boolean hasReferences = false;
		List<String> paths = record.getList(Schemas.PARENT_PATH);

		for (MetadataSchemaType type : metadataSchemasManager.getSchemaTypes(record.getCollection()).getSchemaTypes()) {
			List<Metadata> typeReferencesMetadata = type.getAllNonParentReferences();
			if (!typeReferencesMetadata.isEmpty()) {
				LogicalSearchCondition condition = from(type).whereAny(typeReferencesMetadata).isIn(references);

				if (!paths.isEmpty()) {
					condition = condition.andWhere(Schemas.PATH).isNot(startingWithText(paths.get(0)));
				}

				hasReferences |= searchServices.hasResults(new LogicalSearchQuery(condition));
			}
		}
		return hasReferences;
	}

	private void ensureSameCollection(User user, Record record) {

		if (user != User.GOD && !user.getCollection().equals(record.getCollection())) {
			throw new RecordDeleteServicesRuntimeException_CannotDeleteRecordWithUserFromOtherCollection(record.getCollection(),
					user.getCollection());
		}
	}

	public void totallyDeleteSchemaTypeRecords(MetadataSchemaType type) {
		if (type.isInTransactionLog()) {
			throw new RecordDeleteServicesRuntimeException_CannotTotallyDeleteSchemaType(type.getCode());
		}

		totallyDeleteSchemaTypeRecordsSkippingValidation_WARNING_CANNOT_BE_REVERTED(type);
	}

	public void totallyDeleteSchemaTypeRecordsSkippingValidation_WARNING_CANNOT_BE_REVERTED(MetadataSchemaType type) {
		ModifiableSolrParams params = new ModifiableSolrParams();
		params.set("q", "schema_s:" + type.getCode() + "_*");

		try {
			recordDao.execute(new TransactionDTO(RecordsFlushing.NOW).withDeletedByQueries(params));
		} catch (OptimisticLocking optimisticLocking) {
			throw new RuntimeException(optimisticLocking);
		}

	}
}
