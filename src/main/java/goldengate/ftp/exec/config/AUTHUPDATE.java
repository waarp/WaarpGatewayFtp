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
package goldengate.ftp.exec.config;

import java.io.File;

import goldengate.common.command.ReplyCode;
import goldengate.common.command.exception.CommandAbstractException;
import goldengate.common.command.exception.Reply500Exception;
import goldengate.common.command.exception.Reply501Exception;
import goldengate.common.logging.GgInternalLogger;
import goldengate.common.logging.GgInternalLoggerFactory;
import goldengate.ftp.core.command.AbstractCommand;

/**
 * AUTHENTUPDATE command: implements the command that will try to update the authentications
 * from the file given as second argument or the original one if no argument is given.
 *
 * @author Frederic Bregier
 *
 */
public class AUTHUPDATE extends AbstractCommand {
    /**
     * Internal Logger
     */
    private static final GgInternalLogger logger = GgInternalLoggerFactory
            .getLogger(AUTHUPDATE.class);

    /* (non-Javadoc)
     * @see goldengate.common.command.CommandInterface#exec()
     */
    @Override
    public void exec() throws CommandAbstractException {
        if (!getSession().getAuth().isAdmin()) {
            // not admin
            throw new Reply500Exception("Command Not Allowed");
        }
        String filename = null;
        if (!hasArg()) {
            filename = ((FileBasedConfiguration) getConfiguration()).authenticationFile;
        } else {
            String[] limits = getArgs();
            filename = limits[0];
            File file = new File(filename);
            if (! file.canRead()) {
                throw new Reply501Exception("Filename given as parameter is not found");
            }
        }
        if (! ((FileBasedConfiguration) getConfiguration()).initializeAuthent(filename)) {
            throw new Reply501Exception("Filename given as parameter is not correct");
        }
        logger.warn("Authentication was updated from "+filename);
        getSession().setReplyCode(ReplyCode.REPLY_200_COMMAND_OKAY,
            "Authentication is updated");
    }

}
