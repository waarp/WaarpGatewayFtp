/**
   This file is part of Waarp Project.

   Copyright 2009, Frederic Bregier, and individual contributors by the @author
   tags. See the COPYRIGHT.txt in the distribution for a full listing of
   individual contributors.

   All Waarp Project is free software: you can redistribute it and/or 
   modify it under the terms of the GNU General Public License as published 
   by the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   Waarp is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with Waarp .  If not, see <http://www.gnu.org/licenses/>.
 */
package org.waarp.gateway.ftp.adminssl;

import java.util.LinkedHashMap;

import org.waarp.common.logging.WaarpInternalLogger;
import org.waarp.common.logging.WaarpInternalLoggerFactory;
import org.waarp.gateway.ftp.adminssl.HttpBusinessFactory.FieldsName;
import org.waarp.gateway.kernel.AbstractHttpBusinessRequest;
import org.waarp.gateway.kernel.AbstractHttpField;
import org.waarp.gateway.kernel.HttpPage;

/**
 * @author "Frederic Bregier"
 *
 */
public class HttpBusinessHandler extends AbstractHttpBusinessRequest {
	/**
	 * Internal Logger
	 */
	private static final WaarpInternalLogger logger = WaarpInternalLoggerFactory
			.getLogger(HttpBusinessHandler.class);
	
	boolean requestValid = false;
	public String futurePage = null;
	
	/**
	 * @param fields
	 */
	public HttpBusinessHandler(LinkedHashMap<String, AbstractHttpField> fields, HttpPage page) {
		super(fields, page);
	}

	@Override
	public String getHeader() {
		return futurePage;
	}

	@Override
	public String getFooter() {
		return " ";
	}

	@Override
	public boolean isForm() {
		return false;
	}

	@Override
	public String getBeginForm() {
		return " ";
	}

	@Override
	public String getEndForm() {
		return null;
	}

	@Override
	public String getFieldForm(AbstractHttpField field) {
		return null;
	}

	@Override
	public String getNextFieldInForm() {
		return null;
	}

	@Override
	public boolean isRequestValid() {
		return true;
	}

	@Override
	public String getContentType() {
		return "text/html";
	}

	@Override
	public boolean isFieldValid(AbstractHttpField field) {
		if (field.fieldname.equals(FieldsName.ACTION.name())) {
			String [] values = page.fields.get(field.fieldname).fieldvalue.split(",");
			for (String string : values) {
				if (field.fieldvalue.equals(string)) {
					return true;
				}
			}
			logger.warn("Incorrect field: "+field.fieldname+":"+field.fieldvalue+" not in "
					+page.fields.get(field.fieldname).fieldvalue);
			return false;
		}
		return true;
	}
	
	@Override
	public AbstractHttpField getMainFileUpload() {
		return null;
	}

}
