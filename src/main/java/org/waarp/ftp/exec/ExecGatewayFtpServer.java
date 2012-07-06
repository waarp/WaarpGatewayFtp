/**
 * Copyright 2009, Frederic Bregier, and individual contributors by the @author tags. See the
 * COPYRIGHT.txt in the distribution for a full listing of individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation; either version 3.0 of the
 * License, or (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with this
 * software; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.waarp.ftp.exec;

import org.jboss.netty.logging.InternalLoggerFactory;
import org.waarp.common.file.filesystembased.FilesystemBasedDirImpl;
import org.waarp.common.file.filesystembased.FilesystemBasedFileParameterImpl;
import org.waarp.common.file.filesystembased.specific.FilesystemBasedDirJdk5;
import org.waarp.common.file.filesystembased.specific.FilesystemBasedDirJdk6;
import org.waarp.common.logging.WaarpInternalLogger;
import org.waarp.common.logging.WaarpInternalLoggerFactory;
import org.waarp.common.logging.WaarpSlf4JLoggerFactory;
import org.waarp.ftp.core.config.FtpConfiguration;
import org.waarp.ftp.core.exception.FtpNoConnectionException;
import org.waarp.ftp.exec.config.FileBasedConfiguration;
import org.waarp.ftp.exec.control.ExecBusinessHandler;
import org.waarp.ftp.exec.data.FileSystemBasedDataBusinessHandler;
import org.waarp.ftp.exec.exec.AbstractExecutor;
import org.waarp.openr66.protocol.configuration.Configuration;

/**
 * Exec FTP Server using simple authentication (XML FileInterface based), and standard Directory and
 * FileInterface implementation (Filesystem based).
 * 
 * @author Frederic Bregier
 * 
 */
public class ExecGatewayFtpServer {
	/**
	 * Internal Logger
	 */
	private static WaarpInternalLogger logger = null;

	/**
	 * Take a simple XML file as configuration.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Usage: " +
					ExecGatewayFtpServer.class.getName() + " <config-file> [<r66config-file>]");
			return;
		}
		InternalLoggerFactory.setDefaultFactory(new WaarpSlf4JLoggerFactory(null));
		logger = WaarpInternalLoggerFactory
				.getLogger(ExecGatewayFtpServer.class);
		String config = args[0];
		FileBasedConfiguration configuration = new FileBasedConfiguration(
				ExecGatewayFtpServer.class, ExecBusinessHandler.class,
				FileSystemBasedDataBusinessHandler.class,
				new FilesystemBasedFileParameterImpl());
		if (!configuration.setConfigurationServerFromXml(config)) {
			System.err.println("Bad main configuration");
			return;
		}
		Configuration.configuration.useLocalExec = configuration.useLocalExec;
		// Init according JDK
		if (FtpConfiguration.USEJDK6) {
			FilesystemBasedDirImpl.initJdkDependent(new FilesystemBasedDirJdk6());
		} else {
			FilesystemBasedDirImpl.initJdkDependent(new FilesystemBasedDirJdk5());
		}
		if (AbstractExecutor.useDatabase) {
			// Use R66 module
			if (args.length > 1) {
				if (!org.waarp.openr66.configuration.FileBasedConfiguration
						.setSubmitClientConfigurationFromXml(Configuration.configuration,
								args[1])) {
					// if (!R66FileBasedConfiguration.setSimpleClientConfigurationFromXml(args[1]))
					// {
					System.err.println("Bad R66 configuration");
					return;
				}
			} else {
				// Cannot get R66 functional
				System.err.println("No R66PrepareTransfer configuration file");
			}
		}
		FileBasedConfiguration.fileBasedConfiguration = configuration;
		// Start server.
		configuration.configureLExec();
		configuration.serverStartup();
		configuration.configureHttps();
		configuration.configureConstraint();
		try {
			configuration.configureSnmp();
		} catch (FtpNoConnectionException e) {
			System.err.println("Cannot start SNMP support: " + e.getMessage());
		}
		logger.warn("FTP started");
	}

}
