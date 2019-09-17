package com.constellio.app.modules.tasks.ui.components.fields;

import com.constellio.app.ui.framework.components.fields.list.TaskCollaboratorsGroupItem;
import com.constellio.app.ui.framework.components.fields.lookup.LookupField;
import com.constellio.app.ui.framework.components.fields.lookup.LookupRecordField;
import com.constellio.app.ui.framework.components.layouts.I18NHorizontalLayout;
import com.constellio.model.entities.records.wrappers.Group;
import com.vaadin.data.Validator.InvalidValueException;
import com.vaadin.data.util.converter.Converter.ConversionException;
import com.vaadin.ui.Component;
import com.vaadin.ui.CustomField;
import com.vaadin.ui.OptionGroup;
import com.vaadin.ui.themes.ValoTheme;

import static com.constellio.app.ui.i18n.i18n.$;
import static java.util.Arrays.asList;

public class TaskAssignationListCollaboratorsGoupsField extends CustomField<TaskCollaboratorsGroupItem> {

	private TaskCollaboratorsGroupItem taskCollaboratorsGroupItem;

	private I18NHorizontalLayout mainLayout;

	private LookupField<String> lookupGroupField;

	private OptionGroup authorizationField;

	@Override
	protected Component initContent() {
		mainLayout = new I18NHorizontalLayout();
		mainLayout.setWidth("100%");
		mainLayout.setSpacing(true);

		authorizationField = new OptionGroup("", asList(false, true));
		authorizationField.setItemCaption(false, $("TaskAssignationListCollaboratorsField.collaboratorReadAuthorization"));
		authorizationField.setItemCaption(true, $("TaskAssignationListCollaboratorsField.collaboratorWriteAuthorization"));
		authorizationField.addStyleName(ValoTheme.OPTIONGROUP_HORIZONTAL);

		lookupGroupField = new LookupRecordField(Group.SCHEMA_TYPE);
		lookupGroupField.setCaption($("TaskAssignationListCollaboratorsField.taskCollaboratorsGroups"));

		if (taskCollaboratorsGroupItem != null) {
			authorizationField.setValue(taskCollaboratorsGroupItem.isTaskCollaboratorGroupWriteAuthorization());
			lookupGroupField.setValue(taskCollaboratorsGroupItem.getTaskCollaboratorGroup());
			taskCollaboratorsGroupItem = null;
		}

		mainLayout.addComponents(lookupGroupField, authorizationField);

		return mainLayout;
	}

	@Override
	public Object getConvertedValue() {
		Object convertedValue;
		Boolean writeAuthorization = (Boolean) authorizationField.getValue();
		String groupId = (String) lookupGroupField.getValue();
		if (writeAuthorization != null && groupId != null) {
			convertedValue = new TaskCollaboratorsGroupItem(groupId, writeAuthorization);
		} else {
			convertedValue = null;
		}
		return convertedValue;
	}

	@Override
	protected void setValue(TaskCollaboratorsGroupItem newFieldValue, boolean repaintIsNotNeeded,
							boolean ignoreReadOnly)
			throws ReadOnlyException, ConversionException, InvalidValueException {
		if (authorizationField != null && lookupGroupField != null) {
			Boolean newWriteAuthorization;
			String newGroupId;
			if (newFieldValue != null) {
				newGroupId = newFieldValue.getTaskCollaboratorGroup();
				newWriteAuthorization = newFieldValue.isTaskCollaboratorGroupWriteAuthorization();
			} else {
				newWriteAuthorization = null;
				newGroupId = null;
			}
			authorizationField.setValue(newWriteAuthorization);
			lookupGroupField.setValue(newGroupId);
		} else {
			taskCollaboratorsGroupItem = newFieldValue;
		}
	}

	@Override
	public Class<? extends TaskCollaboratorsGroupItem> getType() {
		return TaskCollaboratorsGroupItem.class;
	}

}
