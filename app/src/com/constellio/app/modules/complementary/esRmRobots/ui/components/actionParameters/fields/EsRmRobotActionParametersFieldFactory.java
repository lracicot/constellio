package com.constellio.app.modules.complementary.esRmRobots.ui.components.actionParameters.fields;

import com.constellio.app.modules.complementary.esRmRobots.model.ClassifyConnectorFolderDirectlyInThePlanActionParameters;
import com.constellio.app.modules.complementary.esRmRobots.model.ClassifyConnectorFolderInTaxonomyActionParameters;
import com.constellio.app.modules.complementary.esRmRobots.ui.components.actionParameters.fields.category.ActionParametersCategoryField;
import com.constellio.app.modules.complementary.esRmRobots.ui.components.actionParameters.fields.category.ActionParametersCategoryFieldImpl;
import com.constellio.app.modules.complementary.esRmRobots.ui.components.actionParameters.fields.retentionRule.ActionParametersRetentionRuleField;
import com.constellio.app.modules.complementary.esRmRobots.ui.components.actionParameters.fields.retentionRule.ActionParametersRetentionRuleFieldImpl;
import com.constellio.app.services.factories.ConstellioFactories;
import com.constellio.app.ui.application.ConstellioUI;
import com.constellio.app.ui.entities.MetadataVO;
import com.constellio.app.ui.entities.RecordVO;
import com.constellio.app.ui.framework.components.RecordFieldFactory;
import com.constellio.app.ui.framework.components.fields.lookup.LookupRecordField;
import com.constellio.app.ui.pages.base.SessionContext;
import com.vaadin.ui.Field;

import java.util.Arrays;

import static com.constellio.app.modules.complementary.esRmRobots.model.ClassifyConnectorFolderDirectlyInThePlanActionParameters.DEFAULT_UNIFORM_SUBDIVISION;

public class EsRmRobotActionParametersFieldFactory extends RecordFieldFactory implements EsRmRobotActionParametersFields {
	
	private ActionParametersCategoryFieldImpl categoryField;

	private ActionParametersRetentionRuleFieldImpl retentionRuleField;

	private LookupRecordField uniformSubdivisionField;

	private EsRmRobotActionParametersPresenter presenter;
	
	private static final String[] CUSTOM_FIELDS = {
			ClassifyConnectorFolderDirectlyInThePlanActionParameters.DEFAULT_CATEGORY,
			ClassifyConnectorFolderInTaxonomyActionParameters.DEFAULT_CATEGORY,
			ClassifyConnectorFolderDirectlyInThePlanActionParameters.DEFAULT_RETENTION_RULE,	
			ClassifyConnectorFolderInTaxonomyActionParameters.DEFAULT_RETENTION_RULE,
	};

	public EsRmRobotActionParametersFieldFactory() {
		this.presenter = new EsRmRobotActionParametersPresenter(this);
	}
	
	@Override
	public Field<?> build(RecordVO recordVO, MetadataVO metadataVO) {
		Field<?> field;
		if(DEFAULT_UNIFORM_SUBDIVISION.equals(metadataVO.getLocalCode()) && !presenter.areUniformSubdivisionsEnabled()) {
			return null;
		}

		String code = MetadataVO.getCodeWithoutPrefix(metadataVO.getCode());
		if (Arrays.asList(CUSTOM_FIELDS).contains(code)) {
			if (categoryField == null) {
				categoryField = new ActionParametersCategoryFieldImpl();
				retentionRuleField = new ActionParametersRetentionRuleFieldImpl();
				presenter.rmFieldsCreated();
			}
			if (ClassifyConnectorFolderDirectlyInThePlanActionParameters.DEFAULT_CATEGORY.equals(code) || 
					ClassifyConnectorFolderInTaxonomyActionParameters.DEFAULT_CATEGORY.equals(code)) {
				field = categoryField;
			} else {
				field = retentionRuleField;
			}
			super.postBuild(field, recordVO, metadataVO);
		} else {
			if(code.equals(DEFAULT_UNIFORM_SUBDIVISION)) {
				uniformSubdivisionField = (LookupRecordField) super.build(recordVO, metadataVO);
				presenter.subdivisionFieldCreated();
				return uniformSubdivisionField;
			}
			field = super.build(recordVO, metadataVO);
		}

		return field;
	}

	@Override
	public SessionContext getSessionContext() {
		return ConstellioUI.getCurrentSessionContext();
	}

	@Override
	public ConstellioFactories getConstellioFactories() {
		return ConstellioUI.getCurrent().getConstellioFactories();
	}

	@Override
	public ActionParametersCategoryField getCategoryField() {
		return categoryField;
	}

	@Override
	public ActionParametersRetentionRuleField getRetentionRuleField() {
		return retentionRuleField;
	}

	@Override
	public LookupRecordField getUniformSubdivision() {
		return uniformSubdivisionField;
	}

}
