/**
   This file is part of Waarp Project.

   Copyright 2009, Frederic Bregier, and individual contributors by the @author
   tags. See the COPYRIGHT.txt in the distribution for a full listing of
   individual contributors.

   All Waarp Project is free software: you can redistribute it and/or 
   modify it under the terms of the GNU General Public License as published 
   by the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   Waarp is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with Waarp .  If not, see <http://www.gnu.org/licenses/>.
 */
package org.waarp.gateway.ftp.service;

import java.util.Timer;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.ChannelGroupFutureListener;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.slf4j.LoggerFactory;
import org.waarp.common.future.WaarpFuture;
import org.waarp.common.logging.WaarpInternalLogger;
import org.waarp.common.logging.WaarpInternalLoggerFactory;
import org.waarp.common.logging.WaarpSlf4JLoggerFactory;
import org.waarp.common.service.EngineAbstract;
import org.waarp.common.utility.SystemPropertyUtil;
import org.waarp.ftp.core.config.FtpConfiguration;
import org.waarp.ftp.core.utils.FtpTimerTask;
import org.waarp.gateway.ftp.ExecGatewayFtpServer;
import org.waarp.gateway.ftp.config.FileBasedConfiguration;
import org.waarp.openr66.protocol.configuration.Configuration;

import ch.qos.logback.classic.LoggerContext;

/**
 * Engine used to start and stop the real Gateway Ftp service
 * @author Frederic Bregier
 *
 */
public class FtpEngine extends EngineAbstract {
	/**
	 * Internal Logger
	 */
	private static final WaarpInternalLogger logger = WaarpInternalLoggerFactory
			.getLogger(FtpEngine.class);
	
	public static WaarpFuture closeFuture = new WaarpFuture(true);
	
	public static final String CONFIGFILE = "org.waarp.gateway.ftp.config.file";
	
	public static final String R66CONFIGFILE = "org.waarp.r66.config.file";
	
	@Override
	public void run() {
		String ftpfile = SystemPropertyUtil.get(CONFIGFILE);
		String r66file = SystemPropertyUtil.get(R66CONFIGFILE);
		if (ftpfile == null) {
			logger.error("Cannot find "+CONFIGFILE+" parameter");
			shutdown();
			return;
		}
		Configuration.configuration.shutdownConfiguration.serviceFuture = closeFuture;
		try {
			if (!ExecGatewayFtpServer.initialize(ftpfile, r66file)) {
				logger.error("Cannot start Gateway FTP");
				shutdown();
				return;
			}
		} catch (Throwable e) {
			logger.error("Cannot start Gateway FTP", e);
			shutdown();
			return;
		}
		logger.warn("Service started with "+ftpfile);
	}

	/**
	 * Finalize resources attached to Control or Data handlers
	 * 
	 * @author Frederic Bregier
	 * 
	 */
	private static class FtpChannelGroupFutureListener implements
			ChannelGroupFutureListener {
		OrderedMemoryAwareThreadPoolExecutor pool;

		ChannelFactory channelFactory;

		ChannelFactory channelFactory2;

		public FtpChannelGroupFutureListener(
				OrderedMemoryAwareThreadPoolExecutor pool,
				ChannelFactory channelFactory, ChannelFactory channelFactory2) {
			this.pool = pool;
			this.channelFactory = channelFactory;
			this.channelFactory2 = channelFactory2;
		}

		public void operationComplete(ChannelGroupFuture future)
				throws Exception {
			pool.shutdownNow();
			channelFactory.releaseExternalResources();
			if (channelFactory2 != null) {
				channelFactory2.releaseExternalResources();
			}
		}
	}
	
	private static void exit(FtpConfiguration configuration) {
		configuration.isShutdown = true;
		long delay = configuration.TIMEOUTCON;
		logger.warn("Exit: Give a delay of " + delay + " ms");
		configuration.inShutdownProcess();
		try {
			Thread.sleep(delay);
		} catch (InterruptedException e) {
		}
		configuration.getFtpInternalConfiguration()
				.getGlobalTrafficShapingHandler().releaseExternalResources();
		Timer timer = new Timer(true);
		FtpTimerTask timerTask = new FtpTimerTask(FtpTimerTask.TIMER_CONTROL);
		timerTask.configuration = configuration;
		timer.schedule(timerTask, configuration.TIMEOUTCON / 2);
		configuration.releaseResources();
		logger.info("Exit Shutdown Data");
		configuration.getFtpInternalConfiguration()
			.getDataChannelGroup().size();
		configuration.getFtpInternalConfiguration().getDataChannelGroup()
			.close().addListener(
				new FtpChannelGroupFutureListener(configuration
						.getFtpInternalConfiguration()
						.getDataPipelineExecutor(), configuration
						.getFtpInternalConfiguration()
						.getDataPassiveChannelFactory(), configuration
						.getFtpInternalConfiguration()
						.getDataActiveChannelFactory()));
		logger.warn("Exit end of Data Shutdown");
		FtpEngine.closeFuture.setSuccess();
		if (WaarpInternalLoggerFactory.getDefaultFactory() instanceof WaarpSlf4JLoggerFactory) {
			LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
			lc.stop();
		}
	}
	
	@Override
	public void shutdown() {
		exit(FileBasedConfiguration.fileBasedConfiguration);
		closeFuture.setSuccess();
		logger.info("Service stopped");
	}

	@Override
	public boolean isShutdown() {
		return closeFuture.isDone();
	}

	@Override
	public boolean waitShutdown() throws InterruptedException {
		closeFuture.await();
		return closeFuture.isSuccess();
	}
}
