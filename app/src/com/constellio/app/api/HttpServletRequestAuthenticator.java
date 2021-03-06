package com.constellio.app.api;

import com.constellio.app.ui.pages.base.VaadinSessionContext;
import com.constellio.data.utils.AuthCache;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.services.factories.ModelLayerFactory;
import com.constellio.model.services.users.SystemWideUserInfos;
import com.constellio.model.services.users.UserServices;
import com.constellio.model.services.users.UserServicesRuntimeException;
import com.constellio.model.services.users.UserServicesRuntimeException.UserServicesRuntimeException_UserIsNotInCollection;
import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.Charset;
import java.security.Principal;

import static com.constellio.data.conf.FoldersLocator.usingAppWrapper;

public class HttpServletRequestAuthenticator {

	private static final Logger LOGGER = LoggerFactory.getLogger(HttpServletRequestAuthenticator.class);
	public static final String USER_SERVICE_KEY = "serviceKey";
	public static final String USER_TOKEN = "token";
	public static final String COLLECTION = "collection";
	public static final String USERNAME = "username";

	static AuthCache authCache = new AuthCache(Duration.standardMinutes(2));

	UserServices userServices;
	ModelLayerFactory modelLayerFactory;

	public HttpServletRequestAuthenticator(ModelLayerFactory modelLayerFactory) {
		this.modelLayerFactory = modelLayerFactory;
		this.userServices = modelLayerFactory.newUserServices();
	}

	public String getUserServiceKey(HttpServletRequest request) {
		String userServiceKey = request.getHeader(USER_SERVICE_KEY);
		if (userServiceKey == null) {
			userServiceKey = request.getParameter(USER_SERVICE_KEY);
		}
		if (userServiceKey != null) {
			userServiceKey = userServiceKey.trim();
		}
		return userServiceKey;
	}

	public String getUserToken(HttpServletRequest request) {
		String userToken = request.getHeader(USER_TOKEN);
		if (userToken == null) {
			userToken = request.getParameter(USER_TOKEN);
		}
		if (userToken != null) {
			userToken = userToken.trim();
		}
		return userToken;
	}

	public SystemWideUserInfos authenticate(HttpServletRequest request) {
		String userServiceKey = getUserServiceKey(request);
		String userToken = getUserToken(request);

		//Save time when testing on a developer computer
		if ("SDK".equals(userToken) && !usingAppWrapper()) {
			return userServices.getUserInfos(userServiceKey);
		}

		UserServices userServices = modelLayerFactory.newUserServices();

		String cachedUsername = authCache.get(userServiceKey, userToken);
		if (cachedUsername != null) {
			return userServices.getUserInfos(cachedUsername);
		} else {
			if (userServices.isAuthenticated(userServiceKey, userToken)) {
				String username = userServices.getUserCredentialByServiceKey(userServiceKey);
				authCache.insert(userServiceKey, userToken, username);
				return userServices.getUserInfos(username);
			} else {
				return ssoLogin(request);
			}
		}
	}

	private SystemWideUserInfos ssoLogin(HttpServletRequest request) {
		Principal userPrincipal = (Principal) request.getSession().getAttribute(VaadinSessionContext.USER_PRINCIPAL_ATTRIBUTE);
		if (userPrincipal != null) {
			String username = userPrincipal.getName();
			SystemWideUserInfos userCredential = userServices.getNullableUserInfos(username);
			if (userCredential != null && userCredential.isActiveInAnyCollection()) {
				return userServices.getUserInfos(username);
			}
		}
		return null;
	}

	public SystemWideUserInfos authenticateSystemAdmin(HttpServletRequest request) {
		SystemWideUserInfos user = authenticate(request);
		if (user == null) {
			return null;
		} else {
			if (user.isSystemAdmin()) {
				return user;
			} else {
				LOGGER.warn("Cannot authenticate : User '" + user.getUsername() + "' is not a system admin");
				return null;
			}
		}

	}

	public User authenticateInCollection(HttpServletRequest request) {
		SystemWideUserInfos userCredential = authenticate(request);
		if (userCredential == null) {
			return null;
		} else {
			String collection = getCollection(request);
			return userServices.getUserInCollection(userCredential.getUsername(), collection);
		}
	}

	public String getCollection(HttpServletRequest request) {
		String collection = request.getHeader(COLLECTION);
		if (collection == null) {
			collection = request.getParameter(COLLECTION);
		}
		return collection;
	}

	public SystemWideUserInfos authenticateUsingUsername(HttpServletRequest request) {
		String username = request.getHeader(USERNAME);
		String userToken = request.getHeader(USER_TOKEN);

		UserServices userServices = modelLayerFactory.newUserServices();
		try {
			byte[] tokenOrPassword = userToken.getBytes(Charset.forName("UTF-8"));

			SystemWideUserInfos userCredential = userServices.getUserInfos(username);
			boolean authenticated = userCredential.getTokenKeys().contains(new String(tokenOrPassword));
			if (!authenticated) {
				authenticated = modelLayerFactory.newAuthenticationService().authenticate(username, new String(tokenOrPassword));
			}

			if (authenticated) {
				return userServices.getUserInfos(username);
			} else {
				LOGGER.warn("Cannot authentify user - Bad token or password");
				return null;
			}

		} catch (UserServicesRuntimeException.UserServicesRuntimeException_NoSuchUser | UserServicesRuntimeException_UserIsNotInCollection e) {
			LOGGER.warn("Cannot authentify user ", e);
			return null;
		}
	}

	public User authenticateSystemAdminInCollection(HttpServletRequest request) {
		SystemWideUserInfos userCredential = authenticateSystemAdmin(request);
		if (userCredential == null) {
			return null;
		} else {
			String collection = getCollection(request);
			if (collection == null) {
				LOGGER.warn("Cannot authenticate : Missing 'collection' parameter");
				return null;
			} else {
				try {
					return userServices.getUserInCollection(userCredential.getUsername(), collection);
				} catch (UserServicesRuntimeException_UserIsNotInCollection e) {
					LOGGER.warn("Cannot authenticate : User '" + userCredential.getUsername() + "' is not in collection '"
								+ collection + "'");
					return null;
				}
			}
		}
	}
}
