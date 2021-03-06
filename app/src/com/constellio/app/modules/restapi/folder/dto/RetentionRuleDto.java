package com.constellio.app.modules.restapi.folder.dto;

import com.constellio.app.modules.restapi.resource.dto.BaseReferenceDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonRootName("RetentionRule")
@Data
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class RetentionRuleDto extends BaseReferenceDto {
	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	String code;

	@Builder
	public RetentionRuleDto(String id, String code, String title) {
		super(id, title);
		this.code = code;
	}
}
