package com.constellio.model.services.background;

import com.constellio.data.conf.FoldersLocatorMode;
import com.constellio.data.dao.managers.StatefulService;
import com.constellio.data.threads.BackgroundThreadConfiguration;
import com.constellio.data.threads.BackgroundThreadExceptionHandling;
import com.constellio.data.threads.BackgroundThreadsManager;
import com.constellio.model.conf.ModelLayerConfiguration;
import com.constellio.model.services.factories.ModelLayerFactory;
import org.apache.commons.lang3.SystemUtils;
import org.joda.time.Duration;

import static com.constellio.data.threads.BackgroundThreadConfiguration.repeatingAction;
import static com.constellio.data.threads.BackgroundThreadExceptionHandling.CONTINUE;
import static org.joda.time.Duration.standardHours;
import static org.joda.time.Duration.standardMinutes;
import static org.joda.time.Duration.standardSeconds;

public class ModelLayerBackgroundThreadsManager implements StatefulService {

	public static Duration FLUSH_EVENTS_EVERY_DURATION = Duration.standardSeconds(15);

	ModelLayerFactory modelLayerFactory;
	BackgroundThreadsManager backgroundThreadsManager;
	RecordsReindexingBackgroundAction recordsReindexingBackgroundAction;
	TemporaryRecordsDeletionBackgroundAction temporaryRecordsDeletionBackgroundAction;
	AuthorizationWithTimeRangeTokenUpdateBackgroundAction authorizationWithTimeRangeTokenUpdateBackgroundAction;
	FlushRecordsBackgroundAction flushRecordsBackgroundAction;
	TemporaryFolderCleanerBackgroundAction temporaryFolderCleanerBackgroundAction;
	BuildRecordIdListAndSortValuesBackgroundAction buildRecordIdListBackgroundAction;
	RefreshSortValuesBackgroundAction refreshSortValuesBackgroundAction;
	IOServiceTemporaryFolderCleanerBackgroundAction ioServiceTemporaryFolderCleanerBackgroundAction;
	FlushOldEmailToSend flushOldEmailToSend;
	ExpireExternalAccessUrlsBackgroundAction expireExternalAccessUrls;

	public ModelLayerBackgroundThreadsManager(ModelLayerFactory modelLayerFactory) {
		this.modelLayerFactory = modelLayerFactory;
		this.backgroundThreadsManager = modelLayerFactory.getDataLayerFactory().getBackgroundThreadsManager();
	}

	@Override
	public void initialize() {
		recordsReindexingBackgroundAction = new RecordsReindexingBackgroundAction(modelLayerFactory);
		backgroundThreadsManager.configure(repeatingAction("recordsReindexingBackgroundAction",
				recordsReindexingBackgroundAction)
				.executedEvery(standardSeconds(5)).handlingExceptionWith(CONTINUE).usingPermits(4));

		ModelLayerConfiguration configuration = modelLayerFactory.getConfiguration();
		backgroundThreadsManager.configure(BackgroundThreadConfiguration.repeatingAction("removeTimedOutTokens", new Runnable() {
			@Override
			public void run() {
				modelLayerFactory.newUserServices().removeTimedOutTokens();
			}
		}).handlingExceptionWith(BackgroundThreadExceptionHandling.CONTINUE)
				.executedEvery(configuration.getTokenRemovalThreadDelayBetweenChecks()));

		temporaryRecordsDeletionBackgroundAction = new TemporaryRecordsDeletionBackgroundAction(modelLayerFactory);
		backgroundThreadsManager.configure(repeatingAction("temporaryRecordsDeletionBackgroundAction",
				temporaryRecordsDeletionBackgroundAction)
				.executedEvery(standardHours(12)).handlingExceptionWith(CONTINUE));

		authorizationWithTimeRangeTokenUpdateBackgroundAction = new AuthorizationWithTimeRangeTokenUpdateBackgroundAction(
				modelLayerFactory);
		backgroundThreadsManager.configure(repeatingAction("authorizationWithTimeRangeTokenUpdateBackgroundAction",
				authorizationWithTimeRangeTokenUpdateBackgroundAction)
				.executedEvery(standardHours(6)).handlingExceptionWith(CONTINUE));

		backgroundThreadsManager.configure(repeatingAction("flushEvents", new FlushEventsBackgroundAction(modelLayerFactory))
				.executedEvery(FLUSH_EVENTS_EVERY_DURATION).handlingExceptionWith(CONTINUE).runningOnAllInstances());

		flushRecordsBackgroundAction = new FlushRecordsBackgroundAction(modelLayerFactory);
		backgroundThreadsManager.configure(repeatingAction("flushRecords", flushRecordsBackgroundAction)
				.executedEvery(standardMinutes(2)).handlingExceptionWith(CONTINUE).runningOnAllInstances());

		if (modelLayerFactory.getFoldersLocator().getFoldersLocatorMode() == FoldersLocatorMode.WRAPPER && SystemUtils.IS_OS_LINUX) {
			temporaryFolderCleanerBackgroundAction = new TemporaryFolderCleanerBackgroundAction();
			backgroundThreadsManager.configure(repeatingAction("TmpFilesDelete", temporaryFolderCleanerBackgroundAction)
					.executedEvery(standardMinutes(5)).handlingExceptionWith(CONTINUE).runningOnAllInstances());
		}

		ioServiceTemporaryFolderCleanerBackgroundAction = new IOServiceTemporaryFolderCleanerBackgroundAction((modelLayerFactory.getDataLayerFactory().getDataLayerConfiguration().getTempFolder()));
		backgroundThreadsManager.configure(repeatingAction("IOServiceTmpFilesDelete", ioServiceTemporaryFolderCleanerBackgroundAction)
				.executedEvery(standardHours(1)).handlingExceptionWith(CONTINUE).runningOnAllInstances());


		flushOldEmailToSend = new FlushOldEmailToSend(modelLayerFactory);
		backgroundThreadsManager.configure(repeatingAction("flushOldEmail", flushOldEmailToSend)
				.executedEvery(standardHours(3)).handlingExceptionWith(CONTINUE));

		buildRecordIdListBackgroundAction = new BuildRecordIdListAndSortValuesBackgroundAction(modelLayerFactory);
		backgroundThreadsManager.configure(repeatingAction("buildRecordIdAndSortValuesListsInVault", buildRecordIdListBackgroundAction)
				.executedEvery(standardHours(1)).runningOnAllInstances().handlingExceptionWith(CONTINUE));

		refreshSortValuesBackgroundAction = new RefreshSortValuesBackgroundAction(modelLayerFactory);
		backgroundThreadsManager.configure(repeatingAction("refreshSortValues", refreshSortValuesBackgroundAction)
				.executedEvery(standardHours(1)).runningOnAllInstances().handlingExceptionWith(CONTINUE));

		expireExternalAccessUrls = new ExpireExternalAccessUrlsBackgroundAction(modelLayerFactory);
		backgroundThreadsManager.configure(repeatingAction("expireExternalAccessUrls", expireExternalAccessUrls)
				.executedEvery(standardHours(1)).handlingExceptionWith(CONTINUE));

		//Disabled for the moment
		//		eventService = new EventService(modelLayerFactory);
		//		backgroundThreadsManager.configure(repeatingAction("eventServiceArchiveEventsAndDeleteFromSolr", eventService
		//		).executedEvery(Duration.standardHours(3)).handlingExceptionWith(CONTINUE));
	}

	@Override
	public void close() {

	}

	public RecordsReindexingBackgroundAction getRecordsReindexingBackgroundAction() {
		return recordsReindexingBackgroundAction;
	}

	public TemporaryRecordsDeletionBackgroundAction getTemporaryRecordsDeletionBackgroundAction() {
		return temporaryRecordsDeletionBackgroundAction;
	}

	public void configureBackgroundThreadConfiguration(BackgroundThreadConfiguration configuration) {
		backgroundThreadsManager.configure(configuration);
	}

	public AuthorizationWithTimeRangeTokenUpdateBackgroundAction getAuthorizationWithTimeRangeTokenUpdateBackgroundAction() {
		return authorizationWithTimeRangeTokenUpdateBackgroundAction;
	}

	public ExpireExternalAccessUrlsBackgroundAction getExpireExternalAccessUrls() {
		return expireExternalAccessUrls;
	}
}
