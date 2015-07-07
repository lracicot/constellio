/*Constellio Enterprise Information Management

Copyright (c) 2015 "Constellio inc."

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
*/
package com.constellio.data.dao.services.contents;

public class ContentDaoRuntimeException extends RuntimeException {

	public ContentDaoRuntimeException(String message) {
		super(message);
	}

	public ContentDaoRuntimeException(String message, Throwable cause) {
		super(message, cause);
	}

	public ContentDaoRuntimeException(Throwable cause) {
		super(cause);
	}

	public static class ContentDaoRuntimeException_CannotDeleteFolder extends ContentDaoRuntimeException {
		public ContentDaoRuntimeException_CannotDeleteFolder(String id, Throwable cause) {
			super("Cannot delete folder '" + id + "'", cause);
		}
	}

	public static class ContentDaoRuntimeException_NoSuchFolder extends ContentDaoRuntimeException {
		public ContentDaoRuntimeException_NoSuchFolder(String id) {
			super("Cannot delete folder '" + id + "'");
		}
	}

	public static class ContentDaoRuntimeException_CannotMoveFolderTo extends ContentDaoRuntimeException {
		public ContentDaoRuntimeException_CannotMoveFolderTo(String folderId, String newFolderId, Throwable cause) {
			super("Cannot move folder '" + folderId + "' to '" + newFolderId + "'", cause);
		}
	}
}

