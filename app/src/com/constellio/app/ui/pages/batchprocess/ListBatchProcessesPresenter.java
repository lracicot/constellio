package com.constellio.app.ui.pages.batchprocess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.joda.time.Hours;
import org.joda.time.LocalDateTime;

import com.constellio.app.ui.entities.BatchProcessVO;
import com.constellio.app.ui.framework.builders.BatchProcessToVOBuilder;
import com.constellio.app.ui.framework.data.BatchProcessDataProvider;
import com.constellio.app.ui.pages.base.BasePresenter;
import com.constellio.model.entities.CorePermissions;
import com.constellio.model.entities.batchprocess.BatchProcess;
import com.constellio.model.entities.batchprocess.BatchProcessAction;
import com.constellio.model.entities.batchprocess.BatchProcessStatus;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.services.batch.manager.BatchProcessesManager;

public class ListBatchProcessesPresenter extends BasePresenter<ListBatchProcessesView> {
	
	private int secondsSinceLastRefresh = 0;

	private BatchProcessToVOBuilder voBuilder = new BatchProcessToVOBuilder();

	private BatchProcessDataProvider userBatchProcessDataProvider = new BatchProcessDataProvider();
	private BatchProcessDataProvider systemBatchProcessDataProvider = new BatchProcessDataProvider();

	public ListBatchProcessesPresenter(ListBatchProcessesView view) {
		super(view);
		init();
	}
	
	private void init() {
		refreshDataProviders();
		view.setUserBatchProcesses(userBatchProcessDataProvider);

		User currentUser = getCurrentUser();
		if (areSystemBatchProcessesVisible(currentUser)) {
			view.setSystemBatchProcesses(systemBatchProcessDataProvider);
		}
	}
	
	private void refreshDataProviders() {
		userBatchProcessDataProvider.clear();
		systemBatchProcessDataProvider.clear();
		
		User currentUser = getCurrentUser();
		String currentUsername = currentUser.getUsername();
		
		BatchProcessesManager batchProcessesManager = modelLayerFactory.getBatchProcessesManager();
		List<BatchProcess> displayedBatchProcesses = new ArrayList<>();
		
		LocalDateTime oldestAgeDisplayed = new LocalDateTime().minus(Hours.FIVE);
		List<BatchProcess> finishedBatchProcesses = batchProcessesManager.getFinishedBatchProcesses();
		for (BatchProcess batchProcess : finishedBatchProcesses) {
			if (oldestAgeDisplayed.isBefore(batchProcess.getStartDateTime())) {
				displayedBatchProcesses.add(batchProcess);
			}
		}
		
		BatchProcess currentBatchProcess = batchProcessesManager.getCurrentBatchProcess();
		if (currentBatchProcess != null) {
			displayedBatchProcesses.add(currentBatchProcess);
		}
		List<BatchProcess> pendingBatchProcesses = batchProcessesManager.getPendingBatchProcesses();
		displayedBatchProcesses.addAll(pendingBatchProcesses);
		
		String[] titles = {
			"userBatchProcess",	
			"reindex.schemasAlteration",
			"reindex.config ZeConfigCode",
			"reindex.transaction",
			"reindex.transaction ZeTransactionTitle",
			"robot ZeRobotId"
		};
		for (String title : titles) {
			String id = UUID.randomUUID().toString();
			BatchProcessStatus status = BatchProcessStatus.PENDING;
			LocalDateTime requestDateTime = new LocalDateTime();
			LocalDateTime startDateTime = null;
			int handledRecordsCount = 42;
			int totalRecordsCount = 4242;
			BatchProcessAction action = null;
			int errors = 0;
			String query = "zeQuery";
			List<String> records = Collections.emptyList();
			String username = null;
			BatchProcess batchProcess = new BatchProcess(id, status, requestDateTime, startDateTime, handledRecordsCount, totalRecordsCount, errors, action, collection, query, records, username, title);
			displayedBatchProcesses.add(batchProcess);
		}
		
		for (int i = 0; i < displayedBatchProcesses.size(); i++) {
			BatchProcess batchProcess = displayedBatchProcesses.get(i);
			BatchProcessVO batchProcessVO = voBuilder.build(batchProcess);
			String batchProcessUsername = batchProcessVO.getUsername();
			if (currentUsername.equals(batchProcessUsername)) {
				userBatchProcessDataProvider.addBatchProcess(batchProcessVO);
			}
			systemBatchProcessDataProvider.addBatchProcess(batchProcessVO);
		}
		userBatchProcessDataProvider.fireDataRefreshEvent();
		systemBatchProcessDataProvider.fireDataRefreshEvent();
	}

	@Override
	protected boolean hasPageAccess(String params, User user) {
		return true;
	}
	
	private boolean areSystemBatchProcessesVisible(User user) {
		return user.has(CorePermissions.VIEW_SYSTEM_BATCH_PROCESSES).globally();
	}

	public void backgroundViewMonitor() {
		secondsSinceLastRefresh++;
		if (secondsSinceLastRefresh >= 10) {
			refreshDataProviders();
			secondsSinceLastRefresh = 0;
		}
	}

}
