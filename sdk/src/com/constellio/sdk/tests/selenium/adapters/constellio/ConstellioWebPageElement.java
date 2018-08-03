/**
 * Constellio
 * Copyright (C) 2010 DocuLibre inc.
 * <p>
 * This program is free software: you can redistribute it and/or modifyTo
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.constellio.sdk.tests.selenium.adapters.constellio;

import org.openqa.selenium.By;

public class ConstellioWebPageElement extends ConstellioWebElement {

	public ConstellioWebPageElement(ConstellioWebDriver webDriver) {
		super(webDriver, By.id("corpsPage"));
	}

	public ConstellioWebPageElement(ConstellioWebDriver webDriver, By pageRootByAccess) {
		super(webDriver, pageRootByAccess);
	}

}
