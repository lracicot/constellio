package com.constellio.app.services.migrations.scripts;

import com.constellio.app.entities.modules.MetadataSchemasAlterationHelper;
import com.constellio.app.entities.modules.MigrationHelper;
import com.constellio.app.entities.modules.MigrationResourcesProvider;
import com.constellio.app.entities.modules.MigrationScript;
import com.constellio.app.services.factories.AppLayerFactory;
import com.constellio.model.entities.records.wrappers.Capsule;
import com.constellio.model.entities.schemas.MetadataValueType;
import com.constellio.model.services.schemas.builders.MetadataSchemaBuilder;
import com.constellio.model.services.schemas.builders.MetadataSchemaTypesBuilder;

import static com.constellio.model.entities.schemas.MetadataValueType.STRING;

public class CoreMigrationTo_7_6 implements MigrationScript {
    @Override
    public String getVersion() {
        return "7.6";
    }

    @Override
    public void migrate(String collection, MigrationResourcesProvider migrationResourcesProvider, AppLayerFactory appLayerFactory) throws Exception {
        new CoreSchemaAlterationFor_7_6(collection, migrationResourcesProvider, appLayerFactory).migrate();
    }

    class CoreSchemaAlterationFor_7_6 extends MetadataSchemasAlterationHelper {

        protected CoreSchemaAlterationFor_7_6(String collection, MigrationResourcesProvider migrationResourcesProvider, AppLayerFactory appLayerFactory) {
            super(collection, migrationResourcesProvider, appLayerFactory);
        }

        @Override
        protected void migrate(MetadataSchemaTypesBuilder typesBuilder) {
            MetadataSchemaBuilder builder = typesBuilder.createNewSchemaType(Capsule.SCHEMA_TYPE).getDefaultSchema();
            builder.create(Capsule.CODE).setType(MetadataValueType.STRING);
            builder.create(Capsule.HTML).setType(MetadataValueType.STRING);
            builder.create(Capsule.KEYWORDS).setType(STRING).setMultivalue(true);
        }
    }
}
