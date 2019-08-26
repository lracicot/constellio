package com.constellio.app.modules.restapi.folder.dto;

import com.constellio.app.modules.restapi.resource.dto.BaseReferenceDto;
import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonRootName("Category")
@Data
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class CategoryDto extends BaseReferenceDto {
	@Builder
	public CategoryDto(String id, String title) {
		super(id, title);
	}
}
