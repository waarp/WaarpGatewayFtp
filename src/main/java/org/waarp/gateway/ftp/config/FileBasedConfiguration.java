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
package org.waarp.gateway.ftp.config;

import java.io.File;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.ChannelGroupFutureListener;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.handler.traffic.AbstractTrafficShapingHandler;
import org.waarp.common.crypto.Des;
import org.waarp.common.crypto.ssl.WaarpSecureKeyStore;
import org.waarp.common.crypto.ssl.WaarpSslContextFactory;
import org.waarp.common.database.DbAdmin;
import org.waarp.common.database.DbPreparedStatement;
import org.waarp.common.database.exception.WaarpDatabaseNoConnectionException;
import org.waarp.common.database.exception.WaarpDatabaseSqlException;
import org.waarp.common.digest.FilesystemBasedDigest;
import org.waarp.common.exception.CryptoException;
import org.waarp.common.exception.InvalidArgumentException;
import org.waarp.common.file.DirInterface;
import org.waarp.common.file.FileParameterInterface;
import org.waarp.common.file.filesystembased.FilesystemBasedDirImpl;
import org.waarp.common.file.filesystembased.FilesystemBasedFileParameterImpl;
import org.waarp.common.file.filesystembased.specific.FilesystemBasedDirJdkAbstract;
import org.waarp.common.logging.WaarpInternalLogger;
import org.waarp.common.logging.WaarpInternalLoggerFactory;
import org.waarp.common.utility.WaarpThreadFactory;
import org.waarp.common.xml.XmlDecl;
import org.waarp.common.xml.XmlHash;
import org.waarp.common.xml.XmlType;
import org.waarp.common.xml.XmlUtil;
import org.waarp.common.xml.XmlValue;
import org.waarp.ftp.core.config.FtpConfiguration;
import org.waarp.ftp.core.control.BusinessHandler;
import org.waarp.ftp.core.control.ftps.FtpsPipelineFactory;
import org.waarp.ftp.core.data.handler.DataBusinessHandler;
import org.waarp.ftp.core.exception.FtpNoConnectionException;
import org.waarp.ftp.core.exception.FtpUnknownFieldException;
import org.waarp.gateway.ftp.adminssl.HttpSslPipelineFactory;
import org.waarp.gateway.ftp.control.FtpConstraintLimitHandler;
import org.waarp.gateway.ftp.database.DbConstant;
import org.waarp.gateway.ftp.database.data.DbTransferLog;
import org.waarp.gateway.ftp.database.model.DbModelFactory;
import org.waarp.gateway.ftp.file.SimpleAuth;
import org.waarp.gateway.ftp.snmp.FtpMonitoring;
import org.waarp.gateway.ftp.snmp.FtpPrivateMib;
import org.waarp.gateway.ftp.snmp.FtpVariableFactory;
import org.waarp.gateway.kernel.exec.AbstractExecutor;
import org.waarp.gateway.kernel.exec.LocalExecClient;
import org.waarp.snmp.SnmpConfiguration;
import org.waarp.snmp.WaarpMOFactory;
import org.waarp.snmp.WaarpSnmpAgent;

/**
 * FtpConfiguration based on a XML file
 * 
 * @author Frederic Bregier
 * 
 */
public class FileBasedConfiguration extends FtpConfiguration {
	/**
	 * Internal Logger
	 */
	private static final WaarpInternalLogger logger = WaarpInternalLoggerFactory
			.getLogger(FileBasedConfiguration.class);

	/**
	 * SERVER HOSTID
	 */
	private static final String XML_SERVER_HOSTID = "hostid";
	/**
	 * Authentication
	 */
	private static final String XML_AUTHENTIFICATION_FILE = "authentfile";
	/**
	 * SERVER CRYPTO for Password
	 */
	private static final String XML_PATH_CRYPTOKEY = "cryptokey";

	/**
	 * Structure of the Configuration file
	 * 
	 */
	private static final XmlDecl[] configIdentityDecls = {
			// identity
			new XmlDecl(XmlType.STRING, XML_SERVER_HOSTID),
			new XmlDecl(XmlType.STRING, XML_PATH_CRYPTOKEY),
			new XmlDecl(XmlType.STRING, XML_AUTHENTIFICATION_FILE)
	};
	/**
	 * Use HTTP compression for R66 HTTP connection
	 */
	private static final String XML_USEHTTPCOMP = "usehttpcomp";
	/**
	 * Use external Waarp Local Exec for ExecTask and ExecMoveTask
	 */
	private static final String XML_USELOCALEXEC = "uselocalexec";

	/**
	 * Address of Waarp Local Exec for ExecTask and ExecMoveTask
	 */
	private static final String XML_LEXECADDR = "lexecaddr";

	/**
	 * Port of Waarp Local Exec for ExecTask and ExecMoveTask
	 */
	private static final String XML_LEXECPORT = "lexecport";
	/**
	 * ADMINISTRATOR SERVER NAME (shutdown)
	 */
	private static final String XML_SERVER_ADMIN = "serveradmin";
	/**
	 * SERVER PASSWORD (shutdown)
	 */
	private static final String XML_SERVER_PASSWD = "serverpasswd";
	/**
	 * SERVER SSL STOREKEY PATH ADMIN
	 */
	private static final String XML_PATH_ADMIN_KEYPATH = "admkeypath";

	/**
	 * SERVER SSL KEY PASS ADMIN
	 */
	private static final String XML_PATH_ADMIN_KEYPASS = "admkeypass";

	/**
	 * SERVER SSL STOREKEY PASS ADMIN
	 */
	private static final String XML_PATH_ADMIN_KEYSTOREPASS = "admkeystorepass";
	/**
	 * HTTP Admin Directory
	 */
	private static final String XML_HTTPADMINPATH = "httpadmin";
	/**
	 * Monitoring: snmp configuration file (if empty, no snmp support)
	 */
	private static final String XML_MONITOR_SNMP_CONFIG = "snmpconfig";

	/**
	 * Structure of the Configuration file
	 * 
	 */
	private static final XmlDecl[] configServerParamDecls = {
			// server
			new XmlDecl(XmlType.BOOLEAN, XML_USELOCALEXEC),
			new XmlDecl(XmlType.STRING, XML_LEXECADDR),
			new XmlDecl(XmlType.INTEGER, XML_LEXECPORT),
			new XmlDecl(XmlType.STRING, XML_SERVER_ADMIN),
			new XmlDecl(XmlType.STRING, XML_SERVER_PASSWD),
			new XmlDecl(XmlType.BOOLEAN, XML_USEHTTPCOMP),
			new XmlDecl(XmlType.STRING, XML_HTTPADMINPATH),
			new XmlDecl(XmlType.STRING, XML_PATH_ADMIN_KEYPATH),
			new XmlDecl(XmlType.STRING, XML_PATH_ADMIN_KEYSTOREPASS),
			new XmlDecl(XmlType.STRING, XML_PATH_ADMIN_KEYPASS),
			new XmlDecl(XmlType.STRING, XML_MONITOR_SNMP_CONFIG)
	};
	/**
	 * SERVER PORT
	 */
	private static final String XML_SERVER_PORT = "serverport";
	/**
	 * SERVER ADDRESS if any
	 */
	private static final String XML_SERVER_ADDRESS = "serveraddress";
	/**
	 * RANGE of PORT for Passive Mode
	 */
	private static final String XML_RANGE_PORT_MIN = "portmin";

	/**
	 * RANGE of PORT for Passive Mode
	 */
	private static final String XML_RANGE_PORT_MAX = "portmax";
	/**
	 * SERVER HTTP PORT MONITORING
	 */
	private static final String XML_SERVER_HTTP_PORT = "serverhttpport";
	/**
	 * SERVER HTTPS PORT ADMINISTRATION
	 */
	private static final String XML_SERVER_HTTPS_PORT = "serverhttpsport";

	/**
	 * Structure of the Configuration file
	 * 
	 */
	private static final XmlDecl[] configNetworkServerDecls = {
			// network
			new XmlDecl(XmlType.INTEGER, XML_SERVER_PORT),
			new XmlDecl(XmlType.STRING, XML_SERVER_ADDRESS),
			new XmlDecl(XmlType.INTEGER, XML_RANGE_PORT_MIN),
			new XmlDecl(XmlType.INTEGER, XML_RANGE_PORT_MAX),
			new XmlDecl(XmlType.INTEGER, XML_SERVER_HTTP_PORT),
			new XmlDecl(XmlType.INTEGER, XML_SERVER_HTTPS_PORT)
	};
	/**
	 * Database Driver as of oracle, mysql, postgresql, h2
	 */
	private static final String XML_DBDRIVER = "dbdriver";

	/**
	 * Database Server connection string as of jdbc:type://[host:port],[failoverhost:port]
	 * .../[database][?propertyName1][ =propertyValue1][&propertyName2][=propertyValue2]...
	 */
	private static final String XML_DBSERVER = "dbserver";

	/**
	 * Database User
	 */
	private static final String XML_DBUSER = "dbuser";

	/**
	 * Database Password
	 */
	private static final String XML_DBPASSWD = "dbpasswd";
	/**
	 * Structure of the Configuration file
	 * 
	 */
	private static final XmlDecl[] configDbDecls = {
			// db
			new XmlDecl(XmlType.STRING, XML_DBDRIVER),
			new XmlDecl(XmlType.STRING, XML_DBSERVER),
			new XmlDecl(XmlType.STRING, XML_DBUSER),
			new XmlDecl(XmlType.STRING, XML_DBPASSWD)
	};
	/**
	 * Should a file be deleted when a Store like command is aborted
	 */
	private static final String XML_DELETEONABORT = "deleteonabort";
	/**
	 * Default number of threads in pool for Server.
	 */
	private static final String XML_SERVER_THREAD = "serverthread";

	/**
	 * Default number of threads in pool for Client.
	 */
	private static final String XML_CLIENT_THREAD = "clientthread";
	/**
	 * Memory Limit to use.
	 */
	private static final String XML_MEMORY_LIMIT = "memorylimit";

	/**
	 * Limit for Session
	 */
	private static final String XML_LIMITSESSION = "sessionlimit";

	/**
	 * Limit for Global
	 */
	private static final String XML_LIMITGLOBAL = "globallimit";
	/**
	 * Delay between two checks for Limit
	 */
	private static final String XML_LIMITDELAY = "delaylimit";
	/**
	 * Nb of milliseconds after connection is in timeout
	 */
	private static final String XML_TIMEOUTCON = "timeoutcon";
	/**
	 * Size by default of block size for receive/sending files. Should be a multiple of 8192
	 * (maximum = 64K due to block limitation to 2 bytes)
	 */
	private static final String XML_BLOCKSIZE = "blocksize";
	/**
	 * Should a file MD5 SHA1 be computed using NIO
	 */
	private static final String XML_USENIO = "usenio";

	/**
	 * Should a file MD5 be computed using FastMD5
	 */
	private static final String XML_USEFASTMD5 = "usefastmd5";

	/**
	 * If using Fast MD5, should we used the binary JNI library, empty meaning no
	 */
	private static final String XML_FASTMD5 = "fastmd5";
	/**
	 * Usage of CPU Limit
	 */
	private static final String XML_CSTRT_USECPULIMIT = "usecpulimit";

	/**
	 * Usage of JDK CPU Limit (True) or SysMon CPU Limit
	 */
	private static final String XML_CSTRT_USECPUJDKLIMIT = "usejdkcpulimit";

	/**
	 * CPU LIMIT between 0 and 1, where 1 stands for no limit
	 */
	private static final String XML_CSTRT_CPULIMIT = "cpulimit";
	/**
	 * Connection limit where 0 stands for no limit
	 */
	private static final String XML_CSTRT_CONNLIMIT = "connlimit";
	/**
	 * CPU LOW limit to apply increase of throttle
	 */
	private static final String XML_CSTRT_LOWCPULIMIT = "lowcpulimit";
	/**
	 * CPU HIGH limit to apply decrease of throttle, 0 meaning no throttle activated
	 */
	private static final String XML_CSTRT_HIGHCPULIMIT = "highcpulimit";
	/**
	 * PERCENTAGE DECREASE of Bandwidth
	 */
	private static final String XML_CSTRT_PERCENTDECREASE = "percentdecrease";
	/**
	 * Delay between 2 checks of throttle test
	 */
	private static final String XML_CSTRT_DELAYTHROTTLE = "delaythrottle";
	/**
	 * Bandwidth low limit to not got below
	 */
	private static final String XML_CSTRT_LIMITLOWBANDWIDTH = "limitlowbandwidth";
	/**
	 * Structure of the Configuration file
	 * 
	 */
	private static final XmlDecl[] configLimitDecls = {
			// limit
			new XmlDecl(XmlType.BOOLEAN, XML_DELETEONABORT),
			new XmlDecl(XmlType.LONG, XML_LIMITSESSION),
			new XmlDecl(XmlType.LONG, XML_LIMITGLOBAL),
			new XmlDecl(XmlType.LONG, XML_LIMITDELAY),
			new XmlDecl(XmlType.INTEGER, XML_SERVER_THREAD),
			new XmlDecl(XmlType.INTEGER, XML_CLIENT_THREAD),
			new XmlDecl(XmlType.LONG, XML_MEMORY_LIMIT),
			new XmlDecl(XmlType.BOOLEAN, XML_CSTRT_USECPULIMIT),
			new XmlDecl(XmlType.BOOLEAN, XML_CSTRT_USECPUJDKLIMIT),
			new XmlDecl(XmlType.DOUBLE, XML_CSTRT_CPULIMIT),
			new XmlDecl(XmlType.INTEGER, XML_CSTRT_CONNLIMIT),
			new XmlDecl(XmlType.DOUBLE, XML_CSTRT_LOWCPULIMIT),
			new XmlDecl(XmlType.DOUBLE, XML_CSTRT_HIGHCPULIMIT),
			new XmlDecl(XmlType.DOUBLE, XML_CSTRT_PERCENTDECREASE),
			new XmlDecl(XmlType.LONG, XML_CSTRT_LIMITLOWBANDWIDTH),
			new XmlDecl(XmlType.LONG, XML_CSTRT_DELAYTHROTTLE),
			new XmlDecl(XmlType.LONG, XML_TIMEOUTCON),
			new XmlDecl(XmlType.BOOLEAN, XML_USENIO),
			new XmlDecl(XmlType.BOOLEAN, XML_USEFASTMD5),
			new XmlDecl(XmlType.STRING, XML_FASTMD5),
			new XmlDecl(XmlType.INTEGER, XML_BLOCKSIZE)
	};

	/**
	 * RETRIEVE COMMAND
	 */
	public static final String XML_RETRIEVE_COMMAND = "retrievecmd";

	/**
	 * STORE COMMAND
	 */
	public static final String XML_STORE_COMMAND = "storecmd";

	/**
	 * DELAY RETRIEVE COMMAND
	 */
	public static final String XML_DELAYRETRIEVE_COMMAND = "retrievedelay";

	/**
	 * DELAY STORE COMMAND
	 */
	public static final String XML_DELAYSTORE_COMMAND = "storedelay";
	/**
	 * Structure of the Configuration file
	 * 
	 */
	private static final XmlDecl[] configExecDecls = {
			// Exec
			new XmlDecl(XmlType.STRING, XML_RETRIEVE_COMMAND),
			new XmlDecl(XmlType.LONG, XML_DELAYRETRIEVE_COMMAND),
			new XmlDecl(XmlType.STRING, XML_STORE_COMMAND),
			new XmlDecl(XmlType.LONG, XML_DELAYSTORE_COMMAND)
	};
	/**
	 * Base Directory
	 */
	private static final String XML_SERVER_HOME = "serverhome";
	/**
	 * Structure of the Configuration file
	 * 
	 */
	private static final XmlDecl[] configDirectoryDecls = {
			// directory
			new XmlDecl(XmlType.STRING, XML_SERVER_HOME)
	};
	/**
	 * SERVER SSL STOREKEY PATH
	 */
	private static final String XML_PATH_KEYPATH = "keypath";

	/**
	 * SERVER SSL KEY PASS
	 */
	private static final String XML_PATH_KEYPASS = "keypass";

	/**
	 * SERVER SSL STOREKEY PASS
	 */
	private static final String XML_PATH_KEYSTOREPASS = "keystorepass";

	/**
	 * SERVER SSL TRUSTSTOREKEY PATH
	 */
	private static final String XML_PATH_TRUSTKEYPATH = "trustkeypath";

	/**
	 * SERVER SSL TRUSTSTOREKEY PASS
	 */
	private static final String XML_PATH_TRUSTKEYSTOREPASS = "trustkeystorepass";

	/**
	 * SERVER SSL Use TrustStore for Client Authentication
	 */
	private static final String XML_USECLIENT_AUTHENT = "trustuseclientauthenticate";
	/**
	 * SERVER SSL Use Implicit FTPS
	 */
	private static final String XML_IMPLICIT_FTPS = "useimplicitftps";
	/**
	 * SERVER SSL Use Explicit FTPS
	 */
	private static final String XML_EXPLICIT_FTPS = "useexplicitftps";
	
	/**
	 * Structure of the Configuration file
	 * 
	 */
	private static final XmlDecl[] configSslDecls = {
			// ssl
			new XmlDecl(XmlType.STRING, XML_PATH_KEYPATH),
			new XmlDecl(XmlType.STRING, XML_PATH_KEYSTOREPASS),
			new XmlDecl(XmlType.STRING, XML_PATH_KEYPASS),
			new XmlDecl(XmlType.STRING, XML_PATH_TRUSTKEYPATH),
			new XmlDecl(XmlType.STRING, XML_PATH_TRUSTKEYSTOREPASS),
			new XmlDecl(XmlType.BOOLEAN, XML_USECLIENT_AUTHENT),
			new XmlDecl(XmlType.BOOLEAN, XML_IMPLICIT_FTPS),
			new XmlDecl(XmlType.BOOLEAN, XML_EXPLICIT_FTPS)
	};
	/**
	 * Overall structure of the Configuration file
	 */
	private static final String XML_ROOT = "/config/";
	private static final String XML_IDENTITY = "identity";
	private static final String XML_SERVER = "server";
	private static final String XML_DIRECTORY = "directory";
	private static final String XML_LIMIT = "limit";
	private static final String XML_NETWORK = "network";
	private static final String XML_EXEC = "exec";
	private static final String XML_DB = "db";
	private static final String XML_SSL = "ssl";
	/**
	 * Global Structure for Server Configuration
	 */
	private static final XmlDecl[] configServer = {
			new XmlDecl(XML_IDENTITY, XmlType.XVAL, XML_ROOT + XML_IDENTITY, configIdentityDecls,
					false),
			new XmlDecl(XML_SERVER, XmlType.XVAL, XML_ROOT + XML_SERVER, configServerParamDecls,
					false),
			new XmlDecl(XML_NETWORK, XmlType.XVAL, XML_ROOT + XML_NETWORK,
					configNetworkServerDecls, false),
			new XmlDecl(XML_EXEC, XmlType.XVAL, XML_ROOT + XML_EXEC, configExecDecls, false),
			new XmlDecl(XML_DIRECTORY, XmlType.XVAL, XML_ROOT + XML_DIRECTORY,
					configDirectoryDecls, false),
			new XmlDecl(XML_LIMIT, XmlType.XVAL, XML_ROOT + XML_LIMIT, configLimitDecls, false),
			new XmlDecl(XML_DB, XmlType.XVAL, XML_ROOT + XML_DB, configDbDecls, false),
			new XmlDecl(XML_SSL, XmlType.XVAL, XML_ROOT + XML_SSL, configSslDecls, false)
	};

	/**
	 * Authentication Fields
	 */
	private static final String XML_AUTHENTIFICATION_ROOT = "authent";
	/**
	 * Authentication Fields
	 */
	private static final String XML_AUTHENTIFICATION_ENTRY = "entry";
	/**
	 * Authentication Fields
	 */
	private static final String XML_AUTHENTIFICATION_BASED = "/" +
			XML_AUTHENTIFICATION_ROOT + "/" + XML_AUTHENTIFICATION_ENTRY;

	/**
	 * Authentication Fields
	 */
	private static final String XML_AUTHENTICATION_USER = "user";

	/**
	 * Authentication Fields
	 */
	private static final String XML_AUTHENTICATION_PASSWD = "passwd";
	/**
	 * Authentication Fields
	 */
	private static final String XML_AUTHENTICATION_PASSWDFILE = "passwdfile";

	/**
	 * Authentication Fields
	 */
	private static final String XML_AUTHENTICATION_ACCOUNT = "account";

	/**
	 * Authentication Fields
	 */
	private static final String XML_AUTHENTICATION_ADMIN = "admin";
	/**
	 * Structure of the Configuration file
	 * 
	 */
	private static final XmlDecl[] configAuthenticationDecls = {
			// identity
			new XmlDecl(XmlType.STRING, XML_AUTHENTICATION_USER),
			new XmlDecl(XmlType.STRING, XML_AUTHENTICATION_PASSWDFILE),
			new XmlDecl(XmlType.STRING, XML_AUTHENTICATION_PASSWD),
			new XmlDecl(XML_AUTHENTICATION_ACCOUNT, XmlType.STRING, XML_AUTHENTICATION_ACCOUNT,
					true),
			new XmlDecl(XmlType.BOOLEAN, XML_AUTHENTICATION_ADMIN),
			// Exec
			new XmlDecl(XmlType.STRING, XML_RETRIEVE_COMMAND),
			new XmlDecl(XmlType.LONG, XML_DELAYRETRIEVE_COMMAND),
			new XmlDecl(XmlType.STRING, XML_STORE_COMMAND),
			new XmlDecl(XmlType.LONG, XML_DELAYSTORE_COMMAND)
	};
	/**
	 * Global Structure for Server Configuration
	 */
	private static final XmlDecl[] authentElements = {
			new XmlDecl(XML_AUTHENTIFICATION_ENTRY, XmlType.XVAL, XML_AUTHENTIFICATION_BASED,
					configAuthenticationDecls, true)
	};

	/**
	 * RANGE of PORT for Passive Mode
	 */
	private static final String RANGE_PORT = "FTP_RANGE_PORT";
	/**
	 * Use to access directly the configuration
	 */
	public static FileBasedConfiguration fileBasedConfiguration;
	/**
	 * All authentications
	 */
	private ConcurrentHashMap<String, SimpleAuth> authentications = new ConcurrentHashMap<String, SimpleAuth>();

	/**
	 * File containing the authentications
	 */
	public String authenticationFile;
	/**
	 * Default HTTP server port
	 */
	public int SERVER_HTTPPORT = 8066;

	/**
	 * Default HTTP server port
	 */
	public int SERVER_HTTPSPORT = 8067;
	/**
	 * Max global memory limit: default is 4GB
	 */
	public long maxGlobalMemory = 0x100000000L;
	/**
	 * Http Admin base
	 */
	public String httpBasePath = "src/main/admin/";
	/**
	 * Delay in ms between two checks
	 */
	public long delayLimit = 10000;
	/**
	 * Does this server will try to compress HTTP connections
	 */
	public boolean useHttpCompression = false;

	/**
	 * Does this server will use Waarp LocalExec Daemon for Execute
	 */
	public boolean useLocalExec = false;

	/**
	 * Crypto Key
	 */
	public Des cryptoKey = null;
	/**
	 * Server Administration Key
	 */
	private byte[] SERVERADMINKEY = null;
	/**
	 * FTP server ID
	 */
	public String HOST_ID = "noId";
	/**
	 * Admin name Id
	 */
	public String ADMINNAME = "noAdmin";
	/**
	 * Limit on CPU and Connection
	 */
	public FtpConstraintLimitHandler constraintLimitHandler = null;

	/**
	 * List of all Http Channels to enable the close call on them using Netty ChannelGroup
	 */
	private ChannelGroup httpChannelGroup = null;
	/**
	 * Bootstrap for Https server
	 */
	private ServerBootstrap httpsBootstrap = null;
	/**
	 * ChannelFactory for HttpsServer part
	 */
	private ChannelFactory httpsChannelFactory = null;
	/**
	 * ThreadPoolExecutor for Http and Https Server
	 */
	private volatile OrderedMemoryAwareThreadPoolExecutor httpPipelineExecutor;
	/**
	 * Monitoring: snmp configuration file (empty means no snmp support)
	 */
	public String snmpConfig = null;
	/**
	 * SNMP Agent (if any)
	 */
	public WaarpSnmpAgent agentSnmp = null;
	/**
	 * Associated MIB
	 */
	public FtpPrivateMib ftpMib = null;
	/**
	 * Monitoring object
	 */
	public FtpMonitoring monitoring = null;

	/**
	 * @param classtype
	 * @param businessHandler
	 *            class that will be used for BusinessHandler
	 * @param dataBusinessHandler
	 *            class that will be used for DataBusinessHandler
	 * @param fileParameter
	 *            the FileParameter to use
	 */
	public FileBasedConfiguration(Class<?> classtype,
			Class<? extends BusinessHandler> businessHandler,
			Class<? extends DataBusinessHandler> dataBusinessHandler,
			FileParameterInterface fileParameter) {
		super(classtype, businessHandler, dataBusinessHandler, fileParameter);
		computeNbThreads();
	}

	private static XmlValue[] configuration = null;
	private static XmlHash hashConfig = null;

	private boolean loadIdentity() {
		XmlValue value = hashConfig.get(XML_SERVER_HOSTID);
		if (value != null && (!value.isEmpty())) {
			HOST_ID = value.getString();
		} else {
			logger.error("Unable to find Host ID in Config file");
			return false;
		}
		return setCryptoKey();
	}

	private boolean loadAuthentication() {
		// if no database, must load authentication from file
		XmlValue value = hashConfig.get(XML_AUTHENTIFICATION_FILE);
		if (value != null && (!value.isEmpty())) {
			authenticationFile = value.getString();
			if (!initializeAuthent(authenticationFile, false)) {
				return false;
			}
		} else {
			logger.warn("Unable to find Authentication file in Config file");
			return false;
		}
		return true;
	}

	private boolean loadServerParam() {
		XmlValue value = hashConfig.get(XML_USEHTTPCOMP);
		if (value != null && (!value.isEmpty())) {
			useHttpCompression = value.getBoolean();
		}
		value = hashConfig.get(XML_USELOCALEXEC);
		if (value != null && (!value.isEmpty())) {
			useLocalExec = value.getBoolean();
			if (useLocalExec) {
				value = hashConfig.get(XML_LEXECADDR);
				String saddr;
				InetAddress addr;
				if (value != null && (!value.isEmpty())) {
					saddr = value.getString();
					try {
						addr = InetAddress.getByName(saddr);
					} catch (UnknownHostException e) {
						logger.error("Unable to find LocalExec Address in Config file");
						return false;
					}
				} else {
					logger.warn("Unable to find LocalExec Address in Config file");
					try {
						addr = InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });
					} catch (UnknownHostException e) {
						logger.error("Unable to find LocalExec Address in Config file");
						return false;
					}
				}
				value = hashConfig.get(XML_LEXECPORT);
				int port;
				if (value != null && (!value.isEmpty())) {
					port = value.getInteger();
				} else {
					port = 9999;
				}
				LocalExecClient.address = new InetSocketAddress(addr, port);
			}
		}
		value = hashConfig.get(XML_SERVER_ADMIN);
		if (value != null && (!value.isEmpty())) {
			ADMINNAME = value.getString();
		} else {
			logger.error("Unable to find Administrator name in Config file");
			return false;
		}
		if (cryptoKey == null) {
			if (!setCryptoKey()) {
				logger.error("Unable to find Crypto Key in Config file");
				return false;
			}
		}
		String passwd;
		value = hashConfig.get(XML_SERVER_PASSWD);
		if (value != null && (!value.isEmpty())) {
			passwd = value.getString();
		} else {
			logger.error("Unable to find Password in Config file");
			return false;
		}
		byte[] decodedByteKeys = null;
		try {
			decodedByteKeys =
					cryptoKey.decryptHexInBytes(passwd);
		} catch (Exception e) {
			logger.error(
					"Unable to Decrypt Server Password in Config file from: " +
							passwd, e);
			return false;
		}
		setSERVERKEY(decodedByteKeys);
		value = hashConfig.get(XML_HTTPADMINPATH);
		if (value == null || (value.isEmpty())) {
			logger.error("Unable to find Http Admin Base in Config file");
			return false;
		}
		String path = value.getString();
		if (path == null || path.length() == 0) {
			logger.warn("Unable to set correct Http Admin Base in Config file. No HTTPS support will be used.");
			httpBasePath = null;
		} else {
			File file = new File(path);
			if (!file.isDirectory()) {
				logger.error("Http Admin is not a directory in Config file");
				return false;
			}
			try {
				httpBasePath =
						FilesystemBasedDirImpl.normalizePath(file.getCanonicalPath()) +
								DirInterface.SEPARATOR;
			} catch (IOException e1) {
				logger.error("Unable to set Http Admin Path in Config file");
				return false;
			}
		}
		httpChannelGroup = new DefaultChannelGroup("HttpOpenR66");
		if (httpBasePath != null) {
			// Key for HTTPS
			value = hashConfig.get(XML_PATH_ADMIN_KEYPATH);
			if (value != null && (!value.isEmpty())) {
				String keypath = value.getString();
				if ((keypath == null) || (keypath.length() == 0)) {
					logger.error("Bad Key Path");
					return false;
				}
				value = hashConfig.get(XML_PATH_ADMIN_KEYSTOREPASS);
				if (value == null || (value.isEmpty())) {
					logger.error("Unable to find KeyStore Passwd");
					return false;
				}
				String keystorepass = value.getString();
				if ((keystorepass == null) || (keystorepass.length() == 0)) {
					logger.error("Bad KeyStore Passwd");
					return false;
				}
				value = hashConfig.get(XML_PATH_ADMIN_KEYPASS);
				if (value == null || (value.isEmpty())) {
					logger.error("Unable to find Key Passwd");
					return false;
				}
				String keypass = value.getString();
				if ((keypass == null) || (keypass.length() == 0)) {
					logger.error("Bad Key Passwd");
					return false;
				}
				try {
					HttpSslPipelineFactory.WaarpSecureKeyStore =
							new WaarpSecureKeyStore(keypath, keystorepass,
									keypass);
				} catch (CryptoException e) {
					logger.error("Bad SecureKeyStore construction for AdminSsl");
					return false;
				}
				// No client authentication
				HttpSslPipelineFactory.WaarpSecureKeyStore.initEmptyTrustStore();
				HttpSslPipelineFactory.waarpSslContextFactory =
						new WaarpSslContextFactory(
								HttpSslPipelineFactory.WaarpSecureKeyStore, true);
			}
		}
		value = hashConfig.get(XML_MONITOR_SNMP_CONFIG);
		if (value != null && (!value.isEmpty())) {
			snmpConfig = value.getString();
			logger.warn("SNMP configuration file: " + snmpConfig);
			File snmpfile = new File(snmpConfig);
			if (snmpfile.canRead()) {
				if (!SnmpConfiguration.setConfigurationFromXml(snmpfile)) {
					logger.warn("Bad SNMP configuration file: " + snmpConfig);
					snmpConfig = null;
				}
			} else {
				logger.warn("Cannot read SNMP configuration file: " + snmpConfig);
				snmpConfig = null;
			}
		} else {
			logger.warn("NO SNMP configuration file");
		}
		return true;
	}

	private boolean loadDirectory() {
		XmlValue value = hashConfig.get(XML_SERVER_HOME);
		if (value == null || (value.isEmpty())) {
			logger.error("Unable to find Home in Config file");
			return false;
		}
		String path = value.getString();
		File file = new File(path);
		if (!file.isDirectory()) {
			logger.error("Home is not a directory in Config file");
			return false;
		}
		try {
			setBaseDirectory(FilesystemBasedDirImpl.normalizePath(file
					.getCanonicalPath()));
		} catch (IOException e1) {
			logger.error("Unable to set Home in Config file: " + path);
			return false;
		}
		return true;
	}

	private boolean loadLimit(boolean updateLimit) {
		XmlValue value = hashConfig.get(XML_LIMITGLOBAL);
		if (value != null && (!value.isEmpty())) {
			serverGlobalReadLimit = value.getLong();
			if (serverGlobalReadLimit <= 0) {
				serverGlobalReadLimit = 0;
			}
			serverGlobalWriteLimit = serverGlobalReadLimit;
			logger.info("Global Limit: {}",
					serverGlobalReadLimit);
		}
		value = hashConfig.get(XML_LIMITSESSION);
		if (value != null && (!value.isEmpty())) {
			serverChannelReadLimit = value.getLong();
			if (serverChannelReadLimit <= 0) {
				serverChannelReadLimit = 0;
			}
			serverChannelWriteLimit = serverChannelReadLimit;
			logger.info("SessionInterface Limit: {}",
					serverChannelReadLimit);
		}
		delayLimit = AbstractTrafficShapingHandler.DEFAULT_CHECK_INTERVAL;
		value = hashConfig.get(XML_LIMITDELAY);
		if (value != null && (!value.isEmpty())) {
			delayLimit = (value.getLong() / 10) * 10;
			if (delayLimit <= 0) {
				delayLimit = 0;
			}
			logger.info("Delay Limit: {}",
					delayLimit);
		}
		boolean useCpuLimit = false;
		boolean useCpuLimitJDK = false;
		double cpulimit = 1.0;
		value = hashConfig.get(XML_CSTRT_USECPULIMIT);
		if (value != null && (!value.isEmpty())) {
			useCpuLimit = value.getBoolean();
			value = hashConfig.get(XML_CSTRT_USECPUJDKLIMIT);
			if (value != null && (!value.isEmpty())) {
				useCpuLimitJDK = value.getBoolean();
			}
			value = hashConfig.get(XML_CSTRT_CPULIMIT);
			if (value != null && (!value.isEmpty())) {
				cpulimit = value.getDouble();
			}
		}
		int connlimit = 0;
		value = hashConfig.get(XML_CSTRT_CONNLIMIT);
		if (value != null && (!value.isEmpty())) {
			connlimit = value.getInteger();
		}
		double lowcpuLimit = 0;
		double highcpuLimit = 0;
		double percentageDecrease = 0;
		long delay = 1000000;
		long limitLowBandwidth = 4096;
		value = hashConfig.get(XML_CSTRT_LOWCPULIMIT);
		if (value != null && (!value.isEmpty())) {
			lowcpuLimit = value.getDouble();
		}
		value = hashConfig.get(XML_CSTRT_HIGHCPULIMIT);
		if (value != null && (!value.isEmpty())) {
			highcpuLimit = value.getDouble();
		}
		value = hashConfig.get(XML_CSTRT_PERCENTDECREASE);
		if (value != null && (!value.isEmpty())) {
			percentageDecrease = value.getDouble();
		}
		value = hashConfig.get(XML_CSTRT_DELAYTHROTTLE);
		if (value != null && (!value.isEmpty())) {
			delay = (value.getLong() / 10) * 10;
		}
		value = hashConfig.get(XML_CSTRT_LIMITLOWBANDWIDTH);
		if (value != null && (!value.isEmpty())) {
			limitLowBandwidth = value.getLong();
		}
		value = hashConfig.get(XML_TIMEOUTCON);
		if (value != null && (!value.isEmpty())) {
			TIMEOUTCON = (value.getLong() / 10) * 10;
		}
		if (highcpuLimit > 0) {
			constraintLimitHandler =
					new FtpConstraintLimitHandler(TIMEOUTCON, useCpuLimit, useCpuLimitJDK,
							cpulimit, connlimit,
							lowcpuLimit, highcpuLimit, percentageDecrease, null, delay,
							limitLowBandwidth);
		} else {
			constraintLimitHandler =
					new FtpConstraintLimitHandler(TIMEOUTCON, useCpuLimit, useCpuLimitJDK,
							cpulimit, connlimit);
		}
		value = hashConfig.get(XML_SERVER_THREAD);
		if (value != null && (!value.isEmpty())) {
			SERVER_THREAD = value.getInteger();
		}
		value = hashConfig.get(XML_CLIENT_THREAD);
		if (value != null && (!value.isEmpty())) {
			CLIENT_THREAD = value.getInteger();
		}
		value = hashConfig.get(XML_MEMORY_LIMIT);
		if (value != null && (!value.isEmpty())) {
			maxGlobalMemory = value.getLong();
		}
		((FilesystemBasedFileParameterImpl) getFileParameter()).deleteOnAbort = false;
		value = hashConfig.get(XML_USENIO);
		if (value != null && (!value.isEmpty())) {
			FilesystemBasedFileParameterImpl.useNio = value.getBoolean();
		}
		value = hashConfig.get(XML_USEFASTMD5);
		if (value != null && (!value.isEmpty())) {
			FilesystemBasedDigest.useFastMd5 = value.getBoolean();
		}
		value = hashConfig.get(XML_BLOCKSIZE);
		if (value != null && (!value.isEmpty())) {
			BLOCKSIZE = value.getInteger();
		}
		value = hashConfig.get(XML_DELETEONABORT);
		if (value != null && (!value.isEmpty())) {
			deleteOnAbort = value.getBoolean();
		}
		// We use Apache Commons IO
		FilesystemBasedDirJdkAbstract.ueApacheCommonsIo = true;
		return true;
	}

	private boolean loadNetworkServer() {
		XmlValue value = hashConfig.get(XML_SERVER_PORT);
		int port = 21;
		if (value != null && (!value.isEmpty())) {
			port = value.getInteger();
		} else {
			port = 21;
		}
		setServerPort(port);
		value = hashConfig.get(XML_SERVER_ADDRESS);
		String address = null;
		if (value != null && (!value.isEmpty())) {
			address = value.getString();
		}
		setServerAddress(address);
		int min = 100;
		int max = 65535;
		value = hashConfig.get(XML_RANGE_PORT_MIN);
		if (value != null && (!value.isEmpty())) {
			min = value.getInteger();
		}
		value = hashConfig.get(XML_RANGE_PORT_MAX);
		if (value != null && (!value.isEmpty())) {
			max = value.getInteger();
		}
		logger.warn("Passive Port range Min: " + min + " Max: " + max);
		CircularIntValue rangePort = new CircularIntValue(min, max);
		setRangePort(rangePort);
		value = hashConfig.get(XML_SERVER_HTTP_PORT);
		int httpport = 8066;
		if (value != null && (!value.isEmpty())) {
			httpport = value.getInteger();
		}
		SERVER_HTTPPORT = httpport;
		value = hashConfig.get(XML_SERVER_HTTPS_PORT);
		int httpsport = 8067;
		if (value != null && (!value.isEmpty())) {
			httpsport = value.getInteger();
		}
		SERVER_HTTPSPORT = httpsport;
		return true;
	}

	/**
	 * Set the Crypto Key from the Document
	 * 
	 * @param document
	 * @return True if OK
	 */
	private boolean setCryptoKey() {
		XmlValue value = hashConfig.get(XML_PATH_CRYPTOKEY);
		if (value == null || (value.isEmpty())) {
			logger.error("Unable to find CryptoKey in Config file");
			return false;
		}
		String filename = value.getString();
		File key = new File(filename);
		Des des = new Des();
		try {
			des.setSecretKey(key);
		} catch (CryptoException e) {
			logger.error("Unable to load CryptoKey from Config file");
			return false;
		} catch (IOException e) {
			logger.error("Unable to load CryptoKey from Config file");
			return false;
		}
		cryptoKey = des;
		return true;
	}

	/**
	 * 
	 * @return True if the global Exec parameters are correctly loaded
	 */
	private boolean loadExec() {
		// Specific Exec command options
		XmlValue value = hashConfig.get(XML_RETRIEVE_COMMAND);
		if (value == null || (value.isEmpty())) {
			logger.error("Unable to find Retrieve Command in Config file");
			return false;
		}
		String retrieve = value.getString();
		value = hashConfig.get(XML_DELAYRETRIEVE_COMMAND);
		long retrievedelay = 0;
		if (value != null && (!value.isEmpty())) {
			retrievedelay = (value.getLong() / 10) * 10;
		}
		value = hashConfig.get(XML_STORE_COMMAND);
		if (value == null || (value.isEmpty())) {
			logger.error("Unable to find Store Command in Config file");
			return false;
		}
		String store = value.getString();
		value = hashConfig.get(XML_DELAYSTORE_COMMAND);
		long storedelay = 0;
		if (value != null && (!value.isEmpty())) {
			storedelay = (value.getLong() / 10) * 10;
		}
		AbstractExecutor.initializeExecutor(retrieve, retrievedelay, store, storedelay);
		return true;
	}

	/**
	 * Load database parameter
	 * 
	 * @param document
	 * @return True if OK
	 */
	private boolean loadDatabase() {
		XmlValue value = hashConfig.get(XML_DBDRIVER);
		if (value == null || (value.isEmpty())) {
			logger.error("Unable to find DBDriver in Config file");
			DbConstant.admin = new DbAdmin(); // no database support
		} else {
			String dbdriver = value.getString();
			value = hashConfig.get(XML_DBSERVER);
			if (value == null || (value.isEmpty())) {
				logger.error("Unable to find DBServer in Config file");
				return false;
			}
			String dbserver = value.getString();
			value = hashConfig.get(XML_DBUSER);
			if (value == null || (value.isEmpty())) {
				logger.error("Unable to find DBUser in Config file");
				return false;
			}
			String dbuser = value.getString();
			value = hashConfig.get(XML_DBPASSWD);
			if (value == null || (value.isEmpty())) {
				logger.error("Unable to find DBPassword in Config file");
				return false;
			}
			String dbpasswd = value.getString();
			if (dbdriver == null || dbserver == null || dbuser == null ||
					dbpasswd == null || dbdriver.length() == 0 ||
					dbserver.length() == 0 || dbuser.length() == 0 ||
					dbpasswd.length() == 0) {
				logger.error("Unable to find Correct DB data in Config file");
				return false;
			}
			try {
				DbConstant.admin =
						DbModelFactory.initialize(dbdriver, dbserver, dbuser, dbpasswd,
								true);
			} catch (WaarpDatabaseNoConnectionException e2) {
				logger.error("Unable to Connect to DB", e2);
				return false;
			}
			AbstractExecutor.useDatabase = true;
		}
		return true;
	}


	protected boolean loadSsl() {
		// StoreKey for Server
		XmlValue value = hashConfig.get(XML_PATH_KEYPATH);
		if (value == null || (value.isEmpty())) {
			logger.info("Unable to find Key Path");
			getFtpInternalConfiguration().setUsingNativeSsl(false);
			getFtpInternalConfiguration().setAcceptAuthProt(false);
			return true;
		} else {
			String keypath = value.getString();
			if ((keypath == null) || (keypath.length() == 0)) {
				logger.error("Bad Key Path");
				return false;
			}
			value = hashConfig.get(XML_PATH_KEYSTOREPASS);
			if (value == null || (value.isEmpty())) {
				logger.error("Unable to find KeyStore Passwd");
				return false;
			}
			String keystorepass = value.getString();
			if ((keystorepass == null) || (keystorepass.length() == 0)) {
				logger.error("Bad KeyStore Passwd");
				return false;
			}
			value = hashConfig.get(XML_PATH_KEYPASS);
			if (value == null || (value.isEmpty())) {
				logger.error("Unable to find Key Passwd");
				return false;
			}
			String keypass = value.getString();
			if ((keypass == null) || (keypass.length() == 0)) {
				logger.error("Bad Key Passwd");
				return false;
			}
			try {
				FtpsPipelineFactory.WaarpSecureKeyStore =
						new WaarpSecureKeyStore(keypath, keystorepass,
								keypass);
			} catch (CryptoException e) {
				logger.error("Bad SecureKeyStore construction");
				return false;
			}

		}
		// TrustedKey for OpenR66 server
		value = hashConfig.get(XML_PATH_TRUSTKEYPATH);
		if (value == null || (value.isEmpty())) {
			logger.info("Unable to find TRUST Key Path");
			FtpsPipelineFactory.WaarpSecureKeyStore.initEmptyTrustStore();
		} else {
			String keypath = value.getString();
			if ((keypath == null) || (keypath.length() == 0)) {
				logger.error("Bad TRUST Key Path");
				return false;
			}
			value = hashConfig.get(XML_PATH_TRUSTKEYSTOREPASS);
			if (value == null || (value.isEmpty())) {
				logger.error("Unable to find TRUST KeyStore Passwd");
				return false;
			}
			String keystorepass = value.getString();
			if ((keystorepass == null) || (keystorepass.length() == 0)) {
				logger.error("Bad TRUST KeyStore Passwd");
				return false;
			}
			boolean useClientAuthent = false;
			value = hashConfig.get(XML_USECLIENT_AUTHENT);
			if (value != null && (!value.isEmpty())) {
				useClientAuthent = value.getBoolean();
			}
			try {
				FtpsPipelineFactory.WaarpSecureKeyStore.initTrustStore(keypath,
						keystorepass, useClientAuthent);
			} catch (CryptoException e) {
				logger.error("Bad TrustKeyStore construction");
				return false;
			}
		}
		FtpsPipelineFactory.waarpSslContextFactory =
				new WaarpSslContextFactory(
						FtpsPipelineFactory.WaarpSecureKeyStore);
		boolean useImplicit = false;
		value = hashConfig.get(XML_IMPLICIT_FTPS);
		if (value != null && (!value.isEmpty())) {
			useImplicit = value.getBoolean();
		}
		boolean useExplicit = false;
		value = hashConfig.get(XML_EXPLICIT_FTPS);
		if (value != null && (!value.isEmpty())) {
			useExplicit = value.getBoolean();
		}
		if (useImplicit && useExplicit) {
			logger.error("Only one of IMPLICIT or EXPLICIT could be True");
			return false;
		}
		if ((!useImplicit) && (!useExplicit)) {
			logger.error("Since all SecureStore are specified, one of IMPLICIT or EXPLICIT should be True");
			logger.warn("FTPS support will be ignored...");
			getFtpInternalConfiguration().setUsingNativeSsl(false);
			getFtpInternalConfiguration().setAcceptAuthProt(false);
			return true;
		}
		getFtpInternalConfiguration().setUsingNativeSsl(useImplicit);
		getFtpInternalConfiguration().setAcceptAuthProt(useExplicit);
		return true;
	}

	/**
	 * Initiate the configuration from the xml file for server
	 * 
	 * @param filename
	 * @return True if OK
	 */
	public boolean setConfigurationServerFromXml(String filename) {
		Document document = null;
		// Open config file
		try {
			document = new SAXReader().read(filename);
		} catch (DocumentException e) {
			logger.error("Unable to read the XML Config file: " + filename, e);
			return false;
		}
		if (document == null) {
			logger.error("Unable to read the XML Config file: " + filename);
			return false;
		}
		configuration = XmlUtil.read(document, configServer);
		hashConfig = new XmlHash(configuration);
		// Now read the configuration
		if (!loadIdentity()) {
			logger.error("Cannot load Identity");
			return false;
		}
		if (!loadDatabase()) {
			logger.error("Cannot load Database configuration");
			return false;
		}
		if (!loadServerParam()) {
			logger.error("Cannot load Server Parameters");
			return false;
		}
		if (!loadDirectory()) {
			logger.error("Cannot load Directory configuration");
			return false;
		}
		if (!loadLimit(false)) {
			logger.error("Cannot load Limit configuration");
			return false;
		}
		if (!loadNetworkServer()) {
			logger.error("Cannot load Network configuration");
			return false;
		}
		if (!loadExec()) {
			logger.error("Cannot load Exec configuration");
			return false;
		}
		// if (!DbConstant.admin.isConnected) {
		// if no database, must load authentication from file
		if (!loadAuthentication()) {
			logger.error("Cannot load Authentication configuration");
			return false;
		}
		if (!loadSsl()) {
			// ignore and continue => No SSL
			getFtpInternalConfiguration().setUsingNativeSsl(false);
			getFtpInternalConfiguration().setAcceptAuthProt(false);
			return false;
		}
		// }
		hashConfig.clear();
		hashConfig = null;
		configuration = null;
		return true;
	}

	/**
	 * Configure HTTPS
	 */
	public void configureHttps() {
		// Now start the HTTPS support
		// Configure the server.
		httpPipelineExecutor = new OrderedMemoryAwareThreadPoolExecutor(
				CLIENT_THREAD, maxGlobalMemory / 10, maxGlobalMemory, 1000,
				TimeUnit.MILLISECONDS, getFtpInternalConfiguration().getObjectSizeEstimator(),
				new WaarpThreadFactory("HttpExecutor"));
		httpsChannelFactory = new NioServerSocketChannelFactory(
				Executors.newCachedThreadPool(),
				Executors.newCachedThreadPool(),
				SERVER_THREAD);
		httpsBootstrap = new ServerBootstrap(
				httpsChannelFactory);
		// Set up the event pipeline factory.
		httpsBootstrap.setPipelineFactory(new HttpSslPipelineFactory(useHttpCompression,
				false, getHttpPipelineExecutor()));
		httpsBootstrap.setOption("child.tcpNoDelay", true);
		httpsBootstrap.setOption("child.keepAlive", true);
		httpsBootstrap.setOption("child.reuseAddress", true);
		httpsBootstrap.setOption("child.connectTimeoutMillis", TIMEOUTCON);
		httpsBootstrap.setOption("tcpNoDelay", true);
		httpsBootstrap.setOption("reuseAddress", true);
		httpsBootstrap.setOption("connectTimeoutMillis", TIMEOUTCON);
		// Bind and start to accept incoming connections.
		logger.warn("Start Https Support on port: " + SERVER_HTTPSPORT);
		httpChannelGroup.add(httpsBootstrap.bind(new InetSocketAddress(SERVER_HTTPSPORT)));
	}

	/**
	 * Configure ConstraintLimitHandler
	 */
	public void configureConstraint() {
		constraintLimitHandler.setHandler(
				this.getFtpInternalConfiguration().getGlobalTrafficShapingHandler());
	}

	/**
	 * Configure LocalExec
	 */
	public void configureLExec() {
		if (useLocalExec) {
			LocalExecClient.initialize(this.CLIENT_THREAD, this.maxGlobalMemory);
		}
	}

	/**
	 * Configure the SNMP support if needed
	 * 
	 * @throws FtpNoConnectionException
	 */
	public void configureSnmp() throws FtpNoConnectionException {
		monitoring = new FtpMonitoring(null);
		if (snmpConfig != null) {
			int snmpPortShow = getServerPort();
			ftpMib =
					new FtpPrivateMib(snmpPortShow);
			WaarpMOFactory.factory = new FtpVariableFactory();
			agentSnmp = new WaarpSnmpAgent(new File(snmpConfig), monitoring, ftpMib);
			try {
				agentSnmp.start();
				logger.debug("SNMP configured");
			} catch (IOException e) {
				monitoring.releaseResources();
				monitoring = null;
				ftpMib = null;
				agentSnmp = null;
				throw new FtpNoConnectionException("AgentSnmp Error while starting", e);
			}
		}
	}

	/**
	 * @param serverkey
	 *            the SERVERADMINKEY to set
	 */
	public void setSERVERKEY(byte[] serverkey) {
		SERVERADMINKEY = serverkey;
	}

	/**
	 * Check the password for Shutdown
	 * 
	 * @param password
	 * @return True if the password is OK
	 */
	public boolean checkPassword(String password) {
		if (password == null) {
			return false;
		}
		return Arrays.equals(SERVERADMINKEY, password.getBytes());
	}

	/**
	 * Initialize Authentication from current authenticationFile
	 * 
	 * @param filename
	 *            the filename from which authentication will be loaded
	 * @param purge
	 *            if True, the current authentications are totally replaced by the new ones
	 * @return True if OK
	 */
	@SuppressWarnings("unchecked")
	public boolean initializeAuthent(String filename, boolean purge) {
		Document document = null;
		try {
			document = new SAXReader().read(filename);
		} catch (DocumentException e) {
			logger.error("Unable to read the XML Authentication file: " +
					filename, e);
			return false;
		}
		if (document == null) {
			logger.error("Unable to read the XML Authentication file: " +
					filename);
			return false;
		}
		XmlValue[] configuration = XmlUtil.read(document, authentElements);
		XmlHash hashConfig = new XmlHash(configuration);

		XmlValue value = hashConfig.get(XML_AUTHENTIFICATION_ENTRY);
		List<XmlValue[]> list = (List<XmlValue[]>) value.getList();
		ConcurrentHashMap<String, SimpleAuth> newAuthents =
				new ConcurrentHashMap<String, SimpleAuth>();
		for (XmlValue[] xmlValues : list) {
			hashConfig = new XmlHash(xmlValues);
			value = hashConfig.get(XML_AUTHENTICATION_USER);
			if (value == null || (value.isEmpty())) {
				logger.error("Unable to find a User in Config file");
				continue;
			}
			String user = value.getString();
			value = hashConfig.get(XML_AUTHENTICATION_ACCOUNT);
			if (value == null || (value.isEmpty())) {
				logger.error("Unable to find a Account in Config file: " + user);
				continue;
			}
			String[] account = null;
			List<String> listaccount = (List<String>) value.getList();
			if (!listaccount.isEmpty()) {
				account = new String[listaccount.size()];
				int i = 0;
				Iterator<String> iteratoraccount = listaccount.iterator();
				while (iteratoraccount.hasNext()) {
					account[i] = iteratoraccount.next();
					// logger.debug("User: {} Acct: {}", user, account[i]);
					File directory = new File(getBaseDirectory() + "/" + user + "/" + account[i]);
					directory.mkdirs();
					i++;
				}
			} else {
				logger.error("Unable to find a Account in Config file: " + user);
				continue;
			}
			value = hashConfig.get(XML_AUTHENTICATION_ADMIN);
			boolean isAdmin = false;
			if (value != null && (!value.isEmpty())) {
				isAdmin = value.getBoolean();
			}
			String retrcmd = null;
			long retrdelay = 0;
			String storcmd = null;
			long stordelay = 0;
			value = hashConfig.get(XML_RETRIEVE_COMMAND);
			if (value != null && (!value.isEmpty())) {
				retrcmd = value.getString();
			}
			value = hashConfig.get(XML_DELAYRETRIEVE_COMMAND);
			if (value != null && (!value.isEmpty())) {
				retrdelay = (value.getLong() / 10) * 10;
			}
			value = hashConfig.get(XML_STORE_COMMAND);
			if (value != null && (!value.isEmpty())) {
				storcmd = value.getString();
			}
			value = hashConfig.get(XML_DELAYSTORE_COMMAND);
			if (value != null && (!value.isEmpty())) {
				stordelay = (value.getLong() / 10) * 10;
			}
			String passwd;
			value = hashConfig.get(XML_AUTHENTICATION_PASSWDFILE);
			if (value != null && (!value.isEmpty())) {
				// load key from file
				File key = new File(value.getString());
				if (!key.canRead()) {
					logger.error("Cannot read key for user " + user + ":" + key.getName());
					continue;
				}
				try {
					byte[] byteKeys = cryptoKey.decryptHexFile(key);
					passwd = new String(byteKeys);
				} catch (Exception e2) {
					logger.error("Cannot read key for user " + user, e2);
					continue;
				}
			} else {
				value = hashConfig.get(XML_AUTHENTICATION_PASSWD);
				if (value != null && (!value.isEmpty())) {
					String encrypted = value.getString();
					byte[] byteKeys = null;
					try {
						byteKeys =
								cryptoKey.decryptHexInBytes(encrypted);
						passwd = new String(byteKeys);
					} catch (Exception e) {
						logger.error(
								"Unable to Decrypt Key for user " + user, e);
						continue;
					}
				} else {
					logger.error("Unable to find Password in Config file");
					// DO NOT Allow empty key
					continue;
				}
			}
			SimpleAuth auth = new SimpleAuth(user, passwd, account,
					storcmd, stordelay, retrcmd, retrdelay);
			auth.setAdmin(isAdmin);
			newAuthents.put(user, auth);
			hashConfig.clear();
		}
		hashConfig.clear();
		configuration = null;
		if (purge) {
			ConcurrentHashMap<String, SimpleAuth> previousOne = authentications;
			authentications = newAuthents;
			previousOne.clear();
		} else {
			authentications.putAll(newAuthents);
			newAuthents.clear();
		}
		document = null;
		return true;
	}

	/**
	 * Export the Authentication to the original files
	 * 
	 * @param filename
	 *            the filename where the authentication will be exported
	 * @return True if successful
	 */
	public boolean saveAuthenticationFile(String filename) {
		Document document = XmlUtil.createEmptyDocument();
		XmlValue[] roots = new XmlValue[1];
		XmlValue root = new XmlValue(authentElements[0]);
		roots[0] = root;
		Enumeration<SimpleAuth> auths = authentications.elements();
		while (auths.hasMoreElements()) {
			SimpleAuth auth = auths.nextElement();
			XmlValue[] values = new XmlValue[configAuthenticationDecls.length];
			for (int i = 0; i < configAuthenticationDecls.length; i++) {
				values[i] = new XmlValue(configAuthenticationDecls[i]);
			}
			try {
				values[0].setFromString(auth.user);
				// PasswdFile: none values[1].setFromString();
				values[2].setFromString(auth.password);
			} catch (InvalidArgumentException e1) {
				logger.error("Error during Write Authentication file", e1);
				return false;
			}
			// Accounts
			String[] accts = auth.accounts;
			for (String string : accts) {
				try {
					values[3].addFromString(string);
				} catch (InvalidObjectException e) {
					logger.error("Error during Write Authentication file", e);
					return false;
				} catch (InvalidArgumentException e) {
					logger.error("Error during Write Authentication file", e);
					return false;
				}
			}
			try {
				values[4].setValue(auth.isAdmin);
			} catch (InvalidObjectException e) {
				logger.error("Error during Write Authentication file", e);
				return false;
			}
			try {
				values[5].setFromString(auth.retrCmd);
			} catch (InvalidArgumentException e1) {
				logger.error("Error during Write Authentication file", e1);
				return false;
			}
			try {
				values[6].setValue(auth.retrDelay);
			} catch (InvalidObjectException e) {
				logger.error("Error during Write Authentication file", e);
				return false;
			}
			try {
				values[7].setFromString(auth.storCmd);
			} catch (InvalidArgumentException e1) {
				logger.error("Error during Write Authentication file", e1);
				return false;
			}
			try {
				values[8].setValue(auth.storDelay);
			} catch (InvalidObjectException e) {
				logger.error("Error during Write Authentication file", e);
				return false;
			}
			try {
				root.addValue(values);
			} catch (InvalidObjectException e) {
				logger.error("Error during Write Authentication file", e);
				return false;
			}
		}
		XmlUtil.write(document, roots);
		try {
			XmlUtil.saveDocument(filename, document);
		} catch (IOException e1) {
			logger.error("Cannot write to file: " + filename + " since {}", e1.getMessage());
			return false;
		}
		return true;
	}

	/**
	 * @param user
	 * @return the SimpleAuth if any for this user
	 */
	public SimpleAuth getSimpleAuth(String user) {
		return authentications.get(user);
	}

	/**
	 * @param format
	 *            Format in HTML to use as ouput format
	 * @return the Html String containing the table of all Authentication entries
	 */
	public String getHtmlAuth(String format) {
		String result;
		StringBuilder builder = new StringBuilder();
		/*
		 * XXXUSERXXX XXXPWDXXX XXXACTSXXX XXXADMXXX XXXSTCXXX XXXSTDXXX XXXRTCXXX XXXRTDXXX
		 */
		Enumeration<SimpleAuth> simpleAuths = authentications.elements();
		SimpleAuth auth = null;
		while (simpleAuths.hasMoreElements()) {
			auth = simpleAuths.nextElement();
			String newElt = format.replace("XXXUSERXXX", auth.user);
			newElt = newElt.replace("XXXPWDXXX", auth.password);
			if (auth.storCmd != null)
				newElt = newElt.replace("XXXSTCXXX", auth.storCmd);
			else
				newElt = newElt.replace("XXXSTCXXX", "");
			if (auth.retrCmd != null)
				newElt = newElt.replace("XXXRTCXXX", auth.retrCmd);
			else
				newElt = newElt.replace("XXXRTCXXX", "");
			newElt = newElt.replace("XXXSTDXXX", Long.toString(auth.storDelay));
			newElt = newElt.replace("XXXRTDXXX", Long.toString(auth.retrDelay));
			newElt = newElt.replace("XXXADMXXX", Boolean.toString(auth.isAdmin));
			if (auth.accounts != null) {
				StringBuilder accts = new StringBuilder();
				for (int i = 0; i < auth.accounts.length - 1; i++) {
					accts.append(auth.accounts[i]);
					accts.append(", ");
				}
				accts.append(auth.accounts[auth.accounts.length - 1]);
				newElt = newElt.replace("XXXACTSXXX", accts.toString());
			} else {
				newElt = newElt.replace("XXXACTSXXX", "No Account");
			}
			builder.append(newElt);
		}
		result = builder.toString();
		return result;
	}

	/**
	 * Only available with Database support for Waarp
	 * 
	 * @param format
	 *            Format in HTML to use as ouput format
	 * @param limit
	 *            number of TransferLog to populate
	 * @return the Html String containing the table of all Transfer entries
	 */
	public String getHtmlTransfer(String format, int limit) {
		String result;
		StringBuilder builder = new StringBuilder();
		/*
		 * XXXIDXXX XXXUSERXXX XXXACCTXXX XXXFILEXXX XXXMODEXXX XXXSTATUSXXX XXXINFOXXX XXXUPINFXXX
		 * XXXSTARTXXX XXXSTOPXXX
		 */
		if (!DbConstant.admin.isConnected) {
			return "";
		}
		DbPreparedStatement preparedStatement = null;
		try {
			try {
				preparedStatement =
						DbTransferLog
								.getStatusPrepareStament(DbConstant.admin.session, null, limit);
				preparedStatement.executeQuery();
			} catch (WaarpDatabaseNoConnectionException e) {
				return "";
			} catch (WaarpDatabaseSqlException e) {
				return "";
			}
			try {
				while (preparedStatement.getNext()) {
					DbTransferLog log = DbTransferLog.getFromStatement(preparedStatement);
					String newElt = format
							.replaceAll("XXXIDXXX", Long.toString(log.getSpecialId()));
					newElt = newElt.replaceAll("XXXUSERXXX", log.getUser());
					newElt = newElt.replaceAll("XXXACCTXXX", log.getAccount());
					newElt = newElt.replace("XXXFILEXXX", log.getFilename());
					newElt = newElt.replace("XXXMODEXXX", log.getMode());
					newElt = newElt.replace("XXXSTATUSXXX", log.getErrorInfo().getMesg());
					newElt = newElt.replace("XXXINFOXXX", log.getInfotransf());
					newElt = newElt.replace("XXXUPINFXXX", log.getUpdatedInfo().name());
					newElt = newElt.replace("XXXSTARTXXX", log.getStart().toString());
					newElt = newElt.replace("XXXSTOPXXX", log.getStop().toString());
					builder.append(newElt);
				}
			} catch (WaarpDatabaseNoConnectionException e) {
				return "";
			} catch (WaarpDatabaseSqlException e) {
				return "";
			}
			result = builder.toString();
			return result;
		} finally {
			if (preparedStatement != null) {
				preparedStatement.realClose();
			}
		}
	}

	/**
	 * @see org.waarp.ftp.core.config.FtpConfiguration#getNextRangePort()
	 */
	@Override
	public int getNextRangePort() {
		try {
			return ((CircularIntValue) getProperty(RANGE_PORT)).getNext();
		} catch (FtpUnknownFieldException e) {
			return -1;
		}
	}

	/**
	 * 
	 * @param rangePort
	 *            the range of available ports for Passive connections
	 */
	private void setRangePort(CircularIntValue rangePort) {
		setProperty(RANGE_PORT, rangePort);
	}

	/**
	 * @return the httpPipelineExecutor
	 */
	public OrderedMemoryAwareThreadPoolExecutor getHttpPipelineExecutor() {
		return httpPipelineExecutor;
	}

	/**
	 * @return the httpChannelGroup
	 */
	public ChannelGroup getHttpChannelGroup() {
		return httpChannelGroup;
	}

	/**
	 * Finalize resources attached to handlers
	 * 
	 * @author Frederic Bregier
	 */
	private static class GgChannelGroupFutureListener implements
			ChannelGroupFutureListener {
		OrderedMemoryAwareThreadPoolExecutor pool;
		String name;
		ChannelFactory channelFactory;

		public GgChannelGroupFutureListener(
				String name,
				OrderedMemoryAwareThreadPoolExecutor pool,
				ChannelFactory channelFactory) {
			this.name = name;
			this.pool = pool;
			this.channelFactory = channelFactory;
		}

		public void operationComplete(ChannelGroupFuture future)
				throws Exception {
			if (pool != null) {
				pool.shutdownNow();
			}
			if (channelFactory != null) {
				channelFactory.releaseExternalResources();
			}
			logger.info("Done with shutdown " + name);
		}
	}

	@Override
	public void releaseResources() {
		super.releaseResources();
		final int result = getHttpChannelGroup().size();
		logger.debug("HttpChannelGroup: " + result);
		getHttpChannelGroup().close().addListener(
				new GgChannelGroupFutureListener(
						"HttpChannelGroup",
						httpPipelineExecutor,
						httpsChannelFactory));
		if (useLocalExec) {
			LocalExecClient.releaseResources();
		}
		this.constraintLimitHandler.release();
		agentSnmp.stop();
		DbAdmin.closeAllConnection();
	}

	/*
	 * (non-Javadoc)
	 * @see goldengate.ftp.core.config.FtpConfiguration#inShutdownProcess()
	 */
	@Override
	public void inShutdownProcess() {
		if (ftpMib != null) {
			ftpMib.notifyStartStop("Shutdown in progress for " + HOST_ID, "Gives extra seconds: "
					+ TIMEOUTCON);
		}
	}
}
