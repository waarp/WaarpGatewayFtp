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
import goldengate.ftp.core.config.FtpInternalConfiguration;

import java.io.File;
import java.io.IOException;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;

/**
 * Executor class. If the command starts with "REFUSED", the command will be refused at execution.
 * If "REFUSED" is set, the command "RETR" or "STOR" like operations will be stopped at starting
 * of command.
 * @author Frederic Bregier
 *
 */
public class Executor {
    /**
     * Internal Logger
     */
    private static final GgInternalLogger logger = GgInternalLoggerFactory
            .getLogger(Executor.class);
    /**
     * Retrieve External Command
     */
    private static String retrieveCMD;
    /**
     * Retrieve Delay (0 = unlimited)
     */
    private static long retrieveDelay = 0;
    /**
     * Store External Command
     */
    private static String storeCMD;
    /**
     * Store Delay (0 = unlimited)
     */
    private static long storeDelay = 0;

    /**
     * Initialize the Executor with the correct command and delay
     * @param retrieve
     * @param retrDelay
     * @param store
     * @param storDelay
     */
    public static void initializeExecutor(String retrieve, long retrDelay,
            String store, long storDelay) {
        retrieveCMD = retrieve;
        retrieveDelay = retrDelay;
        storeCMD = store;
        storeDelay = storDelay;
    }
    /**
     * Check if the given operation is allowed
     * @param isStore
     * @return True if allowed, else False
     */
    public static boolean isValidOperation(boolean isStore) {
        String command;
        if (isStore) {
            command = storeCMD;
        } else {
            command = retrieveCMD;
        }
        if (command.startsWith("REFUSED")) {
            logger.error((isStore?"STORe like operations ":"RETRieve operations ")+
                    "REFUSED by exec: " + command);
            return false;
        }
        return true;
    }
    private final String [] args;
    private final GgFuture futureCompletion;
    private final boolean isStore;

    /**
     *
     * @param args containing in that order
     *          "User Account BaseDir FilePath(relative to BaseDir) Command"
     * @param isStore True for a STORE like operation, else False
     * @param futureCompletion
     */
    public Executor(String []args, boolean isStore, GgFuture futureCompletion) {
        this.args = args;
        this.futureCompletion = futureCompletion;
        this.isStore = isStore;
    }

    public void run() {
        String fullCommand;
        String []command;
        long delay;
        if (isStore) {
            fullCommand = storeCMD;
            command = storeCMD.split(" ");
            delay = storeDelay;
        } else {
            fullCommand = retrieveCMD;
            command = retrieveCMD.split(" ");
            delay = retrieveDelay;
        }
        if (command[0].equals("REFUSED")) {
            logger.error((isStore?"STORe like operations ":"RETRieve operations ")+
                    "REFUSED by exec: " + fullCommand);
            futureCompletion.cancel();
            return;
        }
        File exec = new File(command[0]);
        if (exec.isAbsolute()) {
            if (! exec.canExecute()) {
                logger.error("Exec command is not executable: " + command[0]+" "+
                        args[0]+":"+args[1]+":"+args[3]+":"+args[4]);
                futureCompletion.cancel();
                return;
            }
        }
        CommandLine commandLine = new CommandLine(command[0]);
        for (int i = 1; i < command.length; i ++) {
            commandLine.addArgument(command[i]);
        }
        for (int i = 0; i < args.length; i ++) {
            commandLine.addArgument(args[i]);
        }
        DefaultExecutor defaultExecutor = new DefaultExecutor();
        PumpStreamHandler pumpStreamHandler = new PumpStreamHandler(null, null);
        defaultExecutor.setStreamHandler(pumpStreamHandler);
        int[] correctValues = {
                0, 1 };
        defaultExecutor.setExitValues(correctValues);
        ExecuteWatchdog watchdog = null;
        if (delay > 0) {
            watchdog = new ExecuteWatchdog(delay);
            defaultExecutor.setWatchdog(watchdog);
        }
        int status = -1;
        try {
            status = defaultExecutor.execute(commandLine);
        } catch (ExecuteException e) {
            if (e.getExitValue() == -559038737) {
                // Cannot run immediately so retry once
                try {
                    Thread.sleep(FtpInternalConfiguration.RETRYINMS);
                } catch (InterruptedException e1) {
                }
                try {
                    status = defaultExecutor.execute(commandLine);
                } catch (ExecuteException e2) {
                    pumpStreamHandler.stop();
                    logger.error("System Exception: " + e.getMessage() +
                            " Exec cannot execute command " + commandLine.toString());
                    futureCompletion.setFailure(e);
                    return;
                } catch (IOException e2) {
                    pumpStreamHandler.stop();
                    logger.error("Exception: " + e.getMessage() +
                            " Exec in error with " + commandLine.toString());
                    futureCompletion.setFailure(e);
                    return;
                }
                logger.info("System Exception: " + e.getMessage() +
                        " but finally get the command executed " + commandLine.toString());
            } else {
                pumpStreamHandler.stop();
                logger.error("Exception: " + e.getMessage() +
                    " Exec in error with " + commandLine.toString());
                futureCompletion.setFailure(e);
                return;
            }
        } catch (IOException e) {
            pumpStreamHandler.stop();
            logger.error("Exception: " + e.getMessage() +
                    " Exec in error with " + commandLine.toString());
            futureCompletion.setFailure(e);
            return;
        }
        pumpStreamHandler.stop();
        if (watchdog != null &&
                watchdog.killedProcess()) {
            // kill by the watchdoc (time out)
            logger.error("Exec is in Time Out");
            status = -1;
        }
        if (status == 0) {
            futureCompletion.setSuccess();
            logger.info("Exec OK with {}", commandLine);
        } else if (status == 1) {
            logger.warn("Exec in warning with " + commandLine.toString());
            futureCompletion.setSuccess();
        } else {
            logger.error("Status: " + status + " Exec in error with " +
                    commandLine.toString());
            futureCompletion.cancel();
        }
    }
}
