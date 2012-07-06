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

import java.io.File;

import org.waarp.common.command.exception.CommandAbstractException;
import org.waarp.ftp.core.file.FtpFile;
import org.waarp.ftp.core.session.FtpSession;
import org.waarp.ftp.filesystembased.FilesystemBasedFtpFile;

/**
 * FtpFile implementation based on true directories and files
 * 
 * @author Frederic Bregier
 * 
 */
public class FileBasedFile extends FilesystemBasedFtpFile {
	/**
	 * @param session
	 * @param fileBasedDir
	 *            It is not necessary the directory that owns this file.
	 * @param path
	 * @param append
	 * @throws CommandAbstractException
	 */
	public FileBasedFile(FtpSession session, FileBasedDir fileBasedDir,
			String path, boolean append) throws CommandAbstractException {
		super(session, fileBasedDir, path, append);
	}

	/**
	 * This method is a good to have in a true {@link FtpFile} implementation.
	 * 
	 * @return the File associated with the current FtpFile operation
	 */
	public File getTrueFile() {
		try {
			return getFileFromPath(getFile());
		} catch (CommandAbstractException e) {
			return null;
		}
	}
}
