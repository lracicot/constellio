package com.constellio.model.services.records.populators;

import static java.util.Collections.singletonMap;

import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.constellio.model.entities.schemas.Metadata;
import com.constellio.model.entities.schemas.MetadataSchemaTypes;
import com.constellio.model.services.records.FieldsPopulator;

public class SortFieldsPopulator extends SeparatedFieldsPopulator implements FieldsPopulator {

	private static final Logger LOGGER = LoggerFactory.getLogger(SortFieldsPopulator.class);

	public SortFieldsPopulator(MetadataSchemaTypes types, boolean fullRewrite) {
		super(types, fullRewrite);
	}

	@Override
	public Map<String, Object> populateCopyfields(Metadata metadata, Object value) {

		Metadata sortField = metadata.getSortField();

		if (sortField != null) {
			Object normalizedValue;
			if (value == null) {
				normalizedValue = metadata.getSortFieldNormalizer().normalizeNull();
			} else {
				normalizedValue = metadata.getSortFieldNormalizer().normalize((String) value);
			}
			if (normalizedValue == null) {
				normalizedValue = "";
			}
			return singletonMap(sortField.getDataStoreCode(), normalizedValue);

		} else {
			return Collections.emptyMap();
		}

	}
}
