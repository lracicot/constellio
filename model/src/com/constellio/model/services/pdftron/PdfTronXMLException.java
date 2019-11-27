package com.constellio.model.services.pdftron;

import java.io.IOException;

public class PdfTronXMLException extends Exception {
	public PdfTronXMLException(String message) {
		super(message);
	}

	public PdfTronXMLException(String message, Exception e) {
		super(message, e);
	}

	public static class PdfTronXMLException_CannotEditOtherUsersAnnoations extends PdfTronXMLException {
		public PdfTronXMLException_CannotEditOtherUsersAnnoations() {
			super("You can't exist other user annotations");
		}
	}

	public static class PdfTronXMLException_IOExeption extends PdfTronXMLException {
		public PdfTronXMLException_IOExeption(IOException e) {
			super("Exception d'IO", e);
		}
	}

	public static class PdfTronXMLException_XMLParsingException extends PdfTronXMLException {
		public PdfTronXMLException_XMLParsingException(Exception e) {
			super("Exception d'xml", e);
		}
	}
}
