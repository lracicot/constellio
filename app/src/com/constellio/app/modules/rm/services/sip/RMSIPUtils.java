package com.constellio.app.modules.rm.services.sip;

import com.constellio.app.modules.rm.services.RMSchemasRecordsServices;
import com.constellio.app.modules.rm.wrappers.Category;
import com.constellio.app.services.sip.mets.MetsDivisionInfo;

import java.util.HashMap;
import java.util.Map;

public class RMSIPUtils {


	public static Map<String, MetsDivisionInfo> buildCategoryDivisionInfos(RMSchemasRecordsServices rm) {
		Map<String, MetsDivisionInfo> divisionInfoMap = new HashMap<>();
		for (Category category : rm.getAllCategories()) {
			String parentCode = category.getParent() == null ? null : Category.SCHEMA_TYPE + "-" + rm.getCategory(category.getParent()).getCode();
			String code = Category.SCHEMA_TYPE + "-" + category.getCode();
			divisionInfoMap.put(code, new MetsDivisionInfo(code, parentCode, category.getTitle(), Category.SCHEMA_TYPE));
		}
		return divisionInfoMap;
	}

}
