package com.constellio.app.ui.pages.management.schemas.display.report;

import com.constellio.app.modules.rm.services.RMSchemasRecordsServices;
import com.constellio.app.services.schemasDisplay.SchemaDisplayUtils;
import com.constellio.app.ui.entities.MetadataVO;
import com.constellio.app.ui.entities.ReportVO;
import com.constellio.app.ui.framework.builders.MetadataToVOBuilder;
import com.constellio.app.ui.framework.builders.ReportToVOBuilder;
import com.constellio.app.ui.framework.data.MetadataVODataProvider;
import com.constellio.app.ui.pages.base.BasePresenter;
import com.constellio.model.entities.CorePermissions;
import com.constellio.model.entities.Language;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.records.wrappers.Report;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.entities.records.wrappers.structure.ReportedMetadata;
import com.constellio.model.entities.schemas.Metadata;
import com.constellio.model.entities.schemas.MetadataSchema;
import com.constellio.model.entities.schemas.MetadataSchemaType;
import com.constellio.model.entities.schemas.Schemas;
import com.constellio.model.services.reports.ReportServices;
import com.constellio.model.services.schemas.MetadataSchemasManager;
import com.constellio.model.services.search.SearchServices;
import com.constellio.model.services.search.query.logical.condition.LogicalSearchCondition;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.from;
import static java.util.Arrays.asList;

public class ReportDisplayConfigPresenter extends BasePresenter<ReportConfigurationView> {

	private Map<String, String> parameters;

	private boolean isAddMode = true;

	private ReportVO report;

	public ReportDisplayConfigPresenter(ReportConfigurationView view) {
		super(view);
	}

	@Override
	protected boolean hasPageAccess(String params, User user) {
		return user.has(CorePermissions.MANAGE_EXCEL_REPORT).globally();
	}

	public MetadataVODataProvider getDataProvider() {
		return new MetadataVODataProvider(new MetadataToVOBuilder(), modelLayerFactory, collection, getSchemaTypeCode()) {
			@Override
			public List<MetadataVO> buildList() {
				Set<String> metadataLocalCodeSet = new HashSet<>();
				List<MetadataVO> schemaVOs = new ArrayList<>();
				MetadataSchemaType type = schemaType(getSchemaTypeCode());
				if (type != null) {
					List<Metadata> metadataList = type.getAllMetadatas();
					Collections.sort(metadataList, SchemaDisplayUtils.getMetadataLabelComparator(view.getSessionContext()));
					for (Metadata meta : metadataList) {
						if ((!meta.isSystemReserved() || isSystemReservedAllowedInReport(meta)) && metadataLocalCodeSet.add(meta.getLocalCode())) {
							MetadataVO metadataVO = voBuilder.build(meta, view.getSessionContext());
							if (AllowedMetadataUtil.isAllowedMetadata(metadataVO)) {
								schemaVOs.add(metadataVO);
							}
						}
					}
				}
				return schemaVOs;
			}
		};
	}

	public boolean isSystemReservedAllowedInReport(Metadata meta) {
		List<String> allowedMetadatas = new ArrayList<>(asList(Schemas.IDENTIFIER.getLocalCode(), Schemas.LEGACY_ID.getLocalCode(),
				Schemas.CREATED_ON.getLocalCode(), Schemas.CREATED_BY.getLocalCode(),
				Schemas.MODIFIED_ON.getLocalCode(), Schemas.MODIFIED_BY.getLocalCode()
		));
		allowedMetadatas.addAll(modelLayerFactory.getExtensions().forCollection(collection).getAllowedSystemReservedMetadatasForExcelReport(getSchemaTypeCode()));
		return allowedMetadatas.contains(meta.getLocalCode());
	}

	private String getSelectedReport() {
		return view.getSelectedReport();
	}

	public void setParameters(Map<String, String> params) {
		this.parameters = params;
		checkForId();
	}

	public void saveButtonClicked(List<MetadataVO> metadataVOs) {
		ReportServices reportServices = new ReportServices(modelLayerFactory, collection);
		String reportTile = getSelectedReport();
		String schemaTypeCode = getSchemaTypeCode();
		Report newReport;
		if (report == null) {
			MetadataSchema reportSchema = schemaType(Report.SCHEMA_TYPE).getDefaultSchema();
			Record newReportRecord = modelLayerFactory.newRecordServices().newRecordWithSchema(reportSchema);
			newReport = new Report(newReportRecord, types());
			newReport.setSchemaTypeCode(schemaTypeCode);
		} else {
			newReport = reportServices.getReport(report.getSchemaTypeCode(), report.getTitle());
		}
		newReport.setColumnsCount(metadataVOs.size());
		newReport.setLinesCount(1);
		newReport.setTitle(reportTile);
		List<ReportedMetadata> reportedMetadataList = buildReportedMetadataList(metadataVOs);
		newReport.setReportedMetadata(reportedMetadataList);
		reportServices.saveReport(getCurrentUser(), newReport);
		view.navigate().to().previousView();
	}

	private List<ReportedMetadata> buildReportedMetadataList(List<MetadataVO> metadataVOs) {
		List<ReportedMetadata> returnList = new ArrayList<>();
		for (int i = 0; i < metadataVOs.size(); i++) {
			MetadataVO metadataVO = metadataVOs.get(i);
			returnList.add(new ReportedMetadata(metadataVO.getCode(), i));
		}
		return returnList;
	}

	public void deleteButtonClicked() {
		ReportServices reportServices = new ReportServices(modelLayerFactory, collection);
		String schemaTypeCode = getSchemaTypeCode();
		String reportTile = getSelectedReport();
		Report report = reportServices.getReport(schemaTypeCode, reportTile);
		reportServices.deleteReport(getCurrentUser(), report);

		view.navigate().to().previousView();
	}

	public void cancelButtonClicked() {
		view.navigate().to().previousView();
	}

	private MetadataSchemasManager getMetadataSchemasManager() {
		return modelLayerFactory.getMetadataSchemasManager();
	}

	public String getSchemaTypeCode() {
		return parameters.get("schemaTypeCode");
	}

	public List<ReportVO> getReports() {
		List<ReportVO> returnList = new ArrayList<>();
		ReportServices reportServices = new ReportServices(modelLayerFactory, collection);
		List<Report> reports = reportServices.getReports(getSchemaTypeCode());
		ReportToVOBuilder builder = new ReportToVOBuilder();
		for (Report report : reports) {
			returnList.add(builder.build(report));
		}
		return returnList;
	}

	public List<MetadataVO> getReportMetadatas() {
		List<MetadataVO> returnMetadataVOs = new ArrayList<>();
		if (StringUtils.isBlank(getSelectedReport())) {
			return returnMetadataVOs;
		}
		ReportServices reportServices = new ReportServices(modelLayerFactory, view.getCollection());
		Report report = reportServices.getReport(getSchemaTypeCode(), getSelectedReport());
		if (report == null) {
			return returnMetadataVOs;
		}
		MetadataSchemasManager schemasManager = modelLayerFactory.getMetadataSchemasManager();
		MetadataToVOBuilder builder = new MetadataToVOBuilder();
		for (ReportedMetadata reportedMetadata : report.getReportedMetadata()) {
			if (schemasManager.getSchemaTypes(collection).hasMetadata(reportedMetadata.getMetadataCode())) {
				Metadata metadata = schemasManager.getSchemaTypes(collection).getMetadata(reportedMetadata.getMetadataCode());
				if (metadata.inheritDefaultSchema()) {
					returnMetadataVOs.add(builder.build(metadata.getInheritance(), view.getSessionContext()));
				} else {
					returnMetadataVOs.add(builder.build(metadata, view.getSessionContext()));
				}
			}
		}
		return returnMetadataVOs;
	}

	public String getSchemaName(String metadataCode) {
		Language language = Language.withCode(view.getSessionContext().getCurrentLocale().getLanguage());
		MetadataSchemasManager metadataSchemasManager = appLayerFactory.getModelLayerFactory().getMetadataSchemasManager();
		int index = metadataCode.lastIndexOf("_");
		return metadataSchemasManager.getSchemaTypes(collection).getSchema(metadataCode.substring(0, index))
				.getLabel(language);
	}

	public boolean isAddMode() {
		return isAddMode;
	}

	public void setAddMode(boolean addMode) {
		isAddMode = addMode;
	}

	public void setReport(ReportVO report) {
		this.report = report;
	}

	public void checkForId() {
		if (this.parameters.containsKey("id")) {
			String id = this.parameters.get("id");
			SearchServices searchServices = modelLayerFactory.newSearchServices();
			RMSchemasRecordsServices rm = new RMSchemasRecordsServices(collection, appLayerFactory);
			ReportToVOBuilder builder = new ReportToVOBuilder();
			MetadataSchemasManager metadataSchemasManager = modelLayerFactory.getMetadataSchemasManager();
			LogicalSearchCondition condition = from(metadataSchemasManager.getSchemaTypes(collection).getSchemaType(Report.SCHEMA_TYPE).getDefaultSchema()).where(Schemas.IDENTIFIER).isEqualTo(id);
			this.report = builder.build(rm.wrapReport(searchServices.searchSingleResult(condition)));
		}
	}

	public ReportVO getReport() {
		return this.report;
	}

	public boolean isEditMode() {
		return this.report != null;
	}

	public ArrayList<String> getInheritedMetadataCodesFor(List<String> selectedMetadataCodes) {
		List<Metadata> metadataList = schemaType(getSchemaTypeCode()).getAllMetadataIncludingInheritedOnes();
		HashSet<String> inheritedMetadataCodes = new HashSet<>();
		for (Metadata metadata : metadataList) {
			if (selectedMetadataCodes.contains(metadata.getCode())) {
				if (metadata.inheritDefaultSchema()) {
					inheritedMetadataCodes.add(metadata.getInheritanceCode());
				} else {
					inheritedMetadataCodes.add(metadata.getCode());
				}
			}
		}
		return new ArrayList<>(inheritedMetadataCodes);
	}
}
