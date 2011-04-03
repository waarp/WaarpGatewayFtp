/**
   This file is part of GoldenGate Project (named also GoldenGate or GG).

   Copyright 2009, Frederic Bregier, and individual contributors by the @author
   tags. See the COPYRIGHT.txt in the distribution for a full listing of
   individual contributors.

   All GoldenGate Project is free software: you can redistribute it and/or 
   modify it under the terms of the GNU General Public License as published 
   by the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   GoldenGate is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with GoldenGate .  If not, see <http://www.gnu.org/licenses/>.
 */
package goldengate.ftp.exec.snmp;

import goldengate.ftp.exec.config.FileBasedConfiguration;
import goldengate.snmp.interf.GgTimeTicks;

/**
 * Ftp Exec TimeTicks SNMP implementation
 * 
 * @author Frederic Bregier
 *
 */
public class FtpTimeTicks extends GgTimeTicks {
    /**
     * 
     */
    private static final long serialVersionUID = -4537649552332028472L;
    private int type = 1;
    private int entry = 0;
    
    public FtpTimeTicks(int type, int entry) {
        this.type = type;
        this.entry = entry;
        setInternalValue();
    }
    public FtpTimeTicks(int type, int entry, long value) {
        this.type = type;
        this.entry = entry;
        setInternalValue(value);
    }
    /* (non-Javadoc)
     * @see goldengate.snmp.interf.GgGauge32#setInternalValue()
     */
    @Override
    protected void setInternalValue() {
        FileBasedConfiguration.fileBasedConfiguration.monitoring.run(type, entry);
    }

    /* (non-Javadoc)
     * @see goldengate.snmp.interf.GgGauge32#setInternalValue(long)
     */
    @Override
    protected void setInternalValue(long value) {
        setValue(value);
    }
}
