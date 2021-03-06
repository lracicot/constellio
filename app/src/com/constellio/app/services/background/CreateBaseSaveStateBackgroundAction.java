package com.constellio.app.services.background;

import com.constellio.app.services.factories.AppLayerFactory;
import com.constellio.app.services.importExport.systemStateExport.SystemStateExporter;
import com.constellio.data.conf.FoldersLocator;
import com.constellio.data.utils.TimeProvider;
import org.joda.time.LocalDate;


public class CreateBaseSaveStateBackgroundAction implements Runnable {


	private AppLayerFactory appLayerFactory;
	private LocalDate lastGenerate = TimeProvider.getLocalDate().minusDays(5);

	public CreateBaseSaveStateBackgroundAction(AppLayerFactory appLayerFactory) {
		this.appLayerFactory = appLayerFactory;
	}

	@Override
	public void run() {

		if (!isOfficeHours()
			&& (lastGenerate.isBefore(TimeProvider.getLocalDate().minusDays(5)))
			&& FoldersLocator.usingAppWrapper()) {
			new SystemStateExporter(appLayerFactory).createSavestateBaseFileInVault(false);
			lastGenerate = TimeProvider.getLocalDate();
		}

	}

	protected boolean isOfficeHours() {
		return TimeProvider.getLocalDateTime().getHourOfDay() >= 7
			   && TimeProvider.getLocalDateTime().getHourOfDay() <= 18;
	}

}
