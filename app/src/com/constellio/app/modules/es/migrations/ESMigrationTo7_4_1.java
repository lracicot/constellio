package com.constellio.app.modules.es.migrations;

import com.constellio.app.entities.modules.MetadataSchemasAlterationHelper;
import com.constellio.app.entities.modules.MigrationHelper;
import com.constellio.app.entities.modules.MigrationResourcesProvider;
import com.constellio.app.entities.modules.MigrationScript;
import com.constellio.app.modules.es.model.connectors.smb.ConnectorSmbDocument;
import com.constellio.app.modules.es.model.connectors.smb.ConnectorSmbFolder;
import com.constellio.app.services.factories.AppLayerFactory;
import com.constellio.model.services.records.RecordServicesException;
import com.constellio.model.services.schemas.builders.MetadataSchemaBuilder;
import com.constellio.model.services.schemas.builders.MetadataSchemaTypesBuilder;

import static com.constellio.model.entities.schemas.MetadataValueType.STRING;

public class ESMigrationTo7_4_1 extends MigrationHelper implements MigrationScript {

	@Override
	public String getVersion() {
		return "7.4.1";
	}

	@Override
	public void migrate(String collection, MigrationResourcesProvider migrationResourcesProvider,
						AppLayerFactory appLayerFactory)
			throws Exception {
		new SchemaAlterationFor7_4_1(collection, migrationResourcesProvider, appLayerFactory).migrate();
	}

	static class SchemaAlterationFor7_4_1 extends MetadataSchemasAlterationHelper {

		protected SchemaAlterationFor7_4_1(String collection, MigrationResourcesProvider migrationResourcesProvider,
										   AppLayerFactory appLayerFactory)
				throws RecordServicesException {
			super(collection, migrationResourcesProvider, appLayerFactory);
		}

		@Override
		protected void migrate(MetadataSchemaTypesBuilder typesBuilder) {
			MetadataSchemaBuilder connectorSmbFolderSchema = typesBuilder.getSchema(ConnectorSmbFolder.DEFAULT_SCHEMA);
			connectorSmbFolderSchema.createUndeletable(ConnectorSmbDocument.PERMISSIONS_HASH).setType(STRING);
		}
	}
}
