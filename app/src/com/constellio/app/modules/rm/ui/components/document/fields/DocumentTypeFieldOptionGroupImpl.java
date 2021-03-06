package com.constellio.app.modules.rm.ui.components.document.fields;

import com.constellio.app.modules.rm.wrappers.type.DocumentType;
import com.constellio.app.ui.framework.components.fields.record.RecordComboBox;

public class DocumentTypeFieldOptionGroupImpl extends RecordComboBox implements DocumentTypeField {

	public DocumentTypeFieldOptionGroupImpl() {
		super(DocumentType.DEFAULT_SCHEMA);
		setImmediate(true);
	}

	@Override
	public String getFieldValue() {
		return (String) getConvertedValue();
	}

	@Override
	public void setFieldValue(Object value) {
		setInternalValue((String) value);
	}

}
