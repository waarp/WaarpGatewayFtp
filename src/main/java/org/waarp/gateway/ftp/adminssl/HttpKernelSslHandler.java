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
package org.waarp.gateway.ftp.adminssl;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.DefaultCookie;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import org.jboss.netty.handler.codec.http.multipart.HttpPostRequestDecoder.IncompatibleDataDecoderException;
import org.jboss.netty.handler.traffic.TrafficCounter;
import org.waarp.common.command.ReplyCode;
import org.waarp.common.database.DbAdmin;
import org.waarp.common.database.DbPreparedStatement;
import org.waarp.common.database.DbSession;
import org.waarp.common.database.exception.WaarpDatabaseException;
import org.waarp.common.database.exception.WaarpDatabaseNoConnectionException;
import org.waarp.common.database.exception.WaarpDatabaseSqlException;
import org.waarp.common.logging.WaarpInternalLogger;
import org.waarp.common.logging.WaarpInternalLoggerFactory;
import org.waarp.common.utility.WaarpStringUtils;
import org.waarp.ftp.core.file.FtpDir;
import org.waarp.ftp.core.session.FtpSession;
import org.waarp.ftp.core.utils.FtpChannelUtils;
import org.waarp.gateway.ftp.adminssl.HttpBusinessFactory.FieldsName;
import org.waarp.gateway.ftp.adminssl.HttpBusinessFactory.GatewayRequest;
import org.waarp.gateway.ftp.config.FileBasedConfiguration;
import org.waarp.gateway.ftp.control.FtpConstraintLimitHandler;
import org.waarp.gateway.ftp.database.DbConstant;
import org.waarp.gateway.ftp.database.data.DbTransferLog;
import org.waarp.gateway.ftp.file.FileBasedAuth;
import org.waarp.gateway.ftp.utils.Version;
import org.waarp.gateway.kernel.AbstractHttpField;
import org.waarp.gateway.kernel.HttpBusinessFactory;
import org.waarp.gateway.kernel.HttpIncorrectRequestException;
import org.waarp.gateway.kernel.HttpPageHandler;
import org.waarp.gateway.kernel.exec.AbstractExecutor;
import org.waarp.gateway.kernel.exec.AbstractExecutor.CommandExecutor;
import org.waarp.gateway.kernel.http.HttpRequestHandler;

/**
 * @author Frederic Bregier
 * 
 */
public class HttpKernelSslHandler extends HttpRequestHandler {
	/**
	 * Internal Logger
	 */
	private static final WaarpInternalLogger logger = WaarpInternalLoggerFactory
			.getLogger(HttpKernelSslHandler.class);
	
	
	/**
	 * @param baseStaticPath
	 * @param httpPageHandler
	 */
	public HttpKernelSslHandler(String baseStaticPath, HttpPageHandler httpPageHandler) {
		super(baseStaticPath, FTPSESSION, httpPageHandler);
	}

	/**
	 * Session Management
	 */
	private static final ConcurrentHashMap<String, FileBasedAuth> sessions = new ConcurrentHashMap<String, FileBasedAuth>();
	private static final ConcurrentHashMap<String, DbSession> dbSessions = new ConcurrentHashMap<String, DbSession>();
	private volatile FtpSession ftpSession =
			new FtpSession(FileBasedConfiguration.fileBasedConfiguration,
					null);
	private volatile FileBasedAuth authentHttp =
			new FileBasedAuth(ftpSession);

	private volatile Cookie admin = null;
	private volatile boolean shutdown = false;

	private static final String FTPSESSION = "FTPSESSION";

	public static final int LIMITROW = 48;// better if it can be divided by 4

	/**
	 * The Database connection attached to this NetworkChannel shared among all associated
	 * LocalChannels in the session
	 */
	private volatile DbSession dbSession = null;
	/**
	 * Does this dbSession is private and so should be closed
	 */
	private volatile boolean isPrivateDbSession = false;

	private String index() throws HttpIncorrectRequestException {
		if (! httpPage.pagename.equals(GatewayRequest.index.name())) {
			httpPage = httpPageHandler.getHttpPage("/"+GatewayRequest.index.name()+".html", method.getName(), session);
		}
		String index = httpPage.getPageValue(httpPage.header);
		StringBuilder builder = new StringBuilder(index);
		WaarpStringUtils.replace(builder, "XXXLOCALXXX",
				Integer.toString(
						FileBasedConfiguration.fileBasedConfiguration.
								getFtpInternalConfiguration().getNumberSessions())
						+ " " + Thread.activeCount());
		TrafficCounter trafficCounter =
				FileBasedConfiguration.fileBasedConfiguration.getFtpInternalConfiguration()
						.getGlobalTrafficShapingHandler().getTrafficCounter();
		WaarpStringUtils.replace(builder, "XXXBANDWIDTHXXX",
				"IN:" + (trafficCounter.getLastReadThroughput() / 131072) +
						"Mbits&nbsp;<br>&nbsp;OUT:" +
						(trafficCounter.getLastWriteThroughput() / 131072) + "Mbits");
		WaarpStringUtils.replaceAll(builder, "XXXHOSTIDXXX",
				FileBasedConfiguration.fileBasedConfiguration.HOST_ID);
		WaarpStringUtils.replaceAll(builder, "XXXADMINXXX",
				"Administrator Connected");
		WaarpStringUtils.replace(builder, "XXXVERSIONXXX",
				Version.ID);
		return builder.toString();
	}

	private String error(String mesg) throws HttpIncorrectRequestException {
		if (! httpPage.pagename.equals(GatewayRequest.error.name())) {
			httpPage = httpPageHandler.getHttpPage("/"+GatewayRequest.error.name()+".html", method.getName(), session);
		}
		String index = httpPage.getPageValue(httpPage.header);
		return index.replaceAll("XXXERRORMESGXXX",
				mesg);
	}

	private String Logon() throws HttpIncorrectRequestException {
		if (! httpPage.pagename.equals(GatewayRequest.Logon.name())) {
			httpPage = httpPageHandler.getHttpPage("/"+GatewayRequest.Logon.name()+".html", method.getName(), session);
		}
		return httpPage.getPageValue(httpPage.header);
	}

	private String System() throws HttpIncorrectRequestException {
		FtpConstraintLimitHandler handler =
				FileBasedConfiguration.fileBasedConfiguration.constraintLimitHandler;
		AbstractHttpField field = httpPage.getField(businessRequest, FieldsName.ACTION.name());
		if (! field.present) {
			String system = httpPage.getPageValue(httpPage.header);
			StringBuilder builder = new StringBuilder(system);
			WaarpStringUtils.replace(builder, "XXXXCHANNELLIMITRXXX",
					Long.toString(FileBasedConfiguration.fileBasedConfiguration
							.getServerGlobalReadLimit()));
			WaarpStringUtils.replace(builder, "XXXXCPULXXX",
					Double.toString(handler.getCpuLimit()));
			WaarpStringUtils.replace(builder, "XXXXCONLXXX",
					Integer.toString(handler.getChannelLimit()));
			WaarpStringUtils.replace(builder, "XXXRESULTXXX", "");
			return builder.toString();
		}
		String extraInformation = null;
		if (field.fieldvalue.equalsIgnoreCase("Disconnect")) {
			String logon = Logon();
			clearSession();
			willClose = true;
			return logon;
		} else if (field.fieldvalue.equalsIgnoreCase("Shutdown")) {
			String error = error("Shutdown in progress");
			clearSession();
			willClose = true;
			shutdown = true;
			return error;
		} else if (field.fieldvalue.equalsIgnoreCase("Validate")) {
			String bglobalr = httpPage.getValue(businessRequest, FieldsName.BGLOBR.name()).trim();
			long lglobal = FileBasedConfiguration.fileBasedConfiguration
					.getServerGlobalReadLimit();
			if (bglobalr != null && bglobalr.length() > 0) {
				lglobal = Long.parseLong(bglobalr);
			}
			FileBasedConfiguration.fileBasedConfiguration.changeNetworkLimit(lglobal,
					lglobal);
			bglobalr = httpPage.getValue(businessRequest, FieldsName.CPUL.name()).trim();
			double dcpu = handler.getCpuLimit();
			if (bglobalr != null && bglobalr.length() > 0) {
				dcpu = Double.parseDouble(bglobalr);
			}
			handler.setCpuLimit(dcpu);
			bglobalr = httpPage.getValue(businessRequest, FieldsName.CONL.name()).trim();
			int iconn = handler.getChannelLimit();
			if (bglobalr != null && bglobalr.length() > 0) {
				iconn = Integer.parseInt(bglobalr);
			}
			handler.setChannelLimit(iconn);
			extraInformation = "Configuration Saved";
		}
		String system = httpPage.getPageValue(httpPage.header);
		StringBuilder builder = new StringBuilder(system);
		WaarpStringUtils.replace(builder, "XXXXCHANNELLIMITRXXX",
				Long.toString(FileBasedConfiguration.fileBasedConfiguration
						.getServerGlobalReadLimit()));
		WaarpStringUtils.replace(builder, "XXXXCPULXXX",
				Double.toString(handler.getCpuLimit()));
		WaarpStringUtils.replace(builder, "XXXXCONLXXX",
				Integer.toString(handler.getChannelLimit()));
		if (extraInformation != null) {
			WaarpStringUtils.replace(builder, "XXXRESULTXXX", extraInformation);
		} else {
			WaarpStringUtils.replace(builder, "XXXRESULTXXX", "");
		}
		return builder.toString();
	}

	private String Rule() {
		AbstractHttpField field = httpPage.getField(businessRequest, FieldsName.ACTION.name());
		if (! field.present) {
			String system = httpPage.getPageValue(httpPage.header);
			StringBuilder builder = new StringBuilder(system);
			CommandExecutor exec = AbstractExecutor.getCommandExecutor();
			WaarpStringUtils.replace(builder, "XXXSTCXXX",
					exec.getStorType() + " " + exec.pstorCMD);
			WaarpStringUtils.replace(builder, "XXXSTDXXX",
					Long.toString(exec.pstorDelay));
			WaarpStringUtils.replace(builder, "XXXRTCXXX",
					exec.getRetrType() + " " + exec.pretrCMD);
			WaarpStringUtils.replace(builder, "XXXRTDXXX",
					Long.toString(exec.pretrDelay));
			WaarpStringUtils.replace(builder, "XXXRESULTXXX", "");
			return builder.toString();
		}
		String extraInformation = null;
		if (field.fieldvalue.equalsIgnoreCase("Update")) {
			CommandExecutor exec = AbstractExecutor.getCommandExecutor();
			String bglobalr = httpPage.getValue(businessRequest, FieldsName.std.name()).trim();
			long lglobal = exec.pstorDelay;
			if (bglobalr != null && bglobalr.length() > 0) {
				lglobal = Long.parseLong(bglobalr);
			}
			exec.pstorDelay = lglobal;
			bglobalr = httpPage.getValue(businessRequest, FieldsName.rtd.name()).trim();
			lglobal = exec.pretrDelay;
			if (bglobalr != null && bglobalr.length() > 0) {
				lglobal = Long.parseLong(bglobalr);
			}
			exec.pretrDelay = lglobal;
			bglobalr = httpPage.getValue(businessRequest, FieldsName.stc.name()).trim();
			String store = exec.getStorType() + " " + exec.pstorCMD;
			if (bglobalr != null && bglobalr.length() > 0) {
				store = bglobalr;
			}
			bglobalr = httpPage.getValue(businessRequest, FieldsName.rtc.name()).trim();
			String retr = exec.getRetrType() + " " + exec.pretrCMD;
			if (bglobalr != null && bglobalr.length() > 0) {
				retr = bglobalr;
			}
			AbstractExecutor.initializeExecutor(retr, exec.pretrDelay,
					store, exec.pstorDelay);
			extraInformation = "Configuration Saved";
		}
		String system = httpPage.getPageValue(httpPage.header);
		StringBuilder builder = new StringBuilder(system);
		CommandExecutor exec = AbstractExecutor.getCommandExecutor();
		WaarpStringUtils.replace(builder, "XXXSTCXXX",
				exec.getStorType() + " " + exec.pstorCMD);
		WaarpStringUtils.replace(builder, "XXXSTDXXX",
				Long.toString(exec.pstorDelay));
		WaarpStringUtils.replace(builder, "XXXRTCXXX",
				exec.getRetrType() + " " + exec.pretrCMD);
		WaarpStringUtils.replace(builder, "XXXRTDXXX",
				Long.toString(exec.pretrDelay));
		if (extraInformation != null) {
			WaarpStringUtils.replace(builder, "XXXRESULTXXX", extraInformation);
		} else {
			WaarpStringUtils.replace(builder, "XXXRESULTXXX", "");
		}
		return builder.toString();
	}

	private String Transfer() {
		String head = httpPage.getPageValue(httpPage.header);
		String end = httpPage.getPageValue(httpPage.footer);
		String body = httpPage.getPageValue(httpPage.beginform);
		AbstractHttpField field = httpPage.getField(businessRequest, FieldsName.ACTION.name());
		if (! field.present || (!DbConstant.admin.isConnected)) {
			end = end.replace("XXXRESULTXXX", "");
			body = FileBasedConfiguration.fileBasedConfiguration.getHtmlTransfer(body, LIMITROW);
			return head + body + end;
		}
		String message = "";
		boolean purgeAll = false;
		boolean purgeCorrect = false;
		boolean delete = false;
		if ("PurgeCorrectTransferLogs".equalsIgnoreCase(field.fieldvalue)) {
			purgeCorrect = true;
		} else if ("PurgeAllTransferLogs".equalsIgnoreCase(field.fieldvalue)) {
			purgeAll = true;
		} else if ("Delete".equalsIgnoreCase(field.fieldvalue)) {
			delete = true;
		}
		if (purgeCorrect) {
			DbPreparedStatement preparedStatement = null;
			try {
				preparedStatement =
						DbTransferLog.getStatusPrepareStament(dbSession,
								ReplyCode.REPLY_250_REQUESTED_FILE_ACTION_OKAY, 0);
			} catch (WaarpDatabaseNoConnectionException e) {
				message = "Error during purge";
			} catch (WaarpDatabaseSqlException e) {
				message = "Error during purge";
			}
			if (preparedStatement != null) {
				try {
					FileBasedConfiguration config = FileBasedConfiguration.fileBasedConfiguration;
					String filename =
							config.getBaseDirectory() +
									FtpDir.SEPARATOR + config.ADMINNAME + FtpDir.SEPARATOR +
									config.HOST_ID + "_logs_" + System.currentTimeMillis()
									+ ".xml";
					message = DbTransferLog.saveDbTransferLogFile(preparedStatement, filename);
				} finally {
					preparedStatement.realClose();
				}
			}
		} else if (purgeAll) {
			DbPreparedStatement preparedStatement = null;
			try {
				preparedStatement =
						DbTransferLog.getStatusPrepareStament(dbSession,
								null, 0);
			} catch (WaarpDatabaseNoConnectionException e) {
				message = "Error during purgeAll";
			} catch (WaarpDatabaseSqlException e) {
				message = "Error during purgeAll";
			}
			if (preparedStatement != null) {
				try {
					FileBasedConfiguration config = FileBasedConfiguration.fileBasedConfiguration;
					String filename =
							config.getBaseDirectory() +
									FtpDir.SEPARATOR + config.ADMINNAME + FtpDir.SEPARATOR +
									config.HOST_ID + "_logs_" + System.currentTimeMillis()
									+ ".xml";
					message = DbTransferLog.saveDbTransferLogFile(preparedStatement, filename);
				} finally {
					preparedStatement.realClose();
				}
			}
		} else if (delete) {
			String user = httpPage.getValue(businessRequest, FieldsName.user.name()).trim();
			String acct = httpPage.getValue(businessRequest, FieldsName.account.name()).trim();
			String specid = httpPage.getValue(businessRequest, FieldsName.specialid.name()).trim();
			long specialId = Long.parseLong(specid);
			try {
				DbTransferLog log = new DbTransferLog(dbSession, user, acct, specialId);
				FileBasedConfiguration config = FileBasedConfiguration.fileBasedConfiguration;
				String filename =
						config.getBaseDirectory() +
								FtpDir.SEPARATOR + config.ADMINNAME + FtpDir.SEPARATOR +
								config.HOST_ID + "_log_" + System.currentTimeMillis() + ".xml";
				message = log.saveDbTransferLog(filename);
			} catch (WaarpDatabaseException e) {
				message = "Error during delete 1 Log";
			}
		} else {
			message = "No Action";
		}
		end = end.replace("XXXRESULTXXX", message);
		body = FileBasedConfiguration.fileBasedConfiguration.getHtmlTransfer(body, LIMITROW);
		return head + body + end;
	}

	private String User() {
		String head = httpPage.getPageValue(httpPage.header);
		String end = httpPage.getPageValue(httpPage.footer);
		String body = httpPage.getPageValue(httpPage.beginform);
		FileBasedConfiguration config = FileBasedConfiguration.fileBasedConfiguration;
		String filedefault = config.getBaseDirectory() +
				FtpDir.SEPARATOR + config.ADMINNAME +
				FtpDir.SEPARATOR + "authentication.xml";
		AbstractHttpField field = httpPage.getField(businessRequest, FieldsName.ACTION.name());
		if (! field.present) {
			end = end.replace("XXXRESULTXXX", "");
			end = end.replace("XXXFILEXXX", filedefault);
			body = FileBasedConfiguration.fileBasedConfiguration.getHtmlAuth(body);
			return head + body + end;
		}
		if ("ImportExport".equalsIgnoreCase(field.fieldvalue)) {
			String file = httpPage.getValue(businessRequest, FieldsName.file.name()).trim();
			String exportImport = httpPage.getValue(businessRequest, FieldsName.export.name()).trim();
			String message = "";
			boolean purge = httpPage.getField(businessRequest, FieldsName.purge.name()).present;
			boolean replace = httpPage.getField(businessRequest, FieldsName.replace.name()).present;
			if (file == null || file.length() == 0) {
				file = filedefault;
			}
			end = end.replace("XXXFILEXXX", file);
			if (exportImport.equalsIgnoreCase("import")) {
				if (!config.initializeAuthent(file, purge)) {
					message += "Cannot initialize Authentication from " + file;
				} else {
					message += "Initialization of Authentication OK from " + file;
					if (replace) {
						if (!config.saveAuthenticationFile(
								config.authenticationFile)) {
							message += " but cannot replace server authenticationFile";
						} else {
							message += " and replacement done";
						}
					}
				}
			} else {
				// export
				if (!config.saveAuthenticationFile(file)) {
					message += "Authentications CANNOT be saved into " + file;
				} else {
					message += "Authentications saved into " + file;
				}
			}
			end = end.replace("XXXRESULTXXX", message);
		} else {
			end = end.replace("XXXFILEXXX", filedefault);
		}
		end = end.replace("XXXRESULTXXX", "");
		body = FileBasedConfiguration.fileBasedConfiguration.getHtmlAuth(body);
		return head + body + end;
	}

	private void clearSession() {
		if (admin != null) {
			FileBasedAuth auth = sessions.remove(admin.getValue());
			DbSession ldbsession = dbSessions.remove(admin.getValue());
			admin = null;
			if (auth != null) {
				auth.clear();
			}
			if (ldbsession != null) {
				ldbsession.disconnect();
				DbAdmin.nbHttpSession--;
			}
		}
	}

	private void checkAuthent() throws HttpIncorrectRequestException {
		if (request.getMethod() == HttpMethod.GET) {
			httpPage = httpPageHandler.getHttpPage("/"+GatewayRequest.Logon.name()+".html", method.getName(), session);
			return;
		}
		boolean getMenu = false;
		if (httpPage.pagename.equals(GatewayRequest.index.name())) {
			String name = null, password = null;
			AbstractHttpField field = httpPage.getField(businessRequest, FieldsName.name.name());
			if (field.present) {
				name = field.fieldvalue;
			}
			if (name == null || name.length() == 0) {
				getMenu = true;
			}
			if (!getMenu) {
				field = httpPage.getField(businessRequest, FieldsName.passwd.name());
				if (field.present) {
					password = field.fieldvalue;
				}
				if (password == null || password.length() == 0) {
					getMenu = true;
				}
			}
			if (!getMenu) {
				logger.debug("Name=" + name + " vs "
						+ name.equals(FileBasedConfiguration.fileBasedConfiguration.ADMINNAME) +
						" Passwd=" + password + " vs " +
						FileBasedConfiguration.fileBasedConfiguration.checkPassword(password));
				if (name.equals(FileBasedConfiguration.fileBasedConfiguration.ADMINNAME) &&
						FileBasedConfiguration.fileBasedConfiguration.checkPassword(password)) {
					authentHttp
							.specialNoSessionAuth(FileBasedConfiguration.fileBasedConfiguration.HOST_ID);
				} else {
					getMenu = true;
				}
				if (!authentHttp.isIdentified()) {
					logger.debug("Still not authenticated: {}", authentHttp);
					getMenu = true;
				}
				// load DbSession
				if (this.dbSession == null) {
					try {
						if (DbConstant.admin.isConnected) {
							this.dbSession = new DbSession(DbConstant.admin, false);
							DbAdmin.nbHttpSession++;
							this.isPrivateDbSession = true;
						}
					} catch (WaarpDatabaseNoConnectionException e1) {
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
			httpPage = httpPageHandler.getHttpPage("/"+GatewayRequest.Logon.name()+".html", method.getName(), session);
			return;
		} else {
			httpPage = httpPageHandler.getHttpPage("/"+GatewayRequest.index.name()+".html", method.getName(), session);
			clearSession();
			admin = new DefaultCookie(FTPSESSION,
					FileBasedConfiguration.fileBasedConfiguration.HOST_ID +
							Long.toHexString(new Random().nextLong()));
			sessions.put(admin.getValue(), this.authentHttp);
			if (this.isPrivateDbSession) {
				dbSessions.put(admin.getValue(), dbSession);
			}
			logger.debug("CreateSession: {}", admin);
			((HttpBusinessHandler) this.businessRequest).futurePage = index();
		}
	}

	@Override
	public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
			throws Exception {
		Channel channel = e.getChannel();
		logger.debug("Add channel to ssl");
		FileBasedConfiguration.fileBasedConfiguration.getHttpChannelGroup().add(channel);
		super.channelOpen(ctx, e);
	}

	@Override
	protected void checkConnection(Channel channel) throws HttpIncorrectRequestException {
		if (admin != null) {
			FileBasedAuth auth = sessions.get(admin.getValue());
			if (auth != null) {
				authentHttp = auth;
			}
			DbSession dbSession = dbSessions.get(admin.getValue());
			if (dbSession != null) {
				this.dbSession = dbSession;
			}
			logger.debug("Session: " + request.getUri() + ":{}", admin);
		} else {
			logger.debug("NoSession: " + request.getUri());
		}
	}

	@Override
	protected void error(Channel channel) {
		// TODO Auto-generated method stub
		clearSession();
	}

	@Override
	protected boolean isCookieValid(Cookie cookie) {
		if (cookie.getName().equalsIgnoreCase(FTPSESSION)) {
			admin = cookie;
		}
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	protected String getFilename() {
		// TODO Auto-generated method stub
		return null;
	}

	protected void tryPostDecode(Channel channel) throws HttpIncorrectRequestException {
		try {
            decoder = new HttpPostRequestDecoder(HttpBusinessFactory.factory, request);
        } catch (ErrorDataDecoderException e1) {
            status = HttpResponseStatus.NOT_ACCEPTABLE;
            throw new HttpIncorrectRequestException(e1);
        } catch (IncompatibleDataDecoderException e1) {
            // GETDOWNLOAD Method: should not try to create a HttpPostRequestDecoder
            // So OK but stop here
            return;
        }

        if (request.isChunked()) {
            readHttpDataAllReceive(channel);
            // Chunk version but incompatible with current handler
            //throw new HttpIncorrectRequestException("Cannot handle multiple chunks");
        } else {
            // Not chunk version
            readHttpDataAllReceive(channel);
        }
	}
	
	@Override
	protected void beforeSimplePage(Channel channel) throws HttpIncorrectRequestException {
		// first try to decode post if any
		if (method == HttpMethod.POST) {
			tryPostDecode(channel);
		}
		if (!authentHttp.isIdentified()) {
			logger.debug("Not Authent: " + request.getUri() + ":{}", authentHttp);
			checkAuthent();
			return;
		} else {
			logger.debug("Authent: " + request.getUri()+":"+httpPage.pagename + ":{}", authentHttp);
		}
		// now continue
		if (httpPage.pagename.equals(GatewayRequest.Logon.name())) {
			((HttpBusinessHandler) this.businessRequest).futurePage = index();
		} else if (httpPage.pagename.equals(GatewayRequest.index.name())) {
			((HttpBusinessHandler) this.businessRequest).futurePage = index();
		} else if (httpPage.pagename.equals(GatewayRequest.error.name())) {
			((HttpBusinessHandler) this.businessRequest).futurePage = error(this.errorMesg);
		} else if (httpPage.pagename.equals(GatewayRequest.Transfer.name())) {
			((HttpBusinessHandler) this.businessRequest).futurePage = Transfer();
		} else if (httpPage.pagename.equals(GatewayRequest.Rule.name())) {
			((HttpBusinessHandler) this.businessRequest).futurePage = Rule();
		} else if (httpPage.pagename.equals(GatewayRequest.User.name())) {
			((HttpBusinessHandler) this.businessRequest).futurePage = User();
		} else if (httpPage.pagename.equals(GatewayRequest.System.name())) {
			((HttpBusinessHandler) this.businessRequest).futurePage = System();
		} else {
			((HttpBusinessHandler) this.businessRequest).futurePage = index();
		}
		if (shutdown) {
			/*
			 * Thread thread = new Thread( new FtpChannelUtils(
			 * FileBasedConfiguration.fileBasedConfiguration)); thread.setDaemon(true);
			 * thread.setName("Shutdown Thread"); thread.start();
			 */
			FtpChannelUtils.teminateServer(FileBasedConfiguration.fileBasedConfiguration);
		}
	}

	@Override
	protected void finalDelete(Channel channel) throws HttpIncorrectRequestException {
		// TODO Auto-generated method stub
	}

	@Override
	protected void finalGet(Channel channel) throws HttpIncorrectRequestException {
		// TODO Auto-generated method stub
	}

	@Override
	protected void finalPostUpload(Channel channel) throws HttpIncorrectRequestException {
		// TODO Auto-generated method stub
	}

	@Override
	protected void finalPost(Channel channel) throws HttpIncorrectRequestException {
		// TODO Auto-generated method stub
	}

	@Override
	protected void finalPut(Channel channel) throws HttpIncorrectRequestException {
		// TODO Auto-generated method stub
	}

	@Override
	public void businessValidRequestAfterAllDataReceived(Channel channel)
			throws HttpIncorrectRequestException {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	protected String getNewCookieSession() {
    	return FileBasedConfiguration.fileBasedConfiguration.HOST_ID +
				Long.toHexString(new Random().nextLong());
    }
}
