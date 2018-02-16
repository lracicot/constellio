package com.constellio.model.frameworks.validation;

import java.io.Serializable;
import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class ValidationError implements Serializable {

	private final Class<?> validatorClass;
	private final String errorCode;
	private final String code;
	private final Map<String, Object> parameters;

	public ValidationError(Class<?> validatorClass, String errorCode, Map<String, Object> parameters) {
		this.validatorClass = validatorClass;
		this.errorCode = errorCode;
		this.code = validatorClass.getName() + "_" + errorCode;
		this.parameters = parameters;
	}

	public Class<?> getValidatorClass() {
		return validatorClass;
	}

	public String getValidatorErrorCode() {
		return errorCode;
	}

	public String getCode() {
		return code;
	}

	public Map<String, Object> getParameters() {
		return parameters;
	}

	@Override
	public String toString() {
		return "ValidationError [code=" + code + ", parameters=" + parameters + "]";
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	public String toErrorSummaryString() {
		if (parameters == null || parameters.isEmpty()) {
			return code;
		} else {

			StringBuilder stringBuilder = new StringBuilder(code + "[");

			boolean first = true;
			for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
				if (!first) {
					stringBuilder.append(",");
				}
				Object parameterValue = parameter.getValue();
				if (parameterValue instanceof String) {
					stringBuilder.append(parameter.getKey() + "=" + parameterValue);
				} else if (parameterValue instanceof Map) {
					// TODO Pat manage Map value here...
					Map<String, String> labelsMap = (Map<String, String>) parameterValue;
					boolean firstLabel = true;
					stringBuilder.append(" ");
					for (Map.Entry<String, String> labelsEntry : labelsMap.entrySet()) {
						if (!firstLabel) {
							stringBuilder.append(",");
						}
						stringBuilder.append(labelsEntry.getKey() + "=" + labelsEntry.getValue());
						firstLabel = false;
					}
				}
				first = false;
			}

			return stringBuilder.toString() + "]";
		}
	}

	public String toMultilineErrorSummaryString() {
		StringBuilder sb = new StringBuilder();
		sb.append(code);
		for (Map.Entry<String, Object> entry : parameters.entrySet()) {
			Object entryValue = entry.getValue();
			if (entryValue instanceof String) {
				sb.append("\n\t" + entry.getKey() + "=" + entryValue);
			} else if (entryValue instanceof Map) {
				// TODO Pat manage Map value here...
				Map<String, String> labelsMap = (Map<String, String>) entryValue;
				boolean firstLabel = true;
				for (Map.Entry<String, String> labelsEntry : labelsMap.entrySet()) {
					if (!firstLabel) {
						sb.append(",");
					}
					sb.append(labelsEntry.getKey() + "=" + labelsEntry.getValue());
					firstLabel = false;
				}
			}
		}
		return sb.toString();
	}

	public Object getParameter(String key) {
		return parameters.get(key);
	}
}
