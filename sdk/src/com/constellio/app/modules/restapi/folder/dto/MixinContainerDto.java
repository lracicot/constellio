package com.constellio.app.modules.restapi.folder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_WRITE;

public class MixinContainerDto {
	@JsonProperty(access = READ_WRITE)
	String title;
}
