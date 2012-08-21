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

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.waarp.gateway.kernel.AbstractHttpBusinessRequest;
import org.waarp.gateway.kernel.AbstractHttpField;
import org.waarp.gateway.kernel.DefaultHttpField;
import org.waarp.gateway.kernel.HttpPage;
import org.waarp.gateway.kernel.HttpPageHandler;
import org.waarp.gateway.kernel.AbstractHttpField.FieldPosition;
import org.waarp.gateway.kernel.AbstractHttpField.FieldRole;
import org.waarp.gateway.kernel.HttpPage.PageRole;

/**
 * @author "Frederic Bregier"
 *
 */
public class HttpBusinessFactory extends org.waarp.gateway.kernel.HttpBusinessFactory {

    public static enum GatewayRequest {
        Logon, index, error, Transfer, Rule, User, System;
    }
    
	public static enum FieldsName {
		name, passwd,
		ACTION, BGLOBR, CPUL, CONL,
		purge, replace, file, export,
		std, rtd, stc, rtc,
		user, account, specialid
	}
	
	public static HttpPageHandler httpPageHandler;
	
	public static String baseFilePath = "J:/GG/Gateway/"; // "C:/Temp/Java/GG/Gateway/";
	
	public SocketAddress remoteSocketAddress;
	public HttpPage page;

	public HttpBusinessFactory() {
	}

	@Override
	public AbstractHttpBusinessRequest getNewHttpBusinessRequest(SocketAddress remoteAddress,
			LinkedHashMap<String, AbstractHttpField> fields, HttpPage page) {
		this.remoteSocketAddress = remoteAddress;
		this.page = page;
		return new HttpBusinessHandler(fields, page);
	}

	public static HttpPageHandler initializeHttpPageHandler(String base) {
		// manual creation
		HashMap<String, HttpPage> pages = new HashMap<String, HttpPage>();
		String pagename, header, footer, beginform, endform, nextinform, uri, errorpage, classname;
		PageRole pageRole;
		LinkedHashMap<String, AbstractHttpField> linkedHashMap;
		String fieldname, fieldinfo, fieldvalue;
		FieldRole fieldRole;
		FieldPosition position;
		boolean fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate;
		int fieldrank;
		
		try {
			// Need as default error pages: 400, 401, 403, 404, 406, 500
			HttpPageHandler pageHandler = new HttpPageHandler(pages);
			if (!HttpBusinessFactory.addDefaultErrorPages(pageHandler, "Gateway ERROR", HttpBusinessFactory.class)) {
				throw new IllegalAccessException("Cannot build default error pages");
			}

			classname = HttpBusinessFactory.class.getName();
	
			// XXX Logon
			pageRole = PageRole.HTML;
			pagename = "Logon";
			uri = "/Logon.html";
			header = "Logon.html";
			footer = null;
			beginform = null;
			endform = null;
			nextinform = null;
			errorpage = "404";
			linkedHashMap = new LinkedHashMap<String, AbstractHttpField>();
			// no value
			pages.put(uri, new HttpPage(pagename, base, header, footer, beginform, endform, nextinform, 
					uri, pageRole, errorpage, classname, linkedHashMap));

			// XXX /
			pageRole = PageRole.HTML;
			pagename = "root";
			uri = "/";
			header = "index.html";
			footer = null;
			beginform = null;
			endform = null;
			nextinform = null;
			errorpage = "404";
			linkedHashMap = new LinkedHashMap<String, AbstractHttpField>();
			fieldname = FieldsName.name.name();
			fieldinfo = "Name";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_TEXT;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 1;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			fieldname = FieldsName.passwd.name();
			fieldinfo = "Pwd";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_TEXT;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 2;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			pages.put(uri, new HttpPage(pagename, base, header, footer, beginform, endform, nextinform, 
					uri, pageRole, errorpage, classname, linkedHashMap));

			// XXX Index
			pageRole = PageRole.HTML;
			pagename = "index";
			uri = "/index.html";
			header = "index.html";
			footer = null;
			beginform = null;
			endform = null;
			nextinform = null;
			errorpage = "404";
			linkedHashMap = new LinkedHashMap<String, AbstractHttpField>();
			fieldname = FieldsName.name.name();
			fieldinfo = "Name";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_TEXT;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 1;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			fieldname = FieldsName.passwd.name();
			fieldinfo = "Pwd";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_TEXT;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 2;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			pages.put(uri, new HttpPage(pagename, base, header, footer, beginform, endform, nextinform, 
					uri, pageRole, errorpage, classname, linkedHashMap));

			// XXX Error
			pageRole = PageRole.HTML;
			pagename = "error";
			uri = "/error.html";
			header = "error.html";
			footer = null;
			beginform = null;
			endform = null;
			nextinform = null;
			errorpage = "404";
			linkedHashMap = new LinkedHashMap<String, AbstractHttpField>();
			// no value
			pages.put(uri, new HttpPage(pagename, base, header, footer, beginform, endform, nextinform, 
					uri, pageRole, errorpage, classname, linkedHashMap));

			// XXX Rule
			pageRole = PageRole.HTML;
			pagename = "Rule";
			uri = "/Rule.html";
			header = "Rule.html";
			footer = null;
			beginform = null;
			endform = null;
			nextinform = null;
			errorpage = "404";
			linkedHashMap = new LinkedHashMap<String, AbstractHttpField>();
			fieldname = FieldsName.ACTION.name();
			fieldinfo = "ACTION";
			fieldvalue = "Update";
			fieldRole = FieldRole.BUSINESS_INPUT_RADIO;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 1;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			fieldname = FieldsName.rtc.name();
			fieldinfo = "rtc";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_TEXT;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 2;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			fieldname = FieldsName.rtd.name();
			fieldinfo = "rtd";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_TEXT;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 3;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			fieldname = FieldsName.stc.name();
			fieldinfo = "stc";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_TEXT;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 4;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			fieldname = FieldsName.std.name();
			fieldinfo = "std";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_TEXT;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 5;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			pages.put(uri, new HttpPage(pagename, base, header, footer, beginform, endform, nextinform, 
					uri, pageRole, errorpage, classname, linkedHashMap));

			// XXX Transfer
			pageRole = PageRole.HTML;
			pagename = "Transfer";
			uri = "/Transfer.html";
			header = "Transfer_head.html";
			footer = "Transfer_end.html";
			beginform = "Transfer_body.html";
			endform = null;
			nextinform = null;
			errorpage = "404";
			linkedHashMap = new LinkedHashMap<String, AbstractHttpField>();
			fieldname = FieldsName.ACTION.name();
			fieldinfo = "ACTION";
			fieldvalue = "PurgeCorrectTransferLogs,PurgeAllTransferLogs,Delete";
			fieldRole = FieldRole.BUSINESS_INPUT_RADIO;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 1;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			fieldname = FieldsName.user.name();
			fieldinfo = "user";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_TEXT;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 2;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			fieldname = FieldsName.account.name();
			fieldinfo = "account";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_TEXT;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 3;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			fieldname = FieldsName.specialid.name();
			fieldinfo = "specialid";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_TEXT;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 4;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			pages.put(uri, new HttpPage(pagename, base, header, footer, beginform, endform, nextinform, 
					uri, pageRole, errorpage, classname, linkedHashMap));

			// XXX System
			pageRole = PageRole.HTML;
			pagename = "System";
			uri = "/System.html";
			header = "System.html";
			footer = null;
			beginform = null;
			endform = null;
			nextinform = null;
			errorpage = "404";
			linkedHashMap = new LinkedHashMap<String, AbstractHttpField>();
			fieldname = FieldsName.ACTION.name();
			fieldinfo = "ACTION";
			fieldvalue = "Disconnect,Shutdown,Validate";
			fieldRole = FieldRole.BUSINESS_INPUT_RADIO;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 1;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			fieldname = FieldsName.BGLOBR.name();
			fieldinfo = "BGLOBR";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_TEXT;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 2;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			fieldname = FieldsName.CPUL.name();
			fieldinfo = "CPUL";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_TEXT;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 3;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			fieldname = FieldsName.CONL.name();
			fieldinfo = "CONL";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_TEXT;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 4;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			pages.put(uri, new HttpPage(pagename, base, header, footer, beginform, endform, nextinform, 
					uri, pageRole, errorpage, classname, linkedHashMap));

			// XXX User
			pageRole = PageRole.HTML;
			pagename = "User";
			uri = "/User.html";
			header = "User_head.html";
			footer = "User_end.html";
			beginform = "User_body.html";
			endform = null;
			nextinform = null;
			errorpage = "404";
			linkedHashMap = new LinkedHashMap<String, AbstractHttpField>();
			fieldname = FieldsName.ACTION.name();
			fieldinfo = "ACTION";
			fieldvalue = "ImportExport";
			fieldRole = FieldRole.BUSINESS_INPUT_RADIO;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 1;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			fieldname = FieldsName.file.name();
			fieldinfo = "file";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_TEXT;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 2;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			fieldname = FieldsName.export.name();
			fieldinfo = "export";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_TEXT;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 3;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			fieldname = FieldsName.purge.name();
			fieldinfo = "purge";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_CHECKBOX;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 4;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			fieldname = FieldsName.replace.name();
			fieldinfo = "replace";
			fieldvalue = null;
			fieldRole = FieldRole.BUSINESS_INPUT_CHECKBOX;
			fieldvisibility = false;
			fieldmandatory = false;
			fieldcookieset = false;
			fieldtovalidate = false;
			position = FieldPosition.BODY;
			fieldrank = 5;
			linkedHashMap.put(fieldname, new DefaultHttpField(fieldname, fieldRole, fieldinfo, fieldvalue, 
					fieldvisibility, fieldmandatory, fieldcookieset, fieldtovalidate, position, fieldrank));
			pages.put(uri, new HttpPage(pagename, base, header, footer, beginform, endform, nextinform, 
					uri, pageRole, errorpage, classname, linkedHashMap));

		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return new HttpPageHandler(pages);
	}
}
