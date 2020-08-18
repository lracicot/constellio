package com.constellio.app.api.extensions;

import com.constellio.app.api.extensions.params.ConvertStructureToMapParams;
import com.constellio.app.api.extensions.params.IsMetadataExportedParams;
import com.constellio.app.api.extensions.params.IsRecordExportableParams;
import com.constellio.app.api.extensions.params.OnWriteRecordParams;
import com.constellio.data.frameworks.extensions.ExtensionBooleanResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class RecordExportExtension {

	public Map<String, Object> convertStructureToMap(ConvertStructureToMapParams params) {
		return null;
	}

	public void onWriteRecord(OnWriteRecordParams params) {

	}

	public List<String> getUnwantedTaxonomiesForExportation() {
		return null;
	}

	public Set<String> getHashsToInclude() {
		return Collections.emptySet();
	}

	public ExtensionBooleanResult isExportable(IsRecordExportableParams params) {
		return ExtensionBooleanResult.TRUE;
	}

	public ExtensionBooleanResult isMetadataExportForced(IsMetadataExportedParams params) {
		return null;
	}
}