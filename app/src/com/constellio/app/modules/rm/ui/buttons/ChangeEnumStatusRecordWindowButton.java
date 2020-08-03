package com.constellio.app.modules.rm.ui.buttons;

import com.constellio.app.services.factories.AppLayerFactory;
import com.constellio.app.services.menu.behavior.MenuItemActionBehaviorParams;
import com.constellio.app.ui.framework.buttons.BaseButton;
import com.constellio.app.ui.framework.buttons.WindowButton;
import com.constellio.app.ui.util.MessageUtils;
import com.constellio.model.services.factories.ModelLayerFactory;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.OptionGroup;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

import static com.constellio.app.ui.i18n.i18n.$;

public abstract class ChangeEnumStatusRecordWindowButton<T extends Enum<T>> extends WindowButton {

	private ModelLayerFactory modelLayerFactory;
	private MenuItemActionBehaviorParams params;
	private OptionGroup enumOptionsField;
	private Class<T> enumType;

	public ChangeEnumStatusRecordWindowButton(String caption, String windowCaption, AppLayerFactory appLayerFactory,
											  MenuItemActionBehaviorParams params, Class<T> enumType) {
		super(caption, windowCaption);

		modelLayerFactory = appLayerFactory.getModelLayerFactory();
		this.params = params;
		this.enumType = enumType;
	}

	@Override
	protected Component buildWindowContent() {
		VerticalLayout mainLayout = new VerticalLayout();
		mainLayout.setMargin(new MarginInfo(true, true, false, true));
		mainLayout.setSizeFull();

		HorizontalLayout enumLayout = new HorizontalLayout();
		enumLayout.setSpacing(true);

		enumOptionsField.addStyleName("collections");
		enumOptionsField.addStyleName("collections-username");
		enumOptionsField.setId("enumStatus");
		enumOptionsField.setMultiSelect(false);
		enumLayout.addComponent(enumOptionsField);
		String firstValue = null;
		for (Object enumObject : enumType.getEnumConstants()) {
			if (enumObject instanceof String) {
				if (firstValue == null) {
					firstValue = (String) enumObject;
				}
				enumOptionsField.addItem((String) enumObject);
			}
		}
		if (firstValue != null) {
			enumOptionsField.select(firstValue);
		}

		mainLayout.addComponents(enumLayout);
		BaseButton saveButton;
		BaseButton cancelButton;
		HorizontalLayout buttonLayout = new HorizontalLayout();

		buttonLayout.addComponent(saveButton = new BaseButton($("save")) {
			@Override
			protected void buttonClick(ClickEvent event) {
				try {
					changeStatus(enumOptionsField.getValue());
					getWindow().close();
				} catch (Exception e) {
					e.printStackTrace();
					params.getView().showErrorMessage(MessageUtils.toMessage(e));
				}
			}
		});
		saveButton.addStyleName(ValoTheme.BUTTON_PRIMARY);

		buttonLayout.addComponent(cancelButton = new BaseButton($("cancel")) {
			@Override
			protected void buttonClick(ClickEvent event) {
				getWindow().close();
			}
		});


		buttonLayout.setSpacing(true);
		mainLayout.addComponent(buttonLayout);
		mainLayout.setHeight("100%");
		mainLayout.setWidth("100%");
		mainLayout.setSpacing(true);


		return mainLayout;
	}

	public abstract void changeStatus(Object value);

}
