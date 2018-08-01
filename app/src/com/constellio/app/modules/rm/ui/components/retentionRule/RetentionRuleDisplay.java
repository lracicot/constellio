package com.constellio.app.modules.rm.ui.components.retentionRule;

import com.constellio.app.modules.rm.ui.components.retentionRule.RetentionRuleDisplayFactory.RetentionRuleDisplayPresenter;
import com.constellio.app.modules.rm.ui.entities.RetentionRuleVO;
import com.constellio.app.ui.framework.components.RecordDisplay;
import com.vaadin.ui.Component;
import com.vaadin.ui.Label;

import java.util.Locale;

public class RetentionRuleDisplay extends RecordDisplay {

	public RetentionRuleDisplay(RetentionRuleDisplayPresenter presenter, RetentionRuleVO retentionRuleVO,
								Locale locale) {
		super(retentionRuleVO, new RetentionRuleDisplayFactory(presenter, locale));

		addStyleName("retention-rule-display");
		mainLayout.setWidth("100%");
	}

	@Override
	protected void addCaptionAndDisplayComponent(Label captionLabel, Component displayComponent) {
		if (displayComponent instanceof FolderCopyRetentionRuleTable) {
			FolderCopyRetentionRuleTable folderCopyRetentionRuleTable = (FolderCopyRetentionRuleTable) displayComponent;
			folderCopyRetentionRuleTable.setCaption(captionLabel.getValue());
			folderCopyRetentionRuleTable.setWidth("100%");
			mainLayout.addComponent(folderCopyRetentionRuleTable);
		} else if (displayComponent instanceof DocumentCopyRetentionRuleTable) {
			DocumentCopyRetentionRuleTable documentCopyRetentionRuleTable = (DocumentCopyRetentionRuleTable) displayComponent;
			documentCopyRetentionRuleTable.setCaption(captionLabel.getValue());
			documentCopyRetentionRuleTable.setWidth("100%");
			mainLayout.addComponent(documentCopyRetentionRuleTable);
		} else if (displayComponent instanceof DocumentDefaultCopyRetentionRuleTable) {
			DocumentDefaultCopyRetentionRuleTable documentDefaultCopyRetentionRuleTable = (DocumentDefaultCopyRetentionRuleTable) displayComponent;
			documentDefaultCopyRetentionRuleTable.setCaption(captionLabel.getValue());
			documentDefaultCopyRetentionRuleTable.setWidth("100%");
			mainLayout.addComponent(documentDefaultCopyRetentionRuleTable);
		} else {
			super.addCaptionAndDisplayComponent(captionLabel, displayComponent);
		}
	}

	@Override
	protected boolean isCaptionAndDisplayComponentWidthUndefined() {
		return true;
	}

}
