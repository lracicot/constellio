package com.constellio.app.ui.pages.management.schemas;

import static com.constellio.app.api.extensions.GenericRecordPageExtension.*;
import static com.constellio.app.ui.i18n.i18n.$;

import com.constellio.app.ui.entities.MetadataSchemaTypeVO;
import com.constellio.app.ui.framework.buttons.DisplayButton;
import com.constellio.app.ui.framework.buttons.ListMetadataGroupButton;
import com.constellio.app.ui.framework.buttons.ReportDisplayButton;
import com.constellio.app.ui.framework.components.TabWithTable;
import com.constellio.app.ui.framework.containers.ButtonsContainer;
import com.constellio.app.ui.framework.containers.ButtonsContainer.ContainerButton;
import com.constellio.app.ui.framework.containers.SchemaTypeVOLazyContainer;
import com.constellio.app.ui.framework.data.SchemaTypeVODataProvider;
import com.constellio.app.ui.pages.base.BaseViewImpl;
import com.constellio.app.ui.pages.management.valueDomains.ListValueDomainViewImpl;
import com.vaadin.data.Container;
import com.vaadin.event.ItemClickEvent;
import com.vaadin.event.ItemClickEvent.ItemClickListener;
import com.vaadin.navigator.ViewChangeListener;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.ui.*;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;

import java.util.ArrayList;
import java.util.List;

public class ListSchemaTypeViewImpl extends BaseViewImpl implements ListSchemaTypeView, ClickListener {
	ListSchemaTypePresenter presenter;
	public static final String TYPE_TABLE = "types";
	private TabSheet sheet = new TabSheet();
	private List<TabWithTable> tabs = new ArrayList<>();

	public ListSchemaTypeViewImpl() {
		this.presenter = new ListSchemaTypePresenter(this);
	}

	@Override
	protected boolean isFullWidthIfActionMenuAbsent() {
		return true;
	}

	@Override
	protected String getTitle() {
		return $("ListSchemaTypeView.viewTitle");
	}

	@Override
	protected ClickListener getBackButtonClickListener() {
		return this;
	}

	@Override
	protected Component buildMainComponent(ViewChangeEvent event) {
		VerticalLayout viewLayout = new VerticalLayout();
		viewLayout.setSizeFull();
		addTab(TAXONOMY_TAB, $("ListSchemaTypeView.taxonomyTabCaption"));
		addTab(DDV_TAB, $("ListSchemaTypeView.ddvTabCaption"));
		addTab(OTHERS_TAB, $("ListSchemaTypeView.othersTabCaption"));

		viewLayout.addComponents(sheet);
		return viewLayout;
	}

	public void addTab(final String id, String caption) {
		boolean alreadyExists = false;
		for(TabWithTable tab: tabs) {
			if(tab.getId().equals(id)) {
				alreadyExists = true;
				break;
			}
		}

		if(!alreadyExists) {
			TabWithTable tab = new TabWithTable(id) {
				@Override
				public Table buildTable() {
					return ListSchemaTypeViewImpl.this.buildTable(presenter.getDataProvider(id));
				}
			};
			tabs.add(tab);
			sheet.addTab(tab.getTabLayout(), caption);
		}
	}

	private Table buildTable(final SchemaTypeVODataProvider dataProvider) {
//		final SchemaTypeVODataProvider dataProvider = presenter.getDataProvider();

		Container typeContainer = new SchemaTypeVOLazyContainer(dataProvider);
		ButtonsContainer buttonsContainer = new ButtonsContainer(typeContainer, "buttons");
		buttonsContainer.addButton(new ContainerButton() {
			@Override
			protected Button newButtonInstance(final Object itemId, ButtonsContainer<?> container) {
				return new DisplayButton() {
					@Override
					protected void buttonClick(ClickEvent event) {
						Integer index = (Integer) itemId;
						MetadataSchemaTypeVO entity = dataProvider.getSchemaTypeVO(index);
						presenter.editButtonClicked(entity);
					}
				};
			}
		});

		buttonsContainer.addButton(new ContainerButton() {
			@Override
			protected Button newButtonInstance(final Object itemId, ButtonsContainer<?> container) {
				return new ListMetadataGroupButton() {
					@Override
					protected void buttonClick(ClickEvent event) {
						Integer index = (Integer) itemId;
						MetadataSchemaTypeVO entity = dataProvider.getSchemaTypeVO(index);
						presenter.listGroupButtonClicked(entity);
					}
				};
			}
		});

		typeContainer = buttonsContainer;

		Table table = new Table($("ListSchemaTypeView.tableTitle", typeContainer.size()), typeContainer);
		table.setSizeFull();
		table.setPageLength(Math.min(15, typeContainer.size()));
		table.setColumnHeader("buttons", "");
		table.setColumnHeader("caption", $("ListSchemaTypeView.caption"));
		table.setColumnExpandRatio("caption", 1);
		table.addStyleName(TYPE_TABLE);
		table.addItemClickListener(new ItemClickListener() {
			@Override
			public void itemClick(ItemClickEvent event) {
				Integer index = (Integer) event.getItemId();
				MetadataSchemaTypeVO entity = dataProvider.getSchemaTypeVO(index);
				presenter.editButtonClicked(entity);
			}
		});
		table.setSortContainerPropertyId(SchemaTypeVOLazyContainer.LABEL);

		return table;
	}

	@Override
	public void buttonClick(ClickEvent event) {
		presenter.backButtonClicked();
	}


}
