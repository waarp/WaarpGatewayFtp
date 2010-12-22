/**
   This file is part of GoldenGate Project (named also GoldenGate or GG).

   Copyright 2009, Frederic Bregier, and individual contributors by the @author
   tags. See the COPYRIGHT.txt in the distribution for a full listing of
   individual contributors.

   All GoldenGate Project is free software: you can redistribute it and/or 
   modify it under the terms of the GNU General Public License as published 
   by the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   GoldenGate is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with GoldenGate .  If not, see <http://www.gnu.org/licenses/>.
 */
package goldengate.ftp.exec.adminssl;

import goldengate.common.command.exception.CommandAbstractException;
import goldengate.common.database.DbAdmin;
import goldengate.common.database.DbSession;
import goldengate.common.database.exception.GoldenGateDatabaseNoConnectionError;
import goldengate.common.exception.FileTransferException;
import goldengate.common.exception.InvalidArgumentException;
import goldengate.common.future.GgFuture;
import goldengate.common.logging.GgInternalLogger;
import goldengate.common.logging.GgInternalLoggerFactory;
import goldengate.common.utility.GgStringUtils;
import goldengate.ftp.core.session.FtpSession;
import goldengate.ftp.core.utils.FtpChannelUtils;
import goldengate.ftp.exec.config.FileBasedConfiguration;
import goldengate.ftp.exec.database.DbConstant;
import goldengate.ftp.exec.file.FileBasedAuth;
import goldengate.ftp.exec.utils.Version;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.DefaultCookie;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.traffic.TrafficCounter;

/**
 * @author Frederic Bregier
 *
 */
public class HttpSslHandler extends SimpleChannelUpstreamHandler {
    /**
     * Internal Logger
     */
    private static final GgInternalLogger logger = GgInternalLoggerFactory
            .getLogger(HttpSslHandler.class);
    /**
     * Waiter for SSL handshake is finished
     */
    private static final ConcurrentHashMap<Integer, GgFuture> waitForSsl
        = new ConcurrentHashMap<Integer, GgFuture>();
    /**
     * Session Management
     */
    private static final ConcurrentHashMap<String, FtpSession> sessions
        = new ConcurrentHashMap<String, FtpSession>();
    private static final ConcurrentHashMap<String, DbSession> dbSessions
        = new ConcurrentHashMap<String, DbSession>();
    private volatile FtpSession authentHttp = 
        new FtpSession(FileBasedConfiguration.fileBasedConfiguration,
                null);

    private volatile HttpRequest request;
    private volatile boolean newSession = false;
    private volatile Cookie admin = null;
    private final StringBuilder responseContent = new StringBuilder();
    private volatile String uriRequest;
    private volatile Map<String, List<String>> params;
    private volatile QueryStringDecoder queryStringDecoder;
    private volatile boolean forceClose = false;
    private volatile boolean shutdown = false;

    private static final String FTPSESSION = "FTPSESSION";
    private static enum REQUEST {
        Logon("Logon.html"), 
        index("index.html"),
        error("error.html"),
        Transfers("Transfers.html"), 
        Listing("Listing_head.html","Listing_body0.html","Listing_body.html","Listing_body1.html","Listing_end.html"), 
        CancelRestart("CancelRestart_head.html","CancelRestart_body0.html","CancelRestart_body.html","CancelRestart_body1.html","CancelRestart_end.html"), 
        Export("Export.html"),
        Hosts("Hosts_head.html","Hosts_body0.html","Hosts_body.html","Hosts_body1.html","Hosts_end.html"), 
        Rules("Rules_head.html","Rules_body0.html","Rules_body.html","Rules_body1.html","Rules_end.html"), 
        System("System.html");
        
        private String header;
        private String headerBody;
        private String body;
        private String endBody;
        private String end;
        /**
         * Constructor for a unique file
         * @param uniquefile
         */
        private REQUEST(String uniquefile) {
            this.header = uniquefile;
            this.headerBody = null;
            this.body = null;
            this.endBody = null;
            this.end = null;
        }
        /**
         * @param header
         * @param headerBody
         * @param body
         * @param endBody
         * @param end
         */
        private REQUEST(String header, String headerBody, String body,
                String endBody, String end) {
            this.header = header;
            this.headerBody = headerBody;
            this.body = body;
            this.endBody = endBody;
            this.end = end;
        }
        
        /**
         * Reader for a unique file
         * @return the content of the unique file
         */
        public String readFileUnique(HttpSslHandler handler) {
            return handler.readFileHeader(FileBasedConfiguration.fileBasedConfiguration.httpBasePath+this.header);
        }
        
        public String readHeader(HttpSslHandler handler) {
            return handler.readFileHeader(FileBasedConfiguration.fileBasedConfiguration.httpBasePath+this.header);
        }
        public String readBodyHeader() {
            return GgStringUtils.readFile(FileBasedConfiguration.fileBasedConfiguration.httpBasePath+this.headerBody);
        }
        public String readBody() {
            return GgStringUtils.readFile(FileBasedConfiguration.fileBasedConfiguration.httpBasePath+this.body);
        }
        public String readBodyEnd() {
            return GgStringUtils.readFile(FileBasedConfiguration.fileBasedConfiguration.httpBasePath+this.endBody);
        }
        public String readEnd() {
            return GgStringUtils.readFile(FileBasedConfiguration.fileBasedConfiguration.httpBasePath+this.end);
        }
    }
    
    private static enum REPLACEMENT {
        XXXHOSTIDXXX, XXXADMINXXX, XXXVERSIONXXX, XXXBANDWIDTHXXX,
        XXXXSESSIONLIMITRXXX, XXXXSESSIONLIMITWXXX,
        XXXXCHANNELLIMITRXXX, XXXXCHANNELLIMITWXXX,
        XXXXDELAYCOMMDXXX, XXXXDELAYRETRYXXX,
        XXXLOCALXXX, XXXNETWORKXXX,
        XXXERRORMESGXXX;
    }
    public static final int LIMITROW = 48;// better if it can be divided by 4

    /**
     * The Database connection attached to this NetworkChannel
     * shared among all associated LocalChannels in the session
     */
    private volatile DbSession dbSession = null;
    /**
     * Does this dbSession is private and so should be closed
     */
    private volatile boolean isPrivateDbSession = false;

    /**
     * Remover from SSL HashMap
     */
    private static final ChannelFutureListener remover = new ChannelFutureListener() {
        public void operationComplete(ChannelFuture future) {
            logger.debug("SSL remover");
            waitForSsl.remove(future.getChannel().getId());
        }
    };

    private String readFileHeader(String filename) {
        String value;
        try {
            value = GgStringUtils.readFileException(filename);
        } catch (InvalidArgumentException e) {
            logger.error("Error while trying to open: "+filename,e);
            return "";
        } catch (FileTransferException e) {
            logger.error("Error while trying to read: "+filename,e);
            return "";
        }
        StringBuilder builder = new StringBuilder(value);
        GgStringUtils.replace(builder, REPLACEMENT.XXXLOCALXXX.toString(),
                Integer.toString(
                        FileBasedConfiguration.fileBasedConfiguration.
                        getFtpInternalConfiguration().getNumberSessions())
                        +" "+Thread.activeCount());
        GgStringUtils.replace(builder, REPLACEMENT.XXXNETWORKXXX.toString(),
                Integer.toString(
                        DbAdmin.getNbConnection()));
        GgStringUtils.replace(builder, REPLACEMENT.XXXHOSTIDXXX.toString(),
                FileBasedConfiguration.fileBasedConfiguration.HOST_ID);
        if (((FileBasedAuth)authentHttp.getAuth()).isIdentified()) {
            GgStringUtils.replace(builder, REPLACEMENT.XXXADMINXXX.toString(),
                "Connected");
        } else {
            GgStringUtils.replace(builder, REPLACEMENT.XXXADMINXXX.toString(),
                    "Not authenticated");
        }
        TrafficCounter trafficCounter =
            FileBasedConfiguration.fileBasedConfiguration.getFtpInternalConfiguration()
            .getGlobalTrafficShapingHandler().getTrafficCounter();
        GgStringUtils.replace(builder, REPLACEMENT.XXXBANDWIDTHXXX.toString(),
                "IN:"+(trafficCounter.getLastReadThroughput()/131072)+
                "Mbits&nbsp;<br>&nbsp;OUT:"+
                (trafficCounter.getLastWriteThroughput()/131072)+"Mbits");
        return builder.toString();
    }

    private String getTrimValue(String varname) {
        String value = params.get(varname).get(0).trim();
        if (value.length() == 0) {
            value = null;
        }
        return value;
    }
    private String getValue(String varname) {
        return params.get(varname).get(0);
    }
    /**
     * Add the Channel as SSL handshake is over
     * @param channel
     */
    private static void addSslConnectedChannel(Channel channel) {
        GgFuture futureSSL = new GgFuture(true);
        waitForSsl.put(channel.getId(),futureSSL);
        channel.getCloseFuture().addListener(remover);
    }
    /**
     * Set the future of SSL handshake to status
     * @param channel
     * @param status
     */
    private static void setStatusSslConnectedChannel(Channel channel, boolean status) {
        GgFuture futureSSL = waitForSsl.get(channel.getId());
        if (futureSSL != null) {
            if (status) {
                futureSSL.setSuccess();
            } else {
                futureSSL.cancel();
            }
        }
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.channel.SimpleChannelUpstreamHandler#channelOpen(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ChannelStateEvent)
     */
    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        Channel channel = e.getChannel();
        logger.debug("Add channel to ssl");
        addSslConnectedChannel(channel);
        FileBasedConfiguration.fileBasedConfiguration.getHttpChannelGroup().add(channel);
        super.channelOpen(ctx, e);
    }

    

    private String index() {
        String index = REQUEST.index.readFileUnique(this);
        StringBuilder builder = new StringBuilder(index);
        GgStringUtils.replaceAll(builder, REPLACEMENT.XXXHOSTIDXXX.toString(),
                FileBasedConfiguration.fileBasedConfiguration.HOST_ID);
        GgStringUtils.replaceAll(builder, REPLACEMENT.XXXADMINXXX.toString(),
                "Administrator Connected");
        GgStringUtils.replace(builder, REPLACEMENT.XXXVERSIONXXX.toString(),
                Version.ID);
        return builder.toString();
    }
    private String error(String mesg) {
        String index = REQUEST.error.readFileUnique(this);
        return index.replaceAll(REPLACEMENT.XXXERRORMESGXXX.toString(),
                mesg);
    }
    private String Logon() {
        return REQUEST.Logon.readFileUnique(this);
    }
    private String Transfers() {
        return REQUEST.Transfers.readFileUnique(this);
    }

    private String resetOptionTransfer(String header, String startid, String stopid,
            String start, String stop, String rule, String req,
            boolean pending, boolean transfer, boolean error, boolean done, boolean all) {
        StringBuilder builder = new StringBuilder(header);
        GgStringUtils.replace(builder, "XXXSTARTIDXXX", startid);
        GgStringUtils.replace(builder, "XXXSTOPIDXXX", stopid);
        GgStringUtils.replace(builder, "XXXSTARTXXX", start);
        GgStringUtils.replace(builder, "XXXSTOPXXX", stop);
        GgStringUtils.replace(builder, "XXXRULEXXX", rule);
        GgStringUtils.replace(builder, "XXXREQXXX", req);
        GgStringUtils.replace(builder, "XXXPENDXXX", pending ? "checked":"");
        GgStringUtils.replace(builder, "XXXTRANSXXX", transfer ? "checked":"");
        GgStringUtils.replace(builder, "XXXERRXXX", error ? "checked":"");
        GgStringUtils.replace(builder, "XXXDONEXXX", done ? "checked":"");
        GgStringUtils.replace(builder, "XXXALLXXX", all ? "checked":"");
        return builder.toString();
    }

    private String System() {
        getParams();
        if (params == null) {
            String system = REQUEST.System.readFileUnique(this);
            StringBuilder builder = new StringBuilder(system);
            GgStringUtils.replace(builder, REPLACEMENT.XXXXCHANNELLIMITWXXX.toString(),
                    Long.toString(FileBasedConfiguration.fileBasedConfiguration.getServerGlobalWriteLimit()));
            GgStringUtils.replace(builder, REPLACEMENT.XXXXCHANNELLIMITRXXX.toString(),
                    Long.toString(FileBasedConfiguration.fileBasedConfiguration.getServerGlobalReadLimit()));
            return builder.toString();
        }
        String extraInformation = null;
        if (params.containsKey("ACTION")) {
            List<String> action = params.get("ACTION");
            for (String act : action) {
                if (act.equalsIgnoreCase("Disconnect")) {
                    String logon = Logon();
                    newSession = true;
                    clearSession();
                    forceClose = true;
                    return logon;
                } else if (act.equalsIgnoreCase("Shutdown")) {
                    String error = error("Shutdown in progress");
                    newSession = true;
                    clearSession();
                    forceClose = true;
                    shutdown = true;
                    return error;
                } else if (act.equalsIgnoreCase("Validate")) {
                    String bglobalr = getTrimValue("BGLOBR");
                    long lglobalr = FileBasedConfiguration.fileBasedConfiguration.getServerGlobalReadLimit();
                    if (bglobalr != null) {
                        lglobalr = Long.parseLong(bglobalr);
                    }
                    String bglobalw = getTrimValue("BGLOBW");
                    long lglobalw = FileBasedConfiguration.fileBasedConfiguration.getServerGlobalWriteLimit();
                    if (bglobalw != null) {
                        lglobalw = Long.parseLong(bglobalw);
                    }
                    FileBasedConfiguration.fileBasedConfiguration.changeNetworkLimit(lglobalw, lglobalr);
                    extraInformation = "Configuration Saved";
                }
            }
        }
        String system = REQUEST.System.readFileUnique(this);
        StringBuilder builder = new StringBuilder(system);
        GgStringUtils.replace(builder, REPLACEMENT.XXXXCHANNELLIMITWXXX.toString(),
                Long.toString(FileBasedConfiguration.fileBasedConfiguration.getServerGlobalWriteLimit()));
        GgStringUtils.replace(builder, REPLACEMENT.XXXXCHANNELLIMITRXXX.toString(),
                Long.toString(FileBasedConfiguration.fileBasedConfiguration.getServerGlobalReadLimit()));
        if (extraInformation != null) {
            builder.append(extraInformation);
        }
        return builder.toString();
    }

    private void getParams() {
        if (request.getMethod() == HttpMethod.GET) {
            params = null;
        } else if (request.getMethod() == HttpMethod.POST) {
            ChannelBuffer content = request.getContent();
            if (content.readable()) {
                String param = content.toString(GgStringUtils.UTF8);
                QueryStringDecoder queryStringDecoder2 = new QueryStringDecoder("/?"+param);
                params = queryStringDecoder2.getParameters();
            } else {
                params = null;
            }
        }
    }
    private void clearSession() {
        if (admin != null) {
            FtpSession lsession = sessions.remove(admin.getValue());
            DbSession ldbsession = dbSessions.remove(admin.getValue());
            admin = null;
            if (lsession != null) {
                lsession.clear();
            }
            if (ldbsession != null) {
                ldbsession.disconnect();
            }
        }
    }
    private void checkAuthent(MessageEvent e) {
        newSession = true;
        if (request.getMethod() == HttpMethod.GET) {
            String logon = Logon();
            responseContent.append(logon);
            clearSession();
            writeResponse(e.getChannel());
            return;
        } else if (request.getMethod() == HttpMethod.POST) {
            getParams();
            if (params == null) {
                String logon = Logon();
                responseContent.append(logon);
                clearSession();
                writeResponse(e.getChannel());
                return;
            }
        }
        boolean getMenu = false;
        if (params.containsKey("Logon")) {
            String name = null, password = null;
            List<String> values = null;
            if (!params.isEmpty()) {
                // get values
                if (params.containsKey("name")) {
                    values = params.get("name");
                    if (values != null) {
                        name = values.get(0);
                        if (name == null || name.length() == 0) {
                            getMenu = true;
                        }
                    }
                } else {
                    getMenu = true;
                }
                // search the nb param
                if ((!getMenu) && params.containsKey("passwd")) {
                    values = params.get("passwd");
                    if (values != null) {
                        password = values.get(0);
                        if (password == null || password.length() == 0) {
                            getMenu = true;
                        } else {
                            getMenu = false;
                        }
                    } else {
                        getMenu = true;
                    }
                } else {
                    getMenu = true;
                }
            } else {
                getMenu = true;
            }
            if (! getMenu) {
                logger.info("Name="+name+" vs "+name.equals(FileBasedConfiguration.fileBasedConfiguration.ADMINNAME)+
                        " Passwd="+password+" vs "+
                                FileBasedConfiguration.fileBasedConfiguration.checkPassword(password));
                if (name.equals(FileBasedConfiguration.fileBasedConfiguration.ADMINNAME) &&
                        FileBasedConfiguration.fileBasedConfiguration.checkPassword(password)) {
                    ((FileBasedAuth) authentHttp.getAuth()).specialNoSessionAuth(FileBasedConfiguration.fileBasedConfiguration.HOST_ID);
                } else {
                    getMenu = true;
                }
                if (! ((FileBasedAuth)authentHttp.getAuth()).isIdentified()) {
                    logger.debug("Still not authenticated: {}",authentHttp);
                    getMenu = true;
                }
                // load DbSession
                if (this.dbSession == null) {
                    try {
                        if (DbConstant.admin.isConnected) {
                            this.dbSession = new DbSession(DbConstant.admin, false);
                            this.isPrivateDbSession = true;
                        }
                    } catch (GoldenGateDatabaseNoConnectionError e1) {
                        // Cannot connect so use default connection
                        logger.warn("Use default database connection");
                        this.dbSession = DbConstant.admin.session;
                    }
                }
            }
        } else {
            getMenu = true;
        }
        if (getMenu) {
            String logon = Logon();
            responseContent.append(logon);
            clearSession();
            writeResponse(e.getChannel());
        } else {
            String index = index();
            responseContent.append(index);
            clearSession();
            admin = new DefaultCookie(FTPSESSION, Long.toHexString(new Random().nextLong()));
            sessions.put(admin.getValue(), this.authentHttp);
            if (this.isPrivateDbSession) {
                dbSessions.put(admin.getValue(), dbSession);
            }
            logger.debug("CreateSession: "+uriRequest+":{}",admin);
            writeResponse(e.getChannel());
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
            HttpRequest request = this.request = (HttpRequest) e.getMessage();
            queryStringDecoder = new QueryStringDecoder(request.getUri());
            uriRequest = queryStringDecoder.getPath();
            if (uriRequest.contains("gre/") || uriRequest.contains("img/") ||
                    uriRequest.contains("res/")) {
                writeFile(e.getChannel(), FileBasedConfiguration.fileBasedConfiguration.httpBasePath+uriRequest);
                return;
            }
            checkSession(e.getChannel());
            if (! ((FileBasedAuth)authentHttp.getAuth()).isIdentified()) {
                logger.info("Not Authent: "+uriRequest+":{}",authentHttp);
                checkAuthent(e);
                return;
            }
            String find = uriRequest;
            if (uriRequest.charAt(0) == '/') {
                find = uriRequest.substring(1);
            }
            find = find.substring(0, find.indexOf("."));
            REQUEST req = REQUEST.index;
            try {
                req = REQUEST.valueOf(find);
            } catch (IllegalArgumentException e1) {
                req = REQUEST.index;
                logger.debug("NotFound: "+find+":"+uriRequest);
            }
            switch (req) {
                case index:
                    responseContent.append(index());
                    break;
                case Logon:
                    responseContent.append(index());
                    break;
                case System:
                    responseContent.append(System());
                    break;
                default:
                    responseContent.append(index());
                    break;
            }
            writeResponse(e.getChannel());
    }

    /**
     * Write a File
     * @param e
     */
    private void writeFile(Channel channel, String filename) {
        // Convert the response content to a ChannelBuffer.
        HttpResponse response;
        File file = new File(filename);
        byte [] bytes = new byte[(int) file.length()];
        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.NOT_FOUND);
            channel.write(response);
            return;
        }
        try {
            fileInputStream.read(bytes);
        } catch (IOException e) {
            response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.NOT_FOUND);
            channel.write(response);
            return;
        }
        ChannelBuffer buf = ChannelBuffers.copiedBuffer(bytes);
        // Build the response object.
        response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK);
        response.setContent(buf);
        response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(buf.readableBytes()));
        checkSession(channel);
        handleCookies(response);
        // Write the response.
        channel.write(response);
    }
    private void checkSession(Channel channel) {
        String cookieString = request.getHeader(HttpHeaders.Names.COOKIE);
        if (cookieString != null) {
            CookieDecoder cookieDecoder = new CookieDecoder();
            Set<Cookie> cookies = cookieDecoder.decode(cookieString);
            if(!cookies.isEmpty()) {
                for (Cookie elt : cookies) {
                    if (elt.getName().equalsIgnoreCase(FTPSESSION)) {
                        admin = elt;
                        break;
                    }
                }
            }
        }
        if (admin != null) {
            FtpSession session = sessions.get(admin.getValue());
            if (session != null) {
                authentHttp = session;
            }
            DbSession dbSession = dbSessions.get(admin.getValue());
            if (dbSession != null) {
                this.dbSession = dbSession;
            }
        } else {
            logger.debug("NoSession: "+uriRequest+":{}",admin);
        }
    }
    private void handleCookies(HttpResponse response) {
        String cookieString = request.getHeader(HttpHeaders.Names.COOKIE);
        if (cookieString != null) {
            CookieDecoder cookieDecoder = new CookieDecoder();
            Set<Cookie> cookies = cookieDecoder.decode(cookieString);
            if(!cookies.isEmpty()) {
                // Reset the sessions if necessary.
                int nb = 0;
                CookieEncoder cookieEncoder = new CookieEncoder(true);
                boolean findSession = false;
                for (Cookie cookie : cookies) {
                    if (cookie.getName().equalsIgnoreCase(FTPSESSION)) {
                        if (newSession) {
                            findSession = false;
                        } else {
                            findSession = true;
                            cookieEncoder.addCookie(cookie);
                            nb++;
                        }
                    } else {
                        cookieEncoder.addCookie(cookie);
                        nb++;
                    }
                }
                newSession = false;
                if (! findSession) {
                    if (admin != null) {
                        cookieEncoder.addCookie(admin);
                        nb++;
                        logger.debug("AddSession: "+uriRequest+":{}",admin);
                    }
                }
                if (nb > 0) {
                    response.addHeader(HttpHeaders.Names.SET_COOKIE, cookieEncoder.encode());
                }
            }
        } else if (admin != null) {
            CookieEncoder cookieEncoder = new CookieEncoder(true);
            cookieEncoder.addCookie(admin);
            logger.debug("AddSession: "+uriRequest+":{}",admin);
            response.addHeader(HttpHeaders.Names.SET_COOKIE, cookieEncoder.encode());
        }
    }
    /**
     * Write the response
     * @param e
     */
    private void writeResponse(Channel channel) {
        // Convert the response content to a ChannelBuffer.
        ChannelBuffer buf = ChannelBuffers.copiedBuffer(responseContent.toString(), GgStringUtils.UTF8);
        responseContent.setLength(0);

        // Decide whether to close the connection or not.
        boolean keepAlive = HttpHeaders.isKeepAlive(request);
        boolean close = HttpHeaders.Values.CLOSE.equalsIgnoreCase(request
                .getHeader(HttpHeaders.Names.CONNECTION)) ||
                (!keepAlive) || forceClose;

        // Build the response object.
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.setContent(buf);
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/html");
        if (keepAlive) {
            response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }
        if (!close) {
            // There's no need to add 'Content-Length' header
            // if this is the last response.
            response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(buf.readableBytes()));
        }

        handleCookies(response);

        // Write the response.
        ChannelFuture future = channel.write(response);
        // Close the connection after the write operation is done if necessary.
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
        if (shutdown) {
            Thread thread = 
                new Thread(
                        new FtpChannelUtils(
                                FileBasedConfiguration.fileBasedConfiguration));
            thread.setDaemon(true);
            thread.setName("Shutdown Thread");
            thread.start();
        }
    }
    /**
     * Send an error and close
     * @param ctx
     * @param status
     */
    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        HttpResponse response = new DefaultHttpResponse(
                HttpVersion.HTTP_1_1, status);
        response.setHeader(
                HttpHeaders.Names.CONTENT_TYPE, "text/html");
        responseContent.setLength(0);
        responseContent.append(error(status.toString()));
        response.setContent(ChannelBuffers.copiedBuffer(responseContent.toString(), GgStringUtils.UTF8));
        clearSession();
        // Close the connection as soon as the error message is sent.
        ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
            throws Exception {
        Throwable e1 = e.getCause();
        if (!(e1 instanceof CommandAbstractException)) {
            if (e1 instanceof IOException) {
                // Nothing to do
                return;
            }
            logger.warn("Exception in HttpSslHandler {}", e1.getMessage());
        }
        if (e.getChannel().isConnected()) {
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
        }
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.channel.SimpleChannelUpstreamHandler#channelConnected(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.ChannelStateEvent)
     */
    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        // Get the SslHandler in the current pipeline.
        // We added it in NetworkSslServerPipelineFactory.
        final SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);
        if (sslHandler != null) {
            // Get the SslHandler and begin handshake ASAP.
            // Get notified when SSL handshake is done.
            ChannelFuture handshakeFuture;
            handshakeFuture = sslHandler.handshake();
            if (handshakeFuture != null) {
                handshakeFuture.addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture future)
                            throws Exception {
                        logger.info("Handshake: "+future.isSuccess(),future.getCause());
                        if (future.isSuccess()) {
                            setStatusSslConnectedChannel(future.getChannel(), true);
                        } else {
                            setStatusSslConnectedChannel(future.getChannel(), false);
                            future.getChannel().close();
                        }
                    }
                });
            }
        } else {
            logger.warn("SSL Not found");
        }
        super.channelConnected(ctx, e);
        ChannelGroup group =
            FileBasedConfiguration.fileBasedConfiguration.getHttpChannelGroup();
        if (group != null) {
            group.add(e.getChannel());
        }
    }
}
