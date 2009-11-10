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
package goldengate.ftp.exec.exec;

import openr66.database.DbConstant;
import openr66.database.DbSession;
import openr66.database.data.AbstractDbData;
import openr66.database.data.DbRule;
import openr66.database.data.DbTaskRunner;
import openr66.database.exception.OpenR66DatabaseException;
import openr66.protocol.configuration.Configuration;
import openr66.protocol.localhandler.packet.RequestPacket;
import goldengate.common.command.exception.CommandAbstractException;
import goldengate.common.command.exception.Reply421Exception;
import goldengate.common.future.GgFuture;
import goldengate.common.logging.GgInternalLogger;
import goldengate.common.logging.GgInternalLoggerFactory;

/**
 * R66PreparedTransferExecutor class. If the command starts with "REFUSED", the
 * command will be refused for execution. If "REFUSED" is set, the command
 * "RETR" or "STOR" like operations will be stopped at starting of command.
 *
 *
 *
 * Format is like r66send command in any order except "-info" which should be
 * the last item:<br>
 * "-to Host -file FILE -rule RULE [-md5] [-nolog] [-info INFO]"<br>
 * <br>
 * INFO is the only one field that can contains blank character.<br>
 * <br>
 * The following replacement are done dynamically before the command is
 * executed:<br>
 * - #BASEPATH# is replaced by the full path for the root of FTP Directory<br>
 * - #FILE# is replaced by the current file path relative to FTP Directory (so
 * #BASEPATH##FILE# is the full path of the file)<br>
 * - #USER# is replaced by the username<br>
 * - #ACCOUNT# is replaced by the account<br>
 * - #COMMAND# is replaced by the command issued for the file<br>
 * <br>
 * So for instance"-to Host -file #BASEPATH##FILE# -rule RULE [-md5] [-nolog] [-info #USER# #ACCOUNT# #COMMAND# INFO]"
 * <br>
 * will be a standard use of this function.
 *
 * @author Frederic Bregier
 *
 */
public class R66PreparedTransferExecutor extends AbstractExecutor {
    /**
     * Internal Logger
     */
    private static final GgInternalLogger logger = GgInternalLoggerFactory
            .getLogger(R66PreparedTransferExecutor.class);

    protected final GgFuture future;

    protected String filename = null;

    protected String rulename = null;

    protected String fileinfo = null;

    protected boolean isMD5 = false;

    protected boolean nolog = false;

    protected String remoteHost = null;

    protected int blocksize = Configuration.configuration.BLOCKSIZE;;

    protected DbSession dbsession;
    /**
     *
     * @param command
     * @param delay
     * @param futureCompletion
     */
    public R66PreparedTransferExecutor(String command, long delay,
            GgFuture futureCompletion) {
        String args[] = command.split(" ");
        for (int i = 0; i < args.length; i ++) {
            if (args[i].equalsIgnoreCase("-to")) {
                i ++;
                remoteHost = args[i];
            } else if (args[i].equalsIgnoreCase("-file")) {
                i ++;
                filename = args[i];
            } else if (args[i].equalsIgnoreCase("-rule")) {
                i ++;
                rulename = args[i];
            } else if (args[i].equalsIgnoreCase("-info")) {
                i ++;
                fileinfo = args[i];
                i ++;
                while (i < args.length) {
                    fileinfo += " " + args[i];
                    i ++;
                }
            } else if (args[i].equalsIgnoreCase("-md5")) {
                isMD5 = true;
            } else if (args[i].equalsIgnoreCase("-block")) {
                i ++;
                blocksize = Integer.parseInt(args[i]);
                if (blocksize < 100) {
                    logger.warn("Block size is too small: " + blocksize);
                    blocksize = Configuration.configuration.BLOCKSIZE;
                }
            } else if (args[i].equalsIgnoreCase("-nolog")) {
                nolog = true;
                i ++;
            }
        }
        if (fileinfo == null) {
            fileinfo = "noinfo";
        }
        this.future = futureCompletion;
    }

    /**
     * @param dbsession the dbsession to set
     */
    public void setDbsession(DbSession dbsession) {
        this.dbsession = dbsession;
    }

    public void run() throws CommandAbstractException {
        String message = "R66Prepared with -to " + remoteHost + " -rule " +
                rulename + " -file " + filename + " -nolog: " + nolog +
                " -isMD5: " + isMD5 + " -info " + fileinfo;
        if (remoteHost == null || rulename == null || filename == null) {
            logger.error("Mandatory argument is missing: -to " + remoteHost +
                    " -rule " + rulename + " -file " + filename);
            throw new Reply421Exception("Mandatory argument is missing\n    " + message);
        }
        logger.debug(message);
        DbRule rule;
        try {
            rule = new DbRule(dbsession, rulename);
        } catch (OpenR66DatabaseException e) {
            logger.error("Cannot get Rule: " + rulename + " since {}\n    " +
                    message, e.getMessage());
            throw new Reply421Exception("Cannot get Rule: " +
                    rulename + "\n    " + message);
        }
        int mode = rule.mode;
        if (isMD5) {
            mode = RequestPacket.getModeMD5(mode);
        }
        RequestPacket request = new RequestPacket(rulename, mode, filename,
                blocksize, 0, DbConstant.ILLEGALVALUE, fileinfo);
        // Not isRecv since it is the requester, so send => isRetrieve is true
        boolean isRetrieve = !RequestPacket.isRecvMode(request.getMode());
        logger.debug("Will prepared: " + request.toString());
        DbTaskRunner taskRunner;
        try {
            taskRunner = new DbTaskRunner(dbsession, rule, isRetrieve, request,
                    remoteHost);
        } catch (OpenR66DatabaseException e) {
            logger.error("Cannot get new task since {}\n    " + message, e
                    .getMessage());
            throw new Reply421Exception("Cannot get new task\n    " + message);
        }
        taskRunner.changeUpdatedInfo(AbstractDbData.UpdatedInfo.TOSUBMIT);
        try {
            taskRunner.update();
        } catch (OpenR66DatabaseException e) {
            logger.error("Cannot prepare task since {}\n    " + message, e
                    .getMessage());
            throw new Reply421Exception("Cannot prepare task\n    " + message);
        }
        future.setSuccess();
    }
}
