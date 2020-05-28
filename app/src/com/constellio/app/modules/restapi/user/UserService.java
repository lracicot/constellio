package com.constellio.app.modules.restapi.user;

import com.constellio.app.modules.restapi.core.dao.BaseDao;
import com.constellio.app.modules.restapi.core.service.BaseService;
import com.constellio.app.modules.restapi.user.dao.UserDao;
import com.constellio.app.modules.restapi.user.dto.UserSignatureContentDto;
import com.constellio.app.modules.restapi.user.dto.UserSignatureDto;
import com.constellio.app.modules.restapi.validation.ValidationService;
import com.constellio.app.modules.restapi.validation.dao.ValidationDao;

import javax.inject.Inject;
import java.io.InputStream;

public class UserService extends BaseService {

	@Inject
	private UserDao userDao;

	@Inject
	private ValidationDao validationDao;

	@Inject
	private ValidationService validationService;

	@Override
	protected BaseDao getDao() {
		return userDao;
	}

	public UserSignatureContentDto getContent(String host, String token, String serviceKey, String metadataCode) {
		validationService.validateHost(host);
		validationService.validateToken(token, serviceKey);

		String username = validationDao.getUsernameByServiceKey(serviceKey);
		return userDao.getContent(username, metadataCode);
	}

	public void setContent(String host, String token, String serviceKey, String metadataCode,
						   UserSignatureDto userSignature, InputStream fileStream) {
		validationService.validateHost(host);
		validationService.validateToken(token, serviceKey);

		String username = validationDao.getUsernameByServiceKey(serviceKey);
		userDao.setContent(username, metadataCode, userSignature.getFilename(), fileStream);
	}

	public void deleteContent(String host, String token, String serviceKey, String metadataCode) {
		validationService.validateHost(host);
		validationService.validateToken(token, serviceKey);

		String username = validationDao.getUsernameByServiceKey(serviceKey);
		userDao.deleteContent(username, metadataCode);
	}
}
