package com.constellio.app.modules.reports.wrapper;

import com.constellio.app.modules.rm.wrappers.ContainerRecord;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.records.wrappers.RecordWrapper;
import com.constellio.model.entities.records.wrappers.Report;
import com.constellio.model.entities.schemas.MetadataSchemaTypes;

/**
 * Classe Wrapper pour les Rapports.
 *
 * @author Nicolas D'Amours & Charles Blanchette.
 */
public class ReportConfig extends RecordWrapper {
    public static final String SCHEMA_TYPE = "reportsrecords";
    public static final String DEFAULT_SCHEMA = SCHEMA_TYPE + "_default";
    public static final String JASPERFILE = "jasperFile";

    public ReportConfig(Record record, MetadataSchemaTypes types) {
        super(record, types, SCHEMA_TYPE);
    }

    public ReportConfig setTitle(String title) {
        super.setTitle(title);
        return this;
    }

    public ReportConfig setJasperFile(String file) {
        set(JASPERFILE, file);
        return this;
    }

    public String getJasperfile() {
        return get(JASPERFILE);
    }
}
