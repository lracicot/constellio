package com.constellio.app.modules.rm.reports.model.decommissioning;

import com.constellio.app.modules.rm.reports.model.decommissioning.DecommissioningListReportModel.DecommissioningListReportModel_Folder;
import com.constellio.app.modules.rm.services.RMSchemasRecordsServices;
import com.constellio.app.modules.rm.wrappers.*;
import com.constellio.model.conf.FoldersLocator;
import com.constellio.model.entities.schemas.MetadataSchemaType;
import com.constellio.model.entities.schemas.Schemas;
import com.constellio.model.services.factories.ModelLayerFactory;
import com.constellio.model.services.search.SearchServices;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery;
import com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.constellio.app.ui.i18n.i18n.$;

public class DecommissioningListReportPresenter {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(DecommissioningListReportPresenter.class);

    private String collection;
    private ModelLayerFactory modelLayerFactory;
    private SearchServices searchServices;
    private String decommissioningListId;
    private RMSchemasRecordsServices rm;

    public DecommissioningListReportPresenter(String collection, ModelLayerFactory modelLayerFactory,
                                              String decommissioningListId) {

        this.collection = collection;
        this.modelLayerFactory = modelLayerFactory;
        this.decommissioningListId = decommissioningListId;
        this.rm = new RMSchemasRecordsServices(collection, modelLayerFactory);
        this.searchServices = modelLayerFactory.newSearchServices();
    }

    public DecommissioningListReportModel build() {

        DecommissioningListReportModel model = new DecommissioningListReportModel();

        MetadataSchemaType folderSchemaType = rm.folder.schemaType();
        DecommissioningList decommissioningList = rm.getDecommissioningList(decommissioningListId);

        LogicalSearchQuery foldersQuery = new LogicalSearchQuery()
                .setCondition(LogicalSearchQueryOperators.from(folderSchemaType)
                        .where(Schemas.IDENTIFIER)
                        .isIn(decommissioningList.getFolders()))
                .sortAsc(folderSchemaType.getDefaultSchema().get(Folder.CATEGORY_CODE)).sortAsc(Schemas.LEGACY_ID).sortAsc(Schemas.IDENTIFIER);
        List<Folder> folders = rm.wrapFolders(searchServices.search(foldersQuery));

        List<DecommissioningListReportModel_Folder> foldersModel = new ArrayList<>();
        int currentFolder = 0;
        for (Folder folder : folders) {
            Category category = rm.getCategory(folder.getCategory());
            RetentionRule retentionRule = rm.getRetentionRule(folder.getRetentionRule());
            String categoryCodeTitle = category.getCode() + " - " + category.getTitle();
            String retentionRuleCodeTitle = retentionRule.getCode() + " - " + retentionRule.getTitle();
            String containerRecordId = decommissioningList.getFolderDetails().get(currentFolder).getContainerRecordId();
            String containerRecordTitle = "";
            if(containerRecordId != null){
                ContainerRecord containerRecord = rm.getContainerRecord(containerRecordId);
                containerRecordTitle = containerRecord.getTitle();
            }
            DecommissioningListReportModel_Folder folderModel = new DecommissioningListReportModel_Folder(folder.getLegacyId(), folder.getId(),
                    folder.getTitle(), retentionRuleCodeTitle, categoryCodeTitle, containerRecordTitle);
            foldersModel.add(folderModel);
            currentFolder++;
        }

        String decommissioningListType = $(
                "DecommissioningListType." + decommissioningList.getDecommissioningListType().getCode());
        AdministrativeUnit administrativeUnit = rm.getAdministrativeUnit(
                decommissioningList.getAdministrativeUnit());
        String administrativeUnitCode = administrativeUnit.getCode();
        String administrativeUnitTitle = administrativeUnit.getTitle();
        model.setDecommissioningListTitle(decommissioningList.getTitle());
        model.setDecommissioningListType(decommissioningListType);
        model.setDecommissioningListAdministrativeUnitCodeAndTitle(administrativeUnitCode + " -  " + administrativeUnitTitle);
        model.setFolders(foldersModel);
        return model;
    }

    public FoldersLocator getFoldersLocator() {
        return modelLayerFactory.getFoldersLocator();
    }
}