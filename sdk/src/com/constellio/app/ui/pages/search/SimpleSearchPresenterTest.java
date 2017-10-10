package com.constellio.app.ui.pages.search;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.constellio.model.entities.records.Transaction;
import com.constellio.model.entities.records.wrappers.Capsule;
import com.constellio.model.services.factories.ModelLayerFactory;
import com.constellio.model.services.records.SchemasRecordsServices;
import com.constellio.model.services.schemas.MetadataSchemasManager;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.constellio.app.entities.schemasDisplay.SchemaTypesDisplayConfig;
import com.constellio.app.services.schemasDisplay.SchemasDisplayManager;
import com.constellio.model.entities.enums.SearchSortType;
import com.constellio.model.entities.records.wrappers.SavedSearch;
import com.constellio.model.entities.schemas.Metadata;
import com.constellio.model.services.extensions.ConstellioModulesManager;
import com.constellio.model.services.migrations.ConstellioEIMConfigs;
import com.constellio.model.services.parser.LanguageDetectionManager;
import com.constellio.model.services.search.query.logical.LogicalSearchQuery;
import com.constellio.sdk.tests.ConstellioTest;
import com.constellio.sdk.tests.FakeSessionContext;
import com.constellio.sdk.tests.MockedFactories;
import com.constellio.sdk.tests.MockedNavigation;

import java.util.List;

public class SimpleSearchPresenterTest extends ConstellioTest {
	public static final String EXPRESSION = "zexpression";
	public static final String FACET_CODE = "zeField_s";

	@Mock ConstellioModulesManager modulesManager;
	@Mock SimpleSearchView view;
	MockedNavigation navigator;
	@Mock LanguageDetectionManager detectionManager;
	@Mock LogicalSearchQuery query;
	@Mock Metadata metadata;
	@Mock SchemasDisplayManager schemasDisplayManager;
	@Mock SchemaTypesDisplayConfig typesDisplayConfig;
	@Mock MetadataSchemasManager metadataSchemasManager;
	@Mock ModelLayerFactory modelLayerFactory;
	MockedFactories factories = new MockedFactories();
	SchemasRecordsServices schemasRecordsServices;

	SimpleSearchPresenter presenter;
	@Mock
	private ConstellioEIMConfigs mockedConfigs;

	@Before
	public void setUp() {
		schemasRecordsServices = new SchemasRecordsServices(zeCollection, factories.getModelLayerFactory());
		when(view.getConstellioFactories()).thenReturn(factories.getConstellioFactories());
		when(view.getSessionContext()).thenReturn(FakeSessionContext.gandalfInCollection(zeCollection));

		when(view.navigate()).thenReturn(navigator);
		when(view.getCollection()).thenReturn(zeCollection);

		when(factories.getAppLayerFactory().getMetadataSchemasDisplayManager()).thenReturn(schemasDisplayManager);
		when(factories.getAppLayerFactory().getModelLayerFactory().getMetadataSchemasManager()).thenReturn(metadataSchemasManager);
		when(schemasDisplayManager.getTypes(zeCollection)).thenReturn(typesDisplayConfig);
		when(factories.getModelLayerFactory().getSystemConfigs()).thenReturn(mockedConfigs);
		when(mockedConfigs.getSearchSortType()).thenReturn(SearchSortType.RELEVENCE);
		when(factories.getAppLayerFactory().getModulesManager()).thenReturn(modulesManager);
		presenter = spy(new SimpleSearchPresenter(view));
	}

	@Test
	public void givenParametersWithSearchExpressionAndPageNumberThenBothAreSaved() {
		doReturn(mock(SavedSearch.class)).when(presenter).saveTemporarySearch(anyBoolean());
		presenter.forRequestParameters("q/zexpression/42");
		assertThat(presenter.getUserSearchExpression()).isEqualTo(EXPRESSION);
		assertThat(presenter.getPageNumber()).isEqualTo(42);
		assertThat(presenter.mustDisplayResults()).isTrue();
	}

	@Test
	public void givenParametersWithSearchExpressionWithoutPageNumberThenSearchExpressionIsSavedAndPageNumberIsSetToOne() {
		doReturn(mock(SavedSearch.class)).when(presenter).saveTemporarySearch(anyBoolean());
		presenter.forRequestParameters("q/" + EXPRESSION);
		assertThat(presenter.getUserSearchExpression()).isEqualTo(EXPRESSION);
		assertThat(presenter.getPageNumber()).isEqualTo(1);
		assertThat(presenter.mustDisplayResults()).isTrue();
	}

	@Test
	public void givenEmptyParametersTheSearchExpressionIsEmpty() {
		presenter.forRequestParameters("");
		assertThat(presenter.getUserSearchExpression()).isEqualTo("");
		assertThat(presenter.mustDisplayResults()).isFalse();
	}

	@Test
	public void givenNullParametersTheSearchExpressionIsEmpty() {
		presenter.forRequestParameters(null);
		assertThat(presenter.getUserSearchExpression()).isEqualTo("");
		assertThat(presenter.mustDisplayResults()).isFalse();
	}
}
