package com.constellio.data.dao.dto.records;

import com.constellio.data.utils.LangUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Main advantage of this interface is to delay as longer as possible the transformation of integer ids to String,
 * which are then less efficient for comparison
 */
public interface RecordId extends Comparable, Serializable, Supplier<RecordId> {

	String stringValue();

	int intValue();

	boolean isInteger();

	boolean lesserThan(RecordId anotherRecordId);

	boolean lesserOrEqual(RecordId anotherRecordId);

	boolean greaterThan(RecordId anotherRecordId);

	boolean greaterOrEqual(RecordId anotherRecordId);

	default RecordId get() {
		return this;
	}

	static RecordId id(String stringValue) {
		return toId(stringValue);
	}

	static List<RecordId> toIds(List<?> values) {
		List<RecordId> ids = new ArrayList<>();

		for (Object value : values) {
			if (value instanceof String) {
				ids.add(toId((String) value));
			} else {
				ids.add(toId((Integer) value));
			}
		}

		return ids;
	}

	static int toIntId(String stringValue) {
		RecordId recordId = toId(stringValue);
		return recordId.intValue();
	}

	static boolean isIntId(String stringValue) {
		long intValue = LangUtils.tryParseLong(stringValue, -1);
		return intValue != -1;
	}


	static RecordId toId(String stringValue) {
		if (stringValue == null) {
			return null;

		} else if (stringValue.length() == 11) {
			long intValue = LangUtils.tryParseLong(stringValue, -1);
			return intValue == -1 ? new StringRecordId(stringValue) : new IntegerRecordId((int) intValue);

		} else {
			return new StringRecordId(stringValue);
		}
	}

	static RecordId id(int intValue) {
		return toId(intValue);
	}

	static RecordId toId(int intValue) {
		if (intValue < 0) {
			return new StringRecordId(intValue);
		} else {
			return new IntegerRecordId(intValue);
		}
	}

	static List<String> toStringIds(List<Integer> intIds) {
		List<String> stringIds = new ArrayList<>();

		for (int intId : intIds) {
			stringIds.add(RecordId.toId(intId).stringValue());
		}

		return stringIds;
	}
}