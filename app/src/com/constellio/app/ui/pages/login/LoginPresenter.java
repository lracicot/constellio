package com.constellio.app.ui.pages.login;

import com.constellio.app.modules.rm.navigation.RMViews;
import com.constellio.app.modules.rm.ui.builders.UserToVOBuilder;
import com.constellio.app.services.factories.AppLayerFactory;
import com.constellio.app.services.factories.ConstellioFactories;
import com.constellio.app.ui.application.ConstellioUI;
import com.constellio.app.ui.application.NavigatorConfigurationService;
import com.constellio.app.ui.entities.RecordVO.VIEW_MODE;
import com.constellio.app.ui.entities.UserVO;
import com.constellio.app.ui.i18n.i18n;
import com.constellio.app.ui.pages.base.BasePresenter;
import com.constellio.app.ui.pages.base.SessionContext;
import com.constellio.data.utils.ImpossibleRuntimeException;
import com.constellio.model.entities.CorePermissions;
import com.constellio.model.entities.Language;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.entities.records.wrappers.UserDocument;
import com.constellio.model.entities.schemas.Metadata;
import com.constellio.model.entities.schemas.MetadataSchemaType;
import com.constellio.model.entities.schemas.MetadataSchemaTypes;
import com.constellio.model.entities.schemas.Schemas;
import com.constellio.model.entities.security.global.UserCredential;
import com.constellio.model.entities.security.global.UserCredentialStatus;
import com.constellio.model.services.configs.SystemConfigurationsManager;
import com.constellio.model.services.factories.ModelLayerFactory;
import com.constellio.model.services.logging.LoggingServices;
import com.constellio.model.services.migrations.ConstellioEIMConfigs;
import com.constellio.model.services.schemas.MetadataSchemasManager;
import com.constellio.model.services.search.SearchServices;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery;
import com.constellio.model.services.search.query.logical.QueryExecutionMethod;
import com.constellio.model.services.security.authentification.AuthenticationService;
import com.constellio.model.services.users.UserServices;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.from;

public class LoginPresenter extends BasePresenter<LoginView> {

	private static Logger LOGGER = LoggerFactory.getLogger(LoginPresenter.class);

	private UserToVOBuilder voBuilder = new UserToVOBuilder();

	private LoginView view;

	public LoginPresenter(LoginView view) {
		super(view);
		this.view = view;

		AppLayerFactory appLayerFactory = ConstellioFactories.getInstance().getAppLayerFactory();
		String mainDataLanguage = appLayerFactory.getModelLayerFactory().getConfiguration().getMainDataLanguage();
		if (i18n.getLocale() == null && mainDataLanguage != null) {
			Locale mainDataLocale = Language.withCode(mainDataLanguage).getLocale();
			i18n.setLocale(mainDataLocale);
			view.getSessionContext().setCurrentLocale(mainDataLocale);
		}

		String usernameCookieValue = view.getUsernameCookieValue();
		if (usernameCookieValue != null) {
			view.setUsername(usernameCookieValue);
		}
	}

	@Override
	protected boolean hasPageAccess(String params, User user) {
		return true;
	}

	public void signInAttempt(String enteredUsername, String password, boolean rememberMe) {
		ModelLayerFactory modelLayerFactory = ConstellioFactories.getInstance().getModelLayerFactory();
		UserServices userServices = modelLayerFactory.newUserServices();
		AuthenticationService authenticationService = modelLayerFactory.newAuthenticationService();
		LoggingServices loggingServices = modelLayerFactory.newLoggingServices();

		UserCredential userCredential = userServices.getUserCredential(enteredUsername);
		String username = userCredential != null ? userCredential.getUsername() : enteredUsername;
		List<String> collections = userCredential != null ? userCredential.getCollections() : new ArrayList<String>();
		if (userCredential != null && userCredential.getStatus() == UserCredentialStatus.ACTIVE && authenticationService
				.authenticate(username, password)) {
			if (!collections.isEmpty()) {
				String lastCollection = null;
				User userInLastCollection = null;
				LocalDateTime lastLogin = null;

				for (String collection : collections) {
					User userInCollection = userServices.getUserInCollection(username, collection);
					if (userInLastCollection == null) {
						if (userInCollection != null) {
							lastCollection = collection;
							userInLastCollection = userInCollection;
							lastLogin = userInCollection.getLastLogin();
						}
					} else {
						if (lastLogin == null && userInCollection.getLastLogin() != null) {
							lastCollection = collection;
							userInLastCollection = userInCollection;
							lastLogin = userInCollection.getLastLogin();
						} else if (lastLogin != null && userInCollection.getLastLogin() != null && userInCollection.getLastLogin()
								.isAfter(lastLogin)) {
							lastCollection = collection;
							userInLastCollection = userInCollection;
							lastLogin = userInCollection.getLastLogin();
						}
					}
				}

				if (userInLastCollection != null) {
					//FIXME Disabled / Optimistic locking in load testing
					/*
					try {
						modelLayerFactory.newRecordServices().update(userInLastCollection
								.setLastLogin(TimeProvider.getLocalDateTime())
								.setLastIPAddress(view.getSessionContext().getCurrentUserIPAddress()).getWrappedRecord(), new RecordUpdateOptions().setOptimisticLockingResolution(OptimisticLockingResolution.KEEP_OLDER));
					} catch (RecordServicesException e) {
						LOGGER.error("Unable to update user : " + username, e);
					}
					*/
					if (!userCredential.hasAgreedToPrivacyPolicy() && getPrivacyPolicyConfigValue() != null) {
						view.popPrivacyPolicyWindow(modelLayerFactory, userInLastCollection, lastCollection);
					} else if (hasLastAlertPermission(userInLastCollection) && !userCredential.hasReadLastAlert() && getLastAlertConfigValue() != null) {
						view.popLastAlertWindow(modelLayerFactory, userInLastCollection, lastCollection);
					} else {
						signInValidated(userInLastCollection, lastCollection);
					}
				}
			} else {
				view.showUserHasNoCollectionMessage();
			}
			if (rememberMe) {
				view.setUsernameCookie(username);
			} else {
				view.setUsernameCookie(null);
			}
		} else {
			loggingServices.failingLogin(enteredUsername, ConstellioUI.getCurrent().getPage().getWebBrowser().getAddress());
			view.showBadLoginMessage();
		}
	}

	public void signInValidated(User userInLastCollection, String lastCollection) {
		SessionContext sessionContext = view.getSessionContext();
		userInLastCollection.setLastIPAddress(sessionContext.getCurrentUserIPAddress());
		modelLayerFactory.newLoggingServices().login(userInLastCollection);
		Locale userLocale = getSessionLanguage(userInLastCollection);

		UserVO currentUser = voBuilder
				.build(userInLastCollection.getWrappedRecord(), VIEW_MODE.DISPLAY, sessionContext);
		sessionContext.setCurrentUser(currentUser);
		sessionContext.setCurrentCollection(userInLastCollection.getCollection());
		sessionContext.setForcedSignOut(false);
		i18n.setLocale(userLocale);
		sessionContext.setCurrentLocale(userLocale);

		view.updateUIContent();
		String currentState = view.navigateTo().getState();
		if (StringUtils.contains(currentState, "/")) {
			currentState = StringUtils.substringBefore(currentState, "/");
		}
		boolean homePage = NavigatorConfigurationService.HOME.equals(currentState);
		if (homePage && hasUserDocuments(userInLastCollection, lastCollection)) {
			view.navigate().to(RMViews.class).listUserDocuments();
		}
	}

	Locale getSessionLanguage(User userInLastCollection) {
		String userPreferredLanguage = userInLastCollection.getLoginLanguageCode();
		String systemLanguage = modelLayerFactory.getConfiguration().getMainDataLanguage();
		if (StringUtils.isBlank(userPreferredLanguage)) {
			return getLocale(systemLanguage);
		} else {
			List<String> collectionLanguages = modelLayerFactory.getCollectionsListManager()
					.getCollectionLanguages(userInLastCollection.getCollection());
			if (collectionLanguages == null || collectionLanguages.isEmpty() || !collectionLanguages
					.contains(userPreferredLanguage)) {
				return getLocale(systemLanguage);
			} else {
				return getLocale(userPreferredLanguage);
			}
		}
	}

	private Locale getLocale(String languageCode) {
		for (Language language : Language.values()) {
			if (language.getCode().equals(languageCode)) {
				return new Locale(languageCode);
			}
		}
		throw new ImpossibleRuntimeException("Invalid language " + languageCode);
	}

	boolean hasUserDocuments(User user, String collection) {
		SearchServices searchServices = modelLayerFactory.newSearchServices();
		MetadataSchemasManager metadataSchemasManager = modelLayerFactory.getMetadataSchemasManager();
		MetadataSchemaTypes types = metadataSchemasManager.getSchemaTypes(collection);

		MetadataSchemaType userDocumentsSchemaType = types.getSchemaType(UserDocument.SCHEMA_TYPE);
		Metadata userMetadata = userDocumentsSchemaType.getDefaultSchema().getMetadata(UserDocument.USER);
		LogicalSearchQuery query = new LogicalSearchQuery();
		query.setCondition(from(userDocumentsSchemaType).where(userMetadata).is(user.getId()));
		query.sortDesc(Schemas.MODIFIED_ON);
		query.setQueryExecutionMethod(QueryExecutionMethod.USE_CACHE);
		return searchServices.hasResults(query);
	}

	private boolean hasLastAlertPermission(User user) {
		return user.has(CorePermissions.VIEW_LOGIN_NOTIFICATION_ALERT).globally();
	}

	public String getLogoTarget() {
		SystemConfigurationsManager manager = modelLayerFactory.getSystemConfigurationsManager();
		String linkTarget = manager.getValue(ConstellioEIMConfigs.LOGO_LINK);
		if (StringUtils.isBlank(linkTarget)) {
			linkTarget = "http://www.constellio.com";
		}
		return linkTarget;
	}

	public File getPrivacyPolicyFile() {
		SystemConfigurationsManager manager = modelLayerFactory.getSystemConfigurationsManager();
		File policy = manager.getFileFromValue(ConstellioEIMConfigs.PRIVACY_POLICY, "privacyPolicy_eimUSR");

		return policy;
	}

	public Object getPrivacyPolicyConfigValue() {
		SystemConfigurationsManager manager = modelLayerFactory.getSystemConfigurationsManager();
		return manager.getValue(ConstellioEIMConfigs.PRIVACY_POLICY);
	}

	public File getLastAlertFile() {
		SystemConfigurationsManager manager = modelLayerFactory.getSystemConfigurationsManager();
		File lastAlert = manager.getFileFromValue(ConstellioEIMConfigs.LOGIN_NOTIFICATION_ALERT, "lastAlert.pdf");

		return lastAlert;
	}

	public Object getLastAlertConfigValue() {
		SystemConfigurationsManager manager = modelLayerFactory.getSystemConfigurationsManager();
		return manager.getValue(ConstellioEIMConfigs.LOGIN_NOTIFICATION_ALERT);
	}
}
