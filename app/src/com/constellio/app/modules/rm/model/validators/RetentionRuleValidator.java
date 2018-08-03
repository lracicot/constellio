package com.constellio.app.modules.rm.model.validators;

import com.constellio.app.modules.rm.RMConfigs;
import com.constellio.app.modules.rm.model.CopyRetentionRule;
import com.constellio.app.modules.rm.model.enums.CopyType;
import com.constellio.app.modules.rm.model.enums.DisposalType;
import com.constellio.app.modules.rm.model.enums.RetentionRuleScope;
import com.constellio.app.modules.rm.wrappers.Document;
import com.constellio.app.modules.rm.wrappers.Folder;
import com.constellio.app.modules.rm.wrappers.RetentionRule;
import com.constellio.app.modules.rm.wrappers.structures.RetentionRuleDocumentType;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.schemas.*;
import com.constellio.model.entities.schemas.validation.RecordValidator;
import com.constellio.model.frameworks.validation.DecoratedValidationsErrors;
import com.constellio.model.frameworks.validation.ValidationErrors;
import com.constellio.model.services.records.RecordValidatorParams;
import com.constellio.model.services.search.SearchServices;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.constellio.app.modules.rm.wrappers.RetentionRule.COPY_RETENTION_RULES;
import static com.constellio.app.modules.rm.wrappers.RetentionRule.PRINCIPAL_DEFAULT_DOCUMENT_COPY_RETENTION_RULE;
import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.from;

public class RetentionRuleValidator implements RecordValidator {

	public static final String FIND_FOLDERS_USING_COPY_RULE_QUERY = "FindFoldersUsingCopyRule";
	public static final String FIND_DOCUMENTS_USING_COPY_RULE_QUERY = "FindDocumentsUsingCopyRule";

	public static final String MUST_SPECIFY_ADMINISTRATIVE_UNITS_XOR_RESPONSIBLES_FLAG = "mustSpecifyAdministrativeUnitsXORSetResponsibles";
	public static final String NO_ADMINISTRATIVE_UNITS_OR_RESPONSIBLES_FLAG = "noAdministrativeUnitsOrResponsibles";
	public static final String MUST_SPECIFY_ONE_SECONDARY_COPY_RETENTON_RULE = "mustSpecifyOneSecondaryRetentionRule";
	public static final String MUST_SPECIFY_AT_LEAST_ONE_PRINCIPAL_COPY_RETENTON_RULE = "mustSpecifyAtLeastOnePrincipalRetentionRule";
	public static final String MUST_NOT_SPECIFY_SECONDARY_DOCUMENT_COPY_RETENTON_RULE = "mustNotSpecifySecondaryDocumentRetentionRule";
	public static final String MUST_SPECIFY_AT_LEAST_ONE_PRINCIPAL_DOCUMENT_COPY_RETENTON_RULE = "mustSpecifyAtLeastOnePrincipalDocumentRetentionRule";

	public static final String INVALID_COPY_RETENTION_RULE_FIELD = "invalidCopyRuleField";
	public static final String INVALID_COPY_RETENTION_RULE_FIELD_INVALID = "invalid";
	public static final String INVALID_COPY_RETENTION_RULE_FIELD_REQUIRED = "required";
	public static final String INVALID_COPY_RETENTION_RULE_FIELD_NULL_REQUIRED = "nullRequired";

	public static final String DOCUMENT_COPY_RETENTION_RULE_FIELD_REQUIRED = "documentCopyRetentionRuleFieldRequired";
	public static final String MISSING_DOCUMENT_TYPE_DISPOSAL = "missingDocumentTypeDisposal";
	public static final String DOCUMENT_RULE_MUST_HAVE_ONLY_DOCUMENT_COPY_RULES = "documentRuleMustHaveOnlyDocumentCopyRules";

	//
	public static final String PRINCIPAL_DEFAULT_COPY_RETENTION_RULE_IN_FOLDER_RULE = "principalDefaultCopyRetentionRuleInFolderRule";
	public static final String SECONDARY_DEFAULT_COPY_RETENTION_RULE_IN_FOLDER_RULE = "secondaryDefaultCopyRetentionRuleInFolderRule";
	//
	public static final String PRINCIPAL_DEFAULT_COPY_RETENTION_RULE_REQUIRED_IN_DOCUMENT_RULE = "principalDefaultCopyRetentionRuleRequiredInDocumentRule";
	public static final String SECONDARY_DEFAULT_COPY_RETENTION_RULE_REQUIRED_IN_DOCUMENT_RULE = "secondaryDefaultCopyRetentionRuleRequiredInDocumentRule";
	//
	public static final String DOCUMENT_TYPES_IN_DOCUMENT_RULE = "documentTypesInDocumentRule";

	public static final String PRINCIPAL_COPY_WITHOUT_TYPE_REQUIRED = "principalCopyWithoutTypeRequired";

	//	public static final String PRINCIPAL_COPIES_MUST_HAVE_DIFFERENT_CONTENT_TYPES = "principalCopiesMustHaveDifferentContentTypes";
	//	public static final String PRINCIPAL_COPIES_MUST_HAVE_DIFFERENT_CONTENT_TYPES_DUPLICATES = "duplicates";

	public static final String MISSING_DOCUMENT_TYPE_DISPOSAL_INDEX = "index";

	public static final String CANNOT_DELETE_COPY_IN_USE = "cannotDeleteCopyInUse";

	@Override
	public void validate(RecordValidatorParams params) {
		RetentionRule retentionRule = new RetentionRule(params.getValidatedRecord(), params.getTypes());
		validate(retentionRule, params.getSchema(), params.getConfigProvider(), params.getValidationErrors(),
				params.getSearchServices(), params.getTypes());

	}

	void validate(RetentionRule retentionRule, MetadataSchema schema, ConfigProvider configProvider,
				  ValidationErrors validationErrors, SearchServices searchServices, MetadataSchemaTypes types) {

		if (retentionRule.getScope() != RetentionRuleScope.DOCUMENTS) {
			validateDocumentTypes(retentionRule, validationErrors);
		} else {
			validateNoDocumentTypes(retentionRule, validationErrors);

		}
		validateAdministrativeUnits(retentionRule, schema, configProvider, validationErrors);
		validateCopyRetentionRules(retentionRule, schema, validationErrors, configProvider, searchServices, types);
		validateDocumentCopyRetentionRules(retentionRule, schema, validationErrors, searchServices, types);
		validateDefaultDocumentCopyRetentionRules(retentionRule, schema, validationErrors, configProvider);

		//validateRemovedCopyRetentionRule(retentionRule, schema.getMetadata(RetentionRule.COPY_RETENTION_RULES));
	}

	//	private void validateRemovedCopyRetentionRule(RetentionRule retentionRule, Metadata metadata) {
	//		List<CopyRetentionRule> copyRetentionRules = findRemovedCopyRetentionRule(retentionRule, metadata);
	//
	//		for (CopyRetentionRule copyRetentionRule : copyRetentionRules) {
	//
	//		}
	//	}
	//
	//	private List<CopyRetentionRule> findRemovedCopyRetentionRules(RetentionRule retentionRule) {
	//
	//		List<CopyRetentionRule> previousCopyRetentionRules = new ArrayList<>();
	//		List<String> previousCopyRetentionRulesIds = new ArrayList<>();
	//		List<String> previousCopyRetentionRulesIds = new ArrayList<>();
	//
	//		if (retentionRule.getWrappedRecord().isModified(metadata)) {
	//
	//		}
	//	}

	private void validateDefaultDocumentCopyRetentionRules(RetentionRule retentionRule, MetadataSchema schema,
														   ValidationErrors validationErrors,
														   ConfigProvider configProvider) {
		if (!configProvider.<Boolean>get(RMConfigs.DOCUMENT_RETENTION_RULES)) {
			return;
		}
		if (retentionRule.getScope() == RetentionRuleScope.DOCUMENTS) {
			if (retentionRule.getPrincipalDefaultDocumentCopyRetentionRule() == null) {
				validationErrors.add(getClass(), PRINCIPAL_DEFAULT_COPY_RETENTION_RULE_REQUIRED_IN_DOCUMENT_RULE);
			}
			if (retentionRule.getSecondaryDefaultDocumentCopyRetentionRule() == null) {
				validationErrors.add(getClass(), SECONDARY_DEFAULT_COPY_RETENTION_RULE_REQUIRED_IN_DOCUMENT_RULE);
			}
		} else {
			if (retentionRule.getPrincipalDefaultDocumentCopyRetentionRule() != null) {
				validationErrors.add(getClass(), PRINCIPAL_DEFAULT_COPY_RETENTION_RULE_IN_FOLDER_RULE);
			}
			if (retentionRule.getSecondaryDefaultDocumentCopyRetentionRule() != null) {
				validationErrors.add(getClass(), SECONDARY_DEFAULT_COPY_RETENTION_RULE_IN_FOLDER_RULE);
			}
		}
	}

	private void validateNoDocumentTypes(RetentionRule retentionRule, ValidationErrors validationErrors) {
		if (!retentionRule.getDocumentTypesDetails().isEmpty()) {
			validationErrors.add(getClass(), DOCUMENT_TYPES_IN_DOCUMENT_RULE);
		}
	}

	private void validateDocumentTypes(RetentionRule retentionRule, ValidationErrors validationErrors) {

		List<RetentionRuleDocumentType> documentTypes = retentionRule.getDocumentTypesDetails();
		if (retentionRule.hasCopyRetentionRuleWithSortDispositionType()) {
			for (int i = 0; i < documentTypes.size(); i++) {
				RetentionRuleDocumentType documentType = documentTypes.get(i);

				if (documentType.getDisposalType() == null || documentType.getDisposalType() == DisposalType.SORT) {

					Map<String, Object> parameters = new HashMap<>();
					parameters.put(MISSING_DOCUMENT_TYPE_DISPOSAL_INDEX, "" + i);
					validationErrors.add(getClass(), MISSING_DOCUMENT_TYPE_DISPOSAL, parameters);

				}
			}
		}

	}

	//	on doit ajouter une extension qui valide, lors de la modification d’une règle, qu’un délai utilisé n’est pas supprimé
	//  je ferais cette validation uniquement lorsqu’un délai est retiré d’une règle déjà persistée

	private void validateCopyRetentionRules(RetentionRule retentionRule, MetadataSchema schema,
											ValidationErrors validationErrors, ConfigProvider configProvider,
											SearchServices searchServices,
											MetadataSchemaTypes types) {

		List<CopyRetentionRule> copyRetentionRules = retentionRule.getCopyRetentionRules();

		boolean validIntegrity = true;
		for (int i = 0; i < copyRetentionRules.size(); i++) {
			CopyRetentionRule copyRetentionRule = copyRetentionRules.get(i);
			Map<String, String> parameters = new HashMap<>();
			parameters.put("code", copyRetentionRule.getCode());
			parameters.put("index", "" + (i + 1));
			parameters.put("metadata", RetentionRule.COPY_RETENTION_RULES);
			DecoratedValidationsErrors copyRuleErrors = new DecoratedValidationsErrors(validationErrors, parameters);
			validateCopyRuleIntegrity(copyRetentionRule, copyRuleErrors);
			validIntegrity &= !copyRuleErrors.hasDecoratedErrors();
		}

		if (validIntegrity) {
			int principalCount = 0;
			int principalCountWithoutType = 0;
			int secondaryCount = 0;

			List<CopyRetentionRule> copyRetentionRules1 = retentionRule.getCopyRetentionRules();
			for (int i = 0; i < copyRetentionRules1.size(); i++) {
				CopyRetentionRule copyRetentionRule = copyRetentionRules1.get(i);
				if (copyRetentionRule.getCopyType() == CopyType.PRINCIPAL) {
					principalCount++;
					if (copyRetentionRule.getTypeId() == null) {
						principalCountWithoutType++;
					}

				} else {
					secondaryCount++;

					if (copyRetentionRule.getTypeId() != null) {
						Map<String, Object> parameters = nullRequired(i, copyRetentionRule, "type", COPY_RETENTION_RULES);
						validationErrors.add(getClass(), INVALID_COPY_RETENTION_RULE_FIELD, parameters);
					}
				}

			}

			// The rule is being modified
			if (retentionRule.getWrappedRecord().isSaved()) {
				Record originalRecord = retentionRule.getWrappedRecord().getCopyOfOriginalRecord();
				RetentionRule originalRetentionRule = new RetentionRule(originalRecord, types);
				List<CopyRetentionRule> originalCopyRetentionRules = originalRetentionRule.getCopyRetentionRules();
				for (int j = 0; j < originalCopyRetentionRules.size(); j++) {
					CopyRetentionRule originalCopyRetentionRule = originalCopyRetentionRules.get(j);
					boolean deleted = true;
					for (CopyRetentionRule copyRetentionRule : copyRetentionRules) {
						if (copyRetentionRule.getId().equals(originalCopyRetentionRule.getId())) {
							deleted = false;
						}
					}
					// The copy was deleted, let's see if it was in use
					if (deleted) {
						MetadataSchemaType foldersSchemaType = types.getSchemaType(Folder.SCHEMA_TYPE);
						Metadata folderMainCopyRuleRuleMetadata = foldersSchemaType
								.getMetadata(Folder.DEFAULT_SCHEMA + "_" + Folder.MAIN_COPY_RULE);
						LogicalSearchQuery usedByFolderQuery = new LogicalSearchQuery()
								.setName(FIND_FOLDERS_USING_COPY_RULE_QUERY);
						usedByFolderQuery.setCondition(from(foldersSchemaType).where(folderMainCopyRuleRuleMetadata)
								.isContainingText(originalCopyRetentionRule.getId()));
						boolean usedByFolders = searchServices.hasResults(usedByFolderQuery);
						if (usedByFolders) {
							Map<String, Object> parameters = nullRequired(j, originalCopyRetentionRule, "type",
									COPY_RETENTION_RULES);
							validationErrors.add(getClass(), CANNOT_DELETE_COPY_IN_USE, parameters);
						}
					}
				}
			}

			boolean copyRulePrincipalRequired = configProvider.get(RMConfigs.COPY_RULE_PRINCIPAL_REQUIRED);

			if (retentionRule.getScope() != RetentionRuleScope.DOCUMENTS && principalCount == 0 && copyRulePrincipalRequired) {
				addCopyRetentionRuleError(MUST_SPECIFY_AT_LEAST_ONE_PRINCIPAL_COPY_RETENTON_RULE, validationErrors);
			}

			if (retentionRule.getScope() == RetentionRuleScope.DOCUMENTS && (principalCount + secondaryCount) != 0) {
				addCopyRetentionRuleError(DOCUMENT_RULE_MUST_HAVE_ONLY_DOCUMENT_COPY_RULES, validationErrors);
			}

			if (retentionRule.getScope() != RetentionRuleScope.DOCUMENTS && secondaryCount != 1) {
				addCopyRetentionRuleError(MUST_SPECIFY_ONE_SECONDARY_COPY_RETENTON_RULE, validationErrors);
			}

			if (principalCount != 0 && principalCountWithoutType == 0) {
				addCopyRetentionRuleError(PRINCIPAL_COPY_WITHOUT_TYPE_REQUIRED, validationErrors);
			}
		}

	}

	private Map<String, Object> nullRequired(int i, CopyRetentionRule copyRetentionRule, String type, String metadata) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("code", copyRetentionRule.getCode());
		parameters.put("stringValue", copyRetentionRule.toString());
		parameters.put("index", "" + (i + 1));
		parameters.put("field", "type");
		parameters.put("errorType", "nullRequired");
		parameters.put("metadata", metadata);
		return parameters;
	}

	private void validateDocumentCopyRetentionRules(RetentionRule retentionRule, MetadataSchema schema,
													ValidationErrors validationErrors, SearchServices searchServices,
													MetadataSchemaTypes types) {

		Record record = retentionRule.getWrappedRecord();
		List<CopyRetentionRule> copyRetentionRules = retentionRule.getDocumentCopyRetentionRules();

		boolean validIntegrity = true;
		for (int i = 0; i < copyRetentionRules.size(); i++) {
			CopyRetentionRule copyRetentionRule = copyRetentionRules.get(i);
			Map<String, String> parameters = new HashMap<>();
			parameters.put("code", copyRetentionRule.getCode());
			parameters.put("index", "" + (i + 1));
			parameters.put("metadata", RetentionRule.DOCUMENT_COPY_RETENTION_RULES);
			DecoratedValidationsErrors copyRuleErrors = new DecoratedValidationsErrors(validationErrors, parameters);

			validateCopyRuleIntegrity(copyRetentionRule, copyRuleErrors);

			if (copyRetentionRule.getTypeId() == null) {
				copyRuleErrors.add(getClass(), INVALID_COPY_RETENTION_RULE_FIELD, requiredField("type"));
			}

			validIntegrity &= !copyRuleErrors.hasDecoratedErrors();
		}

		if (validIntegrity) {
			int principalCount = 0;
			int secondaryCount = 0;

			for (CopyRetentionRule copyRetentionRule : retentionRule.getDocumentCopyRetentionRules()) {
				if (copyRetentionRule.getCopyType() == CopyType.PRINCIPAL) {
					principalCount++;
				} else {
					secondaryCount++;
				}
			}

			// The rule is being modified
			if (retentionRule.getWrappedRecord().isSaved()) {
				Record originalRecord = record.getCopyOfOriginalRecord();
				RetentionRule originalRetentionRule = new RetentionRule(originalRecord, types);
				List<CopyRetentionRule> documentCopyRetentionRules = originalRetentionRule.getDocumentCopyRetentionRules();
				for (int i = 0; i < documentCopyRetentionRules.size(); i++) {
					CopyRetentionRule originalCopyRetentionRule = documentCopyRetentionRules.get(i);
					boolean deleted = true;
					for (CopyRetentionRule copyRetentionRule : copyRetentionRules) {
						if (copyRetentionRule.getId().equals(originalCopyRetentionRule.getId())) {
							deleted = false;
						}
					}
					// The copy was deleted, let's see if it was in use
					if (deleted && isUsedWithDocuments(searchServices, types, originalCopyRetentionRule)) {
						Map<String, Object> parameters = nullRequired(i, originalCopyRetentionRule, "type",
								COPY_RETENTION_RULES);
						validationErrors.add(getClass(), CANNOT_DELETE_COPY_IN_USE, parameters);
					}
				}

				if (originalRetentionRule.getPrincipalDefaultDocumentCopyRetentionRule() != null
					&& record.isModified(schema.getMetadata(PRINCIPAL_DEFAULT_DOCUMENT_COPY_RETENTION_RULE))
					&& isUsedWithDocuments(searchServices, types,
						originalRetentionRule.getPrincipalDefaultDocumentCopyRetentionRule())) {
					Map<String, Object> parameters = nullRequired(0,
							originalRetentionRule.getPrincipalDefaultDocumentCopyRetentionRule(), "type",
							COPY_RETENTION_RULES);
					validationErrors.add(getClass(), CANNOT_DELETE_COPY_IN_USE, parameters);
				}

				if (originalRetentionRule.getSecondaryDefaultDocumentCopyRetentionRule() != null
					&& record.isModified(schema.getMetadata(PRINCIPAL_DEFAULT_DOCUMENT_COPY_RETENTION_RULE))
					&& isUsedWithDocuments(searchServices, types,
						originalRetentionRule.getSecondaryDefaultDocumentCopyRetentionRule())) {
					Map<String, Object> parameters = nullRequired(0,
							originalRetentionRule.getSecondaryDefaultDocumentCopyRetentionRule(), "type",
							COPY_RETENTION_RULES);
					validationErrors.add(getClass(), CANNOT_DELETE_COPY_IN_USE, parameters);
				}

			}

			if (retentionRule.getScope() == RetentionRuleScope.DOCUMENTS && principalCount == 0) {
				addCopyRetentionRuleError(MUST_SPECIFY_AT_LEAST_ONE_PRINCIPAL_DOCUMENT_COPY_RETENTON_RULE, validationErrors);
			}

			if (secondaryCount != 0) {
				addCopyRetentionRuleError(MUST_NOT_SPECIFY_SECONDARY_DOCUMENT_COPY_RETENTON_RULE, validationErrors);
			}
		}

	}

	private boolean isUsedWithDocuments(SearchServices searchServices, MetadataSchemaTypes types,
										CopyRetentionRule originalCopyRetentionRule) {
		MetadataSchemaType documentsSchemaType = types.getSchemaType(Document.SCHEMA_TYPE);
		Metadata documentMainCopyRuleRuleMetadata = documentsSchemaType
				.getMetadata(Document.DEFAULT_SCHEMA + "_" + Document.MAIN_COPY_RULE);
		LogicalSearchQuery usedByDocumentQuery = new LogicalSearchQuery().setName(FIND_DOCUMENTS_USING_COPY_RULE_QUERY);
		usedByDocumentQuery.setCondition(from(documentsSchemaType).where(documentMainCopyRuleRuleMetadata)
				.isContainingText(originalCopyRetentionRule.getId()));
		return searchServices.hasResults(usedByDocumentQuery);
	}

	private Map<String, Object> requiredField(String field) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("field", field);
		parameters.put("errorType", "required");
		return parameters;
	}

	private void validateCopyRuleIntegrity(CopyRetentionRule copyRetentionRule, ValidationErrors validationErrors) {

		if (copyRetentionRule.getCopyType() == null) {
			validationErrors.add(getClass(), INVALID_COPY_RETENTION_RULE_FIELD, requiredField("copyType"));
		}
		if (copyRetentionRule.getMediumTypeIds() == null || copyRetentionRule.getMediumTypeIds().isEmpty()) {
			validationErrors.add(getClass(), INVALID_COPY_RETENTION_RULE_FIELD, requiredField("mediumTypes"));
		}

		if (copyRetentionRule.getActiveRetentionPeriod() == null) {
			validationErrors.add(getClass(), INVALID_COPY_RETENTION_RULE_FIELD, requiredField("active"));
		}
		if (copyRetentionRule.getSemiActiveRetentionPeriod() == null) {
			validationErrors.add(getClass(), INVALID_COPY_RETENTION_RULE_FIELD, requiredField("semiActive"));
		}
		if (copyRetentionRule.getInactiveDisposalType() == null) {
			validationErrors.add(getClass(), INVALID_COPY_RETENTION_RULE_FIELD, requiredField("inactive"));
		}

	}

	private void addCopyRetentionRuleError(String code, ValidationErrors validationErrors) {
		Map<String, Object> parameters = new HashMap<>();
		validationErrors.add(getClass(), code, parameters);

	}

	//	private void addCopyRetentionRuleIntegrityError(int index, String copyCode, String field, MetadataSchema schema,
	//			ValidationErrors validationErrors) {
	//		Map<String, Object> parameters = new HashMap<>();
	//		parameters.put("field", field);
	//		parameters.put("index", "" + index);
	//		parameters.put("code", copyCode);
	//		parameters.put(RecordMetadataValidator.METADATA_CODE, COPY_RETENTION_RULES);
	//		parameters.put(RecordMetadataValidator.METADATA_LABEL,
	//				schema.getMetadata(COPY_RETENTION_RULES).getCode());
	//		String errorCode = StringUtils.isBlank(copyCode) ?
	//				COPY_RETENTION_RULE_FIELD_AT_INDEX_REQUIRED :
	//				COPY_RETENTION_RULE_FIELD_WITH_CODE_REQUIRED;
	//		validationErrors.add(getClass(), errorCode, parameters);
	//
	//	}
	//
	//	private void newCopyRetentionRulesParameters(String errorCode, String metadataCode, int index,
	//			CopyRetentionRule copyRetentionRule, ) {
	//		Map<String, Object> parameters = new HashMap<>();
	//		parameters = new HashMap<>();
	//		parameters.put("code", null);
	//		parameters.put("index", "3");
	//		parameters.put("field", "semiActive");
	//		parameters.put("value", "*");
	//		parameters.put("errorType", "invalid");
	//		parameters.put("metadata", RetentionRule.COPY_RETENTION_RULES);
	//
	//	}

	private void validateAdministrativeUnits(RetentionRule retentionRule, MetadataSchema schema,
											 ConfigProvider configProvider, ValidationErrors validationErrors) {

		List<String> administrativeUnits = retentionRule.getAdministrativeUnits();
		boolean responsibleAdministrativeUnits = retentionRule.isResponsibleAdministrativeUnits();

		if (administrativeUnits.isEmpty() && !responsibleAdministrativeUnits) {
			validationErrors.add(getClass(), NO_ADMINISTRATIVE_UNITS_OR_RESPONSIBLES_FLAG);

		} else {
			if (!administrativeUnits.isEmpty() && responsibleAdministrativeUnits) {
				Object openHolder = configProvider.get(RMConfigs.OPEN_HOLDER);
				if (openHolder == null || !(Boolean) openHolder) {
					validationErrors.add(getClass(), MUST_SPECIFY_ADMINISTRATIVE_UNITS_XOR_RESPONSIBLES_FLAG);
				}
			}
		}
	}

}

