package com.constellio.app.ui.framework.components.fields.upload;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.vaadin.dialogs.ConfirmDialog;
import org.vaadin.easyuploads.FileBuffer;

import com.constellio.app.ui.framework.buttons.DeleteButton;
import com.constellio.app.ui.framework.containers.ButtonsContainer;
import com.constellio.app.ui.framework.containers.ButtonsContainer.ContainerButton;
import com.vaadin.data.Item;
import com.vaadin.data.Property;
import com.vaadin.data.util.IndexedContainer;
import com.vaadin.data.util.converter.Converter;
import com.vaadin.data.util.converter.Converter.ConversionException;
import com.vaadin.event.dd.DragAndDropEvent;
import com.vaadin.event.dd.DropHandler;
import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.navigator.ViewChangeListener;
import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.CustomField;
import com.vaadin.ui.DragAndDropWrapper;
import com.vaadin.ui.Label;
import com.vaadin.ui.Table;
import com.vaadin.ui.Table.ColumnHeaderMode;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

@SuppressWarnings("serial")
public class BaseUploadField extends CustomField<Object> implements DropHandler {

	public static final String STYLE_NAME = "base-upload-field";

	private static final String CAPTION_PROPERTY_ID = "caption";

	private boolean multiValue;

	private VerticalLayout mainLayout;

	private BaseMultiFileUpload multiFileUpload;

	private ButtonsContainer<IndexedContainer> fileUploadsContainer;

	private Table fileUploadsTable;

	private Map<Object, Component> itemCaptions = new HashMap<>();

	private ViewChangeListener viewChangeListener;

	private boolean haveDeleteButton;

	public BaseUploadField()
	{
		this(true);
	}

	public BaseUploadField(boolean haveDeleteButton) {
		super();
		
		setSizeFull();

		this.haveDeleteButton = haveDeleteButton;

		mainLayout = new VerticalLayout();
		mainLayout.setSizeFull();
		mainLayout.setSpacing(true);

		multiFileUpload = new BaseMultiFileUpload() {
			@SuppressWarnings("unchecked")
			@Override
			protected void handleFile(File file, String fileName, String mimeType, long length) {
				file.deleteOnExit();
				TempFileUpload newTempFileUpload = new TempFileUpload(fileName, mimeType, length, file);
				Object newConvertedValue;
				Converter<Object, Object> converter = BaseUploadField.this.getConverter();
				if (converter != null) {
					newConvertedValue = converter.convertToModel(newTempFileUpload, Object.class, getLocale());
				} else {
					newConvertedValue = newTempFileUpload;
				}
				if (!isMultiValue()) {
					if (!newConvertedValue.equals(getConvertedValue())) {
						deleteTempFiles();
						BaseUploadField.this.setValue(newConvertedValue);
					}
				} else {
					List<Object> previousListValue = (List<Object>) BaseUploadField.this.getValue();
					List<Object> newListValue = new ArrayList<Object>();
					if (previousListValue != null) {
						newListValue.addAll(previousListValue);
					}
					if (!newListValue.contains(newConvertedValue)) {
						newListValue.add(newConvertedValue);
					}
					BaseUploadField.this.setValue(newListValue);
				}
			}

			@Override
			protected FileBuffer createReceiver() {
				FileBuffer receiver = super.createReceiver();
				/*
                 * Make receiver not to deleteLogically files after they have been
                 * handled by #handleFile().
                 */
				receiver.setDeleteFiles(false);
				return receiver;
			}
		};
		multiFileUpload.setWidth("100%");

		addValueChangeListener(new ValueChangeListener() {
			@SuppressWarnings("unchecked")
			@Override
			public void valueChange(Property.ValueChangeEvent event) {
				List<Object> listValue;
				Object newValue = BaseUploadField.this.getValue();
				if (newValue instanceof List) {
					listValue = (List<Object>) newValue;
				} else {
					listValue = new ArrayList<Object>();
					if (newValue != null) {
						listValue.add(newValue);
					}
				}

				itemCaptions.clear();
				fileUploadsContainer.removeAllItems();
				for (Object listValueElement : listValue) {
					Object itemId = listValueElement;
					fileUploadsContainer.addItem(itemId);
					Item listValueElementItem = fileUploadsContainer.getItem(itemId);
					Component itemCaption = newItemCaption(itemId);
					listValueElementItem.getItemProperty(CAPTION_PROPERTY_ID).setValue(itemCaption);
					itemCaptions.put(itemId, itemCaption);
				}
			}
		});

		fileUploadsContainer = new ButtonsContainer<>(new IndexedContainer());
		fileUploadsContainer.addContainerProperty(CAPTION_PROPERTY_ID, Component.class, null);

		if (haveDeleteButton)
		{
				fileUploadsContainer.addButton(new ContainerButton() {
				@Override
				protected Button newButtonInstance(final Object itemId, ButtonsContainer<?> container) {

					DeleteButton deleteButton = new DeleteButton() {
						@SuppressWarnings("unchecked")
						@Override
						protected void confirmButtonClick(ConfirmDialog dialog) {
							if (itemId instanceof TempFileUpload) {
								TempFileUpload tempFileUpload = (TempFileUpload) itemId;
								tempFileUpload.delete();
							} else {
								deleteTempFile(itemId);
							}
							if (!isMultiValue()) {
								BaseUploadField.this.setValue(null);
							} else {
								List<Object> previousListValue = (List<Object>) BaseUploadField.this.getValue();
								List<Object> newListValue = new ArrayList<Object>(previousListValue);
								newListValue.remove(itemId);
								BaseUploadField.this.setValue(newListValue);
							}
						}
					};
					deleteButton.setReadOnly(BaseUploadField.this.isReadOnly());
					deleteButton.setEnabled(BaseUploadField.this.isEnabled());
					deleteButton.setVisible(isDeleteLink(itemId));
					return deleteButton;
				}
			});
		}
		fileUploadsTable = new Table();

		fileUploadsTable.setContainerDataSource(fileUploadsContainer);
		fileUploadsTable.setPageLength(0);
		fileUploadsTable.setWidth("100%");
		fileUploadsTable.setColumnHeaderMode(ColumnHeaderMode.HIDDEN);
		fileUploadsTable.setColumnWidth(ButtonsContainer.DEFAULT_BUTTONS_PROPERTY_ID, 47);

		mainLayout.addComponents(multiFileUpload, fileUploadsTable);
	}
	
	@Override
	public void attach() {
		super.attach();
		
		if (viewChangeListener == null) {
			viewChangeListener = new ViewChangeListener() {
				@Override
				public boolean beforeViewChange(ViewChangeEvent event) {
					return true;
				}
				
				@Override
				public void afterViewChange(ViewChangeEvent event) {
					deleteTempFiles();
					UI.getCurrent().getNavigator().removeViewChangeListener(viewChangeListener);
				}
			};
			UI.getCurrent().getNavigator().addViewChangeListener(viewChangeListener);
		}
	}

	protected VerticalLayout getMainLayout() {
		return mainLayout;
	}

	protected Object getItemId(TempFileUpload tempFileUpload) {
		return tempFileUpload;
	}
	
	protected Component getItemCaption(Object itemId) {
		return itemCaptions.get(itemId);
	}
	
	protected Map<Object, Component> getItemCaptions() {
		return Collections.unmodifiableMap(itemCaptions);
	}

	protected Component newItemCaption(Object itemId) {
		String itemCaption;
		if (itemId instanceof TempFileUpload) {
			TempFileUpload tempFileUpload = (TempFileUpload) itemId;
			itemCaption = tempFileUpload.getFileName();
		} else {
			itemCaption = itemId.toString();
		}
		return new Label(itemCaption);
	}

	@Override
	public void setInternalValue(Object newValue) {
		super.setInternalValue(newValue);
	}

	protected void deleteTempFile(Object itemId) {

	}

	protected boolean isDeleteLink(Object itemId) {
		return true;
	}

	@Override
	public void drop(DragAndDropEvent event) {
		multiFileUpload.drop(event);
	}

	@Override
	public AcceptCriterion getAcceptCriterion() {
		return multiFileUpload.getAcceptCriterion();
	}

	@Override
	protected Component initContent() {
		DragAndDropWrapper dragAndDropWrapper = new DragAndDropWrapper(mainLayout);
		dragAndDropWrapper.setDropHandler(this);
		return dragAndDropWrapper;
	}

	@SuppressWarnings("unchecked")
	protected final void deleteTempFiles() {
		Object currentValue = getInternalValue();
		if (currentValue instanceof TempFileUpload) {
			TempFileUpload tempFileUpload = (TempFileUpload) currentValue;
			tempFileUpload.delete();
		} else if (currentValue instanceof List) {
			List<Object> currentListValue = (List<Object>) currentValue;
			for (Object currentListElement : currentListValue) {
				if (currentListElement instanceof TempFileUpload) {
					TempFileUpload tempFileUpload = (TempFileUpload) currentListElement;
					tempFileUpload.delete();
				} else {
					deleteTempFile(currentListElement);
				}
			}
		} else if (currentValue != null) {
			deleteTempFile(currentValue);
		}
	}

	@Override
	public void discard()
			throws SourceException {
		super.discard();
		deleteTempFiles();
	}

	public final boolean isMultiValue() {
		return multiValue;
	}

	public final void setMultiValue(boolean multiValue) {
		this.multiValue = multiValue;
	}

	public String getUploadButtonCaption() {
		return multiFileUpload.getUploadButtonCaption();
	}

	public void setUploadButtonCaption(String uploadButtonCaption) {
		multiFileUpload.setUploadButtonCaption(uploadButtonCaption);
	}

	public final String getDropZoneCaption() {
		return multiFileUpload.getDropZoneCaption();
	}

	public final void setDropZoneCaption(String dropZoneCaption) {
		multiFileUpload.setDropZoneCaption(dropZoneCaption);
	}

	@Override
	public Class<? extends Object> getType() {
		Class<?> type;
		if (isMultiValue()) {
			type = List.class;
		} else {
			type = Object.class;
		}
		return type;
	}

	@Override
	public void setValue(Object newFieldValue)
			throws com.vaadin.data.Property.ReadOnlyException, ConversionException {
		super.setValue(newFieldValue);
	}

}
