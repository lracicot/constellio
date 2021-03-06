package com.constellio.app.ui.framework.components.fields.lookup;

import java.util.ArrayList;
import java.util.List;

import com.constellio.app.modules.rm.ui.components.converters.DocumentIdToContextCaptionConverter;
import com.constellio.app.ui.application.ConstellioUI;
import com.constellio.app.ui.entities.UserVO;
import com.constellio.app.ui.framework.components.converters.ConverterWithCache;
import com.constellio.app.ui.framework.data.ObjectsResponse;
import com.constellio.app.ui.pages.base.SessionContext;
import com.constellio.data.utils.AccentApostropheCleaner;
import com.constellio.model.entities.records.wrappers.User;
import com.constellio.model.services.factories.ModelLayerFactory;
import com.constellio.model.services.users.UserServices;
import com.vaadin.data.util.converter.Converter;
import com.vaadin.server.Resource;

public class DocumentLookupField extends LookupField<String> {

	public DocumentLookupField(List<String> options) {
		super(new DocumentTextInputDataProvider(options, new DocumentIdToContextCaptionConverter()),
				new DocumentLookupTreeDataProvider(options, new DocumentIdToContextCaptionConverter()));
		setItemConverter(new DocumentIdToContextCaptionConverter());
	}

	public void setOptions(List<String> options) {
		for (LookupTreeDataProvider<String> lookUpTreeDataProviders : this.getLookupTreeDataProviders()) {
			if (lookUpTreeDataProviders instanceof DocumentLookupTreeDataProvider) {
				(((DocumentLookupTreeDataProvider) lookUpTreeDataProviders).getTextInputDataProvider()).setOptions(options);
			}
		}
		((DocumentTextInputDataProvider) this.suggestInputDataProvider).setOptions(options);
	}

	@Override
	public ConverterWithCache getNiceTitleItemConverter() {
		return new ConverterWithCache(new DocumentIdToContextCaptionConverter());
	}

	@Override
	public Class<? extends String> getType() {
		return String.class;
	}

	private static class DocumentTextInputDataProvider extends TextInputDataProvider<String> {

		private List<String> options;

		private Converter<String, String> itemConverter;

		private DocumentTextInputDataProvider(List<String> options, Converter<String, String> itemConverter) {
			this.options = options;
			this.itemConverter = itemConverter;
		}

		public List<String> getOptions() {
			return options;
		}

		public void setOptions(List<String> options) {
			this.options = options;
		}

		private List<String> filter(String text) {
			List<String> results = new ArrayList<>();
			if (text != null) {
				text = AccentApostropheCleaner.cleanAll(text.toLowerCase());
				if (options == null) {
					options = new ArrayList<>();
				}
				for (String option : options) {
					String optionText;
					if (itemConverter != null) {
						optionText = itemConverter
								.convertToPresentation(option, String.class, ConstellioUI.getCurrent().getLocale());
					} else {
						optionText = option;
					}
					optionText = AccentApostropheCleaner.cleanAll(optionText.toLowerCase());
					if (optionText.toLowerCase().contains(text)) {
						results.add(option);
					}
				}
			} else {
				results.addAll(options);
			}
			return results;
		}

		@Override
		public List<String> getData(String text, int startIndex, int count) {
			if (text == null) {
				text = "";
			}
			List<String> results = filter(text);
			int end = Math.min(startIndex + count, results.size());
			return results.subList(startIndex, end);
		}

		@Override
		public ModelLayerFactory getModelLayerFactory() {
			return ConstellioUI.getCurrent().getConstellioFactories().getModelLayerFactory();
		}

		@Override
		public int size(String text) {
			return filter(text).size();
		}

		@Override
		public User getCurrentUser() {
			SessionContext sessionContext = ConstellioUI.getCurrentSessionContext();
			ModelLayerFactory modelLayerFactory = getModelLayerFactory();
			String currentCollection = sessionContext.getCurrentCollection();
			UserVO currentUserVO = sessionContext.getCurrentUser();
			UserServices userServices = modelLayerFactory.newUserServices();
			return userServices.getUserInCollection(currentUserVO.getUsername(), currentCollection);
		}

		@Override
		public void setOnlyLinkables(boolean onlyLinkables) {
			// Ignore
		}
	}

	private static class DocumentLookupTreeDataProvider implements LookupTreeDataProvider<String> {

		private Converter<String, String> itemConverter;

		private DocumentLookupField.DocumentTextInputDataProvider textInputDataProvider;

		private DocumentLookupTreeDataProvider(List<String> options, Converter<String, String> itemConverter) {
			this.itemConverter = itemConverter;
			this.textInputDataProvider = new DocumentLookupField.DocumentTextInputDataProvider(options, itemConverter);
		}

		public DocumentLookupField.DocumentTextInputDataProvider getTextInputDataProvider() {
			return textInputDataProvider;
		}

		@Override
		public ObjectsResponse<String> getRootObjects(int start, int maxSize) {
			List<String> results = textInputDataProvider.getData(null, start, maxSize);
			return new ObjectsResponse<>(results, new Long(results.size()));
		}

		@Override
		public String getParent(String child) {
			return null;
		}

		@Override
		public ObjectsResponse<String> getChildren(String parent, int start, int maxSize) {
			return new ObjectsResponse<String>(new ArrayList<String>(), 0L);
		}

		@Override
		public boolean hasChildren(String parent) {
			return false;
		}

		@Override
		public boolean isLeaf(String object) {
			return true;
		}

		@Override
		public String getTaxonomyCode() {
			return null;
		}

		@Override
		public String getCaption(String id) {
			return itemConverter != null ?
					itemConverter.convertToPresentation(id, String.class, ConstellioUI.getCurrent().getLocale()) :
					id;
		}

		@Override
		public String getDescription(String id) {
			return null;
		}

		@Override
		public Resource getIcon(String id, boolean expanded) {
			return null;
		}

		@Override
		public int getEstimatedRootNodesCount() {
			return textInputDataProvider.size(null);
		}

		@Override
		public int getEstimatedChildrenNodesCount(String parent) {
			return 0;
		}

		@Override
		public void addDataRefreshListener(DataRefreshListener dataRefreshListener) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<DataRefreshListener> getDataRefreshListeners() {
			throw new UnsupportedOperationException();
		}

		@Override
		public void removeDataRefreshListener(DataRefreshListener dataRefreshListener) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void fireDataRefreshEvent() {
			throw new UnsupportedOperationException();
		}

		@Override
		public com.constellio.app.ui.framework.components.fields.lookup.LookupField.TextInputDataProvider<String> search() {
			return textInputDataProvider;
		}

		@Override
		public com.constellio.app.ui.framework.components.fields.lookup.LookupField.TextInputDataProvider<String> searchWithoutDisabled() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isSelectable(String selection) {
			return true;
		}
	}
}
