/**
 * This file is part of Waarp Project.
 * 
 * Copyright 2009, Frederic Bregier, and individual contributors by the @author tags. See the
 * COPYRIGHT.txt in the distribution for a full listing of individual contributors.
 * 
 * All Waarp Project is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * 
 * Waarp is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Waarp . If not, see
 * <http://www.gnu.org/licenses/>.
 */
package org.waarp.ftp.exec.control;

import org.jboss.netty.handler.traffic.GlobalTrafficShapingHandler;
import org.waarp.common.cpu.WaarpConstraintLimitHandler;
import org.waarp.ftp.exec.config.FileBasedConfiguration;

/**
 * Constraint Limit (CPU and connection - network and local -) handler.
 * 
 * @author Frederic Bregier
 * 
 */
public class FtpConstraintLimitHandler extends WaarpConstraintLimitHandler {

	/**
	 * @param useJdkCpuLimit
	 *            True to use JDK Cpu native or False for JavaSysMon
	 * @param lowcpuLimit
	 *            for proactive cpu limitation (throttling bandwidth) (0<= x < 1 & highcpulimit)
	 * @param highcpuLimit
	 *            for proactive cpu limitation (throttling bandwidth) (0<= x <= 1) 0 meaning no
	 *            throttle activated
	 * @param percentageDecrease
	 *            for proactive cpu limitation, throttling bandwidth reduction (0 < x < 1) as 0.25
	 *            for 25% of reduction
	 * @param handler
	 *            the GlobalTrafficShapingHandler associated (null to have no proactive cpu
	 *            limitation)
	 * @param delay
	 *            the delay between 2 tests for proactive cpu limitation
	 * @param limitLowBandwidth
	 *            the minimal bandwidth (read or write) to apply when decreasing bandwidth (low
	 *            limit = 4096)
	 */
	public FtpConstraintLimitHandler(long timeoutcon,
			boolean useJdkCpuLimit, double lowcpuLimit, double highcpuLimit,
			double percentageDecrease, GlobalTrafficShapingHandler handler,
			long delay, long limitLowBandwidth) {
		super(1000, timeoutcon,
				useJdkCpuLimit, lowcpuLimit,
				highcpuLimit, percentageDecrease, handler, delay,
				limitLowBandwidth);
	}

	/**
	 * @param useCpuLimit
	 *            True to enable cpuLimit on connection check
	 * @param useJdKCpuLimit
	 *            True to use JDK Cpu native or False for JavaSysMon
	 * @param cpulimit
	 *            high cpu limit (0<= x < 1) to refuse new connections
	 * @param channellimit
	 *            number of connection limit (0<= x)
	 */
	public FtpConstraintLimitHandler(long timeoutcon, boolean useCpuLimit,
			boolean useJdKCpuLimit, double cpulimit, int channellimit) {
		super(1000, timeoutcon, useCpuLimit, useJdKCpuLimit, cpulimit, channellimit);
	}

	/**
	 * @param useCpuLimit
	 *            True to enable cpuLimit on connection check
	 * @param useJdKCpuLimit
	 *            True to use JDK Cpu native or False for JavaSysMon
	 * @param cpulimit
	 *            high cpu limit (0<= x < 1) to refuse new connections
	 * @param channellimit
	 *            number of connection limit (0<= x)
	 * @param lowcpuLimit
	 *            for proactive cpu limitation (throttling bandwidth) (0<= x < 1 & highcpulimit)
	 * @param highcpuLimit
	 *            for proactive cpu limitation (throttling bandwidth) (0<= x <= 1) 0 meaning no
	 *            throttle activated
	 * @param percentageDecrease
	 *            for proactive cpu limitation, throttling bandwidth reduction (0 < x < 1) as 0.25
	 *            for 25% of reduction
	 * @param handler
	 *            the GlobalTrafficShapingHandler associated (null to have no proactive cpu
	 *            limitation)
	 * @param delay
	 *            the delay between 2 tests for proactive cpu limitation
	 * @param limitLowBandwidth
	 *            the minimal bandwidth (read or write) to apply when decreasing bandwidth (low
	 *            limit = 4096)
	 */
	public FtpConstraintLimitHandler(long timeoutcon,
			boolean useCpuLimit, boolean useJdKCpuLimit, double cpulimit,
			int channellimit, double lowcpuLimit, double highcpuLimit,
			double percentageDecrease, GlobalTrafficShapingHandler handler,
			long delay, long limitLowBandwidth) {
		super(1000, timeoutcon,
				useCpuLimit, useJdKCpuLimit,
				cpulimit, channellimit, lowcpuLimit, highcpuLimit,
				percentageDecrease, handler, delay, limitLowBandwidth);
	}

	/*
	 * (non-Javadoc)
	 * @see org.waarp.common.cpu.WaarpConstraintLimitHandler#getNumberLocalChannel()
	 */
	@Override
	protected int getNumberLocalChannel() {
		return FileBasedConfiguration.fileBasedConfiguration.getFtpInternalConfiguration()
				.getNumberSessions();
	}

	/*
	 * (non-Javadoc)
	 * @see org.waarp.common.cpu.WaarpConstraintLimitHandler#getReadLimit()
	 */
	@Override
	protected long getReadLimit() {
		return FileBasedConfiguration.fileBasedConfiguration.getServerGlobalReadLimit();
	}

	/*
	 * (non-Javadoc)
	 * @see org.waarp.common.cpu.WaarpConstraintLimitHandler#getWriteLimit()
	 */
	@Override
	protected long getWriteLimit() {
		return FileBasedConfiguration.fileBasedConfiguration.getServerGlobalWriteLimit();
	}

}
