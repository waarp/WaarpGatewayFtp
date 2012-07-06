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
package org.waarp.ftp.exec.file;

import org.waarp.common.logging.WaarpInternalLogger;
import org.waarp.common.logging.WaarpInternalLoggerFactory;
import org.waarp.ftp.exec.exec.AbstractExecutor.CommandExecutor;

/**
 * Simple Authentication based on a previously load XML file.
 * 
 * @author Frederic Bregier
 * 
 */
public class SimpleAuth {
	/**
	 * Internal Logger
	 */
	private static final WaarpInternalLogger logger = WaarpInternalLoggerFactory
			.getLogger(SimpleAuth.class);

	/**
	 * User name
	 */
	public String user = null;

	/**
	 * Password
	 */
	public String password = null;

	/**
	 * Multiple accounts
	 */
	public String[] accounts = null;

	/**
	 * Is the current user an administrator (which can shutdown or change bandwidth limitation)
	 */
	public boolean isAdmin = false;
	/**
	 * Specific Store command for this user
	 */
	public String storCmd = null;
	/**
	 * Specific Store command delay for this user
	 */
	public long storDelay = 0;
	/**
	 * Specific Retrieve command for this user
	 */
	public String retrCmd = null;
	/**
	 * Specific Retrieve command delay for this user
	 */
	public long retrDelay = 0;

	public CommandExecutor commandExecutor = null;

	/**
	 * @param user
	 * @param password
	 * @param accounts
	 * @param storCmd
	 * @param storDelay
	 * @param retrCmd
	 * @param retrDelay
	 */
	public SimpleAuth(String user, String password, String[] accounts,
			String storCmd, long storDelay, String retrCmd, long retrDelay) {
		this.user = user;
		this.password = password;
		this.accounts = accounts;
		this.storCmd = storCmd;
		this.storDelay = storDelay;
		this.retrCmd = retrCmd;
		this.retrDelay = retrDelay;
		this.commandExecutor = new CommandExecutor(retrCmd, retrDelay, storCmd, storDelay);
		logger.info("Executor for " + user + " configured as [RETR: " +
				commandExecutor.pretrCMD + ":" + commandExecutor.pretrDelay + ":" +
				commandExecutor.pretrRefused +
				"] [STOR: " + commandExecutor.pstorCMD + ":" +
				commandExecutor.pstorDelay + ":" + commandExecutor.pstorRefused + "]");
	}

	/**
	 * Is the given password a valid one
	 * 
	 * @param newpassword
	 * @return True if the password is valid (or any password is valid)
	 */
	public boolean isPasswordValid(String newpassword) {
		if (password == null) {
			return true;
		}
		if (newpassword == null) {
			return false;
		}
		return password.equals(newpassword);
	}

	/**
	 * Is the given account a valid one
	 * 
	 * @param account
	 * @return True if the account is valid (or any account is valid)
	 */
	public boolean isAccountValid(String account) {
		if (accounts == null) {
			logger.debug("No account needed");
			return true;
		}
		if (account == null) {
			logger.debug("No account given");
			return false;
		}
		for (String acct : accounts) {
			if (acct.equals(account)) {
				logger.debug("Account found");
				return true;
			}
		}
		logger.debug("No account found");
		return false;
	}

	/**
	 * 
	 * @param isAdmin
	 *            True if the user should be an administrator
	 */
	public void setAdmin(boolean isAdmin) {
		this.isAdmin = isAdmin;
	}
}
