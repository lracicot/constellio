package com.constellio.app.modules.restapi.url.dao;

import com.constellio.app.modules.restapi.core.util.DateUtils;
import com.constellio.app.modules.restapi.core.util.SchemaTypes;
import com.constellio.data.utils.TimeProvider;
import com.constellio.model.services.configs.SystemConfigurationsManager;
import com.constellio.model.services.migrations.ConstellioEIMConfigs;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import javax.ws.rs.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class UrlDaoTest {

	@Mock private SystemConfigurationsManager systemConfigurationsManager;

	@InjectMocks private UrlDao urlDao;

	private String host = "localhost";
	private String token = "token";
	private String serviceKey = "serviceKey";
	private SchemaTypes schemaType = SchemaTypes.DOCUMENT;
	private String method = HttpMethod.GET;
	private String id = "id";
	private String folderId = "A01";
	private String expiration = "3600";
	private String version;
	private String physical;
	private String copySourceId;

	private String expectedSignedUrl = "http://localhost/constellio/rest/v1/documents?" +
									   "id=id&serviceKey=serviceKey&method=GET&date=&expiration=3600&signature=";
	private String signedUrl;

	@Before
	public void setUp() throws Exception {
		initMocks(this);

		when(systemConfigurationsManager.getValue(ConstellioEIMConfigs.CONSTELLIO_URL)).thenReturn("http://localhost/constellio/");
	}

	@Test
	public void testGetSignedUrl() throws Exception {
		folderId = null;
		signedUrl = urlDao.getSignedUrl(host, token, serviceKey, schemaType, method, id, folderId, expiration,
				version, physical, copySourceId);
		adjustExpectedSignedUrl();

		assertThat(signedUrl).startsWith(expectedSignedUrl);
	}

	@Test
	public void testGetSignedUrlWithSwappedParameters() throws Exception {
		folderId = null;
		signedUrl = urlDao.getSignedUrl(host, token, method, schemaType, serviceKey, id, folderId, expiration,
				version, physical, copySourceId);
		adjustExpectedSignedUrl();

		boolean startsWith = signedUrl.startsWith(expectedSignedUrl);
		assertThat(startsWith).isFalse();
	}

	@Test
	public void testGetSignedUrlWithContentPath() throws Exception {
		folderId = null;
		version = "1.0";
		expectedSignedUrl = expectedSignedUrl
				.replace("/documents", "/documents/content")
				.replace("&expiration=3600", "&expiration=3600&version=1.0");

		signedUrl = urlDao.getSignedUrl(host, token, serviceKey, schemaType, method, id, folderId, expiration,
				version, physical, copySourceId);
		adjustExpectedSignedUrl();

		assertThat(signedUrl).startsWith(expectedSignedUrl);
	}

	@Test
	public void testGetSignedUrlWithPhysical() throws Exception {
		physical = "false";
		expectedSignedUrl = expectedSignedUrl
				.replace("&expiration=3600", "&expiration=3600&physical=false");

		signedUrl = urlDao.getSignedUrl(host, token, serviceKey, schemaType, method, id, folderId, expiration,
				version, physical, copySourceId);
		adjustExpectedSignedUrl();

		assertThat(signedUrl).startsWith(expectedSignedUrl);
	}

	private void adjustExpectedSignedUrl() {
		String now = DateUtils.formatIsoNoMillis(TimeProvider.getLocalDateTime());
		expectedSignedUrl = expectedSignedUrl.replace("date=", "date=".concat(now));
	}

}
