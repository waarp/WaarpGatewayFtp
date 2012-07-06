/**
 * This file is part of Waarp Project.
 * 
 * Copyright 2009, Frederic Bregier, and individual contributors by the @author tags. See the
 * COPYRIGHT.txt in the distribution for a full listing of individual contributors.
 * 
 * All Waarp Project is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * 
 * Waarp is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Waarp . If not, see
 * <http://www.gnu.org/licenses/>.
 */
package org.waarp.ftp.exec;

import org.jboss.netty.logging.InternalLoggerFactory;
import org.waarp.common.database.exception.WaarpDatabaseNoConnectionException;
import org.waarp.common.file.filesystembased.FilesystemBasedFileParameterImpl;
import org.waarp.common.logging.WaarpInternalLogger;
import org.waarp.common.logging.WaarpInternalLoggerFactory;
import org.waarp.common.logging.WaarpSlf4JLoggerFactory;
import org.waarp.ftp.core.utils.FtpChannelUtils;
import org.waarp.ftp.exec.config.FileBasedConfiguration;
import org.waarp.ftp.exec.control.ExecBusinessHandler;
import org.waarp.ftp.exec.data.FileSystemBasedDataBusinessHandler;
import org.waarp.ftp.exec.database.DbConstant;
import org.waarp.ftp.exec.database.model.DbModelFactory;

/**
 * Program to initialize the database for Waarp Ftp Exec
 * 
 * @author Frederic Bregier
 * 
 */
public class ServerInitDatabase {
	/**
	 * Internal Logger
	 */
	static volatile WaarpInternalLogger logger;

	static String sxml = null;
	static boolean database = false;

	protected static boolean getParams(String[] args) {
		if (args.length < 1) {
			logger.error("Need at least the configuration file as first argument then optionally\n"
					+
					"    -initdb");
			return false;
		}
		sxml = args[0];
		for (int i = 1; i < args.length; i++) {
			if (args[i].equalsIgnoreCase("-initdb")) {
				database = true;
			}
		}
		return true;
	}

	/**
	 * @param args
	 *            as config_database file [rules_directory host_authent limit_configuration]
	 */
	public static void main(String[] args) {
		InternalLoggerFactory.setDefaultFactory(new WaarpSlf4JLoggerFactory(null));
		if (logger == null) {
			logger = WaarpInternalLoggerFactory.getLogger(ServerInitDatabase.class);
		}
		if (!getParams(args)) {
			logger.error("Need at least the configuration file as first argument then optionally\n"
					+
					"    -initdb");
			if (DbConstant.admin != null && DbConstant.admin.isConnected) {
				DbConstant.admin.close();
			}
			FtpChannelUtils.stopLogger();
			System.exit(1);
		}
		FileBasedConfiguration configuration = new FileBasedConfiguration(
				ExecGatewayFtpServer.class, ExecBusinessHandler.class,
				FileSystemBasedDataBusinessHandler.class,
				new FilesystemBasedFileParameterImpl());
		try {
			if (!configuration.setConfigurationServerFromXml(args[0])) {
				System.err.println("Bad main configuration");
				if (DbConstant.admin != null) {
					DbConstant.admin.close();
				}
				FtpChannelUtils.stopLogger();
				System.exit(1);
				return;
			}
			if (database) {
				// Init database
				try {
					initdb();
				} catch (WaarpDatabaseNoConnectionException e) {
					logger.error("Cannot connect to database");
					return;
				}
				System.out.println("End creation");
			}
			System.out.println("Load done");
		} finally {
			if (DbConstant.admin != null) {
				DbConstant.admin.close();
			}
		}
	}

	public static void initdb() throws WaarpDatabaseNoConnectionException {
		// Create tables: configuration, hosts, rules, runner, cptrunner
		DbModelFactory.dbModel.createTables(DbConstant.admin.session);
	}

}
