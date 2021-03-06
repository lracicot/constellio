package com.constellio.app.modules.rm.ui.pages.retentionRule;

import com.constellio.app.modules.rm.constants.RMPermissionsTo;
import com.constellio.app.modules.rm.navigation.RMViews;
import com.constellio.app.modules.rm.wrappers.RetentionRule;
import com.constellio.app.ui.entities.MetadataSchemaVO;
import com.constellio.app.ui.entities.RecordVO;
import com.constellio.app.ui.entities.RecordVO.VIEW_MODE;
import com.constellio.app.ui.framework.builders.MetadataSchemaToVOBuilder;
import com.constellio.app.ui.framework.builders.RecordToVOBuilder;
import com.constellio.app.ui.framework.data.RecordVODataProvider;
import com.constellio.app.ui.pages.base.SingleSchemaBasePresenter;
import com.constellio.app.ui.util.MessageUtils;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.entities.schemas.MetadataSchema;
import com.constellio.model.entities.schemas.Schemas;
import com.constellio.model.frameworks.validation.ValidationErrors;
import com.constellio.model.services.records.RecordServices;
import com.constellio.model.services.search.StatusFilter;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery;

import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.from;

public class SearchRetentionRulesPresenter extends SingleSchemaBasePresenter<SearchRetentionRulesView> {
	private RecordToVOBuilder voBuilder = new RecordToVOBuilder();
	private MetadataSchemaVO schemaVO;
	private String queryExpression;
	private RecordVODataProvider dataProvider;

	public SearchRetentionRulesPresenter(SearchRetentionRulesView view) {
		super(view, RetentionRule.DEFAULT_SCHEMA);
		schemaVO = new MetadataSchemaToVOBuilder().build(defaultSchema(), VIEW_MODE.TABLE, view.getSessionContext());
	}

	public void forParams(String parameters) {
		String[] splitParams = parameters.split("/");
		queryExpression = splitParams[0];
	}

	public void viewAssembled() {
		dataProvider = new RecordVODataProvider(schemaVO, voBuilder, modelLayerFactory, view.getSessionContext()) {
			@Override
			public LogicalSearchQuery getQuery() {
				MetadataSchema schema = defaultSchema();
				return new LogicalSearchQuery(from(schema).returnAll())
						.filteredByStatus(StatusFilter.ACTIVES)
						.setFreeTextQuery(queryExpression)
						.sortAsc(schema.getMetadata(RetentionRule.CODE));
			}
		};
		view.setDataProvider(dataProvider);
	}

	public boolean userHaveManageRetentionRulePermission() {
		return getCurrentUser().has(RMPermissionsTo.MANAGE_RETENTIONRULE).onSomething();
	}

	public void backButtonClicked() {
		view.navigate().to(RMViews.class).listRetentionRules();
	}

	public void displayButtonClicked(RecordVO recordVO) {
		view.navigate().to(RMViews.class).displayRetentionRule(recordVO.getId());
	}

	public void editButtonClicked(RecordVO recordVO) {
		view.navigate().to(RMViews.class).editRetentionRule(recordVO.getId());
	}

	public void deleteButtonClicked(RecordVO recordVO) {
		ValidationErrors validationErrors = validateDeletable(recordVO);
		if (validationErrors.isEmpty()) {
			Record record = getRecord(recordVO.getId());
			delete(record, false);
			view.navigate().to(RMViews.class).listRetentionRules();
		} else {
			MessageUtils.getCannotDeleteWindow(validationErrors).openWindow();
		}
	}

	@Override
	protected boolean hasPageAccess(String params, User user) {
		return user.has(RMPermissionsTo.MANAGE_RETENTIONRULE).onSomething() || user.has(RMPermissionsTo.CONSULT_RETENTIONRULE).onSomething();
	}

	@Override
	public ValidationErrors validateDeletable(RecordVO entity) {
		RecordServices recordService = modelLayerFactory.newRecordServices();
		Record record = getRecord(entity.getId());
		User user = getCurrentUser();
		return recordService.validateLogicallyDeletable(record, user);
	}

	public String getDefaultOrderField() {
		return Schemas.CODE.getLocalCode();
	}

	public void search(String freeText) {
		view.navigate().to(RMViews.class).retentionRuleSearch(freeText);
	}

	public String getQueryExpression() {
		return queryExpression;
	}

	public RecordVODataProvider getDataProvider() {
		return dataProvider;
	}
}
