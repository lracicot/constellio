package com.constellio.app.modules.restapi.cart.dto;

import com.fasterxml.jackson.annotation.JsonRootName;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonRootName("Cart")
public class CartDto {
	private String id;
	private String owner;
	private String title;
}
