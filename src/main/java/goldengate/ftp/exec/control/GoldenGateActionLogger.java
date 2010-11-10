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

import goldengate.common.logging.GgInternalLogger;
import goldengate.common.logging.GgInternalLoggerFactory;
import goldengate.ftp.core.control.BusinessHandler;
import goldengate.ftp.core.session.FtpSession;

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
     * @param message
     * @param handler
     */
    public static void logAction(String message, BusinessHandler handler) {
        FtpSession session = handler.getFtpSession();
        String sessionContexte = session.toString();
        logger.warn(message+" "+sessionContexte);
    }
}
