package com.constellio.data.dao.services.sql;

import com.constellio.data.dao.dto.sql.TransactionSqlDTO;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class MicrosoftSqlTransactionDao implements SqlRecordDao<TransactionSqlDTO> {

	private final SqlConnector connector;
	public static final String TABLE_NAME = "transactions";
	public static final String SCHEMA_NAME = "constellio";
	private static final String DBO = "dbo";
	private static String fullTableName = SCHEMA_NAME + "." + DBO + "." + TABLE_NAME;
	private final QueryRunner queryRunner;
	private ScalarHandler<Integer> defaultHandler = new ScalarHandler<>();

	public MicrosoftSqlTransactionDao(SqlConnector connector) {
		queryRunner = new QueryRunner();
		this.connector = connector;
	}

	public MicrosoftSqlTransactionDao(SqlConnector connector, QueryRunner queryRunner) {
		this.queryRunner = queryRunner;
		this.connector = connector;
	}

	@Override
	public void insert(String query) throws SQLException {

		int numberOfRowsUpdated = queryRunner.insert(connector.getConnection(), query, defaultHandler);
	}

	@Override
	public void insert(TransactionSqlDTO dto) throws SQLException {

		String insertQuery = "INSERT INTO " + fullTableName
							 + " (transactionUUID,timestamp, logVersion, transactionSummary, content) "
							 + "VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?)";

		queryRunner.insert(connector.getConnection(),
				insertQuery, defaultHandler, dto.getTransactionUUID(),
				dto.getLogVersion(), dto.getTransactionSummary(), dto.getContent());

		//dto.setId(newId);
	}

	@Override
	public void insertBulk(List<TransactionSqlDTO> dtos) throws SQLException {

		String insertQuery = "INSERT INTO " + fullTableName
							 + " (transactionUUID,timestamp, logVersion, transactionSummary, content) "
							 + "VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?)";

		Connection connection = connector.getConnection();
		PreparedStatement ps = connection.prepareStatement(insertQuery);

		final int batchSize = 1000;
		int count = 0;

		for (TransactionSqlDTO transactions : dtos) {

			ps.setString(1, transactions.getTransactionUUID());
			ps.setInt(2, transactions.getLogVersion());
			ps.setString(3, transactions.getTransactionSummary());
			ps.setString(4, transactions.getContent());
			ps.addBatch();

			if (++count % batchSize == 0) {
				ps.executeBatch();
			}
		}
		ps.executeBatch();

	}

	@Override
	public TransactionSqlDTO get(int id) throws SQLException {

		ResultSetHandler<TransactionSqlDTO> handler = new BeanHandler<>(TransactionSqlDTO.class);

		String selectQuery = "SELECT * FROM " + fullTableName + " WHERE id=?";

		TransactionSqlDTO dto = queryRunner.query(connector.getConnection(),
				selectQuery, handler, id);

		return dto;
	}

	@Override
	public TransactionSqlDTO get(String transactionId) throws SQLException {

		ResultSetHandler<TransactionSqlDTO> handler = new BeanHandler<>(TransactionSqlDTO.class);

		String fetchQuery = "SELECT TOP 1 * FROM "+fullTableName+" WHERE transactionUUID=?";

		TransactionSqlDTO dto = queryRunner.query(connector.getConnection(),
				fetchQuery, handler, transactionId);

		return dto;
	}

	@Override
	public ResultSetHandler<TransactionSqlDTO> realGet(int id) throws SQLException {
		return null;
	}

	@Override
	public List<TransactionSqlDTO> getAll() throws SQLException {
		ResultSetHandler<List<TransactionSqlDTO>> handler = new BeanListHandler<>(TransactionSqlDTO.class);

		String fecthQuery = "SELECT * FROM "+fullTableName;

		List<TransactionSqlDTO> dto = queryRunner.query(connector.getConnection(),
				fecthQuery, handler);

		return dto;
	}

	@Override
	public void delete(int id) throws SQLException {

		String deleteQuery = "DELETE FROM "+fullTableName+" WHERE id =?";
		queryRunner.execute(connector.getConnection(),
				deleteQuery, id);
	}

	@Override
	public void deleteAll() throws SQLException {

		String deleteQuery = "DELETE FROM "+fullTableName+" WHERE id > 0";
		queryRunner.execute(connector.getConnection(),
				deleteQuery);
	}

	@Override
	public void deleteAll(int[] ids) throws SQLException {
		throw new NotImplementedException();
	}

	@Override
	public void deleteAll(List<String> ids) throws SQLException {
		throw new NotImplementedException();
	}

	@Override
	public void deleteAllByLogVersion(int logVersion) throws SQLException {
		String deleteQuery = "DELETE FROM "+fullTableName+" WHERE logVersion < ?";

		queryRunner.execute(connector.getConnection(),
				deleteQuery, logVersion);

	}

	@Override
	public void update(TransactionSqlDTO dto) throws SQLException {

		String updateQuery = "UPDATE "+fullTableName+" tr "
							 + "SET tr.transactionUUID=?, tr.timestamp=?, tr.logVersion=?, tr.transactionSummary=?, tr.content=?) "
							 + "WHERE id=?";
		queryRunner.update(connector.getConnection(), updateQuery,
				dto.getTransactionUUID(), dto.getTimestamp(), dto.getLogVersion(), dto.getTransactionSummary(), dto.getContent(), dto.getId());
	}

	@Override
	public void increaseVersion() throws SQLException {

		queryRunner.update(connector.getConnection(),
				"UPDATE versions SET version = version + 1 WHERE name = 'transactionLog' ");
	}

	@Override
	public int getCurrentVersion() throws SQLException {

		ScalarHandler<Integer> scalarHandler = new ScalarHandler<>();

		Integer version = queryRunner.query(connector.getConnection(),
				"SELECT version FROM versions WHERE name = 'transactionLog' ", scalarHandler);

		if (version == null) {
			String insertQuery = "INSERT INTO versions "
								 + "(name, version) "
								 + "VALUES ('transactionLog', 1)";

			queryRunner.insert(connector.getConnection(),
					insertQuery, defaultHandler);
			return 1;
		}

		return version;
	}


	@Override
	public void flush() {

	}

	@Override
	public long getTableCount() throws SQLException {
		ScalarHandler<Long> scalarHandler = new ScalarHandler<>();
		String fetchQuery = "SELECT COUNT(*) FROM "+fullTableName;

		long count = queryRunner.query(connector.getConnection(),
				fetchQuery, scalarHandler);

		return count;
	}


}
