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
package org.waarp.gateway.ftp.exec;

import org.waarp.common.command.exception.CommandAbstractException;
import org.waarp.common.future.WaarpFuture;
import org.waarp.common.logging.WaarpInternalLogger;
import org.waarp.common.logging.WaarpInternalLoggerFactory;
import org.waarp.gateway.ftp.file.FileBasedAuth;

/**
 * Abstract Executor class. If the command starts with "REFUSED", the command will be refused for
 * execution. If "REFUSED" is set, the command "RETR" or "STOR" like operations will be stopped at
 * starting of command.<br>
 * If the command starts with "EXECUTE", the following will be a command to be executed.<br>
 * If the command starts with "R66PREPARETRANSFER", the following will be a r66 prepare transfer
 * execution (asynchrone only).<br>
 * 
 * 
 * The following replacement are done dynamically before the command is executed:<br>
 * - #BASEPATH# is replaced by the full path for the root of FTP Directory<br>
 * - #FILE# is replaced by the current file path relative to FTP Directory (so #BASEPATH##FILE# is
 * the full path of the file)<br>
 * - #USER# is replaced by the username<br>
 * - #ACCOUNT# is replaced by the account<br>
 * - #COMMAND# is replaced by the command issued for the file<br>
 * 
 * @author Frederic Bregier
 * 
 */
public abstract class AbstractExecutor {
	/**
	 * Internal Logger
	 */
	private static final WaarpInternalLogger logger = WaarpInternalLoggerFactory
			.getLogger(AbstractExecutor.class);
	protected static final String USER = "#USER#";
	protected static final String ACCOUNT = "#ACCOUNT#";
	protected static final String BASEPATH = "#BASEPATH#";
	protected static final String FILE = "#FILE#";
	protected static final String COMMAND = "#COMMAND#";

	protected static final String REFUSED = "REFUSED";
	protected static final String NONE = "NONE";
	protected static final String EXECUTE = "EXECUTE";
	protected static final String R66PREPARETRANSFER = "R66PREPARETRANSFER";

	protected static final int tREFUSED = -1;
	protected static final int tNONE = 0;
	protected static final int tEXECUTE = 1;
	protected static final int tR66PREPARETRANSFER = 2;

	protected static CommandExecutor commandExecutor = null;

	/**
	 * For OpenR66 access
	 */
	public static boolean useDatabase = false;

	/**
	 * Local Exec Daemon is used or not for execution of external commands
	 */
	public static boolean useLocalExec = false;

	public static class CommandExecutor {
		/**
		 * Retrieve External Command
		 */
		public String pretrCMD;
		public int pretrType;
		public boolean pretrRefused;
		/**
		 * Retrieve Delay (0 = unlimited)
		 */
		public long pretrDelay;
		/**
		 * Store External Command
		 */
		public String pstorCMD;
		public int pstorType;
		public boolean pstorRefused;
		/**
		 * Store Delay (0 = unlimited)
		 */
		public long pstorDelay;

		/**
		 * 
		 * @param retrieve
		 * @param retrDelay
		 * @param store
		 * @param storDelay
		 */
		public CommandExecutor(String retrieve, long retrDelay,
				String store, long storDelay) {
			if (retrieve == null || retrieve.trim().length() == 0) {
				pretrCMD = NONE;
				pretrType = tNONE;
				pretrRefused = false;
			} else if (isRefused(retrieve)) {
				pretrCMD = REFUSED;
				pretrType = tREFUSED;
				pretrRefused = true;
			} else {
				if (isExecute(retrieve)) {
					pretrCMD = getExecuteCmd(retrieve);
					pretrType = tEXECUTE;
				} else if (isR66PrepareTransfer(retrieve)) {
					pretrCMD = getR66PrepareTransferCmd(retrieve);
					pretrType = tR66PREPARETRANSFER;
					useDatabase = true;
				} else {
					// Default NONE
					pretrCMD = getNone(retrieve);
					pretrType = tNONE;
				}
			}
			pretrDelay = retrDelay;
			if (store == null || store.trim().length() == 0) {
				pstorCMD = NONE;
				pstorRefused = false;
				pstorType = tNONE;
			} else if (isRefused(store)) {
				pstorCMD = REFUSED;
				pstorRefused = true;
				pstorType = tREFUSED;
			} else {
				if (isExecute(store)) {
					pstorCMD = getExecuteCmd(store);
					pstorType = tEXECUTE;
				} else if (isR66PrepareTransfer(store)) {
					pstorCMD = getR66PrepareTransferCmd(store);
					pstorType = tR66PREPARETRANSFER;
					useDatabase = true;
				} else {
					// Default NONE
					pstorCMD = getNone(store);
					pstorType = tNONE;
				}
			}
			pstorDelay = storDelay;
		}

		/**
		 * Check if the given operation is allowed
		 * 
		 * @param isStore
		 * @return True if allowed, else False
		 */
		public boolean isValidOperation(boolean isStore) {
			if (isStore && pstorRefused) {
				logger.info("STORe like operations REFUSED");
				return false;
			} else if ((!isStore) && pretrRefused) {
				logger.info("RETRieve operations REFUSED");
				return false;
			}
			return true;
		}

		public String getRetrType() {
			switch (pretrType) {
				case tREFUSED:
					return REFUSED;
				case tNONE:
					return NONE;
				case tEXECUTE:
					return EXECUTE;
				case tR66PREPARETRANSFER:
					return R66PREPARETRANSFER;
				default:
					return NONE;
			}
		}

		public String getStorType() {
			switch (pstorType) {
				case tREFUSED:
					return REFUSED;
				case tNONE:
					return NONE;
				case tEXECUTE:
					return EXECUTE;
				case tR66PREPARETRANSFER:
					return R66PREPARETRANSFER;
				default:
					return NONE;
			}
		}
	}

	private static String getNone(String cmd) {
		return cmd.substring(NONE.length()).trim();
	}

	private static String getExecuteCmd(String cmd) {
		return cmd.substring(EXECUTE.length()).trim();
	}

	private static String getR66PrepareTransferCmd(String cmd) {
		return cmd.substring(R66PREPARETRANSFER.length()).trim();
	}

	private static boolean isRefused(String cmd) {
		return cmd.startsWith(REFUSED);
	}

	private static boolean isExecute(String cmd) {
		return cmd.startsWith(EXECUTE);
	}

	private static boolean isR66PrepareTransfer(String cmd) {
		return cmd.startsWith(R66PREPARETRANSFER);
	}

	/**
	 * Initialize the Executor with the correct command and delay
	 * 
	 * @param retrieve
	 * @param retrDelay
	 * @param store
	 * @param storDelay
	 */
	public static void initializeExecutor(String retrieve, long retrDelay,
			String store, long storDelay) {
		commandExecutor =
				new CommandExecutor(retrieve, retrDelay, store, storDelay);
		logger.info("Executor configured as [RETR: " +
				commandExecutor.pretrCMD + ":" + commandExecutor.pretrDelay + ":" +
				commandExecutor.pretrRefused +
				"] [STOR: " + commandExecutor.pstorCMD + ":" +
				commandExecutor.pstorDelay + ":" + commandExecutor.pstorRefused + "]");
	}

	/**
	 * Check if the given operation is allowed
	 * 
	 * @param isStore
	 * @return True if allowed, else False
	 */
	public static boolean isValidOperation(boolean isStore) {
		return commandExecutor.isValidOperation(isStore);
	}

	/**
	 * @param auth
	 *            the current Authentication
	 * @param args
	 *            containing in that order
	 *            "User Account BaseDir FilePath(relative to BaseDir) Command"
	 * @param isStore
	 *            True for a STORE like operation, else False
	 * @param futureCompletion
	 */
	public static AbstractExecutor createAbstractExecutor(FileBasedAuth auth,
			String[] args, boolean isStore, WaarpFuture futureCompletion) {
		if (isStore) {
			CommandExecutor executor = auth.getCommandExecutor();
			if (executor == null) {
				executor = commandExecutor;
			} else if (executor.pstorType == tNONE) {
				executor = commandExecutor;
			}
			if (executor.pstorRefused) {
				logger.error("STORe like operation REFUSED");
				futureCompletion.cancel();
				return null;
			}
			String replaced = getPreparedCommand(executor.pstorCMD, args);
			switch (executor.pstorType) {
				case tREFUSED:
					logger.error("STORe like operation REFUSED");
					futureCompletion.cancel();
					return null;
				case tEXECUTE:
					return new ExecuteExecutor(replaced, executor.pstorDelay, futureCompletion);
				case tR66PREPARETRANSFER:
					return new R66PreparedTransferExecutor(replaced, executor.pstorDelay,
							futureCompletion);
				default:
					return new NoTaskExecutor(replaced, executor.pstorDelay, futureCompletion);
			}
		} else {
			CommandExecutor executor = auth.getCommandExecutor();
			if (executor == null) {
				executor = commandExecutor;
			} else if (executor.pretrType == tNONE) {
				executor = commandExecutor;
			}
			if (executor.pretrRefused) {
				logger.error("RETRieve operation REFUSED");
				futureCompletion.cancel();
				return null;
			}
			String replaced = getPreparedCommand(executor.pretrCMD, args);
			switch (executor.pretrType) {
				case tREFUSED:
					logger.error("RETRieve operation REFUSED");
					futureCompletion.cancel();
					return null;
				case tEXECUTE:
					return new ExecuteExecutor(replaced, executor.pretrDelay, futureCompletion);
				case tR66PREPARETRANSFER:
					return new R66PreparedTransferExecutor(replaced, executor.pretrDelay,
							futureCompletion);
				default:
					return new NoTaskExecutor(replaced, executor.pretrDelay, futureCompletion);
			}
		}
	}

	/**
	 * 
	 * @param command
	 * @param args
	 *            as {User, Account, BaseDir, FilePath(relative to BaseDir), Command}
	 * @return the prepared command
	 */
	public static String getPreparedCommand(String command, String[] args) {
		StringBuilder builder = new StringBuilder(command);
		logger.debug("Will replace value in " + command + " with User=" + args[0] + ":Acct="
				+ args[1] + ":Base=" + args[2] + ":File=" + args[3] + ":Cmd=" + args[4]);
		replaceAll(builder, USER, args[0]);
		replaceAll(builder, ACCOUNT, args[1]);
		replaceAll(builder, BASEPATH, args[2]);
		replaceAll(builder, FILE, args[3]);
		replaceAll(builder, COMMAND, args[4]);
		logger.debug("Result: {}", builder);
		return builder.toString();
	}

	/**
	 * Make a replacement of first "find" string by "replace" string into the StringBuilder
	 * 
	 * @param builder
	 * @param find
	 * @param replace
	 */
	public static boolean replace(StringBuilder builder, String find, String replace) {
		int start = builder.indexOf(find);
		if (start == -1) {
			return false;
		}
		int end = start + find.length();
		builder.replace(start, end, replace);
		return true;
	}

	/**
	 * Make replacement of all "find" string by "replace" string into the StringBuilder
	 * 
	 * @param builder
	 * @param find
	 * @param replace
	 */
	public static void replaceAll(StringBuilder builder, String find, String replace) {
		while (replace(builder, find, replace)) {
		}
	}

	public static CommandExecutor getCommandExecutor() {
		return commandExecutor;
	}

	public abstract void run() throws CommandAbstractException;
}
