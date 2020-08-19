package com.constellio.app.modules.rm.ui.buttons;

import com.constellio.app.modules.rm.ui.field.CollectionSelectOptionField;
import com.constellio.app.services.factories.AppLayerFactory;
import com.constellio.app.services.menu.behavior.MenuItemActionBehaviorParams;
import com.constellio.app.ui.application.ConstellioUI;
import com.constellio.app.ui.framework.buttons.BaseButton;
import com.constellio.app.ui.framework.buttons.DeleteButton;
import com.constellio.app.ui.framework.buttons.WindowButton;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.entities.security.global.UserSyncMode;
import com.constellio.model.services.users.UserAddUpdateRequest;
import com.constellio.model.services.users.UserServices;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;
import org.vaadin.dialogs.ConfirmDialog;

import java.util.List;
import java.util.stream.Collectors;

import static com.constellio.app.ui.i18n.i18n.$;

public class DeleteUsersWindowButton extends WindowButton {
	private AppLayerFactory appLayerFactory;
	private List<Record> userRecords;
	private List<User> users;
	private MenuItemActionBehaviorParams params;
	private UserServices userServices;
	private CollectionSelectOptionField collectionsField;

	public DeleteUsersWindowButton(List<User> users, MenuItemActionBehaviorParams params) {
		super($("CollectionSecurityManagement.deleteUsers"), $("CollectionSecurityManagement.deleteUsers"),
				WindowConfiguration.modalDialog("50%", "300px"));
		this.appLayerFactory = params.getView().getConstellioFactories().getAppLayerFactory();
		this.users = users;
		this.userRecords = users.stream().map(u -> u.getWrappedRecord()).collect(Collectors.toList());
		this.params = params;
		this.userServices = params.getView().getConstellioFactories().getAppLayerFactory().getModelLayerFactory().newUserServices();
	}


	@Override
	protected Component buildWindowContent() {
		VerticalLayout mainLayout = new VerticalLayout();
		mainLayout.setHeight("90%");
		mainLayout.setSpacing(true);

		this.collectionsField = new CollectionSelectOptionField(appLayerFactory, userRecords, $("CollectionSecurityManagement.deleteUsersFromCollection", users.size()));
		mainLayout.addComponent(collectionsField);
		List<User> synchronizedUsers = ListSelectedSynchronizedUsers();
		Button deleteUserButton = new DeleteButton(null, $("CollectionSecurityManagement.delete"), false) {
			@Override
			protected void confirmButtonClick(ConfirmDialog dialog) {
				if (synchronizedUsers.isEmpty()) {
					deleteUsersAction();
				} else {
					new DesynchronizationWarningDialog(synchronizedUsers).showConfirm(ConstellioUI.getCurrent(), (ConfirmDialog.Listener) warningDialog -> {
						if (warningDialog.isConfirmed()) {
							deleteUsersAction();
						}
					});
				}
				getWindow().close();
			}

			@Override
			protected String getConfirmDialogMessage() {
				return $("ConfirmDialog.confirmDeleteWithAllRecords", $("CollectionSecurityManagement.userLowerCase"));
			}

			private void deleteUsersAction() {
				deleteUserFromCollections(users);
				params.getView().navigate().to().collectionSecurity();
				params.getView().showMessage($("CollectionSecurityManagement.userRemovedFromCollection"));
			}
		};
		deleteUserButton.addStyleName(ValoTheme.BUTTON_PRIMARY);

		HorizontalLayout buttonsLayout = new HorizontalLayout();
		buttonsLayout.setSpacing(true);
		buttonsLayout.addComponent(deleteUserButton);
		Button cancelButton = new BaseButton($("cancel")) {
			@Override
			protected void buttonClick(ClickEvent event) {
				getWindow().close();
			}
		};
		buttonsLayout.addComponent(cancelButton);
		mainLayout.addComponent(buttonsLayout);
		mainLayout.setComponentAlignment(buttonsLayout, Alignment.BOTTOM_CENTER);
		return mainLayout;
	}

	private List<User> ListSelectedSynchronizedUsers() {
		return users.stream()
				.filter(u -> userServices.getUserCredential(u.getUsername()).getSyncMode().equals(UserSyncMode.SYNCED))
				.collect(Collectors.toList());
	}

	public void deleteUserFromCollections(List<User> userRecords) {
		List<String> collections = collectionsField.getSelectedValues();
		for (String collection : collections) {
			for (User currentUser : userRecords) {
				UserAddUpdateRequest userAddUpdateRequest = userServices.addUpdate(currentUser.getUsername());
				userAddUpdateRequest.removeFromCollection(collection);
				userServices.execute(userAddUpdateRequest);
			}
		}
	}
}
