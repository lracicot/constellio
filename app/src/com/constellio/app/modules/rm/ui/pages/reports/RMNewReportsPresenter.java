package com.constellio.app.modules.rm.ui.pages.reports;

import com.constellio.app.extensions.AppLayerCollectionExtensions;
import com.constellio.app.modules.rm.ConstellioRMModule;
import com.constellio.app.modules.rm.constants.RMPermissionsTo;
import com.constellio.app.modules.rm.extensions.api.RMModuleExtensions;
import com.constellio.app.modules.rm.navigation.RMViews;
import com.constellio.app.modules.rm.reports.builders.administration.plan.AdministrativeUnitExcelReportParameters;
import com.constellio.app.modules.rm.reports.builders.administration.plan.AdministrativeUnitReportParameters;
import com.constellio.app.modules.rm.reports.builders.administration.plan.AvailableSpaceReportParameters;
import com.constellio.app.modules.rm.reports.builders.administration.plan.ClassificationReportPlanParameters;
import com.constellio.app.modules.rm.reports.builders.administration.plan.ConservationRulesReportParameters;
import com.constellio.app.modules.rm.reports.builders.administration.plan.UserReportParameters;
import com.constellio.app.modules.rm.reports.factories.ExampleReportWithoutRecordsParameters;
import com.constellio.app.modules.rm.wrappers.AdministrativeUnit;
import com.constellio.app.ui.framework.components.NewReportPresenter;
import com.constellio.app.ui.framework.reports.NewReportWriterFactory;
import com.constellio.app.ui.framework.reports.ReportWithCaptionVO;
import com.constellio.app.ui.pages.base.BasePresenter;
import com.constellio.model.entities.records.wrappers.User;

import java.util.Arrays;
import java.util.List;

import static com.constellio.app.ui.i18n.i18n.$;

public class RMNewReportsPresenter extends BasePresenter<RMReportsView> implements NewReportPresenter {

	private static final boolean BY_ADMINISTRATIVE_UNIT = true;
	private String schemaTypeValue;

	public RMNewReportsPresenter(RMReportsView view) {
		super(view);
	}

	@Override
	public List<ReportWithCaptionVO> getSupportedReports() {
		return Arrays.asList(new ReportWithCaptionVO("Reports.ClassificationPlan", $("Reports.ClassificationPlan")),
				new ReportWithCaptionVO("Reports.DetailedClassificationPlan", $("Reports.DetailedClassificationPlan")),
				new ReportWithCaptionVO("Reports.ClassificationPlanByAdministrativeUnit", $("Reports.ClassificationPlanByAdministrativeUnit")),
				new ReportWithCaptionVO("Reports.ConservationRulesList", $("Reports.ConservationRulesList")),
				new ReportWithCaptionVO("Reports.ConservationRulesListByAdministrativeUnit", $("Reports.ConservationRulesListByAdministrativeUnit")),
				new ReportWithCaptionVO("Reports.AdministrativeUnits", $("Reports.AdministrativeUnits")),
				new ReportWithCaptionVO("Reports.AdministrativeUnitsAndUsers", $("Reports.AdministrativeUnitsAndUsers")),
				new ReportWithCaptionVO("Reports.Users", $("Reports.Users")),
				new ReportWithCaptionVO("Reports.AvailableSpaceReport", $("Reports.AvailableSpaceReport")),
				new ReportWithCaptionVO("Reports.AvailableSpaceReportAll", $("Reports.AvailableSpaceReportAll")),
				new ReportWithCaptionVO("Reports.administrativeUnitExcelFormat", $("Reports.administrativeUnitExcelFormat")));
	}

	public NewReportWriterFactory getReport(String report) {
		AppLayerCollectionExtensions appCollectionExtentions = appLayerFactory.getExtensions().forCollection(collection);
		RMModuleExtensions rmModuleExtensions = appCollectionExtentions.forModule(ConstellioRMModule.ID);

		switch (report) {
			case "Reports.fakeReport2":
				return rmModuleExtensions.getReportBuilderFactories().exampleWithoutRecordsBuilderFactory.getValue();
			case "Reports.ClassificationPlan":
				return rmModuleExtensions.getReportBuilderFactories().classifcationPlanRecordBuilderFactory.getValue();
			case "Reports.DetailedClassificationPlan":
				return rmModuleExtensions.getReportBuilderFactories().classifcationPlanRecordBuilderFactory.getValue();
			case "Reports.ConservationRulesList":
				return rmModuleExtensions.getReportBuilderFactories().conservationRulesRecordBuilderFactory.getValue();
			case "Reports.ConservationRulesListByAdministrativeUnit":
				return rmModuleExtensions.getReportBuilderFactories().conservationRulesRecordBuilderFactory.getValue();
			case "Reports.AdministrativeUnits":
				return rmModuleExtensions.getReportBuilderFactories().administrativeUnitRecordBuilderFactory.getValue();
			case "Reports.AdministrativeUnitsAndUsers":
				return rmModuleExtensions.getReportBuilderFactories().administrativeUnitRecordBuilderFactory.getValue();
			case "Reports.Users":
				return rmModuleExtensions.getReportBuilderFactories().userRecordBuilderFactory.getValue();
			case "Reports.ClassificationPlanByAdministrativeUnit":
				return rmModuleExtensions.getReportBuilderFactories().classifcationPlanRecordBuilderFactory.getValue();
			case "Reports.AvailableSpaceReport":
				return rmModuleExtensions.getReportBuilderFactories().availableSpaceBuilderFactory.getValue();
			case "Reports.AvailableSpaceReportAll":
				return rmModuleExtensions.getReportBuilderFactories().availableSpaceBuilderFactory.getValue();
			case "Reports.administrativeUnitExcelFormat":
				return 	rmModuleExtensions.getReportBuilderFactories().administrativeUnitExcelBuilderFactory.getValue();

		}

		throw new RuntimeException("BUG: Unknown report: " + report);

	}

	@Override
	public Object getReportParameters(String report) {
		switch (report) {
			case "Reports.fakeReport2":
				return new ExampleReportWithoutRecordsParameters();
			case "Reports.ClassificationPlan":
				return new ClassificationReportPlanParameters(false, null);
			case "Reports.DetailedClassificationPlan":
				return new ClassificationReportPlanParameters(true, null);
			case "Reports.ConservationRulesList":
				return new ConservationRulesReportParameters(false, null);
			case "Reports.ConservationRulesListByAdministrativeUnit":
				return new ConservationRulesReportParameters(true, null);
			case "Reports.AdministrativeUnits":
				return new AdministrativeUnitReportParameters(false);
			case "Reports.AdministrativeUnitsAndUsers":
				return new AdministrativeUnitReportParameters(true);
			case "Reports.Users":
				return new UserReportParameters();
			case "Reports.ClassificationPlanByAdministrativeUnit":
				return new ClassificationReportPlanParameters(false, schemaTypeValue);
			case "Reports.AvailableSpaceReport":
				return new AvailableSpaceReportParameters(false);
			case "Reports.AvailableSpaceReportAll":
				return new AvailableSpaceReportParameters(true);
			case "Reports.administrativeUnitExcelFormat":
				return 	new AdministrativeUnitExcelReportParameters();
		}

		throw new RuntimeException("BUG: Unknown report: " + report);
	}

	public boolean isWithSchemaType(String report) {
		switch (report) {
			case "Reports.ConservationRulesListByAdministrativeUnit":
			case "Reports.ClassificationPlanByAdministrativeUnit":
				return true;
			default:
				return false;
		}
	}

	public String getSchemaTypeValue(String report) {
		switch (report) {
			case "Reports.ConservationRulesListByAdministrativeUnit":
			case "Reports.ClassificationPlanByAdministrativeUnit":
				return AdministrativeUnit.SCHEMA_TYPE;
			default:
				return null;
		}
	}

	public String getSchemaTypeValue() {
		return schemaTypeValue;
	}

	public void setSchemaTypeValue(String schemaTypeValue) {
		this.schemaTypeValue = schemaTypeValue;
	}

	public void backButtonClicked() {
		view.navigate().to(RMViews.class).archiveManagement();
	}

	@Override
	protected boolean hasPageAccess(String params, User user) {
		return user.has(RMPermissionsTo.MANAGE_REPORTS).onSomething();
	}
}
