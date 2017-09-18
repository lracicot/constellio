package com.constellio.app.api.cmis.binding.global;

import static com.constellio.model.entities.CorePermissions.USE_EXTERNAL_APIS_FOR_COLLECTION;

import java.math.BigInteger;
import java.util.Map;

import org.apache.chemistry.opencmis.commons.exceptions.CmisPermissionDeniedException;
import org.apache.chemistry.opencmis.commons.impl.server.AbstractServiceFactory;
import org.apache.chemistry.opencmis.commons.server.CallContext;
import org.apache.chemistry.opencmis.commons.server.CmisService;
import org.apache.chemistry.opencmis.commons.server.MutableCallContext;
import org.apache.chemistry.opencmis.server.support.wrapper.CallContextAwareCmisService;
import org.apache.chemistry.opencmis.server.support.wrapper.CmisServiceWrapperManager;
import org.apache.chemistry.opencmis.server.support.wrapper.ConformanceCmisServiceWrapper;
import org.joda.time.Duration;

import com.constellio.app.api.cmis.CmisExceptions.CmisExceptions_InvalidLogin;
import com.constellio.app.services.factories.AppLayerFactory;
import com.constellio.app.services.factories.ConstellioFactories;
import com.constellio.data.utils.AuthCache;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.entities.security.global.UserCredential;
import com.constellio.model.services.factories.ModelLayerFactory;
import com.constellio.model.services.records.RecordServices;
import com.constellio.model.services.security.AuthorizationsServices;
import com.constellio.model.services.security.authentification.AuthenticationService;
import com.constellio.model.services.users.UserServices;
import com.constellio.model.services.users.UserServicesRuntimeException;

public class ConstellioCmisRequestFactory extends AbstractServiceFactory {

	static AuthCache authCache = new AuthCache(Duration.standardMinutes(2));

	/** Default maxItems value for getTypeChildren()}. */
	private static final BigInteger DEFAULT_MAX_ITEMS_TYPES = BigInteger.valueOf(50);

	/** Default depth value for getTypeDescendants(). */
	private static final BigInteger DEFAULT_DEPTH_TYPES = BigInteger.valueOf(-1);

	/**
	 * Default maxItems value for getChildren() and other methods returning lists of objects.
	 */
	private static final BigInteger DEFAULT_MAX_ITEMS_OBJECTS = BigInteger.valueOf(200);

	/** Default depth value for getDescendants(). */
	private static final BigInteger DEFAULT_DEPTH_OBJECTS = BigInteger.valueOf(10);

	private CmisServiceWrapperManager wrapperManager;

	@Override
	public void init(Map<String, String> parameters) {
		configureWrappers(parameters);
	}

	private void configureWrappers(Map<String, String> parameters) {
		wrapperManager = new CmisServiceWrapperManager();
		wrapperManager.addWrappersFromServiceFactoryParameters(parameters);
		wrapperManager.addOuterWrapper(ConformanceCmisServiceWrapper.class, DEFAULT_MAX_ITEMS_TYPES, DEFAULT_DEPTH_TYPES,
				DEFAULT_MAX_ITEMS_OBJECTS, DEFAULT_DEPTH_OBJECTS);
	}

	@Override
	public void destroy() {
	}

	@Override
	public CmisService getService(CallContext context) {

		authenticate(context);

		CallContextAwareCmisService service = getCurrentThreadCmisService();

		addConstellioParameters(context);

		service.setCallContext(context);

		return service;
	}

	private void addConstellioParameters(CallContext context) {

		String collection = context.getRepositoryId();
		User currentUser = getCurrentUser(context, collection);

		MutableCallContext mcc = (MutableCallContext) context;
		mcc.put(ConstellioCmisContextParameters.COLLECTION, collection);
		mcc.put(ConstellioCmisContextParameters.USER, currentUser);
	}

	public static UserCredential authenticateUserFromContext(CallContext callContext, UserServices userServices) {
		String collection = callContext.getRepositoryId();
		String serviceKey = callContext.getUsername();
		String token = callContext.getPassword();

		String cacheKey = serviceKey + token;

		String cachedUsername = authCache.get(serviceKey, token);
		if (cachedUsername != null) {
			return userServices.getUser(cachedUsername);
		} else {

			try {
				String username = userServices.getTokenUser(serviceKey, token);
				authCache.insert(serviceKey, token, username);
				UserCredential userCredential = userServices.getUser(username);
				if (collection != null) {
					User user = userServices.getUserInCollection(username, collection);
					if (!userCredential.isSystemAdmin() && !user.has(USE_EXTERNAL_APIS_FOR_COLLECTION).globally()) {
						throw new CmisPermissionDeniedException(
								"User does not have permission to use CMIS in collection '" + collection + "'");
					}
				} else {
					if (!userServices.has(userCredential).globalPermissionInAnyCollection(USE_EXTERNAL_APIS_FOR_COLLECTION)) {
						throw new CmisPermissionDeniedException(
								"User must have the permission to use CMIS in at least one collection");
					}

				}
				return userCredential;

			} catch (UserServicesRuntimeException e) {
				throw new CmisExceptions_InvalidLogin();
			}
		}
	}

	private UserCredential authenticate(CallContext context) {
		UserServices userServices = getUserServices();
		return authenticateUserFromContext(context, userServices);
	}

	private User getCurrentUser(CallContext context, String collection) {
		UserCredential userCredential = authenticate(context);
		User currentUser = null;
		if (userCredential != null) {
			if (collection != null) {
				currentUser = getUserServices().getUserInCollection(userCredential.getUsername(), collection);
			}
		}
		return currentUser;
	}

	private CallContextAwareCmisService getCurrentThreadCmisService() {

		ThreadLocal<CallContextAwareCmisService> threadLocalService = getConstellioCmisRepositoriesManager()
				.getCallContextAwareCmisServiceThreadLocal();
		CallContextAwareCmisService service = threadLocalService.get();
		if (service == null) {
			ConstellioCmisRequests constellioCmisService = new ConstellioCmisRequests(getAppLayerFactory(),
					getConstellioCmisRepositoriesManager());
			service = (CallContextAwareCmisService) wrapperManager.wrap(constellioCmisService);
			threadLocalService.set(service);
		}
		return service;
	}

	public UserServices getUserServices() {
		return getModelLayerFactory().newUserServices();
	}

	public RecordServices getRecordServices() {
		return getModelLayerFactory().newRecordServices();
	}

	public AuthorizationsServices getAuthorizationsServices() {
		return getModelLayerFactory().newAuthorizationsServices();
	}

	public AuthenticationService getAuthenticationService() {
		return getModelLayerFactory().newAuthenticationService();
	}

	public ModelLayerFactory getModelLayerFactory() {
		ConstellioFactories constellioFactories = ConstellioFactories.getInstance();
		return constellioFactories.getModelLayerFactory();
	}

	public AppLayerFactory getAppLayerFactory() {
		ConstellioFactories constellioFactories = ConstellioFactories.getInstance();
		return constellioFactories.getAppLayerFactory();
	}

	public CmisCacheManager getConstellioCmisRepositoriesManager() {
		ConstellioFactories constellioFactories = ConstellioFactories.getInstance();
		return constellioFactories.getAppLayerFactory().getConstellioCmisRepositoriesManager();
	}

}
