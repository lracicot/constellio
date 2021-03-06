package com.constellio.data.conf;

import com.constellio.data.dao.services.transactionLog.SecondTransactionLogReplayFilter;
import org.joda.time.Duration;

import java.io.File;
import java.util.List;

public interface DataLayerConfiguration {

	void validate();

	SolrServerType getRecordsDaoSolrServerType();

	boolean isCopyingRecordsInSearchCollection();

	String getRecordsDaoHttpSolrServerUrl();

	String getRecordsDaoCloudSolrServerZKHost();

	boolean isRecordsDaoHttpSolrServerFaultInjectionEnabled();

	ContentDaoType getContentDaoType();

	String getContentDaoHadoopUrl();

	String getContentDaoHadoopUser();

	File getContentDaoFileSystemFolder();

	void setContentDaoFileSystemFolder(File contentsFolder);

	DigitSeparatorMode getContentDaoFileSystemDigitsSeparatorMode();

	void setContentDaoFileSystemDigitsSeparatorMode(DigitSeparatorMode mode);

	String getContentDaoReplicatedVaultMountPoint();

	void setContentDaoReplicatedVaultMountPoint(String replicatedVaultMountPoint);

	File getTempFolder();

	ConfigManagerType getSettingsConfigType();

	CacheType getCacheType();

	List<String> getSubvaults();

	String getCacheUrl();

	String getSettingsZookeeperAddress();

	File getSettingsFileSystemBaseFolder();

	IdGeneratorType getIdGeneratorType();

	IdGeneratorType getSecondaryIdGeneratorType();

	boolean isSecondTransactionLogEnabled();

	boolean isWriteZZRecords();

	boolean useSolrTupleStreamsIfSupported();

	HashingEncoding getHashingEncoding();

	File getSecondTransactionLogBaseFolder();

	int getBackgroudThreadsPoolSize();

	Duration getSecondTransactionLogMergeFrequency();

	int getSecondTransactionLogBackupCount();

	boolean isBackgroundThreadsEnabled();

	void setBackgroundThreadsEnabled(boolean backgroundThreadsEnabled);

	SecondTransactionLogReplayFilter getSecondTransactionLogReplayFilter();

	void setWriteZZRecords(boolean enable);

	boolean isLocalHttpSolrServer();

	boolean isInRollbackTestMode();

	String createRandomUniqueKey();

	void setHashingEncoding(HashingEncoding encoding);

	String getKafkaServers();

	SecondTransactionLogType getSecondTransactionLogMode();

	String getKafkaTopic();

	long getReplayTransactionStartVersion();

	int getConversionProcesses();

	String getOnlineConversionUrl();

	EventBusSendingServiceType getEventBusSendingServiceType();

	ElectionServiceType getElectionServiceType();

	boolean isSystemDistributed();

	Duration getSolrEventBusSendingServiceTypeEventLifespan();

	Duration getSolrEventBusSendingServiceTypePollAndRetrieveFrequency();

	boolean areTiffFilesConvertedForPreview();

	int getSequentialIdReservedBatchSize();

	String getMicrosoftSqlServerUrl();

	String getMicrosoftSqlServerDatabase();

	String getMicrosoftSqlServeruser();

	String getMicrosoftSqlServerpassword();

	boolean getMicrosoftSqlServerencrypt();

	boolean getMicrosoftSqlServertrustServerCertificate();

	int getMicrosoftSqlServerloginTimeout();

	boolean isAsyncSQLSecondTransactionLogInsertion();

	boolean isReplaySQLSecondTransactionLogDuringOfficeHours();

	int getSolrMinimalReplicationFactor();

	String getRecordsDaoCollection();

	String getEventsDaoCollection();

	String getNotificationsDaoCollection();

	String getAzureBlobStorageConnectionString();

	String getAzureBlobStorageConnectionAccountName();

	String getAzureBlobStorageConnectionAccountKey();

	String getAzureBlobStorageContainerName();

	void setAzureBlobStorageConnectionAccountName(String accountName);

	void setAzureBlobStorageConnectionAccountKey(String accountKey);

	void setAzureBlobStorageContainerName(String containerName);

	void setAzureBlobStorageConnectionString(String containerName);

}
