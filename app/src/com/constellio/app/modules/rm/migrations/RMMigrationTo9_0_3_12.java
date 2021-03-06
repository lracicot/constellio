package com.constellio.app.modules.rm.migrations;

import com.constellio.app.entities.modules.MetadataSchemasAlterationHelper;
import com.constellio.app.entities.modules.MigrationResourcesProvider;
import com.constellio.app.entities.modules.MigrationScript;
import com.constellio.app.modules.rm.model.calculators.rule.RuleMediumTypeReferencesCalculator;
import com.constellio.app.modules.rm.wrappers.RetentionRule;
import com.constellio.app.services.factories.AppLayerFactory;
import com.constellio.model.entities.schemas.Schemas;
import com.constellio.model.services.schemas.builders.MetadataSchemaBuilder;
import com.constellio.model.services.schemas.builders.MetadataSchemaTypesBuilder;

//9.0
public class RMMigrationTo9_0_3_12 implements MigrationScript {
	@Override
	public String getVersion() {
		return "9.0.3.12";
	}

	@Override
	public void migrate(String collection, MigrationResourcesProvider migrationResourcesProvider,
						AppLayerFactory appLayerFactory) throws Exception {
		new SchemaAlterationFor9_0_3_12(collection, migrationResourcesProvider, appLayerFactory).migrate();
	}

	private class SchemaAlterationFor9_0_3_12 extends MetadataSchemasAlterationHelper {
		SchemaAlterationFor9_0_3_12(String collection, MigrationResourcesProvider migrationResourcesProvider,
									AppLayerFactory appLayerFactory) {
			super(collection, migrationResourcesProvider, appLayerFactory);
		}

		@Override
		protected void migrate(MetadataSchemaTypesBuilder typesBuilder) {
			MetadataSchemaBuilder retentionRuleSchema = typesBuilder.getSchemaType(RetentionRule.SCHEMA_TYPE).getDefaultSchema();
			retentionRuleSchema.getMetadata(Schemas.ALL_REFERENCES.getLocalCode())
					.defineDataEntry().asCalculated(RuleMediumTypeReferencesCalculator.class);
		}
	}
}
