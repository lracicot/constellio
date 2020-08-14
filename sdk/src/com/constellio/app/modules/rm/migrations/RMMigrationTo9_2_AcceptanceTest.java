package com.constellio.app.modules.rm.migrations;

import com.constellio.model.entities.records.Record;
import com.constellio.model.entities.records.wrappers.Collection;
import com.constellio.model.entities.records.wrappers.Group;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.entities.schemas.MetadataSchema;
import com.constellio.model.entities.schemas.Schemas;
import com.constellio.model.entities.security.global.GlobalGroup;
import com.constellio.model.entities.security.global.SystemWideGroup;
import com.constellio.model.entities.security.global.UserCredential;
import com.constellio.model.entities.security.global.UserCredentialStatus;
import com.constellio.model.entities.security.global.UserSyncMode;
import com.constellio.model.services.factories.ModelLayerFactory;
import com.constellio.model.services.records.SchemasRecordsServices;
import com.constellio.model.services.search.SearchServices;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery;
import com.constellio.model.services.users.SystemWideUserInfos;
import com.constellio.model.services.users.UserServicesRuntimeException.UserServicesRuntimeException_NoSuchUser;
import com.constellio.sdk.tests.ConstellioTest;
import com.constellio.sdk.tests.SDKFoldersLocator;
import lombok.AllArgsConstructor;
import org.assertj.core.api.Condition;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.constellio.model.entities.schemas.entries.DataEntryType.CALCULATED;
import static com.constellio.model.entities.schemas.entries.DataEntryType.MANUAL;
import static com.constellio.model.entities.security.global.UserCredentialStatus.ACTIVE;
import static com.constellio.model.services.search.query.logical.LogicalSearchQueryOperators.from;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class RMMigrationTo9_2_AcceptanceTest extends ConstellioTest {

	@Test
	public void givenSystemIn9_0WValidateMigrationOf9_0And9_2ThenOk() {
		givenTransactionLogIsEnabled();
		File statesFolder = new SDKFoldersLocator().getInitialStatesFolder();
		File state = new File(statesFolder, "given_system_in_9.0.3.zip");

		getCurrentTestSession().getFactoriesTestFeatures()
				.givenSystemInState(state).withPasswordsReset()
				.withFakeEncryptionServices();

		List<String> collections = getAppLayerFactory().getCollectionsManager().getCollectionCodes();
		List<User> users = new ArrayList<>();

		ModelLayerFactory modelLayerFactory = getModelLayerFactory();
		for (String collection :
				collections) {
			users.addAll(getAllUsersInCollection(modelLayerFactory, collection));
		}
		List<UserCredential> credentials = getUserCredentials(modelLayerFactory);

		List<Group> groups = new ArrayList<>();
		for (String collection :
				collections) {
			groups.addAll(getAllGroupsInCollection(modelLayerFactory, collection));
		}

		//Verify users

		//Check a certain user is not there? In LDAP case they are added to system collection?
		String collectiondef = collections.isEmpty() && collections.size() < 2 ? zeCollection : collections.get(1);

		MetadataSchema credentialMetadataSchema = modelLayerFactory.getMetadataSchemasManager().getSchemaTypes(Collection.SYSTEM_COLLECTION).getDefaultSchema(UserCredential.SCHEMA_TYPE);
		MetadataSchema userMetadataSchema = modelLayerFactory.getMetadataSchemasManager().getSchemaTypes(collectiondef).getDefaultSchema(User.SCHEMA_TYPE);
		MetadataSchema groupMetadataSchema = modelLayerFactory.getMetadataSchemasManager().getSchemaTypes(collectiondef).getDefaultSchema(Group.SCHEMA_TYPE);
		MetadataSchema globalGroupMetadataSchema = modelLayerFactory.getMetadataSchemasManager().getSchemaTypes(Collection.SYSTEM_COLLECTION).getDefaultSchema(GlobalGroup.SCHEMA_TYPE);

		//SyncMode est dans les utilisateurs?
		assertThat(credentialMetadataSchema.getMetadata(UserCredential.SYNC_MODE).getDataEntry().getType()).isEqualTo(MANUAL);
		for (UserCredential user : credentials) {
			System.out.println(user.getId());
			System.out.println(user.getSyncMode());
			assertThat(user.getSyncMode()).isNotNull();
		}

		//Metadonnées retirées?
		assertThat(credentialMetadataSchema.hasMetadataWithCode("firstname")).isFalse();
		assertThat(credentialMetadataSchema.hasMetadataWithCode("lastname")).isFalse();
		assertThat(credentialMetadataSchema.hasMetadataWithCode("email")).isFalse();
		assertThat(credentialMetadataSchema.hasMetadataWithCode("personalEmails")).isFalse();
		assertThat(credentialMetadataSchema.hasMetadataWithCode("phone")).isFalse();
		assertThat(credentialMetadataSchema.hasMetadataWithCode("globalGroups")).isFalse();
		assertThat(credentialMetadataSchema.hasMetadataWithCode("fax")).isFalse();
		assertThat(credentialMetadataSchema.hasMetadataWithCode("jobTitle")).isFalse();
		assertThat(credentialMetadataSchema.hasMetadataWithCode("address")).isFalse();

		//Métadonnées déplacés dans users?
		assertThat(userMetadataSchema.hasMetadataWithCode(User.DOMAIN)).isTrue();
		assertThat(userMetadataSchema.hasMetadataWithCode(User.MS_EXCHANGE_DELEGATE_LIST)).isTrue();

		//Les utilisateurs qui n'étaient pas actifs sont supprimés logiquement?
		List<UserCredential> credentialsDeleted = credentials.stream()
				.filter(x -> !x.getStatus().equals(UserCredentialStatus.ACTIVE))
				.collect(toList());
		for (UserCredential credential : credentialsDeleted) {
			assertThat(credential.getStatus()).isEqualTo(UserCredentialStatus.DISABLED);
		}

		//physically removed users if not used. Need to be encoded.

		//Verify Groups

		assertThat(globalGroupMetadataSchema.hasMetadataWithCode("usersAutomaticallyAddedToCollections")).isFalse();
		assertThat(globalGroupMetadataSchema.hasMetadataWithCode("status")).isFalse();
		assertThat(globalGroupMetadataSchema.hasMetadataWithCode("locallyCreated")).isFalse();
		assertThat(globalGroupMetadataSchema.hasMetadataWithCode("hierarchy")).isFalse();
		assertThat(groupMetadataSchema.hasMetadataWithCode("status")).isTrue();
		assertThat(groupMetadataSchema.hasMetadataWithCode("locallyCreated")).isTrue();
		assertThat(groupMetadataSchema.hasMetadataWithCode("hierarchy")).isTrue();

		verifySaveState(groups, users, credentials);
		verifyData(groups, users, credentials);

	}

	private void verifyData(List<Group> groups, List<User> users, List<UserCredential> credentials) {
		List<Group> nobility = groups.stream().filter(x -> x.getCode().equals("nobility")).collect(toList());
		assertThat(nobility).isNotEmpty().hasSize(3).has(new Condition<List<Group>>() {
			@Override
			public boolean matches(List<Group> value) {
				return value.stream().anyMatch(x -> x.getCollection().equals(zeCollection));
			}
		});

		List<Group> collectionRidaGroups = groups.stream().filter(x -> x.getCollection().equals("LaCollectionDeRida")).collect(toList());

		assertThat(collectionRidaGroups).isNotEmpty().has(new Condition<List<Group>>() {
			@Override
			public boolean matches(List<Group> value) {
				return value.stream().filter(x -> x.getCode().equals("explorers")
												  || x.getCode().equals("Bosses")
												  || x.getCode().equals("royale"))
							   .collect(toList()).size() > 2;

			}
		});

		//dusty (edouard's rival) is part of big bad bosses
		User dusty = users.stream().filter(x -> x.getUsername().equals("dusty")).findFirst().get();

		assertThat(dusty.getUserGroups()).hasSize(1).has(new Condition<List<String>>() {
			@Override
			public boolean matches(List<String> value) {
				return groups.stream().filter(x -> value.contains(x.getId())).findAny().get().getCode().equals("Bosses");
			}
		});


		//String bossesGroupId = "00000012345"; On auraut très bien pu hardcoder l'id, il est dans le savestate
		String bossesGroupId = getModelLayerFactory().newUserServices().getGroupInCollection("Bosses", "LaCollectionDeRida").getId();
		assertThat(dusty.getUserGroups()).containsOnly(bossesGroupId);

		assertThat(dusty.getUserGroups().stream().map((id -> record(id).get(Schemas.CODE))).collect(toList()))
				.containsOnly("Bosses");

		assertThat(dusty.getUserGroups()).hasSize(1).has(new Condition<List<String>>() {
			@Override
			public boolean matches(List<String> value) {
				return groups.stream().filter(x -> value.contains(x.getId())).findAny().get().getCode().equals("Bosses");
			}
		});

		//assertThatUser("MachoMan").isInCollections(zeCollection).hasGroupsInCollection("G1", "zeCollection");
		//assertThatUser("Embalmer").doesNotExist();
	}

	private void verifySaveState(List<Group> groups, List<User> users, List<UserCredential> credentials) {
		User dusty = users.stream().filter(x -> x.getUsername().equals("dusty")).findFirst().get();
		User marie = users.stream().filter(x -> x.getUsername().equals("marie")).findFirst().get();
		User cartier = users.stream().filter(x -> x.getUsername().equals("cartie")).findFirst().get();
		User colomb = users.stream().filter(x -> x.getUsername().equals("colomb")).findFirst().get();
		User elizabeth = users.stream().filter(x -> x.getUsername().equals("elizabeth")).findFirst().get();
		User naruto = users.stream().filter(x -> x.getUsername().equals("naruto")).findFirst().get();
		User queen = users.stream().filter(x -> x.getUsername().equals("queen")).findFirst().get();
		User sauron = users.stream().filter(x -> x.getUsername().equals("sauron")).findFirst().get();
		User admin = users.stream().filter(x -> x.getUsername().equals("admin")).findFirst().get();
		User bob = users.stream().filter(x -> x.getUsername().equals("bob")).findFirst().get();
		User chuck = users.stream().filter(x -> x.getUsername().equals("chuck")).findFirst().get();
		User dakota = users.stream().filter(x -> x.getUsername().equals("dakota")).findFirst().get();

		//not suppose to exists
		User wanderer = users.stream().filter(x -> x.getUsername().equals("wanderer")).findFirst().orElse(null);
		User oscar = users.stream().filter(x -> x.getUsername().equals("oscar")).findFirst().orElse(null);
		User louis16 = users.stream().filter(x -> x.getUsername().equals("louis16")).findFirst().orElse(null);
		User sasuke = users.stream().filter(x -> x.getUsername().equals("sasuke")).findFirst().orElse(null);
		User moe = users.stream().filter(x -> x.getUsername().equals("moe")).findFirst().orElse(null);


		assertThat(dusty).isNotNull();
		assertThat(marie).isNotNull();
		assertThat(cartier).isNotNull();
		assertThat(colomb).isNotNull();
		assertThat(elizabeth).isNotNull();
		assertThat(naruto).isNotNull();
		assertThat(queen).isNotNull();
		assertThat(sauron).isNotNull();
		assertThat(admin).isNotNull();
		assertThat(bob).isNotNull();
		assertThat(chuck).isNotNull();
		assertThat(dakota).isNotNull();

		assertThat(wanderer).isNull();
		assertThat(oscar).isNull();
		assertThat(louis16).isNull();
		assertThat(sasuke).isNull();
		assertThat(moe).isNull();

		Group rumors = groups.stream().filter(x -> x.getCode().equals("rumors")).findFirst().get();
		Group explorers = groups.stream().filter(x -> x.getCode().equals("explorers")).findFirst().get();
		Group heroes = groups.stream().filter(x -> x.getCode().equals("heroes")).findFirst().get();
		Group legends = groups.stream().filter(x -> x.getCode().equals("legends")).findFirst().get();
		Group nobility = groups.stream().filter(x -> x.getCode().equals("nobility")).findFirst().get();
		Group villains = groups.stream().filter(x -> x.getCode().equals("villains")).findFirst().get();
		Group england = groups.stream().filter(x -> x.getCode().equals("england")).findFirst().get();
		Group france = groups.stream().filter(x -> x.getCode().equals("france")).findFirst().get();
		Group royale = groups.stream().filter(x -> x.getCode().equals("royale")).findFirst().get();
		Group bosses = groups.stream().filter(x -> x.getCode().equals("bosses")).findFirst().orElse(null);
		Group sidekicks = groups.stream().filter(x -> x.getCode().equals("sidekicks")).findFirst().get();

		assertThat(rumors).isNotNull();
		assertThat(explorers).isNotNull();
		assertThat(heroes).isNotNull();
		assertThat(legends).isNotNull();
		assertThat(nobility).isNotNull();
		assertThat(villains).isNotNull();
		assertThat(england).isNotNull();
		assertThat(france).isNotNull();
		assertThat(royale).isNotNull();
		assertThat(sidekicks).isNotNull();
	}

	@Test
	public void givenSystemIn9_0WWithLDAPValidateMigrationOf9_0And9_2ThenOk() {
		givenTransactionLogIsEnabled();
		File statesFolder = new SDKFoldersLocator().getInitialStatesFolder();
		File state = new File(statesFolder, "given_system_in_9.0.3_ldap.zip");

		getCurrentTestSession().getFactoriesTestFeatures()
				.givenSystemInState(state).withPasswordsReset()
				.withFakeEncryptionServices();

		ModelLayerFactory modelLayerFactory = getModelLayerFactory();

		List<String> collections = getAppLayerFactory().getCollectionsManager().getCollectionCodes();

		//Verify users

		List<User> users = new ArrayList<>();
		for (String collection :
				collections) {
			users.addAll(getAllUsersInCollection(modelLayerFactory, collection));
		}

		List<UserCredential> credentials = getUserCredentials(modelLayerFactory);

		//Les UserCredential sans collection sont supprimés?
		List<UserCredential> userCredentialsWithCollection = credentials.stream()
				.filter(credential -> !credential.getCollections().isEmpty())
				.collect(toList());

		//Check a certain user is not there? In LDAP case they are added to system collection?

		String collectiondef = collections.isEmpty() ? zeCollection : collections.get(0);
		MetadataSchema credentialMetadataSchema = modelLayerFactory.getMetadataSchemasManager().getSchemaTypes(Collection.SYSTEM_COLLECTION).getDefaultSchema(UserCredential.SCHEMA_TYPE);
		MetadataSchema userMetadataSchema = modelLayerFactory.getMetadataSchemasManager().getSchemaTypes(zeCollection).getDefaultSchema(User.SCHEMA_TYPE);
		MetadataSchema groupMetadataSchema = modelLayerFactory.getMetadataSchemasManager().getSchemaTypes(zeCollection).getDefaultSchema(Group.SCHEMA_TYPE);
		MetadataSchema globalGroupMetadataSchema = modelLayerFactory.getMetadataSchemasManager().getSchemaTypes(Collection.SYSTEM_COLLECTION).getDefaultSchema(GlobalGroup.SCHEMA_TYPE);

		//SyncMode est dans les utilisateurs?
		assertThat(credentialMetadataSchema.getMetadata(UserCredential.SYNC_MODE).getDataEntry().getType()).isEqualTo(CALCULATED);
		for (UserCredential user : credentials) {
			assertThat(user.getSyncMode()).isNotNull();
		}

		//Metadonnées retirées?
		assertThat(credentialMetadataSchema.hasMetadataWithCode("firstname")).isFalse();
		assertThat(credentialMetadataSchema.hasMetadataWithCode("lastname")).isFalse();
		assertThat(credentialMetadataSchema.hasMetadataWithCode("email")).isFalse();
		assertThat(credentialMetadataSchema.hasMetadataWithCode("personalEmails")).isFalse();
		assertThat(credentialMetadataSchema.hasMetadataWithCode("phone")).isFalse();
		assertThat(credentialMetadataSchema.hasMetadataWithCode("globalGroups")).isFalse();
		assertThat(credentialMetadataSchema.hasMetadataWithCode("fax")).isFalse();
		assertThat(credentialMetadataSchema.hasMetadataWithCode("jobTitle")).isFalse();
		assertThat(credentialMetadataSchema.hasMetadataWithCode("address")).isFalse();

		//Métadonnées déplacés dans users?
		assertThat(userMetadataSchema.hasMetadataWithCode(User.DOMAIN)).isTrue();
		assertThat(userMetadataSchema.hasMetadataWithCode(User.MS_EXCHANGE_DELEGATE_LIST)).isTrue();

		//Les utilisateurs qui n'étaient pas actifs sont supprimés logiquement?
		List<UserCredential> credentialsDeleted = credentials.stream()
				.filter(x -> !x.getStatus().equals(UserCredentialStatus.ACTIVE))
				.collect(toList());
		for (UserCredential credential : credentialsDeleted) {
			assertThat(credential.getStatus()).isEqualTo(UserCredentialStatus.DISABLED);
		}

		//physically removed users if not used. Need to be encoded.

		//Verify Groups

		List<Group> groups = new ArrayList<>();
		for (String collection :
				collections) {
			groups.addAll(getAllGroupsInCollection(modelLayerFactory, collection));
		}

		assertThat(globalGroupMetadataSchema.hasMetadataWithCode("usersAutomaticallyAddedToCollections")).isFalse();
		assertThat(globalGroupMetadataSchema.hasMetadataWithCode("status")).isFalse();
		assertThat(globalGroupMetadataSchema.hasMetadataWithCode("locallyCreated")).isFalse();
		assertThat(globalGroupMetadataSchema.hasMetadataWithCode("hierarchy")).isFalse();
		assertThat(groupMetadataSchema.hasMetadataWithCode("status")).isTrue();
		assertThat(groupMetadataSchema.hasMetadataWithCode("locallyCreated")).isTrue();
		assertThat(groupMetadataSchema.hasMetadataWithCode("hierarchy")).isTrue();

	}

	//helpers
	private List<GlobalGroup> getAllGlobalGroups(ModelLayerFactory modelLayerFactory) {
		SearchServices searchServices = modelLayerFactory.newSearchServices();
		SchemasRecordsServices systemSchemas = new SchemasRecordsServices(Collection.SYSTEM_COLLECTION, modelLayerFactory);

		List<Record> groupeRecord = searchServices.search(new LogicalSearchQuery(
				from(systemSchemas.globalGroupSchemaType()).returnAll()));
		return groupeRecord == null ? null : systemSchemas.wrapOldGlobalGroups(groupeRecord);
	}

	private List<UserCredential> getUserCredentials(ModelLayerFactory modelLayerFactory) {
		//This method may exist in UserServices for the moment, but we will try to remove it from the service
		SearchServices searchServices = modelLayerFactory.newSearchServices();
		SchemasRecordsServices systemSchemas = new SchemasRecordsServices(Collection.SYSTEM_COLLECTION, modelLayerFactory);

		List<Record> userCredentialRecord = searchServices.search(new LogicalSearchQuery(
				from(systemSchemas.credentialSchemaType()).returnAll()));
		return userCredentialRecord == null ? null : systemSchemas.wrapUserCredentials(userCredentialRecord);
	}

	private List<User> getAllUsersInCollection(ModelLayerFactory modelLayerFactory, String collection) {
		SearchServices searchServices = modelLayerFactory.newSearchServices();
		//This method may exist in UserServices for the moment, but we will try to remove it from the service
		SchemasRecordsServices schemas = new SchemasRecordsServices(collection, modelLayerFactory);
		List<Record> userRecord = searchServices.search(new LogicalSearchQuery(
				from(schemas.user.schemaType()).returnAll()));
		return userRecord == null ? null : schemas.wrapUsers(userRecord);
	}

	private List<Group> getAllGroupsInCollection(ModelLayerFactory modelLayerFactory, String collection) {
		SearchServices searchServices = modelLayerFactory.newSearchServices();
		//This method may exist in UserServices for the moment, but we will try to remove it from the service
		SchemasRecordsServices schemas = new SchemasRecordsServices(collection, modelLayerFactory);
		List<Record> groupRecord = searchServices.search(new LogicalSearchQuery(
				from(schemas.group.schemaType()).returnAll()));
		return groupRecord == null ? null : schemas.wrapGroups(groupRecord);
	}


	private RMMigrationTo9_2_AcceptanceTest.UserAssertions assertThatUser(String username) {
		return new RMMigrationTo9_2_AcceptanceTest.UserAssertions(username);
	}

	@AllArgsConstructor
	private class UserAssertions {

		String username;

		RMMigrationTo9_2_AcceptanceTest.UserAssertions isInCollections(String... expectedCollectionsArray) {
			List<String> expectedCollections = asList(expectedCollectionsArray);

			SystemWideUserInfos systemWideUser = userInfos(username);
			assertThat(systemWideUser).isNotNull();
			assertThat(systemWideUser.getCollections()).containsOnly(expectedCollectionsArray);

			for (String collection : asList(zeCollection, "LaCollectionDeRida")) {
				boolean expectedInThisCollection = expectedCollections.contains(collection);
				if (expectedInThisCollection) {
					assertThat(user(username, collection)).describedAs("User '" + username + "' is expected in collection '" + collection + "'").isNotNull();
				} else {
					assertThat(user(username, collection)).describedAs("User '" + username + "' is not expected in collection '" + collection + "'").isNull();
				}
			}

			return this;
		}

		public RMMigrationTo9_2_AcceptanceTest.UserAssertions doesNotExist() {
			assertThat(userInfos(username)).isNull();
			assertThat(user(username, "LaCollectionDeRida")).isNull();
			assertThat(user(username, "LaCollectionDeRida")).isNull();

			return this;
		}

		private RMMigrationTo9_2_AcceptanceTest.UserAssertions hasStatusIn(UserCredentialStatus expectedStatus,
																		   String collection) {
			User user = user(username, collection);
			assertThat(user).describedAs("Expecting user '" + username + "' to exist in collection '" + collection + "', but it does not").isNotNull();
			assertThat(userInfos(user.getUsername()).getStatus(user.getCollection()))
					.describedAs("Status in collection '" + collection + "'").isEqualTo(expectedStatus);
			assertThat(user.getStatus()).isEqualTo(expectedStatus);
			boolean expectedLogicallyDeletedStatus = expectedStatus != ACTIVE;
			assertThat(user.isLogicallyDeletedStatus()).isEqualTo(expectedLogicallyDeletedStatus);

			return this;
		}

		private RMMigrationTo9_2_AcceptanceTest.UserAssertions isPhysicicallyDeletedIn(String collection) {
			assertThat(userInfos(username).getStatus(collection)).isNull();
			assertThat(userInfos(username).getCollections()).doesNotContain(collection);
			assertThat(user(username, collection)).isNull();

			return this;
		}

		private RMMigrationTo9_2_AcceptanceTest.UserAssertions hasSyncMode(UserSyncMode mode) {
			assertThat(userInfos(username).getSyncMode()).isSameAs(mode);

			return this;
		}

		private RMMigrationTo9_2_AcceptanceTest.UserAssertions hasGroupsInCollection(String collection,
																					 String... groupCodes) {
			String[] groupIds = new String[groupCodes.length];
			for (int i = 0; i < groupCodes.length; i++) {
				groupIds[i] = group(groupCodes[i], collection).getId();
			}

			assertThat(user(username, collection).getUserGroups()).containsOnly(groupIds);

			return this;
		}

		public RMMigrationTo9_2_AcceptanceTest.UserAssertions hasName(String firstName, String lastName) {
			SystemWideUserInfos userInfos = getModelLayerFactory().newUserServices().getUserInfos(username);
			assertThat(userInfos.getFirstName()).isEqualTo(firstName);
			assertThat(userInfos.getLastName()).isEqualTo(lastName);

			for (String collection : userInfos.getCollections()) {
				assertThat(user(username, collection).getFirstName()).isEqualTo(firstName);
				assertThat(user(username, collection).getLastName()).isEqualTo(lastName);
			}


			return this;
		}

		public RMMigrationTo9_2_AcceptanceTest.UserAssertions hasEmail(String email) {
			SystemWideUserInfos userInfos = getModelLayerFactory().newUserServices().getUserInfos(username);
			assertThat(userInfos.getEmail()).isEqualTo(email);

			for (String collection : userInfos.getCollections()) {
				assertThat(user(username, collection).getEmail()).isEqualTo(email);
			}


			return this;
		}
	}


	private SystemWideUserInfos userInfos(String username) {
		try {
			return getModelLayerFactory().newUserServices().getUserInfos(username);

		} catch (UserServicesRuntimeException_NoSuchUser e) {
			return null;
		}
	}

	private User user(String username, String collection) {
		//This method may exist in UserServices for the moment, but we will try to remove it from the service
		return getModelLayerFactory().newUserServices().getUserInCollection(username, collection);
	}

	private Group group(String code, String collection) {
		return getModelLayerFactory().newUserServices().getGroupInCollection(code, collection);
	}

	private SystemWideGroup groupInfo(String code) {
		return getModelLayerFactory().newUserServices().getGroup(code);
	}


}