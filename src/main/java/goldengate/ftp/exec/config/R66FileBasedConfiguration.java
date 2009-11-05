/**
 * Copyright 2009, Frederic Bregier, and individual contributors by the @author
 * tags. See the COPYRIGHT.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3.0 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package goldengate.ftp.exec.config;

import goldengate.common.logging.GgInternalLogger;
import goldengate.common.logging.GgInternalLoggerFactory;

import openr66.context.authentication.R66Auth;
import openr66.database.DbAdmin;
import openr66.database.DbConstant;
import openr66.database.data.DbConfiguration;
import openr66.database.exception.OpenR66DatabaseException;
import openr66.database.exception.OpenR66DatabaseNoConnectionError;
import openr66.database.model.DbModelFactory;
import openr66.protocol.configuration.Configuration;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

/**
 * OpenR66 File Based Configuration for submission only
 *
 * @author frederic bregier
 *
 */
public class R66FileBasedConfiguration {
    /**
     * Internal Logger
     */
    private static final GgInternalLogger logger = GgInternalLoggerFactory
            .getLogger(R66FileBasedConfiguration.class);

    /**
     * SERVER HOSTID
     */
    private static final String XML_SERVER_HOSTID = "/config/hostid";

    /**
     * SERVER SSL HOSTID
     */
    private static final String XML_SERVER_SSLHOSTID = "/config/sslhostid";

    /**
     * Size by default of block size for receive/sending files. Should be a
     * multiple of 8192 (maximum = 64K due to block limitation to 2 bytes)
     */
    private static final String XML_BLOCKSIZE = "/config/blocksize";

    /**
     * Database Driver as of oracle, mysql, postgresql, h2
     */
    private static final String XML_DBDRIVER = "/config/dbdriver";

    /**
     * Database Server connection string as of
     * jdbc:type://[host:port],[failoverhost:port]
     * .../[database][?propertyName1][
     * =propertyValue1][&propertyName2][=propertyValue2]...
     */
    private static final String XML_DBSERVER = "/config/dbserver";

    /**
     * Database User
     */
    private static final String XML_DBUSER = "/config/dbuser";

    /**
     * Database Password
     */
    private static final String XML_DBPASSWD = "/config/dbpasswd";

    /**
     * Initiate the configuration from the xml file for database client
     * used by GoldenGate Ftp Exec
     *
     * @param filename
     * @return True if OK
     */
    public static boolean setSimpleClientConfigurationFromXml(String filename) {
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
        if (!loadCommon(document)) {
            logger.error("Unable to load commons in Config file: " + filename);
            return false;
        }
        if (!loadDatabase(document)) {
            return false;
        }
        if (!loadFromDatabase(document)) {
            return false;
        }

        Configuration.configuration.HOST_AUTH = R66Auth.getServerAuth(
                DbConstant.admin.session, Configuration.configuration.HOST_ID);
        if (Configuration.configuration.HOST_AUTH == null) {
            logger.error("Cannot find Authentication for current host");
            return false;
        }
        if (Configuration.configuration.HOST_SSLID != null) {
            Configuration.configuration.HOST_SSLAUTH = R66Auth.getServerAuth(
                    DbConstant.admin.session,
                    Configuration.configuration.HOST_SSLID);
            if (Configuration.configuration.HOST_SSLAUTH == null) {
                logger.error("Cannot find SSL Authentication for current host");
                return false;
            }
        }
        return true;
    }

    /**
     * Load common configuration from XML document
     *
     * @param document
     * @return True if OK
     */
    public static boolean loadCommon(Document document) {
        Node node = null;
        node = document.selectSingleNode(XML_SERVER_HOSTID);
        if (node == null) {
            logger.error("Unable to find Host ID in Config file");
            return false;
        }
        Configuration.configuration.HOST_ID = node.getText();
        node = document.selectSingleNode(XML_SERVER_SSLHOSTID);
        if (node == null) {
            logger
                    .warn("Unable to find Host SSL ID in Config file so no SSL support will be used");
            Configuration.configuration.HOST_SSLID = null;
        } else {
            Configuration.configuration.HOST_SSLID = node.getText();
        }
        node = document.selectSingleNode(XML_BLOCKSIZE);
        if (node != null) {
            Configuration.configuration.BLOCKSIZE = Integer.parseInt(node
                    .getText());
        }
        return true;
    }

    /**
     * Load data from database or from files if not connected
     *
     * @param document
     * @return True if OK
     */
    private static boolean loadFromDatabase(Document document) {
        if (DbConstant.admin.isConnected) {
            // load from database the limit to apply
            try {
                DbConfiguration configuration = new DbConfiguration(
                        DbConstant.admin.session,
                        Configuration.configuration.HOST_ID);
                configuration.updateConfiguration();
            } catch (OpenR66DatabaseException e) {
                logger.warn("Cannot load configuration from database", e);
            }
        } else {
            return false;
        }
        return true;
    }

    /**
     * Load database parameter
     *
     * @param document
     * @return True if OK
     */
    public static boolean loadDatabase(Document document) {
        Node node = document.selectSingleNode(XML_DBDRIVER);
        if (node == null) {
            logger.error("Unable to find DBDriver in Config file");
            DbConstant.admin = new DbAdmin(); // no database support
        } else {
            String dbdriver = node.getText();
            node = document.selectSingleNode(XML_DBSERVER);
            if (node == null) {
                logger.error("Unable to find DBServer in Config file");
                return false;
            }
            String dbserver = node.getText();
            node = document.selectSingleNode(XML_DBUSER);
            if (node == null) {
                logger.error("Unable to find DBUser in Config file");
                return false;
            }
            String dbuser = node.getText();
            node = document.selectSingleNode(XML_DBPASSWD);
            if (node == null) {
                logger.error("Unable to find DBPassword in Config file");
                return false;
            }
            String dbpasswd = node.getText();
            if (dbdriver == null || dbserver == null || dbuser == null ||
                    dbpasswd == null || dbdriver.length() == 0 ||
                    dbserver.length() == 0 || dbuser.length() == 0 ||
                    dbpasswd.length() == 0) {
                logger.error("Unable to find Correct DB data in Config file");
                return false;
            }
            try {
                DbModelFactory.initialize(dbdriver, dbserver, dbuser, dbpasswd,
                        true);
            } catch (OpenR66DatabaseNoConnectionError e2) {
                logger.error("Unable to Connect to DB", e2);
                return false;
            }
        }
        return true;
    }
}
