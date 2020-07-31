package com.constellio.app.modules.rm.ui.buttons;

import com.constellio.app.modules.rm.services.RMSchemasRecordsServices;
import com.constellio.app.services.factories.AppLayerFactory;
import com.constellio.app.services.menu.behavior.MenuItemActionBehaviorParams;
import com.constellio.app.ui.framework.buttons.BaseButton;
import com.constellio.app.ui.framework.buttons.WindowButton;
import com.constellio.app.ui.pages.base.BaseView;
import com.constellio.app.ui.util.MessageUtils;
import com.constellio.data.utils.ImpossibleRuntimeException;
import com.constellio.model.entities.records.Record;
import com.constellio.model.services.records.RecordServices;
import com.constellio.model.services.users.UserServices;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.OptionGroup;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

import java.util.Collections;
import java.util.List;

import static com.constellio.app.ui.i18n.i18n.$;

public class CollectionsWindowButton extends WindowButton {

	private RMSchemasRecordsServices rm;
	private MenuItemActionBehaviorParams params;
	private AppLayerFactory appLayerFactory;
	private RecordServices recordServices;
	private UserServices userServices;
	private String collection;
	private List<Record> records;
	private AddedToCollectionRecordType addedRecordType;
	final OptionGroup collectionsField;

	public void addToCollections() {
		click();
	}

	public enum AddedToCollectionRecordType {
		USER, GROUP
	}

	public CollectionsWindowButton(Record record, MenuItemActionBehaviorParams params,
								   AddedToCollectionRecordType addedRecordType) {
		this(Collections.singletonList(record), params, addedRecordType);
	}

	public CollectionsWindowButton(List<Record> records, MenuItemActionBehaviorParams params,
								   AddedToCollectionRecordType addedRecordType) {
		super($("CollectionSecurityManagement.AddToCollection"), $("CollectionSecurityManagement.selectCollections"));

		this.params = params;
		this.appLayerFactory = params.getView().getConstellioFactories().getAppLayerFactory();
		this.recordServices = appLayerFactory.getModelLayerFactory().newRecordServices();
		this.userServices = appLayerFactory.getModelLayerFactory().newUserServices();
		this.collection = params.getView().getSessionContext().getCurrentCollection();
		this.rm = new RMSchemasRecordsServices(collection, appLayerFactory);
		this.records = records;
		this.addedRecordType = addedRecordType;
		this.collectionsField = new OptionGroup($("CollectionSecurityManagement.SelectCollections"));
	}

	@Override
	protected Component buildWindowContent() {
		VerticalLayout layout = new VerticalLayout();
		layout.setMargin(new MarginInfo(true, true, false, true));
		layout.setSizeFull();

		HorizontalLayout collectionLayout = new HorizontalLayout();
		collectionLayout.setSpacing(true);

		collectionsField.addStyleName("collections");
		collectionsField.addStyleName("collections-username");
		collectionsField.setId("collections");
		collectionsField.setMultiSelect(true);
		for (String collection : appLayerFactory.getCollectionsManager().getCollectionCodes()) {
			collectionsField.addItem(collection);
			boolean existsforAll = records.stream().allMatch(record -> record.getCollection().equals(collection));
			if (existsforAll) {
				collectionsField.select(collection);
			}
		}

		BaseButton saveButton;

		collectionLayout.addComponent(saveButton = new BaseButton($("save")) {
			@Override
			protected void buttonClick(ClickEvent event) {
				try {
					//getUserServices().addUsersToCollections(records, collectionField);
					addToCollectionRequested(params.getView());
					getWindow().close();
				} catch (Exception e) {
					e.printStackTrace();
					params.getView().showErrorMessage(MessageUtils.toMessage(e));
				}
			}
		});
		saveButton.addStyleName(ValoTheme.BUTTON_PRIMARY);

		layout.addComponents(collectionLayout);
		return layout;
	}

	private UserServices getUserServices() {
		return this.userServices;
	}

	private void addToCollectionRequested(BaseView baseView) {

		//Transaction transaction = new Transaction(RecordUpdateOptions.validationExceptionSafeOptions());
		//transaction.update(records);
		try {
			//recordServices.execute(transaction);
			switch (addedRecordType) {
				case USER:
					baseView.showMessage($("CollectionSecurityManagement.addedUserToCollections"));
					break;
				case GROUP:
					baseView.showMessage($("CollectionSecurityManagement.addedGroupToCollections"));
					break;
			}

		} catch (Exception e) {
			throw new ImpossibleRuntimeException(e);
		}

	}
}
