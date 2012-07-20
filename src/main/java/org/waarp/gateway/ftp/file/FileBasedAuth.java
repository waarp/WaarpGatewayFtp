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
package org.waarp.gateway.ftp.file;

import java.io.File;

import org.waarp.common.command.NextCommandReply;
import org.waarp.common.command.ReplyCode;
import org.waarp.common.command.exception.Reply421Exception;
import org.waarp.common.command.exception.Reply530Exception;
import org.waarp.common.logging.WaarpInternalLogger;
import org.waarp.common.logging.WaarpInternalLoggerFactory;
import org.waarp.ftp.core.command.FtpCommandCode;
import org.waarp.ftp.core.file.FtpDir;
import org.waarp.ftp.core.session.FtpSession;
import org.waarp.ftp.filesystembased.FilesystemBasedFtpAuth;
import org.waarp.ftp.filesystembased.FilesystemBasedFtpRestart;
import org.waarp.gateway.ftp.config.FileBasedConfiguration;
import org.waarp.gateway.ftp.database.DbConstant;
import org.waarp.gateway.ftp.exec.AbstractExecutor.CommandExecutor;

/**
 * FtpAuth implementation based on a list of (user/password/account) stored in a xml file load at
 * startup from configuration.
 * 
 * @author Frederic Bregier
 * 
 */
public class FileBasedAuth extends FilesystemBasedFtpAuth {
	/**
	 * Internal Logger
	 */
	private static final WaarpInternalLogger logger = WaarpInternalLoggerFactory
			.getLogger(FileBasedAuth.class);

	/**
	 * Current authentication
	 */
	private SimpleAuth currentAuth = null;

	/**
	 * Special Id for the current transfer
	 */
	private long specialId = DbConstant.ILLEGALVALUE;

	/**
	 * @param session
	 */
	public FileBasedAuth(FtpSession session) {
		super(session);
	}

	@Override
	protected void businessClean() {
		currentAuth = null;
	}

	/**
	 * @param user
	 *            the user to set
	 * @return (NOOP,230) if the user is OK, else return the following command that must follow
	 *         (usually PASS) and the associated reply
	 * @throws Reply421Exception
	 *             if there is a problem during the authentication
	 * @throws Reply530Exception
	 *             if there is a problem during the authentication
	 */
	@Override
	protected NextCommandReply setBusinessUser(String user)
			throws Reply421Exception, Reply530Exception {
		SimpleAuth auth = ((FileBasedConfiguration) ((FtpSession) getSession())
				.getConfiguration()).getSimpleAuth(user);
		if (auth == null) {
			setIsIdentified(false);
			currentAuth = null;
			throw new Reply530Exception("User name not allowed");
		}
		currentAuth = auth;
		// logger.debug("User: {}", user);
		return new NextCommandReply(FtpCommandCode.PASS,
				ReplyCode.REPLY_331_USER_NAME_OKAY_NEED_PASSWORD, null);
	}

	/**
	 * Set the password according to any implementation and could set the rootFromAuth. If NOOP is
	 * returned, isIdentifed must be TRUE. A special case is implemented for test user.
	 * 
	 * @param password
	 * @return (NOOP,230) if the Password is OK, else return the following command that must follow
	 *         (usually ACCT) and the associated reply
	 * @throws Reply421Exception
	 *             if there is a problem during the authentication
	 * @throws Reply530Exception
	 *             if there is a problem during the authentication
	 */
	@Override
	protected NextCommandReply setBusinessPassword(String password)
			throws Reply421Exception, Reply530Exception {
		if (currentAuth == null) {
			setIsIdentified(false);
			throw new Reply530Exception("PASS needs a USER first");
		}
		if (currentAuth.isPasswordValid(password)) {
			return new NextCommandReply(FtpCommandCode.ACCT,
					ReplyCode.REPLY_332_NEED_ACCOUNT_FOR_LOGIN, null);
		}
		throw new Reply530Exception("Password is not valid");
	}

	/**
	 * Set the account according to any implementation and could set the rootFromAuth. If NOOP is
	 * returned, isIdentifed must be TRUE.
	 * 
	 * @param account
	 * @return (NOOP,230) if the Account is OK, else return the following command that must follow
	 *         and the associated reply
	 * @throws Reply421Exception
	 *             if there is a problem during the authentication
	 * @throws Reply530Exception
	 *             if there is a problem during the authentication
	 */
	@Override
	protected NextCommandReply setBusinessAccount(String account)
			throws Reply421Exception, Reply530Exception {
		if (currentAuth == null) {
			throw new Reply530Exception("ACCT needs a USER first");
		}
		if (currentAuth.isAccountValid(account)) {
			// logger.debug("Account: {}", account);
			setIsIdentified(true);
			logger.info("User {} is authentified with account {}", user,
					account);
			return new NextCommandReply(FtpCommandCode.NOOP,
					ReplyCode.REPLY_230_USER_LOGGED_IN, null);
		}
		throw new Reply530Exception("Account is not valid");
	}

	public boolean isBusinessPathValid(String newPath) {
		if (newPath == null) {
			return false;
		}
		return newPath.startsWith(getBusinessPath());
	}

	@Override
	protected String setBusinessRootFromAuth() throws Reply421Exception {
		String path = null;
		if (account == null) {
			path = FtpDir.SEPARATOR + user;
		} else {
			path = FtpDir.SEPARATOR + user + FtpDir.SEPARATOR +
					account;
		}
		String fullpath = getAbsolutePath(path);
		File file = new File(fullpath);
		if (!file.isDirectory()) {
			throw new Reply421Exception("Filesystem not ready");
		}
		return path;
	}

	public boolean isAdmin() {
		if (currentAuth == null)
			return false;
		return currentAuth.isAdmin;
	}

	/**
	 * Special Authentication for local execution
	 * 
	 * @param hostid
	 */
	public void specialNoSessionAuth(String hostid) {
		this.isIdentified = true;
		SimpleAuth auth = new SimpleAuth(hostid, hostid, null, null, 0, null, 0);
		currentAuth = auth;
		setIsIdentified(true);
		user = auth.user;
		account = auth.user;
		((FtpSession) getSession()).setSpecialInit(this,
				new FileBasedDir(((FtpSession) getSession())),
				new FilesystemBasedFtpRestart(((FtpSession) getSession())));
		try {
			setBusinessRootFromAuth();
		} catch (Reply421Exception e) {
		}
		getSession().getDir().initAfterIdentification();
		currentAuth.setAdmin(true);
	}

	/**
	 * @return the specialId
	 */
	public long getSpecialId() {
		return specialId;
	}

	/**
	 * @param specialId
	 *            the specialId to set
	 */
	public void setSpecialId(long specialId) {
		this.specialId = specialId;
	}

	/**
	 * 
	 * @return the associated Command Executor
	 */
	public CommandExecutor getCommandExecutor() {
		return this.currentAuth.commandExecutor;
	}
}
