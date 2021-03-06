package com.constellio.app.ui.framework.buttons;

import com.constellio.app.modules.rm.reports.builders.administration.plan.ConservationRulesReportParameters;
import com.constellio.app.modules.rm.ui.pages.reports.RMNewReportsPresenter;
import com.constellio.app.ui.framework.components.NewReportPresenter;
import com.constellio.app.ui.framework.components.ReportPresenter;
import com.constellio.app.ui.framework.components.ReportViewer;
import com.constellio.app.ui.framework.reports.NewReportWriterFactory;
import com.constellio.app.ui.framework.reports.ReportWithCaptionVO;
import com.constellio.app.ui.framework.reports.ReportWriter;
import com.vaadin.server.ThemeResource;
import com.vaadin.ui.Component;
import com.vaadin.ui.Label;
import com.vaadin.ui.themes.ValoTheme;

import static com.constellio.app.ui.i18n.i18n.$;

public class ReportButton extends WindowButton {
	private final String report;
	//	private final RMReportsPresenter presenter;
	private final ReportPresenter presenter;
	private final NewReportPresenter newPresenter;
	private Object params;

	public ReportButton(ReportWithCaptionVO report, ReportPresenter presenter) {
		super(report.getCaption(), report.getCaption(), new WindowConfiguration(true, true, "75%", "90%"));
		this.report = report.getTitle();
		this.presenter = presenter;
		this.newPresenter = null;

		String iconPathKey = report.getTitle() + ".icon";
		String iconPath = $(iconPathKey);
		if (!iconPathKey.equals(iconPath)) {
			setIcon(new ThemeResource(iconPath));
		}
		addStyleName(ValoTheme.BUTTON_ICON_ALIGN_TOP);
		addStyleName(ValoTheme.BUTTON_BORDERLESS);
	}

	public ReportButton(ReportWithCaptionVO report, NewReportPresenter presenter) {
		super(report.getCaption(), report.getCaption(), new WindowConfiguration(true, true, "75%", "90%"));
		this.report = report.getTitle();
		this.presenter = null;
		this.newPresenter = presenter;

		String iconPathKey = report.getTitle() + ".icon";
		String iconPath = $(iconPathKey);
		if (!iconPathKey.equals(iconPath)) {
			setIcon(new ThemeResource(iconPath));
		}
		addStyleName(ValoTheme.BUTTON_ICON_ALIGN_TOP);
		addStyleName(ValoTheme.BUTTON_BORDERLESS);
	}

	@Override
	protected Component buildWindowContent() {
		if (presenter != null) {
			return new ReportViewer(presenter.getReport(report).getReportBuilder(newPresenter.getReportParameters(report)),
					presenter.getReport(report).getFilename(newPresenter.getReportParameters(report)));
		} else {
			NewReportWriterFactory<Object> reportBuilderFactory = (NewReportWriterFactory<Object>) newPresenter
					.getReport(report);

			if (reportBuilderFactory == null) {
				return new Label($("ReportViewer.noReportFactoryAvailable"));
			} else {

				Object parameters = newPresenter.getReportParameters(report);

				if (parameters instanceof ConservationRulesReportParameters && newPresenter instanceof RMNewReportsPresenter) {
					((ConservationRulesReportParameters) parameters).setAdministrativeUnit(((RMNewReportsPresenter) newPresenter).getSchemaTypeValue());
				}

				ReportWriter reportWriter = reportBuilderFactory.getReportBuilder(parameters);


				return new ReportViewer(reportWriter, reportBuilderFactory.getFilename(parameters));
			}
		}
	}

	public Object getParams() {
		return params;
	}

	public void setParams(Object params) {
		this.params = params;
	}
}
