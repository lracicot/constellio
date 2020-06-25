"use strict";

function SignaturePadAnnotation(x, y, width, height) {
	Annotation.call(this, x, y, width, height);
	this.setEditor(new SignaturePadAnnotationEditor());
}

SignaturePadAnnotation.prototype = Object.create(Annotation.prototype);
SignaturePadAnnotation.prototype.constructor = SignaturePadAnnotation;

SignaturePadAnnotation.prototype.getType = function() {
	return "signature-pad-annotation";
}

SignaturePadAnnotation.prototype.isInitials = function() {
	return this.initials;
};

SignaturePadAnnotation.prototype.setInitials = function(initials) {
	this.initials = initials;
};

SignaturePadAnnotation.prototype.getBakeInfoI10nKey = function() {
	return "annotation.signature.bakeInfo";
};	

SignaturePadAnnotation.prototype.toJSON = function() {
	var json = Annotation.prototype.toJSON.call(this);
	json.initials = this.isInitials();
	if (this.imageUrl) {
		json.imageUrl = this.imageUrl;
	}	
	return json;
};

SignaturePadAnnotation.prototype.fromJSON = function(json) {
	Annotation.prototype.fromJSON.call(this, json);
	if (json.imageUrl) {
		this.imageUrl = json.imageUrl;
	}
	this.initials = json.initials;
};	

SignaturePadAnnotation.prototype.bind = function(htmlElement) {
	Annotation.prototype.bind.call(this, htmlElement);
	
	htmlElement.classList.add("signature-pad-annotation");	
	if (this.imageUrl) {
		htmlElement.style.backgroundImage = "url(" + this.imageUrl + ")"; 
	}
	if (!this.isBaked()) {
		htmlElement.title = this.i10n("annotation.signature.clickToCertify", "Click on the Certify button to save the signature");
		htmlElement.classList.add("tooltip");
		$(htmlElement).tooltipster();
	}
};

SignaturePadAnnotation.prototype.annotationDefined = function(htmlElement) {
	this.openEditor();
};

SignaturePadAnnotation.prototype.getSaveCallback = function() {
	this.imageUrl = this.editor.getImageUrl();
	this.htmlElement.style.backgroundImage = "url(" + this.imageUrl + ")";  
};
