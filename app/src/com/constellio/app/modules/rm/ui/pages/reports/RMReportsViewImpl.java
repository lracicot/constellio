package com.constellio.app.modules.rm.ui.pages.reports;

import com.constellio.app.modules.rm.ui.components.retentionRule.RetentionRuleListAddRemoveAdministrativeUnitLookupField;
import com.constellio.app.modules.rm.wrappers.AdministrativeUnit;
import com.constellio.app.modules.rm.wrappers.Category;
import com.constellio.app.ui.framework.buttons.BaseButton;
import com.constellio.app.ui.framework.buttons.ReportButton;
import com.constellio.app.ui.framework.buttons.WindowButton;
import com.constellio.app.ui.framework.buttons.WindowButton.WindowConfiguration;
import com.constellio.app.ui.framework.components.fields.list.ListAddRemoveRecordLookupField;
import com.constellio.app.ui.framework.components.fields.lookup.LookupRecordField;
import com.constellio.app.ui.framework.data.RecordTextInputDataProvider;
import com.constellio.app.ui.framework.reports.ReportWithCaptionVO;
import com.constellio.app.ui.pages.base.BaseViewImpl;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.entities.schemas.MetadataSchemaType;
import com.constellio.model.entities.schemas.Schemas;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery;
import com.vaadin.data.Property;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.server.ThemeResource;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Component;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.Field;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.OptionGroup;
import com.vaadin.ui.Panel;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.Reindeer;
import com.vaadin.ui.themes.ValoTheme;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

import static com.constellio.app.ui.i18n.i18n.$;

public class RMReportsViewImpl extends BaseViewImpl implements RMReportsView {

	public static final String OK_BUTTON = "seleniumOkButton";
	private final RMNewReportsPresenter presenter;

	public RMReportsViewImpl() {
		presenter = new RMNewReportsPresenter(this);
	}

	public enum Selected {
		All,
		Select
	}

	@Override
	protected String getTitle() {
		return $("RMReportsView.viewTitle");
	}

	@Override
	protected Component buildMainComponent(ViewChangeEvent event) {
		VerticalLayout layout = new VerticalLayout();
		CssLayout panel = new CssLayout();
		layout.addStyleName("view-group");

		for (ReportWithCaptionVO report : presenter.getSupportedReports()) {
			if(presenter.isDetailedClassificationPlan(report.getTitle())) {
				final Field field = new ListAddRemoveRecordLookupField(AdministrativeUnit.SCHEMA_TYPE) {
					@Override
					protected LookupRecordField newAddEditField() {
						LookupRecordField field = new LookupRecordField(
								new RecordTextInputDataProvider(getConstellioFactories(), getSessionContext(),
										Category.SCHEMA_TYPE, null, false, false) {
									@Override
									public LogicalSearchQuery getQuery(User user, String text, int startIndex, int count) {
										LogicalSearchQuery logicalSearchQuery = super.getQuery(user, text, startIndex, count);
										MetadataSchemaType type = getModelLayerFactory().getMetadataSchemasManager()
												.getSchemaTypes(getCurrentCollection()).getSchemaType(schemaTypeCode);
										logicalSearchQuery.setCondition(logicalSearchQuery.getCondition().andWhere(type
												.getAllMetadatas().getMetadataWithLocalCode(Schemas.PATH_PARTS.getLocalCode())).isContaining(Arrays
												.asList("R")));

										return logicalSearchQuery;
									}
								},
								LookupRecordField.getTreeDataProvider(Category.SCHEMA_TYPE, null, false, true, true)); {

								}

						for(ValueChangeListener listener: lookupFieldListenerList) {
							field.addValueChangeListener(listener);
						}
						field.setWidth("100%");
						field.setIgnoreLinkability(ignoreLinkability);
						return field;
					}

					@Override
					protected String getReadOnlyMessage() {
						String readOnlyMessage = super.getReadOnlyMessage();
						if(!StringUtils.isBlank(readOnlyMessage)) {
							return readOnlyMessage;
						} else {
							return super.getReadOnlyMessage();
						}
					}
				} ;


				field.setCaption($("RMReportsViewImpl.category"));

				WindowButton windowButton = getParametersFromUser(field, $("RMReportsViewImpl.allCategory"), $("RMReportsViewImpl.onlyCategory"), report);

				setReportButtonStyle(report.getTitle(), windowButton);
				panel.addComponent(windowButton);
			} else if(presenter.isAdministrativeUnitExcelReport(report.getTitle())) {
				final Field field = new ListAddRemoveRecordLookupField(AdministrativeUnit.SCHEMA_TYPE);

				WindowButton windowButton = getParametersFromUser(field, $("RMReportsViewImpl.allAdministrativeUnit"), $("RMReportsViewImpl.onlyAdministrativeUnit"), report);


				setReportButtonStyle(report.getTitle(), windowButton);
				panel.addComponent(windowButton);
			} else if (presenter.isWithSchemaType(report.getTitle())) {
				String schemaType = presenter.getSchemaTypeValue(report.getTitle());
				WindowButton windowButton = buildLookupButton(schemaType, report);

				setReportButtonStyle(report.getTitle(), windowButton);
				panel.addComponent(windowButton);
			} else {
				ReportButton button = new ReportButton(report, presenter);
				//				setReportButtonStyle(report, button);
				panel.addComponent(button);
			}
		}

		layout.addComponent(panel);
		return layout;
	}

	@Override
	protected ClickListener getBackButtonClickListener() {
		return new ClickListener() {
			@Override
			public void buttonClick(ClickEvent event) {
				presenter.backButtonClicked();
			}
		};
	}

	private WindowButton buildLookupButton(final String schemaType, final ReportWithCaptionVO report) {
		return new WindowButton($(report.getCaption()),
				$(report.getCaption())) {
			@Override
			protected Component buildWindowContent() {

				final Field<?> lookupSchemaType = new LookupRecordField(schemaType);
				lookupSchemaType.setCaption($("search"));
				lookupSchemaType.setId("schemaType");
				lookupSchemaType.addStyleName("schemaType");

				BaseButton okButton = new BaseButton($("Ok")) {
					@Override
					protected void buttonClick(ClickEvent event) {
						presenter.setSchemaTypeValue((String) lookupSchemaType.getValue());
						ReportButton reportButton = new ReportButton(report, presenter);
						reportButton.click();
						getWindow().close();
					}
				};
				okButton.addStyleName(OK_BUTTON);
				okButton.addStyleName(ValoTheme.BUTTON_PRIMARY);

				BaseButton cancelButton = new BaseButton($("cancel")) {
					@Override
					protected void buttonClick(ClickEvent event) {
						getWindow().close();
					}
				};
				cancelButton.addStyleName(ValoTheme.BUTTON_PRIMARY);

				HorizontalLayout horizontalLayout = new HorizontalLayout();
				horizontalLayout.setSpacing(true);
				horizontalLayout.addComponents(okButton, cancelButton);
				horizontalLayout.setWidth("195px");


				HorizontalLayout horizontalLayout2 = new HorizontalLayout();
				horizontalLayout2.setWidth("100%");
				horizontalLayout2.addComponent(horizontalLayout);
				horizontalLayout2.setComponentAlignment(horizontalLayout, Alignment.MIDDLE_CENTER);


				VerticalLayout verticalLayout = new VerticalLayout();
				verticalLayout.addComponents(lookupSchemaType, horizontalLayout2);
				verticalLayout.setSpacing(true);

				return verticalLayout;
			}
		};
	}

	private WindowButton getParametersFromUser(final Field field, final String allCaption, final String onlyCaption, final ReportWithCaptionVO report) {
		WindowButton windowButton = new WindowButton($(report.getCaption()),
				$(report.getCaption()), new WindowConfiguration(true, true, "600px", "500px")) {
			@Override
			protected Component buildWindowContent() {
				VerticalLayout verticalLayout = new VerticalLayout();
				verticalLayout.setWidth("100%");
				verticalLayout.setHeight("100%");

				VerticalLayout verticalLayout2 = new VerticalLayout();
				verticalLayout2.setSpacing(true);

				final OptionGroup optionGroup = new OptionGroup();
				optionGroup.addItem(Selected.All);
				optionGroup.setItemCaption(Selected.All, allCaption);
				optionGroup.addItem(Selected.Select);
				optionGroup.setItemCaption(Selected.Select, onlyCaption);
				optionGroup.setMultiSelect(false);
				verticalLayout2.addComponent(optionGroup);

				final Button okButton = new Button($("Ok"));


				okButton.addStyleName(ValoTheme.BUTTON_PRIMARY);
				Button cancelButton = new Button($("cancel"));
				verticalLayout2.addComponent(field);

				HorizontalLayout horizontalLayout1 = new HorizontalLayout();
				horizontalLayout1.setWidth("195px");
				horizontalLayout1.setSpacing(true);
				horizontalLayout1.addComponent(okButton);
				horizontalLayout1.addComponent(cancelButton);

				optionGroup.addValueChangeListener(new ValueChangeListener() {
					@Override
					public void valueChange(Property.ValueChangeEvent event) {
						Selected selected = (Selected) optionGroup.getValue();

						if(selected == Selected.All) {
							field.setVisible(false);
							field.setRequired(false);
						} else {
							field.setVisible(true);
							field.setRequired(true);
						}
					}
				});
				optionGroup.select(Selected.All);

				okButton.addClickListener(new ClickListener() {
					@Override
					public void buttonClick(ClickEvent event) {
						Selected selected = (Selected) optionGroup.getValue();

						if(selected == Selected.All) {
							ReportButton reportButton = new ReportButton(report, presenter);
							reportButton.click();
							getWindow().close();
						} else {
							if(field.getValue() == null) {
								showErrorMessage($("requiredFieldWithName", "\"" + field.getCaption() + "\""));
							} else {
								ReportButton reportButton = new ReportButton(report, presenter);
								reportButton.setParams(field.getValue());

								reportButton.click();
							}
						}
					}
				});

				cancelButton.addClickListener(new ClickListener() {
					@Override
					public void buttonClick(ClickEvent event) {
						getWindow().close();
					}
				});

				HorizontalLayout horizontalLayout2 = new HorizontalLayout();
				horizontalLayout2.setWidth("100%");
				horizontalLayout2.addComponent(horizontalLayout1);
				horizontalLayout2.setComponentAlignment(horizontalLayout1, Alignment.MIDDLE_CENTER);

				verticalLayout2.addComponent(horizontalLayout2);

				verticalLayout.addComponent(verticalLayout2);

				return verticalLayout;
			}
		};


		return windowButton;
	}

	private void setReportButtonStyle(String report, WindowButton windowButton) {
		String iconPathKey = report + ".icon";
		String iconPath = $(iconPathKey);
		if (!iconPathKey.equals(iconPath)) {
			windowButton.setIcon(new ThemeResource(iconPath));
		}
		windowButton.addStyleName(ValoTheme.BUTTON_ICON_ALIGN_TOP);
		windowButton.addStyleName(ValoTheme.BUTTON_BORDERLESS);
	}
}
