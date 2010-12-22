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
package goldengate.ftp.exec.control;

import java.io.File;

import goldengate.common.command.ReplyCode;
import goldengate.common.command.exception.CommandAbstractException;
import goldengate.common.command.exception.Reply421Exception;
import goldengate.common.command.exception.Reply502Exception;
import goldengate.common.command.exception.Reply504Exception;
import goldengate.common.database.DbSession;
import goldengate.common.database.data.AbstractDbData.UpdatedInfo;
import goldengate.common.database.exception.GoldenGateDatabaseNoConnectionError;
import goldengate.common.future.GgFuture;
import goldengate.common.logging.GgInternalLogger;
import goldengate.common.logging.GgInternalLoggerFactory;
import goldengate.ftp.core.command.AbstractCommand;
import goldengate.ftp.core.command.FtpCommandCode;
import goldengate.ftp.core.command.access.QUIT;
import goldengate.ftp.core.control.BusinessHandler;
import goldengate.ftp.core.data.FtpTransfer;
import goldengate.ftp.core.exception.FtpNoFileException;
import goldengate.ftp.core.file.FtpFile;
import goldengate.ftp.core.session.FtpSession;
import goldengate.ftp.filesystembased.FilesystemBasedFtpAuth;
import goldengate.ftp.filesystembased.FilesystemBasedFtpRestart;
import goldengate.ftp.exec.config.AUTHUPDATE;
import goldengate.ftp.exec.database.DbConstant;
import goldengate.ftp.exec.exec.AbstractExecutor;
import goldengate.ftp.exec.exec.R66PreparedTransferExecutor;
import goldengate.ftp.exec.file.FileBasedAuth;
import goldengate.ftp.exec.file.FileBasedDir;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ExceptionEvent;

/**
 * BusinessHandler implementation that allows pre and post actions on any
 * operations and specifically on transfer operations
 *
 * @author Frederic Bregier
 *
 */
public class ExecBusinessHandler extends BusinessHandler {
    /**
     * Internal Logger
     */
    private static final GgInternalLogger logger = GgInternalLoggerFactory
            .getLogger(ExecBusinessHandler.class);

    /**
     * Associated DbFtpSession
     */
    public DbSession dbFtpSession = null;
    /**
     * Associated DbR66Session
     */
    public DbSession dbR66Session = null;
    private boolean internalDb = false;



    /* (non-Javadoc)
     * @see goldengate.ftp.core.control.BusinessHandler#afterTransferDoneBeforeAnswer(goldengate.ftp.core.data.FtpTransfer)
     */
    @Override
    public void afterTransferDoneBeforeAnswer(FtpTransfer transfer)
            throws CommandAbstractException {
        // if Admin, do nothing
        if (getFtpSession() == null || getFtpSession().getAuth() == null) {
            return;
        }
        if (getFtpSession().getAuth().isAdmin()) {
            return;
        }
        long specialId =
            ((FileBasedAuth)getFtpSession().getAuth()).getSpecialId();
        if (getFtpSession().getReplyCode() != ReplyCode.REPLY_250_REQUESTED_FILE_ACTION_OKAY) {
            // Do nothing
            String message = "Transfer done with code: "+getFtpSession().getReplyCode().getMesg();
            GoldenGateActionLogger.logErrorAction(dbFtpSession, 
                    specialId, transfer, message, getFtpSession().getReplyCode(), this);
            return;
        }
        // if STOR like: get file (can be STOU) and execute external action
        switch (transfer.getCommand()) {
            case RETR:
                // nothing to do since All done
                break;
            case APPE:
            case STOR:
            case STOU:
                // execute the store command
                GgFuture futureCompletion = new GgFuture(true);
                String []args = new String[5];
                args[0] = getFtpSession().getAuth().getUser();
                args[1] = getFtpSession().getAuth().getAccount();
                args[2] = ((FilesystemBasedFtpAuth) getFtpSession().getAuth()).getBaseDirectory();
                FtpFile file;
                try {
                    file = transfer.getFtpFile();
                } catch (FtpNoFileException e1) {
                    // File cannot be sent
                    String message = 
                        "PostExecution in Error for Transfer since No File found: " +
                        transfer.getCommand()+" "+
                        transfer.getStatus() + " "+transfer.getPath();
                    CommandAbstractException exc = new Reply421Exception(
                            "PostExecution in Error for Transfer since No File found");
                    GoldenGateActionLogger.logErrorAction(dbFtpSession, 
                            specialId, transfer, message, exc.code, this);
                    throw exc;
                }
                try {
                    args[3] = file.getFile();
                    File newfile = new File(args[2]+args[3]);
                    if (! newfile.canRead()) {
                        // File cannot be sent
                        String message = 
                            "PostExecution in Error for Transfer since File is not readable: " +
                            transfer.getCommand()+" "+
                            newfile.getAbsolutePath()+":"+newfile.canRead()+
                            " "+transfer.getStatus() + " "+transfer.getPath();
                        CommandAbstractException exc =
                            new Reply421Exception(
                            "Transfer done but force disconnection since an error occurs on PostOperation");
                        GoldenGateActionLogger.logErrorAction(dbFtpSession, 
                                specialId, transfer, message, exc.code, this);
                        throw exc;
                    }
                } catch (CommandAbstractException e1) {
                    // File cannot be sent
                    String message = 
                        "PostExecution in Error for Transfer since No File found: " +
                        transfer.getCommand()+" "+
                        transfer.getStatus() + " "+ transfer.getPath();
                    CommandAbstractException exc =
                        new Reply421Exception(
                        "Transfer done but force disconnection since an error occurs on PostOperation");
                    GoldenGateActionLogger.logErrorAction(dbFtpSession, 
                            specialId, transfer, message, exc.code, this);
                    throw exc;
                }
                args[4] = transfer.getCommand().toString();
                AbstractExecutor executor =
                    AbstractExecutor.createAbstractExecutor(args, true, futureCompletion);
                if (executor instanceof R66PreparedTransferExecutor){
                    ((R66PreparedTransferExecutor)executor).setDbsession(dbR66Session);
                }
                executor.run();
                try {
                    futureCompletion.await();
                } catch (InterruptedException e) {
                }
                if (futureCompletion.isSuccess()) {
                    // All done
                } else {
                    // File cannot be sent
                    String message = 
                        "PostExecution in Error for Transfer: " +
                        transfer.getCommand()+" "+
                        transfer.getStatus() + " "+transfer.getPath()
                        +"\n   "+(futureCompletion.getCause() != null?
                                futureCompletion.getCause().getMessage():"Internal error of PostExecution");
                    CommandAbstractException exc =
                        new Reply421Exception(
                        "Transfer done but force disconnection since an error occurs on PostOperation");
                    GoldenGateActionLogger.logErrorAction(dbFtpSession, 
                            specialId, transfer, message, exc.code, this);
                    throw exc;
                }
                break;
            default:
                // nothing to do
        }
    }

    @Override
    public void afterTransferDone(FtpTransfer transfer) {
        // Do nothing
        ((FileBasedAuth)getFtpSession().getAuth()).setSpecialId(DbConstant.ILLEGALVALUE);
    }

    @Override
    public void afterRunCommandKo(CommandAbstractException e) {
        String message = "ExecHandler: KO: "+getFtpSession()+" "+e.getMessage();
        long specialId =
            ((FileBasedAuth)getFtpSession().getAuth()).getSpecialId();
        GoldenGateActionLogger.logErrorAction(dbFtpSession, 
                specialId, null, message, e.code, this);
    }

    @Override
    public void afterRunCommandOk() throws CommandAbstractException {
        // nothing to do since it is only Command and not transfer
        // except if QUIT due to database error
        if (this.getFtpSession().getCurrentCommand() instanceof QUIT
                && this.dbR66Session == null) {
            throw new Reply421Exception(
                    "Post operations cannot be done so force disconnection... Try again later on");
        } else {
            long specialId =
                ((FileBasedAuth)getFtpSession().getAuth()).getSpecialId();
            GoldenGateActionLogger.logAction(dbFtpSession, specialId,
                    "ExecHandler: OK:", this, getFtpSession().getReplyCode(),
                    UpdatedInfo.DONE);
        }
    }

    @Override
    public void beforeRunCommand() throws CommandAbstractException {
        long specialId = DbConstant.ILLEGALVALUE;
        // if Admin, do nothing
        if (getFtpSession() == null || getFtpSession().getAuth() == null) {
            return;
        }
        if (getFtpSession().getAuth().isAdmin()) {
            return;
        }
        FtpCommandCode code = getFtpSession().getCurrentCommand().getCode();
        switch (code) {
            case APPE:
            case STOR:
            case STOU:
                ((FileBasedAuth)getFtpSession().getAuth()).setSpecialId(specialId);
                if (!AbstractExecutor.isValidOperation(true)) {
                    throw new Reply504Exception("STORe like operations are not allowed");
                }
                // create entry in log
                specialId = GoldenGateActionLogger.logCreate(dbFtpSession, 
                        "PrepareTransfer: OK",
                        getFtpSession().getCurrentCommand().getArg(),
                        this);
                ((FileBasedAuth)getFtpSession().getAuth()).setSpecialId(specialId);
                // nothing to do now
                break;
            case RETR:
                ((FileBasedAuth)getFtpSession().getAuth()).setSpecialId(specialId);
                if (!AbstractExecutor.isValidOperation(false)) {
                    throw new Reply504Exception("RETRieve like operations are not allowed");
                }
                // create entry in log
                specialId = GoldenGateActionLogger.logCreate(dbFtpSession, 
                        "PrepareTransfer: OK",
                        getFtpSession().getCurrentCommand().getArg(),
                        this);
                ((FileBasedAuth)getFtpSession().getAuth()).setSpecialId(specialId);
                // execute the external retrieve command before the execution of RETR
                GgFuture futureCompletion = new GgFuture(true);
                String []args = new String[5];
                args[0] = getFtpSession().getAuth().getUser();
                args[1] = getFtpSession().getAuth().getAccount();
                args[2] = ((FilesystemBasedFtpAuth) getFtpSession().getAuth()).getBaseDirectory();
                String filename = getFtpSession().getCurrentCommand().getArg();
                FtpFile file = getFtpSession().getDir().setFile(filename, false);
                args[3] = file.getFile();
                args[4] = code.toString();
                AbstractExecutor executor =
                    AbstractExecutor.createAbstractExecutor(args, false, futureCompletion);
                if (executor instanceof R66PreparedTransferExecutor){
                    ((R66PreparedTransferExecutor)executor).setDbsession(dbR66Session);
                }
                executor.run();
                try {
                    futureCompletion.await();
                } catch (InterruptedException e) {
                }
                if (futureCompletion.isSuccess()) {
                    // File should be ready
                    if (! file.canRead()) {
                        logger.error("PreExecution in Error for Transfer since " +
                                "File downloaded but not ready to be retrieved: {} " +
                                " {} \n   "+(futureCompletion.getCause() != null?
                                        futureCompletion.getCause().getMessage():
                                            "File downloaded but not ready to be retrieved"),
                                            args[4], args[3]);
                        throw new Reply421Exception(
                            "File downloaded but not ready to be retrieved");
                    }
                } else {
                    // File cannot be retrieved
                    logger.error("PreExecution in Error for Transfer since " +
                            "File cannot be prepared to be retrieved: {} " +
                            " {} \n   "+(futureCompletion.getCause() != null?
                                    futureCompletion.getCause().getMessage():
                                        "File cannot be prepared to be retrieved"),
                                        args[4], args[3]);
                    throw new Reply421Exception(
                        "File cannot be prepared to be retrieved");
                }
                break;
            default:
                // nothing to do
        }
    }

    @Override
    protected void cleanSession() {
    }

    @Override
    public void exceptionLocalCaught(ExceptionEvent e) {
    }

    @Override
    public void executeChannelClosed() {
        if (AbstractExecutor.useDatabase){
            if (! internalDb) {
                if (dbR66Session != null) {
                    dbR66Session.disconnect();
                    dbR66Session = null;
                }
            }
            if (dbFtpSession != null) {
                dbFtpSession.disconnect();
                dbFtpSession = null;
            }
        }
    }

    @Override
    public void executeChannelConnected(Channel channel) {
        if (AbstractExecutor.useDatabase) {
            if (openr66.database.DbConstant.admin != null && 
                    openr66.database.DbConstant.admin.isConnected) {
                try {
                    dbR66Session = new DbSession(openr66.database.DbConstant.admin, false);
                } catch (GoldenGateDatabaseNoConnectionError e1) {
                    logger.warn("Database not ready due to {}", e1.getMessage());
                    QUIT command = (QUIT)
                        FtpCommandCode.getFromLine(getFtpSession(), FtpCommandCode.QUIT.name());
                    this.getFtpSession().setNextCommand(command);
                    dbR66Session = null;
                    internalDb = true;
                }
            }
            if (DbConstant.admin.isConnected) {
                try {
                    dbFtpSession = new DbSession(DbConstant.admin, false);
                } catch (GoldenGateDatabaseNoConnectionError e1) {
                    logger.warn("Database not ready due to {}", e1.getMessage());
                    QUIT command = (QUIT)
                        FtpCommandCode.getFromLine(getFtpSession(), FtpCommandCode.QUIT.name());
                    this.getFtpSession().setNextCommand(command);
                    dbFtpSession = null;
                }
            }
        }
    }

    @Override
    public FileBasedAuth getBusinessNewAuth() {
        return new FileBasedAuth(getFtpSession());
    }

    @Override
    public FileBasedDir getBusinessNewDir() {
        return new FileBasedDir(getFtpSession());
    }

    @Override
    public FilesystemBasedFtpRestart getBusinessNewRestart() {
        return new FilesystemBasedFtpRestart(getFtpSession());
    }

    @Override
    public String getHelpMessage(String arg) {
        return "This FTP server is only intend as a Gateway. RETRieve actions may be unallowed.\n"
                + "This FTP server refers to RFC 959, 775, 2389, 2428, 3659 and supports XCRC, XMD5 and XSHA1 commands.\n"
                + "XCRC, XMD5 and XSHA1 take a simple filename as argument and return \"250 digest-value is the digest of filename\".";
    }

    @Override
    public String getFeatMessage() {
        StringBuilder builder = new StringBuilder("Extensions supported:");
        builder.append('\n');
        builder.append(getDefaultFeatMessage());
        builder.append('\n');
        builder.append(FtpCommandCode.SITE.name());
        builder.append(' ');
        builder.append("AUTHUPDATE");
        builder.append("\nEnd");
        return builder.toString();
    }

    @Override
    public String getOptsMessage(String[] args) throws CommandAbstractException {
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase(FtpCommandCode.MLST.name()) ||
                    args[0].equalsIgnoreCase(FtpCommandCode.MLSD.name())) {
                return getMLSxOptsMessage(args);
            }
            throw new Reply502Exception("OPTS not implemented for " + args[0]);
        }
        throw new Reply502Exception("OPTS not implemented");
    }

    /* (non-Javadoc)
     * @see goldengate.ftp.core.control.BusinessHandler#getSpecializedSiteCommand(goldengate.ftp.core.session.FtpSession, java.lang.String)
     */
    @Override
    public AbstractCommand getSpecializedSiteCommand(FtpSession session,
            String line) {
        if (getFtpSession() == null || getFtpSession().getAuth() == null) {
            return null;
        }
        if (!session.getAuth().isAdmin()) {
            return null;
        }
        String newline = line;
        if (newline == null) {
            return null;
        }
        String command = null;
        String arg = null;
        if (newline.indexOf(' ') == -1) {
            command = newline;
            arg = null;
        } else {
            command = newline.substring(0, newline.indexOf(' '));
            arg = newline.substring(newline.indexOf(' ') + 1);
            if (arg.length() == 0) {
                arg = null;
            }
        }
        String COMMAND = command.toUpperCase();
        if (! COMMAND.equals("AUTHUPDATE")) {
            return null;
        }
        AbstractCommand abstractCommand = new AUTHUPDATE();
        abstractCommand.setArgs(session, COMMAND, arg, FtpCommandCode.SITE);
        return abstractCommand;
    }
}
