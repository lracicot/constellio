package com.constellio.app.ui.framework.buttons;

import com.constellio.app.services.factories.AppLayerFactory;
import com.constellio.app.services.guide.GuideManager;
import com.constellio.app.ui.application.ConstellioUI;
import com.vaadin.navigator.View;
import com.vaadin.ui.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.constellio.app.ui.framework.components.BaseForm.SAVE_BUTTON;
import static com.constellio.app.ui.i18n.i18n.$;
import static com.vaadin.ui.themes.ValoTheme.BUTTON_PRIMARY;


/*
todo:
 - refactor getGuideUrl() de BaseView en Map<Langue, URL> au lieu de string (son propre objet idéallement)
 - passer ça au bouton ici
 - setter les nouvelles infos dans save
 - faire le visuel
 */


public class GuideConfigButton extends WindowButton {
	private static String KEY_PREFIX = "guide.";
	private AppLayerFactory appLayerFactory;
	private String collection;
	private VerticalLayout mainLayout;

	List<String> languages;

	public GuideConfigButton(String caption, String windowCaption, WindowConfiguration configuration,
							 AppLayerFactory appLayerFactory) {
		super(caption, windowCaption, configuration);
		this.appLayerFactory = appLayerFactory;
		this.collection = ConstellioUI.getCurrentSessionContext().getCurrentCollection();
	}

	private String generateGuideKey(View view) {
		if (view == null) {
			return null;
		}
		return KEY_PREFIX + view.getClass().getSimpleName();
	}

	@Override
	protected Component buildWindowContent() {
		mainLayout = new VerticalLayout();
		mainLayout.setSpacing(true);
		this.languages = appLayerFactory.getCollectionsManager().getCollectionLanguages(collection);
		for (String languageCode : languages) {
			String labelText = "Documentation en " + $("Language." + languageCode);
			mainLayout.addComponent(new Label(labelText));

			TextField inputField = new TextField();
			inputField.setId(languageCode);
			inputField.setInputPrompt("url");
			inputField.setWidth("500px");
			mainLayout.addComponent(inputField);
		}
		mainLayout.addComponent(buildButtonsLayout());
		return mainLayout;
	}

	private HorizontalLayout buildButtonsLayout() {
		HorizontalLayout buttonsLayout = new HorizontalLayout();
		buttonsLayout.addComponent(buildSaveButton());
		buttonsLayout.addComponent(buildCancelButton());
		return buttonsLayout;
	}

	private Button buildSaveButton() {
		Button saveButton = new Button($("save"));
		saveButton.addStyleName(SAVE_BUTTON);
		saveButton.addStyleName(BUTTON_PRIMARY);
		saveButton.addClickListener(new ClickListener() {
			@Override
			public void buttonClick(ClickEvent event) {
				String guideKey = generateGuideKey(ConstellioUI.getCurrent().getCurrentView());
				Map<String, String> newValues = getNewUrlValues();
				GuideManager guideManager = new GuideManager(appLayerFactory.getModelLayerFactory().getDataLayerFactory());
				for (String language : languages) {
					guideManager.alterProperty(language, guideKey, newValues.get(language));
				}
				getWindow().close();
			}
		});
		return saveButton;
	}

	private Map<String, String> getNewUrlValues() {
		Map<String, String> newValues = new HashMap<>();
		for (String language : languages) {
			Component inputField = findComponentById(mainLayout.getParent(), language);
			if (inputField instanceof TextField) {
				String newUrl = ((TextField) inputField).getValue();
				if (!newUrl.isEmpty()) {
					newValues.put(language, newUrl);
				}
			}
		}
		return newValues;
	}


	private Button buildCancelButton() {
		Button cancelButton = new Button($("cancel"));
		cancelButton.addClickListener(new ClickListener() {
			@Override
			public void buttonClick(ClickEvent event) {
				getWindow().close();
			}
		});
		return cancelButton;
	}

	public static Component findComponentById(HasComponents root, String id) {
		for (Component child : root) {
			if (id.equals(child.getId())) {
				return child;
			} else if (child instanceof HasComponents) {
				Component result = findComponentById((HasComponents) child, id);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}


}
