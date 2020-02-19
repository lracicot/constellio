package com.constellio.data.dao.services.sql;

import com.constellio.data.dao.services.DataLayerLogger;
import com.constellio.data.dao.services.DataStoreTypesFactory;
import com.constellio.data.dao.services.sql.MicrosoftSqlTransactionDao;
import com.constellio.data.dao.services.sql.SqlServerConnector;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.constellio.data.dao.services.DataLayerLogger;
import com.constellio.data.dao.services.DataStoreTypesFactory;
import com.constellio.data.dao.services.bigVault.solr.BigVaultServer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import static org.assertj.core.api.Assertions.assertThat;


public class MicrosoftSqlTransactionDaoUnitTest {

	static List<Object> listWithOneNull = new ArrayList<>();

	static {
		listWithOneNull.add(null);
	}

	@Mock SqlServerConnector sqlServerConnector;
	@Mock QueryRunner queryRunner;

	MicrosoftSqlTransactionDao recordDao;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);

		recordDao = new MicrosoftSqlTransactionDao(sqlServerConnector,queryRunner);
	}

	@Test
	public void givenVersiondoesNotExistsCreateVersionOnGetVersion() throws Exception{
		when(queryRunner.query((Connection) any(),any(), (ScalarHandler) any())).thenReturn(null);
		when(queryRunner.insert((Connection) any(),any(),any())).thenReturn(0);
		Integer testGetCurrentVersion = recordDao.getCurrentVersion();

		assertThat(testGetCurrentVersion).isEqualTo(1);
	}

	@Test
	public void givenVersiondoesExistsGetVersion() throws Exception{
		when(queryRunner.query((Connection) any(),any(), (ScalarHandler) any())).thenReturn(1);
		Integer testGetCurrentVersion = recordDao.getCurrentVersion();

		assertThat(testGetCurrentVersion).isEqualTo(1);
	}

	@Test(expected = SQLException.class)
	public void givenSqlExceptionIsThrownWhenGetVersion() throws Exception{
		when(queryRunner.query((Connection) any(),any(), (ScalarHandler) any())).thenThrow(SQLException.class);
		Integer testGetCurrentVersion = recordDao.getCurrentVersion();

	}
}