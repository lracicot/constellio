package com.constellio.app.ui.acceptation.collection;

import com.constellio.app.modules.rm.RMTestRecords;
import com.constellio.app.ui.entities.RecordVO;
import com.constellio.app.ui.entities.RecordVO.VIEW_MODE;
import com.constellio.app.ui.framework.builders.RecordToVOBuilder;
import com.constellio.app.ui.pages.base.SessionContext;
import com.constellio.app.ui.pages.collection.CollectionUserView;
import com.constellio.app.ui.pages.management.authorizations.TransferPermissionPresenter;
import com.constellio.app.ui.pages.management.authorizations.TransferPermissionPresenterException;
import com.constellio.app.ui.pages.management.authorizations.TransferPermissionPresenterException.TransferPermissionPresenterException_CannotRemovePermission;
import com.constellio.app.ui.pages.management.authorizations.TransferPermissionPresenterException.TransferPermissionPresenterException_CannotSelectUser;
import com.constellio.app.ui.pages.management.authorizations.TransferPermissionPresenterException.TransferPermissionPresenterException_EmptyDestinationList;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.records.wrappers.Authorization;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.services.security.AuthorizationsServices;
import com.constellio.model.services.users.UserServices;
import com.constellio.sdk.tests.ConstellioTest;
import com.constellio.sdk.tests.FakeSessionContext;
import com.constellio.sdk.tests.setups.Users;
import com.vaadin.ui.Window;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class TransferPermissionPresenterAcceptanceTest extends ConstellioTest {
	RMTestRecords records = new RMTestRecords(zeCollection);
	private Users users;

	@Mock Window window;
	@Mock CollectionUserView collectionUserView;
	private SessionContext sessionContext;
	TransferPermissionPresenter transferPermissionPresenter;
	private AuthorizationsServices authorizationsServices;
	private UserServices userServices;

	private User sourceUser;
	private User destUser1;
	private User destUser2;

	@Before
	public void setUp() {
		users = new Users();

		prepareSystem(withZeCollection()
				.withConstellioRMModule()
				.withAllTestUsers()
				.withRMTest(records)
				.withFoldersAndContainersOfEveryStatus());
		sessionContext = FakeSessionContext.gandalfInCollection(zeCollection);
		when(collectionUserView.getSessionContext()).thenReturn(sessionContext);

		userServices = getModelLayerFactory().newUserServices();
		authorizationsServices = getModelLayerFactory().newAuthorizationsServices();

		initOrUpdateTestUsers();
		transferPermissionPresenter = spy(new TransferPermissionPresenter(collectionUserView, sourceUser.getId()));
	}


	@Test
	public void whenCopyingAccessRightsThenAllSelectedUsersAccessRightsAreExactlyTheSameAsSourceUser() {
		List<String> destinationUsers = new ArrayList<>();
		destinationUsers.add(destUser1.getId());
		destinationUsers.add(destUser2.getId());

		List<Authorization> sourceUserAuthorizations = authorizationsServices.getRecordAuthorizations(sourceUser);
		for (Authorization authorization : sourceUserAuthorizations) {
			assertThat(authorization.getPrincipals()).contains(sourceUser.getId());
			assertThat(authorization.getPrincipals()).doesNotContain(destUser1.getId(), destUser2.getId());
		}

		transferPermissionPresenter.transferAccessSaveButtonClicked(convertUserToUserVO(sourceUser), destinationUsers, window);

		for (Authorization authorization : sourceUserAuthorizations) {
			assertThat(authorization.getPrincipals()).contains(destUser1.getId(), destUser2.getId());
		}
	}

	@Test
	public void givenRemoveAccessCheckboxCheckedWhenTransferringAccessRightsThenSourceUserAuthorizationsAreRemoved() {
		when(transferPermissionPresenter.isDeletionEnabled()).thenReturn(true);
		Record sourceUserRecord = sourceUser.getWrappedRecord();
		List<String> destinationUsers = new ArrayList<>();
		destinationUsers.add(destUser1.getId());
		destinationUsers.add(destUser2.getId());

		transferPermissionPresenter.setRemoveUserAccessCheckboxValue(true);

		transferPermissionPresenter.transferAccessSaveButtonClicked(convertUserToUserVO(sourceUser), destinationUsers, window);

		List<Authorization> sourceUserAuthorizations = authorizationsServices.getRecordAuthorizations(sourceUserRecord);
		for (Authorization authorization : sourceUserAuthorizations) {
			assertThat(authorization.getPrincipals()).doesNotContain(sourceUser.getId());
		}
	}

	@Test
	public void givenRemoveAccessCheckboxUncheckedWhenTransferringAccessRightsThenSourceUserAuthorizationsRemain() {
		Record sourceUserRecord = sourceUser.getWrappedRecord();
		List<String> destinationUsers = new ArrayList<>();
		destinationUsers.add(destUser1.getId());
		destinationUsers.add(destUser2.getId());

		transferPermissionPresenter.setRemoveUserAccessCheckboxValue(false);
		transferPermissionPresenter.transferAccessSaveButtonClicked(convertUserToUserVO(sourceUser), destinationUsers, window);
		List<Authorization> sourceUserAuthorizations = authorizationsServices.getRecordAuthorizations(sourceUserRecord);
		for (Authorization authorization : sourceUserAuthorizations) {
			assertThat(authorization.getPrincipals()).contains(sourceUser.getId());
		}
	}

	@Test(expected = TransferPermissionPresenterException_EmptyDestinationList.class)
	public void givenNoDestinationUserSelectedDestinationUsersWhenValidatingTransferThenErrorThrown()
			throws TransferPermissionPresenterException {
		List<String> destinationsUsersList = new ArrayList<>();
		transferPermissionPresenter.validateAccessTransfer(sourceUser.getWrappedRecord(), destinationsUsersList);
	}

	@Test(expected = TransferPermissionPresenterException_CannotSelectUser.class)
	public void givenSourceUserInDestinationUsersWhenValidatingTransferThenErrorThrown()
			throws TransferPermissionPresenterException {
		List<String> destinationUsers = new ArrayList<>();
		destinationUsers.add(sourceUser.getId());
		transferPermissionPresenter.validateAccessTransfer(sourceUser.getWrappedRecord(), destinationUsers);
	}

	@Test
	public void givenDestinationUsersWhenTransferringAccessRightsThenGroupsSameAsSourceUser() {
		List<String> destinationIds = new ArrayList<>();
		destinationIds.add(destUser1.getId());
		destinationIds.add(destUser2.getId());

		assertThat(destUser1.getUserGroups()).isNotEqualTo(sourceUser.getUserGroups());
		assertThat(destUser2.getUserGroups()).isNotEqualTo(sourceUser.getUserGroups());

		transferPermissionPresenter.transferAccessSaveButtonClicked(convertUserToUserVO(sourceUser), destinationIds, window);
		initOrUpdateTestUsers();

		assertThat(destUser1.getUserGroups()).isEqualTo(sourceUser.getUserGroups());
		assertThat(destUser2.getUserGroups()).isEqualTo(sourceUser.getUserGroups());
	}


	@Test
	public void givenDestinationUserWithSameAuthorizationAsSourceUserWhenTransferringAccessRightsThenDestinationUserNotAddedAgainToAuthorization() {
		List<String> destinationUsers = new ArrayList<>();
		destinationUsers.add(sourceUser.getId());

		transferPermissionPresenter.transferAccessSaveButtonClicked(convertUserToUserVO(sourceUser), destinationUsers, window);
		List<Authorization> sourceUserAuthorizations = authorizationsServices.getRecordAuthorizations(sourceUser);

		for (Authorization authorization : sourceUserAuthorizations) {
			assertThat(authorization.getPrincipals()).containsOnlyOnce(sourceUser.getId());
		}
	}

	@Test(expected = TransferPermissionPresenterException_CannotRemovePermission.class)
	public void givenUserDeletionDisabledAndRemoveRightsCheckboxCheckedWhenTransferringAccessRightErrorMessage()
			throws TransferPermissionPresenterException {
		when(transferPermissionPresenter.isDeletionEnabled()).thenReturn(false);

		transferPermissionPresenter.setRemoveUserAccessCheckboxValue(true);
		transferPermissionPresenter.validateAccessTransfer(sourceUser.getWrappedRecord(), Arrays.asList(destUser1.getId()));
	}

	@Test
	public void givenUserDeletionDisabledAndRemoveRightsCheckboxCheckedWhenTransferringAccessRightThenAuthorizationsAreNotRemoved() {
		User adminUser = userServices.getUserInCollection(admin, zeCollection);
		when(transferPermissionPresenter.isDeletionEnabled()).thenReturn(false);

		Record adminUserRecord = adminUser.getWrappedRecord();
		List<Authorization> adminUserAuthorizationsBeforeClick = authorizationsServices.getRecordAuthorizations(adminUserRecord);

		transferPermissionPresenter.setRemoveUserAccessCheckboxValue(true);
		transferPermissionPresenter.transferAccessSaveButtonClicked(convertUserToUserVO(adminUser), Arrays.asList(destUser1.getId()), window);

		List<Authorization> adminUserAuthorizationsAfterClick = authorizationsServices.getRecordAuthorizations(adminUserRecord);
		assertThat(adminUserAuthorizationsAfterClick).isEqualTo(adminUserAuthorizationsBeforeClick);
	}


	public void initOrUpdateTestUsers() {
		sourceUser = userServices.getUserInCollection(gandalf, zeCollection);
		destUser1 = userServices.getUserInCollection(aliceWonderland, zeCollection);
		destUser2 = userServices.getUserInCollection(bobGratton, zeCollection);
	}


	public RecordVO convertUserToUserVO(User user) {
		RecordToVOBuilder voBuilder = new RecordToVOBuilder();
		return voBuilder.build(user.getWrappedRecord(), VIEW_MODE.FORM, sessionContext);
	}

}
