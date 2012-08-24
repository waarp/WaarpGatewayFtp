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
package org.waarp.gateway.ftp.data;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ExceptionEvent;
import org.waarp.ftp.core.data.handler.DataBusinessHandler;

/**
 * DataBusinessHandler implementation based on Simple Filesystem : do nothing
 * 
 * @author Frederic Bregier
 * 
 */
public class FileSystemBasedDataBusinessHandler extends DataBusinessHandler {
	/*
	 * (non-Javadoc)
	 * @see goldengate.ftp.core.data.handler.DataBusinessHandler#cleanSession(goldengate
	 * .ftp.core.session.FtpSession)
	 */
	@Override
	protected void cleanSession() {
	}

	/*
	 * (non-Javadoc)
	 * @see goldengate.ftp.core.data.handler.DataBusinessHandler#exceptionLocalCaught
	 * (org.jboss.netty.channel.ExceptionEvent)
	 */
	@Override
	public void exceptionLocalCaught(ExceptionEvent e) {
	}

	/*
	 * (non-Javadoc)
	 * @see goldengate.ftp.core.data.handler.DataBusinessHandler#executeChannelClosed ()
	 */
	@Override
	public void executeChannelClosed() {
	}

	/*
	 * (non-Javadoc)
	 * @see goldengate.ftp.core.data.handler.DataBusinessHandler#executeChannelConnected
	 * (org.jboss.netty.channel.Channel)
	 */
	@Override
	public void executeChannelConnected(Channel channel) {
	}
}
