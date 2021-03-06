package com.constellio.app.api.cmis.builders.object;

import com.constellio.app.api.cmis.binding.collection.ConstellioCollectionRepository;
import com.constellio.app.api.cmis.binding.global.ConstellioCmisContextParameters;
import com.constellio.app.extensions.api.cmis.params.BuildAllowableActionsParams;
import com.constellio.app.services.factories.AppLayerFactory;
import com.constellio.model.entities.CorePermissions;
import com.constellio.model.entities.Taxonomy;
import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.entities.schemas.MetadataSchemaType;
import com.constellio.model.entities.schemas.MetadataValueType;
import com.constellio.model.services.migrations.ConstellioEIMConfigs;
import com.constellio.model.services.schemas.SchemaUtils;
import com.constellio.model.services.taxonomies.TaxonomiesManager;
import org.apache.chemistry.opencmis.commons.data.AllowableActions;
import org.apache.chemistry.opencmis.commons.enums.Action;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.AllowableActionsImpl;
import org.apache.chemistry.opencmis.commons.server.CallContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_APPLY_ACL;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_CHECK_IN;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_CHECK_OUT;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_CREATE_DOCUMENT;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_CREATE_FOLDER;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_DELETE_CONTENT_STREAM;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_DELETE_OBJECT;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_DELETE_TREE;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_GET_ACL;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_GET_ALL_VERSIONS;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_GET_CHILDREN;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_GET_CONTENT_STREAM;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_GET_FOLDER_PARENT;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_GET_FOLDER_TREE;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_GET_OBJECT_PARENTS;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_GET_PROPERTIES;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_MOVE_OBJECT;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_SET_CONTENT_STREAM;
import static org.apache.chemistry.opencmis.commons.enums.Action.CAN_UPDATE_PROPERTIES;

public class AllowableActionsBuilder {

	private final ConstellioCollectionRepository repository;

	private final AppLayerFactory appLayerFactory;

	private final CallContext context;

	private final User user;

	private TaxonomiesManager taxonomiesManager;

	private ConstellioEIMConfigs configs;

	public AllowableActionsBuilder(ConstellioCollectionRepository repository, AppLayerFactory appLayerFactory,
								   CallContext context) {
		this.repository = repository;
		this.appLayerFactory = appLayerFactory;
		this.context = context;
		this.user = (User) context.get(ConstellioCmisContextParameters.USER);
		this.taxonomiesManager = appLayerFactory.getModelLayerFactory().getTaxonomiesManager();
		this.configs = new ConstellioEIMConfigs(appLayerFactory.getModelLayerFactory().getSystemConfigurationsManager());
	}

	public static final List<Action> TAXONOMY_ACTIONS = asList(CAN_GET_PROPERTIES, CAN_GET_CHILDREN, CAN_GET_FOLDER_PARENT,
			CAN_GET_OBJECT_PARENTS);
	public static final List<Action> ROOT_ACTIONS = asList(CAN_GET_PROPERTIES, CAN_GET_CHILDREN);
	public static final List<Action> UNSECURIZED_RECORD_ACTIONS = asList(CAN_GET_PROPERTIES);
	public static final List<Action> NO_RIGHT_ON_RECORD_ACTIONS = asList(CAN_GET_CHILDREN, CAN_GET_FOLDER_PARENT,
			CAN_GET_FOLDER_TREE);
	public static final List<Action> READ_RIGHT_ON_RECORD_ACTIONS = asList(CAN_GET_PROPERTIES);
	public static final List<Action> WRITE_RIGHT_ON_RECORD_ACTIONS = asList(CAN_UPDATE_PROPERTIES, CAN_MOVE_OBJECT,
			CAN_CREATE_FOLDER);
	public static final List<Action> DELETE_RIGHT_ON_RECORD_ACTIONS = asList(CAN_DELETE_OBJECT, CAN_DELETE_TREE);
	public static final List<Action> RECORD_WITH_CONTENT_READ_ACTIONS = asList(CAN_GET_CONTENT_STREAM, CAN_GET_ALL_VERSIONS);
	public static final List<Action> RECORD_WITH_CONTENT_WRITE_ACTIONS = asList(CAN_CREATE_DOCUMENT,
			CAN_SET_CONTENT_STREAM, CAN_DELETE_CONTENT_STREAM, CAN_CHECK_IN, CAN_CHECK_OUT);

	public static final List<Action> SECONDARY_TAXONOMY_ACTIONS = asList(CAN_GET_PROPERTIES, CAN_GET_CHILDREN,
			CAN_GET_OBJECT_PARENTS, CAN_GET_FOLDER_TREE, CAN_CREATE_FOLDER, CAN_GET_FOLDER_PARENT);
	public static final List<Action> NO_RIGHT_ON_PRINCIPAL_TAXONOMY_CONCEPT_ACTIONS = asList(CAN_GET_CHILDREN,
			CAN_GET_FOLDER_PARENT, CAN_GET_FOLDER_TREE);
	public static final List<Action> READ_RIGHT_ON_PRINCIPAL_TAXONOMY_CONCEPT_ACTIONS = asList(CAN_GET_PROPERTIES,
			CAN_GET_FOLDER_PARENT, CAN_GET_FOLDER_TREE, CAN_GET_OBJECT_PARENTS);
	public static final List<Action> WRITE_RIGHT_ON_PRINCIPAL_TAXONOMY_CONCEPT_ACTIONS = asList(CAN_CREATE_FOLDER);
	public static final List<Action> MANAGE_SECURITY_ACTIONS = asList(CAN_GET_ACL, CAN_APPLY_ACL);

	public AllowableActions build(Record record) {
		MetadataSchemaType type = appLayerFactory.getModelLayerFactory().getMetadataSchemasManager()
				.getSchemaTypes(repository.getCollection())
				.getSchemaType(new SchemaUtils().getSchemaTypeCode(record.getSchemaCode()));

		boolean readAccess = user.hasReadAccess().on(record);
		boolean writeAccess = user.hasWriteAccess().on(record);
		boolean deleteAccess = user.hasDeleteAccess().on(record);
		boolean canManageACL = user.has(CorePermissions.MANAGE_SECURITY).on(record) && writeAccess && deleteAccess;
		boolean hasContentMetadata = !type.getAllMetadatas().onlyWithType(MetadataValueType.CONTENT).isEmpty();
		if (record == null) {
			throw new IllegalArgumentException("File must not be null!");
		}
		boolean isRoot = record.getSchemaCode().startsWith("collection_");

		Set<Action> availableActions = new HashSet<>();
		if (isRoot) {
			availableActions.addAll(ROOT_ACTIONS);
		} else {

			Taxonomy taxonomy = taxonomiesManager.getTaxonomyOf(record);
			if (taxonomy == null) {

				if (type.hasSecurity()) {
					availableActions.addAll(NO_RIGHT_ON_RECORD_ACTIONS);
					if (readAccess) {
						availableActions.addAll(READ_RIGHT_ON_RECORD_ACTIONS);
						if (hasContentMetadata) {
							availableActions.addAll(RECORD_WITH_CONTENT_READ_ACTIONS);
						}
					}
					if (writeAccess) {
						availableActions.addAll(WRITE_RIGHT_ON_RECORD_ACTIONS);
						if (hasContentMetadata) {
							availableActions.addAll(RECORD_WITH_CONTENT_WRITE_ACTIONS);
						}

						if (canManageACL) {
							availableActions.addAll(MANAGE_SECURITY_ACTIONS);
						}
					}
					if (deleteAccess) {
						availableActions.addAll(DELETE_RIGHT_ON_RECORD_ACTIONS);
					}

				} else {
					availableActions.addAll(UNSECURIZED_RECORD_ACTIONS);
				}

			} else {
				if (taxonomy.getCode().equals(taxonomiesManager.getPrincipalTaxonomy(record.getCollection()).getCode())) {
					availableActions.addAll(NO_RIGHT_ON_PRINCIPAL_TAXONOMY_CONCEPT_ACTIONS);
					if (readAccess) {
						availableActions.addAll(READ_RIGHT_ON_PRINCIPAL_TAXONOMY_CONCEPT_ACTIONS);

						if (hasContentMetadata) {
							availableActions.addAll(RECORD_WITH_CONTENT_READ_ACTIONS);
						}
					}
					if (writeAccess) {
						availableActions.addAll(WRITE_RIGHT_ON_PRINCIPAL_TAXONOMY_CONCEPT_ACTIONS);

						if (canManageACL) {
							availableActions.addAll(MANAGE_SECURITY_ACTIONS);
						}

						if (hasContentMetadata) {
							availableActions.addAll(RECORD_WITH_CONTENT_WRITE_ACTIONS);
						}
					}
				} else {
					availableActions.addAll(SECONDARY_TAXONOMY_ACTIONS);
				}
			}
		}

		if (configs.isCmisNeverReturnAcl()) {
			availableActions.remove(CAN_APPLY_ACL);
			availableActions.remove(CAN_GET_ACL);
		}

		BuildAllowableActionsParams params = new BuildAllowableActionsParams(user, record, availableActions);
		appLayerFactory.getExtensions().forCollectionOf(record).buildAllowableActions(params);

		AllowableActionsImpl result = new AllowableActionsImpl();
		result.setAllowableActions(availableActions);

		return result;
	}

	private void addAction(Set<Action> aas, Action action, boolean condition) {
		if (condition) {
			aas.add(action);
		}
	}

	public AllowableActions buildTaxonomyActions(String substring) {
		Set<Action> availableActions = new HashSet<>(TAXONOMY_ACTIONS);
		AllowableActionsImpl result = new AllowableActionsImpl();
		result.setAllowableActions(availableActions);
		return result;
	}
}
