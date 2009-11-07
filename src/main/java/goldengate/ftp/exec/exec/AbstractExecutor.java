/**
 * Copyright 2009, Frederic Bregier, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3.0 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package goldengate.ftp.exec.exec;

import goldengate.common.future.GgFuture;
import goldengate.common.logging.GgInternalLogger;
import goldengate.common.logging.GgInternalLoggerFactory;

/**
 * Abstract Executor class. If the command starts with "REFUSED", the command will be refused for execution.
 * If "REFUSED" is set, the command "RETR" or "STOR" like operations will be stopped at starting
 * of command.<br>
 * If the command starts with "EXECUTE", the following will be a command to be executed.<br>
 * If the command starts with "R66PREPARETRANSFER", the following will be a r66 prepare transfer execution (asynchrone only).<br>
 *
 *
 * The following replacement are done dynamically before the command is executed:<br>
 * - #BASEPATH# is replaced by the full path for the root of FTP Directory<br>
 * - #FILE# is replaced by the current file path relative to FTP Directory (so #BASEPATH##FILE# is the full path of the file)<br>
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
    private static final GgInternalLogger logger = GgInternalLoggerFactory
            .getLogger(AbstractExecutor.class);
    protected static final String USER = "#USER#";
    protected static final String ACCOUNT = "#ACCOUNT#";
    protected static final String BASEPATH = "#BASEPATH#";
    protected static final String FILE = "#FILE#";
    protected static final String COMMAND = "#COMMAND#";

    protected static final String REFUSED = "REFUSED";
    protected static final String EXECUTE = "EXECUTE";
    protected static final String R66PREPARETRANSFER = "R66PREPARETRANSFER";

    protected static final int tREFUSED = -1;
    protected static final int tEXECUTE = 1;
    protected static final int tR66PREPARETRANSFER = 2;
    /**
     * Retrieve External Command
     */
    protected static String retrieveCMD;
    protected static int retrieveType = -1;
    protected static boolean retrieveRefused = false;
    /**
     * Retrieve Delay (0 = unlimited)
     */
    protected static long retrieveDelay = 0;
    /**
     * Store External Command
     */
    protected static String storeCMD;
    protected static int storeType = -1;
    protected static boolean storeRefused = false;
    /**
     * Store Delay (0 = unlimited)
     */
    protected static long storeDelay = 0;

    public static boolean useDatabase = false;

    /**
     * Initialize the Executor with the correct command and delay
     * @param retrieve
     * @param retrDelay
     * @param store
     * @param storDelay
     */
    public static void initializeExecutor(String retrieve, long retrDelay,
            String store, long storDelay) {
        if (retrieve.startsWith(REFUSED)) {
            retrieveCMD = REFUSED;
            retrieveType = tREFUSED;
            retrieveRefused = true;
        } else {
            if (retrieve.startsWith(EXECUTE)) {
                retrieveCMD = retrieve.substring(EXECUTE.length()).trim();
                retrieveType = tEXECUTE;
            } else if (retrieve.startsWith(R66PREPARETRANSFER)) {
                retrieveCMD = retrieve.substring(R66PREPARETRANSFER.length()).trim();
                retrieveType = tR66PREPARETRANSFER;
                useDatabase = true;
            } else {
                // Default EXECUTE
                retrieveCMD = retrieve.trim();
                retrieveType = tEXECUTE;
            }
        }
        retrieveDelay = retrDelay;
        if (store.startsWith(REFUSED)) {
            storeCMD = REFUSED;
            storeRefused = true;
            storeType = tREFUSED;
        } else {
            if (store.startsWith(EXECUTE)) {
                storeCMD = store.substring(EXECUTE.length()).trim();
                storeType = tEXECUTE;
            } else if (store.startsWith(R66PREPARETRANSFER)) {
                storeCMD = store.substring(R66PREPARETRANSFER.length()).trim();
                storeType = tR66PREPARETRANSFER;
                useDatabase = true;
            } else {
                // Default EXECUTE
                storeCMD = store.trim();
                storeType = tEXECUTE;
            }
        }
        storeDelay = storDelay;
        logger.warn("Executor configured as [RETR: "+retrieveCMD+":"+retrieveDelay+":"+retrieveRefused+
                "] [STOR: "+storeCMD+":"+storeDelay+":"+storeRefused+"]");
    }
    /**
     * Check if the given operation is allowed
     * @param isStore
     * @return True if allowed, else False
     */
    public static boolean isValidOperation(boolean isStore) {
        if (isStore && storeRefused) {
            logger.info("STORe like operations REFUSED");
            return false;
        } else if ((!isStore) && retrieveRefused) {
            logger.info("RETRieve operations REFUSED");
            return false;
        }
        return true;
    }
    /**
    *
    * @param args containing in that order
    *          "User Account BaseDir FilePath(relative to BaseDir) Command"
    * @param isStore True for a STORE like operation, else False
    * @param futureCompletion
    */
    public static AbstractExecutor createAbstractExecutor(String []args, boolean isStore, GgFuture futureCompletion) {
        if (isStore) {
            if (storeRefused)  {
                logger.error("STORe like operation REFUSED");
                futureCompletion.cancel();
                return null;
            }
            String replaced = getPreparedCommand(storeCMD, args);
            switch (storeType) {
                case tREFUSED:
                    logger.error("STORe like operation REFUSED");
                    futureCompletion.cancel();
                    return null;
                case tEXECUTE:
                    return new ExecuteExecutor(replaced, storeDelay, futureCompletion);
                case tR66PREPARETRANSFER:
                    return new R66PreparedTransferExecutor(replaced, storeDelay, futureCompletion);
                default:
                    return new ExecuteExecutor(replaced, storeDelay, futureCompletion);
            }
        } else {
            if (retrieveRefused)  {
                logger.error("RETRieve operation REFUSED");
                futureCompletion.cancel();
                return null;
            }
            String replaced = getPreparedCommand(retrieveCMD, args);
            switch (retrieveType) {
                case tREFUSED:
                    logger.error("RETRieve operation REFUSED");
                    futureCompletion.cancel();
                    return null;
                case tEXECUTE:
                    return new ExecuteExecutor(replaced, retrieveDelay, futureCompletion);
                case tR66PREPARETRANSFER:
                    return new R66PreparedTransferExecutor(replaced, retrieveDelay, futureCompletion);
                default:
                    return new ExecuteExecutor(replaced, retrieveDelay, futureCompletion);
            }
        }
    }
    /**
     *
     * @param command
     * @param args as {User, Account, BaseDir, FilePath(relative to BaseDir), Command}
     * @return the prepared command
     */
    public static String getPreparedCommand(String command, String []args) {
        StringBuilder builder = new StringBuilder(command);
        logger.debug("Will replace value in "+command+" with User="+args[0]+":Acct="
                +args[1]+":Base="+args[2]+":File="+args[3]+":Cmd="+args[4]);
        replaceAll(builder, USER, args[0]);
        replaceAll(builder, ACCOUNT, args[1]);
        replaceAll(builder, BASEPATH, args[2]);
        replaceAll(builder, FILE, args[3]);
        replaceAll(builder, COMMAND, args[4]);
        logger.debug("Result: {}",builder);
        return builder.toString();
    }
    /**
     * Make a replacement of first "find" string by "replace" string into the StringBuilder
     * @param builder
     * @param find
     * @param replace
     */
    public static boolean replace(StringBuilder builder, String find, String replace) {
        int start = builder.indexOf(find);
        if (start == -1) {
            return false;
        }
        int end = start+find.length();
        builder.replace(start, end, replace);
        return true;
    }
    /**
     * Make replacement of all "find" string by "replace" string into the StringBuilder
     * @param builder
     * @param find
     * @param replace
     */
    public static void replaceAll(StringBuilder builder, String find, String replace) {
        while (replace(builder, find, replace)) {
        }
    }

    public abstract void run();
}
