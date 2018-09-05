package com.constellio.model.services.schemas.calculators;

import com.constellio.model.entities.calculators.CalculatorParameters;
import com.constellio.model.entities.calculators.MetadataValueCalculator;
import com.constellio.model.entities.calculators.dependencies.Dependency;
import com.constellio.model.entities.calculators.dependencies.HierarchyDependencyValue;
import com.constellio.model.entities.calculators.dependencies.LocalDependency;
import com.constellio.model.entities.calculators.dependencies.SpecialDependencies;
import com.constellio.model.entities.calculators.dependencies.SpecialDependency;
import com.constellio.model.entities.schemas.MetadataValueType;
import com.constellio.model.entities.security.SecurityModel;

import java.util.ArrayList;
import java.util.List;

import static com.constellio.model.entities.schemas.MetadataValueType.STRING;
import static com.constellio.model.services.schemas.builders.CommonMetadataBuilder.DETACHED_AUTHORIZATIONS;
import static java.util.Arrays.asList;

public class AttachedAncestorsCalculator implements MetadataValueCalculator<List<String>> {

	SpecialDependency<HierarchyDependencyValue> taxonomiesParam = SpecialDependencies.HIERARCHY;
	LocalDependency<Boolean> isDetachedAuthsParams = LocalDependency.toABoolean(DETACHED_AUTHORIZATIONS);
	//SpecialDependency<AllAuthorizationsTargettingRecordDependencyValue> authorizationsParam = SpecialDependencies.AURHORIZATIONS_TARGETTING_RECORD;
	SpecialDependency<SecurityModel> securityModelDependency = SpecialDependencies.SECURITY_MODEL;


	@Override

	public List<String> calculate(CalculatorParameters parameters) {
		SecurityModel securityModel = parameters.get(securityModelDependency);
		HierarchyDependencyValue hierarchyDependencyValue = parameters.get(taxonomiesParam);
		boolean isDetachedAuths = Boolean.TRUE == parameters.get(isDetachedAuthsParams);
		boolean hasSecurity = parameters.getSchemaType().hasSecurity();

		List<String> ancestors = new ArrayList<>();
		if (hasSecurity) {
			if (hierarchyDependencyValue != null
				&& !isDetachedAuths
				//TODO Replace using new security model && !authorizations.isInheritedAuthorizationsOverridenByMetadatasProvidingSecurity()
			) {
				ancestors.addAll(hierarchyDependencyValue.getAttachedAncestors());
			}
			ancestors.add(parameters.getId());
		}
		return ancestors;
	}


	@Override
	public List<String> getDefaultValue() {
		return new ArrayList<>();
	}

	@Override
	public MetadataValueType getReturnType() {
		return STRING;
	}

	@Override
	public boolean isMultiValue() {
		return true;
	}

	@Override
	public List<? extends Dependency> getDependencies() {
		return asList(taxonomiesParam, isDetachedAuthsParams, securityModelDependency);
	}
}
