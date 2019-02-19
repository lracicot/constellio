package com.constellio.app.services.sip.record;

import com.constellio.app.api.extensions.SIPExtension;
import com.constellio.app.api.extensions.params.ExportCollectionInfosSIPParams;
import com.constellio.app.extensions.AppLayerCollectionExtensions;
import com.constellio.app.modules.rm.wrappers.Printable;
import com.constellio.app.services.factories.AppLayerFactory;
import com.constellio.app.services.sip.mets.MetsDivisionInfo;
import com.constellio.app.services.sip.zip.SIPZipWriter;
import com.constellio.model.entities.Language;
import com.constellio.model.entities.Taxonomy;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.records.wrappers.Facet;
import com.constellio.model.entities.records.wrappers.Group;
import com.constellio.model.entities.records.wrappers.SavedSearch;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.entities.schemas.MetadataSchemaType;
import com.constellio.model.entities.schemas.MetadataSchemaTypes;
import com.constellio.model.services.search.SearchServices;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery;
import com.constellio.model.services.taxonomies.ConceptNodesTaxonomySearchServices;
import com.constellio.model.services.taxonomies.TaxonomiesManager;

import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

import static com.constellio.model.entities.schemas.Schemas.IDENTIFIER;
import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.from;

public class CollectionInfosSIPWriter {

	String collection;
	AppLayerFactory appLayerFactory;
	AppLayerCollectionExtensions extensions;
	SIPZipWriter writer;
	RecordSIPWriter recordSIPWriter;
	MetadataSchemaTypes types;
	Language language;
	SearchServices searchServices;
	ConceptNodesTaxonomySearchServices taxonomySearchServices;
	TaxonomiesManager taxonomiesManager;

	public CollectionInfosSIPWriter(String collection, AppLayerFactory appLayerFactory, SIPZipWriter writer,
									Locale locale)
			throws IOException {
		this.collection = collection;
		this.appLayerFactory = appLayerFactory;
		this.extensions = appLayerFactory.getExtensions().forCollection(collection);
		this.writer = writer;
		this.types = appLayerFactory.getModelLayerFactory().getMetadataSchemasManager().getSchemaTypes(collection);
		this.recordSIPWriter = new RecordSIPWriter(appLayerFactory, writer,
				new DefaultRecordZipPathProvider(collection, appLayerFactory.getModelLayerFactory()), locale);
		this.language = Language.withLocale(locale);
		this.searchServices = appLayerFactory.getModelLayerFactory().newSearchServices();
		this.taxonomiesManager = appLayerFactory.getModelLayerFactory().getTaxonomiesManager();
		this.taxonomySearchServices = new ConceptNodesTaxonomySearchServices(appLayerFactory.getModelLayerFactory());
	}

	public void exportCollectionConfigs() throws IOException {

		exportRecordsInSchemaTypesDivision(User.SCHEMA_TYPE);
		exportRecordsInSchemaTypesDivision(Group.SCHEMA_TYPE);
		exportRecordsInSchemaTypesDivision(Printable.SCHEMA_TYPE);
		exportRecordsInSchemaTypesDivision(SavedSearch.SCHEMA_TYPE);
		exportRecordsInSchemaTypesDivision(Facet.SCHEMA_TYPE);

		exportValueLists();
		exportTaxonomies();
		for (SIPExtension extension : extensions.sipExtensions) {
			extension.exportCollectionInfosSIP(new ExportCollectionInfosSIPParams(this, recordSIPWriter, writer));
		}
	}

	public void close() {
		writer.close();
	}

	private void exportValueLists() throws IOException {
		for (MetadataSchemaType schemaType : types.getSchemaTypes()) {
			if (schemaType.getCode().startsWith("ddv")) {
				exportRecordsInSchemaTypesDivision(schemaType.getCode());
			}
		}
	}

	private void exportTaxonomies() throws IOException {

		for (Taxonomy taxonomy : taxonomiesManager.getEnabledTaxonomies(collection)) {
			if (extensions.isExportedTaxonomyInSIPCollectionInfos(taxonomy)) {
				writer.addDivisionInfo(new MetsDivisionInfo(taxonomy.getCode(), taxonomy.getTitle(language), "taxonomy"));


				for (String taxonomySchemaTypeCode : taxonomy.getSchemaTypes()) {
					exportRecordsInSchemaTypesDivision(taxonomySchemaTypeCode, false);
					//					MetadataSchemaType schemaType = types.getSchemaType(taxonomySchemaTypeCode);
					//					writer.addDivisionInfo(toDivisionInfo(schemaType, taxonomy.getCode()));
					//
					//					Iterator<Record> recordIterator = searchServices.recordsIterator(new LogicalSearchQuery(from(schemaType).returnAll()));
					//					while (recordIterator.hasNext()) {
					//						recordSIPWriter.add(recordIterator.next());
					//					}
				}
			}
		}
	}


	public void exportRecordsInSchemaTypesDivision(String schemaTypeCode)
			throws IOException {
		exportRecordsInSchemaTypesDivision(schemaTypeCode, true);
	}

	public void exportRecordsInSchemaTypesDivision(String schemaTypeCode, boolean createDivision)
			throws IOException {

		MetadataSchemaType schemaType = types.getSchemaType(schemaTypeCode);
		LogicalSearchQuery query = new LogicalSearchQuery(from(schemaType).returnAll());
		query.sortAsc(IDENTIFIER);

		if (createDivision) {
			writer.addDivisionInfo(toDivisionInfo(schemaType, null));
		}
		Iterator<Record> recordIterator = searchServices.recordsIterator(query);
		while (recordIterator.hasNext()) {
			recordSIPWriter.add(recordIterator.next());
		}

	}

	private MetsDivisionInfo toDivisionInfo(MetadataSchemaType schemaType, String parent) {
		return new MetsDivisionInfo(schemaType.getCode(), parent, schemaType.getLabel(language), "schemaType");
	}


}
