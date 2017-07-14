package com.constellio.app.ui.framework.builders;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.constellio.app.entities.schemasDisplay.MetadataDisplayConfig;
import com.constellio.app.entities.schemasDisplay.SchemaTypeDisplayConfig;
import com.constellio.app.entities.schemasDisplay.SchemaTypesDisplayConfig;
import com.constellio.app.entities.schemasDisplay.enums.MetadataDisplayType;
import com.constellio.app.entities.schemasDisplay.enums.MetadataInputType;
import com.constellio.app.services.schemasDisplay.SchemasDisplayManager;
import com.constellio.app.ui.application.ConstellioUI;
import com.constellio.app.ui.entities.FormMetadataVO;
import com.constellio.app.ui.entities.MetadataSchemaVO;
import com.constellio.app.ui.pages.base.SessionContext;
import com.constellio.model.entities.Language;
import com.constellio.model.entities.schemas.Metadata;
import com.constellio.model.entities.schemas.MetadataSchema;
import com.constellio.model.entities.schemas.MetadataValueType;

@SuppressWarnings("serial")
public class MetadataToFormVOBuilder implements Serializable {

	private SessionContext sessionContext;

	public MetadataToFormVOBuilder(SessionContext sessionContext) {
		this.sessionContext = sessionContext;
	}

	public FormMetadataVO build(Metadata metadata, SchemasDisplayManager configManager, String schemaTypeCode,
			SessionContext sessionContext) {
		return build(metadata, null, configManager, schemaTypeCode, sessionContext);
	}

	public FormMetadataVO build(Metadata metadata, MetadataSchemaVO schemaVO, SchemasDisplayManager configManager,
			String schemaTypeCode, SessionContext sessionContext) {
		MetadataDisplayConfig config = configManager.getMetadata(metadata.getCollection(), metadata.getCode());
		SchemaTypesDisplayConfig types = configManager.getTypes(metadata.getCollection());

		this.sessionContext = sessionContext;

		String code = metadata.getCode();
		MetadataValueType type = metadata.getType();
		boolean required = metadata.isDefaultRequirement();
		boolean multivalue = metadata.isMultivalue();
		Map<Language, String> labels = metadata.getLabels();
		MetadataInputType entry = config.getInputType();
		MetadataDisplayType displayType = config.getDisplayType();
		boolean sortable = metadata.isSortable();
		boolean searchable = metadata.isSearchable();
		boolean advancedSearch = config.isVisibleInAdvancedSearch();
		boolean highlight = config.isHighlight();
		boolean enabled = metadata.isEnabled();
		boolean facet = false;
		String metadataGroup = getValidMetadataGroup(config.getMetadataGroupCode(),
				configManager.getType(metadata.getCollection(), schemaTypeCode));

		for (String codeFacet : types.getFacetMetadataCodes()) {
			if (codeFacet.equals(metadata.getCode())) {
				facet = true;
				break;
			}
		}

		String reference = null;
		if (metadata.getType().equals(MetadataValueType.REFERENCE)) {
			reference = metadata.getAllowedReferences().getAllowedSchemaType();
		}

		boolean autocomplete = metadata.isSchemaAutocomplete();

		Object defaultValue = metadata.getDefaultValue();
		String inputMask = metadata.getInputMask();

		Map<String, String> newLabels = new HashMap<>();
		for (Entry<Language, String> entryLabels : labels.entrySet()) {
			newLabels.put(entryLabels.getKey().getCode(), entryLabels.getValue());
		}

		boolean duplicable = metadata.isDuplicable();

		FormMetadataVO formMetadataVO = new FormMetadataVO(code, type, required, schemaVO, reference, newLabels, searchable, multivalue, sortable,
				advancedSearch, facet, entry, displayType, highlight, autocomplete, enabled, metadataGroup, defaultValue,
				inputMask,
				duplicable,
				metadata.getCustomAttributes(),
				sessionContext);
		if(metadata.getInheritance() != null) {
			formMetadataVO.setInheritance(this.build(metadata.getInheritance(), schemaVO, configManager, schemaTypeCode,sessionContext ));
		}
		return formMetadataVO;
	}

	private String getValidMetadataGroup(String metadataGroupCode, SchemaTypeDisplayConfig config) {
		String validGroup = metadataGroupCode;
		boolean found = config.getMetadataGroup().keySet().contains(metadataGroupCode);

		if (!found) {
			validGroup = config.getMetadataGroup().keySet().iterator().next();
			for (String aGroup : config.getMetadataGroup().keySet()) {
				if (aGroup.startsWith("default")) {
					validGroup = aGroup;
				}
			}
		}

		return validGroup;
	}
}
