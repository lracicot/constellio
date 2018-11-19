package com.constellio.app.modules.rm.extensions;

import com.constellio.app.modules.rm.services.RMSchemasRecordsServices;
import com.constellio.app.modules.rm.wrappers.AdministrativeUnit;
import com.constellio.app.modules.rm.wrappers.Category;
import com.constellio.app.modules.rm.wrappers.ContainerRecord;
import com.constellio.app.modules.rm.wrappers.FilingSpace;
import com.constellio.app.modules.rm.wrappers.Folder;
import com.constellio.app.modules.rm.wrappers.RetentionRule;
import com.constellio.app.modules.rm.wrappers.UniformSubdivision;
import com.constellio.app.modules.rm.wrappers.type.VariableRetentionPeriod;
import com.constellio.app.modules.tasks.services.TasksSchemasRecordsServices;
import com.constellio.app.services.factories.AppLayerFactory;
import com.constellio.data.frameworks.extensions.ExtensionBooleanResult;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.records.Transaction;
import com.constellio.model.entities.schemas.Schemas;
import com.constellio.model.extensions.behaviors.RecordExtension;
import com.constellio.model.extensions.events.records.RecordLogicalDeletionEvent;
import com.constellio.model.extensions.events.records.RecordLogicalDeletionValidationEvent;
import com.constellio.model.frameworks.validation.ExtensionValidationErrors;
import com.constellio.model.frameworks.validation.ValidationErrors;
import com.constellio.model.services.factories.ModelLayerFactory;
import com.constellio.model.services.records.RecordServices;
import com.constellio.model.services.records.RecordServicesException;
import com.constellio.model.services.search.SearchServices;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery;

import java.util.ArrayList;
import java.util.List;

import static com.constellio.app.modules.rm.model.CopyRetentionRuleFactory.variablePeriodCode;
import static com.constellio.model.entities.schemas.Schemas.PATH_PARTS;
import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.from;
import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.fromAllSchemasExcept;
import static java.util.Arrays.asList;

public class RMSchemasLogicalDeleteExtension extends RecordExtension {

	String collection;

	ModelLayerFactory modelLayerFactory;

	RMSchemasRecordsServices rm;

	TasksSchemasRecordsServices taskSchemas;

	SearchServices searchServices;

	RecordServices recordServices;

	public RMSchemasLogicalDeleteExtension(String collection, AppLayerFactory appLayerFactory) {
		this.modelLayerFactory = appLayerFactory.getModelLayerFactory();
		this.collection = collection;
		this.rm = new RMSchemasRecordsServices(collection, appLayerFactory);
		this.taskSchemas = new TasksSchemasRecordsServices(collection, appLayerFactory);
		this.searchServices = modelLayerFactory.newSearchServices();
		this.recordServices = modelLayerFactory.newRecordServices();
	}

	@Override
	public void recordLogicallyDeleted(RecordLogicalDeletionEvent event) {
		Record deletedRule = event.getRecord();
		if (event.isSchemaType(RetentionRule.SCHEMA_TYPE)) {
			Transaction transaction = new Transaction();

			List<Category> categories = rm.wrapCategorys(searchServices.search(new LogicalSearchQuery()
					.setCondition(from(rm.category.schemaType()).where(rm.category.retentionRules()).isEqualTo(deletedRule))));
			for (Category category : categories) {
				List<String> rules = new ArrayList<>(category.getRententionRules());
				rules.remove(deletedRule.getId());
				category.setRetentionRules(rules);
				transaction.add(category);
			}

			List<UniformSubdivision> uniformSubdivisions = rm.wrapUniformSubdivisions(searchServices.search(
					new LogicalSearchQuery().setCondition(from(rm.uniformSubdivision.schemaType())
							.where(rm.uniformSubdivision.retentionRule()).isEqualTo(deletedRule))));
			for (UniformSubdivision uniformSubdivision : uniformSubdivisions) {
				List<String> rules = new ArrayList<>(uniformSubdivision.getRetentionRules());
				rules.remove(deletedRule.getId());
				uniformSubdivision.setRetentionRules(rules);
				transaction.add(uniformSubdivision);
			}

			try {
				recordServices.execute(transaction);
			} catch (RecordServicesException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public ExtensionValidationErrors isLogicallyDeletable(RecordLogicalDeletionValidationEvent event) {
		if (event.isSchemaType(VariableRetentionPeriod.SCHEMA_TYPE)) {
			return isVariableRetentionPeriodLogicallyDeletable(event);

		} else if (event.isSchemaType(RetentionRule.SCHEMA_TYPE)) {
			return isRetentionRuleLogicallyDeletable(event);

		} else if (event.isSchemaType(FilingSpace.SCHEMA_TYPE)) {
			return isFilingSpaceLogicallyDeletable(event);

		} else if (event.isSchemaType(AdministrativeUnit.SCHEMA_TYPE)) {
			return isAdministrativeUnitLogicallyDeletable(event);

		} else if (event.isSchemaType(Category.SCHEMA_TYPE)) {
			return isCategoryLogicallyDeletable(event);

		} else if (event.isSchemaType(ContainerRecord.SCHEMA_TYPE)) {
			return isContainerLogicallyDeletable(event);

		} else if (event.isSchemaType(Folder.SCHEMA_TYPE)) {
			return isFolderLogicallyDeletable(event);

		} else {
			return new ExtensionValidationErrors(ExtensionBooleanResult.NOT_APPLICABLE);
		}

	}

	private ExtensionValidationErrors isAdministrativeUnitLogicallyDeletable(
			RecordLogicalDeletionValidationEvent event) {
		ValidationErrors validationErrors = new ValidationErrors();
		boolean inPath = searchServices.hasResults(fromAllSchemasExcept(asList(rm.administrativeUnit.schemaType()))
				.where(Schemas.PATH).isContainingText(event.getRecord().getId()));
		boolean referenced = event.isRecordReferenced();
		if (event.isRecordReferenced()) {
			validationErrors.add(RMSchemasLogicalDeleteExtension.class, "administrativeUnitIsReferenced");
		}
		if (inPath) {
			validationErrors.add(RMSchemasLogicalDeleteExtension.class, "recordInAdministrativeUnit");
		}
		return new ExtensionValidationErrors(validationErrors, ExtensionBooleanResult.falseIf(inPath || referenced));
	}

	private ExtensionValidationErrors isCategoryLogicallyDeletable(RecordLogicalDeletionValidationEvent event) {
		ValidationErrors validationErrors = new ValidationErrors();
		boolean inPath = searchServices.hasResults(fromAllSchemasExcept(asList(rm.category.schemaType()))
				.where(Schemas.PATH).isContainingText(event.getRecord().getId()));
		boolean referenced = event.isRecordReferenced();
		if (event.isRecordReferenced()) {
			validationErrors.add(RMSchemasLogicalDeleteExtension.class, "categoryIsReferenced");
		}
		if (inPath) {
			validationErrors.add(RMSchemasLogicalDeleteExtension.class, "recordInCategory");
		}
		return new ExtensionValidationErrors(validationErrors, ExtensionBooleanResult.falseIf(inPath || referenced));
	}

	private ExtensionValidationErrors isFolderLogicallyDeletable(RecordLogicalDeletionValidationEvent event) {
		//TODO check if user can delete borrowed documents
		ValidationErrors validationErrors = new ValidationErrors();
		ExtensionBooleanResult extensionBooleanResult = ExtensionBooleanResult.NOT_APPLICABLE;
		boolean hasCheckedOutDocument = searchServices.hasResults(from(rm.document.schemaType())
				.where(rm.document.contentCheckedOutBy()).isNotNull()
				.andWhere(PATH_PARTS).isEqualTo(event.getRecord()));

		if (hasCheckedOutDocument) {
			validationErrors.add(RMSchemasLogicalDeleteExtension.class, "folderWithCheckoutDocuments");
			extensionBooleanResult = ExtensionBooleanResult.FALSE;
		}

		if (!event.isThenPhysicallyDeleted()) {
			if (searchServices.hasResults(from(rm.userTask.schemaType())
					.where(rm.userTask.linkedFolders()).isContaining(asList(event.getRecord().getId()))
					.andWhere(taskSchemas.userTask.status()).isNotIn(taskSchemas.getFinishedOrClosedStatuses())
					.andWhere(Schemas.LOGICALLY_DELETED_STATUS).isFalseOrNull())) {
				validationErrors.add(RMSchemasLogicalDeleteExtension.class, "folderLinkedToTask");
				extensionBooleanResult = ExtensionBooleanResult.FALSE;
			}
		}

		return new ExtensionValidationErrors(validationErrors, extensionBooleanResult);
	}

	private ExtensionValidationErrors isContainerLogicallyDeletable(RecordLogicalDeletionValidationEvent event) {
		ExtensionBooleanResult extensionBooleanResult = ExtensionBooleanResult.NOT_APPLICABLE;
		ValidationErrors validationErrors = new ValidationErrors();
		boolean taskVerification = searchServices.hasResults(from(rm.userTask.schemaType())
						.where(rm.userTask.linkedContainers()).isContaining(asList(event.getRecord().getId()))
						.andWhere(taskSchemas.userTask.status()).isNotIn(taskSchemas.getFinishedOrClosedStatuses())
						.andWhere(Schemas.LOGICALLY_DELETED_STATUS).isFalseOrNull()
		);
		if (taskVerification) {
			validationErrors.add(RMSchemasLogicalDeleteExtension.class, "containerLinkedToTask");
			extensionBooleanResult = ExtensionBooleanResult.FALSE;
		}
		return new ExtensionValidationErrors(validationErrors, extensionBooleanResult);
	}

	private ExtensionValidationErrors isFilingSpaceLogicallyDeletable(RecordLogicalDeletionValidationEvent event) {
		ValidationErrors validationErrors = new ValidationErrors();
		if (event.isRecordReferenced()) {
			validationErrors.add(RMSchemasLogicalDeleteExtension.class, "referencedRecord");
		}
		return new ExtensionValidationErrors(validationErrors, ExtensionBooleanResult.falseIf(event.isRecordReferenced()));
	}

	private ExtensionValidationErrors isRetentionRuleLogicallyDeletable(RecordLogicalDeletionValidationEvent event) {
		ValidationErrors validationErrors = new ValidationErrors();
		boolean logicallyDeletable = !searchServices.hasResults(from(rm.folder.schemaType())
				.where(rm.folder.retentionRule()).isEqualTo(event.getRecord()));
		if (!logicallyDeletable) {
			validationErrors.add(RMSchemasLogicalDeleteExtension.class, "retentionRuleUsedByFolder");
		}
		return new ExtensionValidationErrors(validationErrors, ExtensionBooleanResult.forceTrueIf(logicallyDeletable));
	}

	private ExtensionValidationErrors isVariableRetentionPeriodLogicallyDeletable(
			RecordLogicalDeletionValidationEvent event) {
		ValidationErrors validationErrors = new ValidationErrors();
		VariableRetentionPeriod variableRetentionPeriod = rm.wrapVariableRetentionPeriod(event.getRecord());
		String code = variableRetentionPeriod.getCode();
		if (code.equals("888") || code.equals("999")) {
			validationErrors.add(RMSchemasLogicalDeleteExtension.class, "cannotDelete888Or999VariableRetentionPeriod");
			return new ExtensionValidationErrors(validationErrors, ExtensionBooleanResult.FALSE);
		} else {
			long count = searchServices.getResultsCount(from(rm.retentionRule.schemaType())
					.where(rm.retentionRule.copyRetentionRules()).is(variablePeriodCode(code)));
			if (count != 0) {
				validationErrors.add(RMSchemasLogicalDeleteExtension.class, "variablePeriodTypeIsUsedInRetentionRule");
			}
			return new ExtensionValidationErrors(validationErrors, ExtensionBooleanResult.trueIf(count == 0));
		}
	}
}
