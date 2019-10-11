package com.constellio.app.ui.framework.components.fields;

import com.constellio.model.utils.MaskUtils;
import com.vaadin.annotations.JavaScript;
import com.vaadin.data.Property;
import com.vaadin.data.Validator.InvalidValueException;
import com.vaadin.ui.TextField;
import org.apache.commons.lang.StringUtils;

import java.util.UUID;

@JavaScript({"theme://jquery/jquery-2.1.4.min.js", "theme://inputmask/jquery.inputmask.bundle.js"})
public class BaseTextField extends TextField {

	private String inputMask;

	private boolean maskSet = false;

	private boolean trim = true;

	public BaseTextField() {
		super();
		init();
	}

	public BaseTextField(Property<?> dataSource) {
		super(dataSource);
		init();
	}

	public BaseTextField(String caption, Property<?> dataSource) {
		super(caption, dataSource);
		init();
	}

	public BaseTextField(String caption, String value) {
		super(caption, value);
		init();
	}

	public BaseTextField(String caption) {
		super(caption);
		init();
	}

	public BaseTextField(boolean trim) {
		super();
		this.trim = trim;
		init();
	}

	private void init() {
		setNullRepresentation("");
	}

	@Override
	public void setValue(String newValue)
			throws com.vaadin.data.Property.ReadOnlyException {
		newValue = StringUtils.trim(newValue);
		super.setValue(newValue);
	}

	public String getInputMask() {
		return inputMask;
	}

	public void setInputMask(String inputMask) {
		this.inputMask = inputMask;
	}

	@Override
	public void attach() {
		super.attach();
		if (!maskSet && StringUtils.isNotBlank(inputMask)) {
			String id = getId();
			if (id == null) {
				id = UUID.randomUUID().toString();
				setId(id);
			}
			StringBuffer js = new StringBuffer();
			if (MaskUtils.MM_DD.equals(inputMask)) {
				inputMask = "m/d";
			}
			js.append("$(document).ready(function() {");
			js.append(" $(\"#" + id + "\").inputmask(\"" + inputMask + "\"); ");
			js.append("})");

			com.vaadin.ui.JavaScript.eval(js.toString());
			maskSet = true;
		}
	}

	@Override
	public void commit() throws SourceException, InvalidValueException {
		if (trim) {
			setInternalValue(StringUtils.trim(getValue()));
		}
		super.commit();
	}
}
