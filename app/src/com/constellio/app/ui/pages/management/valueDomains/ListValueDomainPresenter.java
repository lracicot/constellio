package com.constellio.app.ui.pages.management.valueDomains;

import static com.constellio.app.ui.i18n.i18n.$;

import java.util.ArrayList;
import java.util.*;
import java.util.regex.Pattern;

import com.jgoodies.common.base.Strings;

import com.constellio.app.modules.rm.services.ValueListServices;
import com.constellio.app.ui.entities.MetadataSchemaTypeVO;
import com.constellio.app.ui.framework.builders.MetadataSchemaTypeToVOBuilder;
import com.constellio.app.ui.pages.base.BasePresenter;
import com.constellio.model.entities.CorePermissions;
import com.constellio.model.entities.Language;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.entities.schemas.MetadataSchemaType;
import com.constellio.model.frameworks.validation.ValidationException;
import com.constellio.model.services.schemas.MetadataSchemasManagerException.OptimisticLocking;
import com.constellio.model.services.schemas.builders.MetadataSchemaTypesBuilder;

public class ListValueDomainPresenter extends BasePresenter<ListValueDomainView> {

	private List<Map<Language, String>> labels;

	public ListValueDomainPresenter(ListValueDomainView view) {
		super(view);
	}

	public void valueDomainCreationRequested(Map<Language, String> mapLanguage) {

		boolean canCreate = canCreate(mapLanguage);
		if (canCreate) {
			valueListServices().createValueDomain(mapLanguage, mapLanguage.size() > 1);
			view.refreshTable();
			labels.add(mapLanguage);
		}
	}

	public void displayButtonClicked(MetadataSchemaTypeVO schemaType) {
		view.navigate().to().listSchemaRecords(schemaType.getCode() + "_default");
	}

	public void editButtonClicked(MetadataSchemaTypeVO schemaTypeVO, Map<Language, String> newLabel) {

		if (verifyIfExists(newLabel)) {
			MetadataSchemaTypesBuilder metadataSchemaTypesBuilder = modelLayerFactory.getMetadataSchemasManager()
					.modify(view.getCollection());

			metadataSchemaTypesBuilder.getSchemaType(schemaTypeVO.getCode()).setLabels(newLabel);
			metadataSchemaTypesBuilder.getSchemaType(schemaTypeVO.getCode()).getDefaultSchema().setLabels(newLabel);

			try {
				modelLayerFactory.getMetadataSchemasManager().saveUpdateSchemaTypes(metadataSchemaTypesBuilder);
			} catch (OptimisticLocking optimistickLocking) {
				throw new RuntimeException(optimistickLocking);
			}
			view.refreshTable();
		} else if (newLabel != null && !newLabel.equals(schemaTypeVO.getLabel(view.getSessionContext().getCurrentLocale()))) {
			view.showErrorMessage($("ListValueDomainView.existingValueDomain", newLabel));
		}
	}

	public List<MetadataSchemaTypeVO> getDomainValues() {
		labels = new ArrayList<>();
		MetadataSchemaTypeToVOBuilder builder = newMetadataSchemaTypeToVOBuilder();
		List<MetadataSchemaTypeVO> result = new ArrayList<>();
		for (MetadataSchemaType schemaType : valueListServices().getValueDomainTypes()) {
			result.add(builder.build(schemaType));
			labels.add(schemaType.getLabel());
		}
		return result;
	}

	public List<MetadataSchemaTypeVO> getDomainValues(boolean isUserCreated) {
		labels = new ArrayList<>();
		MetadataSchemaTypeToVOBuilder builder = newMetadataSchemaTypeToVOBuilder();
		List<MetadataSchemaTypeVO> result = new ArrayList<>();
		for (MetadataSchemaType schemaType : valueListServices().getValueDomainTypes()) {
			if((isUserCreated && schemaType.getCode().matches(".*\\d")) ||
					(!isUserCreated && !schemaType.getCode().matches(".*\\d"))) {
				result.add(builder.build(schemaType));
			}

			labels.add(schemaType.getLabel());
		}
		return result;
	}

	MetadataSchemaTypeToVOBuilder newMetadataSchemaTypeToVOBuilder() {
		return new MetadataSchemaTypeToVOBuilder();
	}

	ValueListServices valueListServices() {
		return new ValueListServices(appLayerFactory, view.getCollection());
	}

	boolean canCreate(Map<Language, String> taxonomyMapTitle) {
		boolean canCreate = false;
		if (taxonomyMapTitle != null && taxonomyMapTitle.size() > 0) {
			boolean exist = verifyIfExists(taxonomyMapTitle);
			canCreate = !exist;
		}
		return canCreate;
	}

	private boolean verifyIfExists(Map<Language, String> taxonomy) {
		if (labels == null || labels.size() == 0) {
			getDomainValues();
		}
		boolean exits = false;
		for(Language lang : taxonomy.keySet()) {
			if(Strings.isBlank(taxonomy.get(lang))){
				return true;
			}
			for(Map<Language, String> existingTitleMap : labels) {
				if(existingTitleMap.get(lang).equals(taxonomy.get(lang))) {
					return true;
				}
			}
		}
		return exits;
	}

	private boolean isTitleChanged(Map<Language, String> mapLang, Map<Language, String> mapLang2) {
		for(Language language : mapLang.keySet()) {
			if(!mapLang.get(language).equals(mapLang2.get(language))) {
				return true;
			}
		}
		return false;
	}

	public void backButtonClicked() {
		view.navigate().to().adminModule();
	}

	@Override
	protected boolean hasPageAccess(String params, User user) {
		return user.has(CorePermissions.MANAGE_VALUELIST).globally();
	}

	public void deleteButtonClicked(String schemaTypeCode)
			throws ValidationException {

		valueListServices().deleteValueListOrTaxonomy(schemaTypeCode);
		view.refreshTable();
	}

	public boolean isValueListPossiblyDeletable(String schemaTypeCode) {
		String lcSchemaTypeCode = schemaTypeCode.toLowerCase();
		Pattern pattern = Pattern.compile("ddv[0-9]+[0-9a-z]*");
		return lcSchemaTypeCode.startsWith("ddvusr")
				|| lcSchemaTypeCode.startsWith("usrddv")
				|| pattern.matcher(lcSchemaTypeCode).matches();

	}
}
