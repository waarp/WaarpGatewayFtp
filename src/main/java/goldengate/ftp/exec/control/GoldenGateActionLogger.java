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
package goldengate.ftp.exec.control;

import goldengate.common.command.ReplyCode;
import goldengate.common.command.exception.CommandAbstractException;
import goldengate.common.database.DbSession;
import goldengate.common.database.data.AbstractDbData.UpdatedInfo;
import goldengate.common.database.exception.GoldenGateDatabaseException;
import goldengate.common.logging.GgInternalLogger;
import goldengate.common.logging.GgInternalLoggerFactory;
import goldengate.ftp.core.command.FtpCommandCode;
import goldengate.ftp.core.control.BusinessHandler;
import goldengate.ftp.core.data.FtpTransfer;
import goldengate.ftp.core.exception.FtpNoFileException;
import goldengate.ftp.core.session.FtpSession;
import goldengate.ftp.exec.database.DbConstant;
import goldengate.ftp.exec.database.data.DbTransferLog;

/**
 * Class to help to log any actions through the interface of GoldenGate
 *
 * @author Frederic Bregier
 *
 */
public class GoldenGateActionLogger {
    /**
     * Internal Logger
     */
    private static final GgInternalLogger logger = GgInternalLoggerFactory
            .getLogger(GoldenGateActionLogger.class);

    /**
     * Log the action
     * @param ftpSession
     * @param message
     * @param file
     * @param handler
     */
    public static long logCreate(DbSession ftpSession, 
            String message, String file, BusinessHandler handler) {
        FtpSession session = handler.getFtpSession();
        String sessionContexte = session.toString();
        logger.warn(message+" "+sessionContexte);
        if (ftpSession != null) {
            FtpCommandCode code = session.getCurrentCommand().getCode();
            if (FtpCommandCode.isRetrLikeCommand(code) ||
                    FtpCommandCode.isStoreLikeCommand(code)) {
                boolean isSender = 
                    FtpCommandCode.isRetrLikeCommand(code);
                try {
                    // Insert new one
                    DbTransferLog log = 
                        new DbTransferLog(ftpSession, 
                            session.getAuth().getUser(), 
                            session.getAuth().getAccount(), 
                            DbConstant.ILLEGALVALUE,
                            isSender, file,
                            code.name(), 
                            ReplyCode.REPLY_000_SPECIAL_NOSTATUS, message,
                            UpdatedInfo.TOSUBMIT);
                    logger.warn("Create FS: "+log.toString());
                    return log.getSpecialId();
                } catch (GoldenGateDatabaseException e1) {
                    // Do nothing
                }
            }
        }
        return DbConstant.ILLEGALVALUE;
    }
    /**
     * Log the action
     * @param ftpSession
     * @param specialId
     * @param message
     * @param handler
     * @param rcode
     * @param info
     */
    public static long logAction(DbSession ftpSession, long specialId,
            String message, BusinessHandler handler, ReplyCode rcode,
            UpdatedInfo info) {
        FtpSession session = handler.getFtpSession();
        String sessionContexte = session.toString();
        logger.warn(message+" "+sessionContexte);
        if (ftpSession != null && specialId != DbConstant.ILLEGALVALUE) {
            FtpCommandCode code = session.getCurrentCommand().getCode();
            if (FtpCommandCode.isRetrLikeCommand(code) ||
                    FtpCommandCode.isStoreLikeCommand(code)) {
                boolean isSender = 
                    FtpCommandCode.isRetrLikeCommand(code);
                try {
                    // Try load
                    DbTransferLog log = 
                        new DbTransferLog(ftpSession,
                                session.getAuth().getUser(), 
                                session.getAuth().getAccount(), specialId);
                    log.changeUpdatedInfo(info);
                    log.setInfotransf(message);
                    log.setReplyCodeExecutionStatus(rcode);
                    log.update();
                    logger.warn("Update FS: "+log.toString());
                    return log.getSpecialId();
                } catch (GoldenGateDatabaseException e) {
                    try {
                        // Insert new one
                        DbTransferLog log = 
                            new DbTransferLog(ftpSession, 
                                session.getAuth().getUser(), 
                                session.getAuth().getAccount(), specialId,
                                isSender, null,
                                code.name(), 
                                rcode, message,
                                info);
                        logger.warn("Create FS: "+log.toString());
                        return log.getSpecialId();
                    } catch (GoldenGateDatabaseException e1) {
                        // Do nothing
                    }
                }
            }
        }
        return specialId;
    }
    /**
     * Log the action in error
     * @param ftpSession
     * @param specialId
     * @param transfer
     * @param message
     * @param rcode
     * @param handler
     */
    public static void logErrorAction(DbSession ftpSession, long specialId,
            FtpTransfer transfer,
            String message, ReplyCode rcode, BusinessHandler handler) {
        FtpSession session = handler.getFtpSession();
        String sessionContexte = session.toString();
        logger.error(rcode.getCode()+":"+message+" "+sessionContexte);
        if (ftpSession != null && specialId != DbConstant.ILLEGALVALUE) {
            FtpCommandCode code = session.getCurrentCommand().getCode();
            if (FtpCommandCode.isRetrLikeCommand(code) ||
                    FtpCommandCode.isStoreLikeCommand(code)) {
                boolean isSender = false;
                isSender = FtpCommandCode.isRetrLikeCommand(code);
                String file = null;
                if (transfer != null) {
                    try {
                        file = transfer.getFtpFile().getFile();
                    } catch (CommandAbstractException e1) {
                    } catch (FtpNoFileException e1) {
                    }
                } else {
                    file = null;
                }
                UpdatedInfo info = UpdatedInfo.INERROR;
                try {
                    // Try load
                    DbTransferLog log = 
                        new DbTransferLog(ftpSession,
                                session.getAuth().getUser(), 
                                session.getAuth().getAccount(), specialId);
                    log.changeUpdatedInfo(info);
                    log.setInfotransf(message);
                    log.setReplyCodeExecutionStatus(rcode);
                    if (file != null) {
                        log.setFilename(file);
                    }
                    log.update();
                    logger.warn("Update FS: "+log.toString());
                } catch (GoldenGateDatabaseException e) {
                    try {
                        // Insert new one
                        DbTransferLog log = 
                            new DbTransferLog(ftpSession, 
                                session.getAuth().getUser(), 
                                session.getAuth().getAccount(), specialId,
                                isSender, file,
                                code.name(), 
                                rcode, message,
                                info);
                        logger.warn("Create FS: "+log.toString());
                    } catch (GoldenGateDatabaseException e1) {
                        // Do nothing
                    }
                }
            }
        }
    }
}
