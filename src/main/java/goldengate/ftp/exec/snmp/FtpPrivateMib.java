/**
 * This file is part of GoldenGate Project (named also GoldenGate or GG).
 * 
 * Copyright 2009, Frederic Bregier, and individual contributors by the @author
 * tags. See the COPYRIGHT.txt in the distribution for a full listing of
 * individual contributors.
 * 
 * All GoldenGate Project is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * GoldenGate is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * GoldenGate . If not, see <http://www.gnu.org/licenses/>.
 */
package goldengate.ftp.exec.snmp;

import org.snmp4j.agent.DuplicateRegistrationException;
import org.snmp4j.agent.MOScope;
import org.snmp4j.agent.MOServer;
import org.snmp4j.agent.mo.MOAccessImpl;
import org.snmp4j.agent.mo.snmp.SNMPv2MIB;
import org.snmp4j.agent.mo.snmp.SysUpTime;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.SMIConstants;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.VariableBinding;

import goldengate.common.command.ReplyCode;
import goldengate.common.logging.GgInternalLogger;
import goldengate.common.logging.GgInternalLoggerFactory;
import goldengate.ftp.exec.config.FileBasedConfiguration;
import goldengate.ftp.exec.database.data.DbTransferLog;
import goldengate.ftp.exec.utils.Version;
import goldengate.snmp.GgSnmpAgent;
import goldengate.snmp.interf.GgInterfaceMib;
import goldengate.snmp.utils.GgEntry;
import goldengate.snmp.utils.GgMORow;
import goldengate.snmp.utils.GgMOScalar;
import goldengate.snmp.utils.GgUptime;
import goldengate.snmp.utils.MemoryGauge32;
import goldengate.snmp.utils.MemoryGauge32.MemoryType;

/**
 * FTP Private MIB implementation
 * 
 * @author Frederic Bregier
 * 
 */
public class FtpPrivateMib implements GgInterfaceMib {
    /**
     * Internal Logger
     */
    private static GgInternalLogger logger = GgInternalLoggerFactory
            .getLogger(FtpPrivateMib.class);

    public static final String SnmpName = "GoldenGate FTP Exec SNMP";

    public static final int SnmpPrivateId = 66666;

    public static final int SnmpFtpId = 21;

    public static final String SnmpDefaultAuthor = "Frederic Bregier";

    public static final String SnmpVersion = "GoldenGate Ftp Exec " +
            Version.ID;

    public static final String SnmpDefaultLocalization = "Paris, France";

    public static final int SnmpService = 72;

    /**
     * SnmpConstants.sysObjectID
     */
    public OID ggObjectId = null; // will be smiPrivateCode.typeGoldenGate

    /**
     * SnmpConstants.sysUpTime
     */
    public SysUpTime upTime = null;

    /**
     * need to add ".port" like "6666" Only in TCP (no UDP supported for
     * GoldenGate)
     * 
     * example: rootEnterpriseMib+"66666"+".1.1.4.";
     */
    public String applicationProtocolBase = null;

    /**
     * will be = new OID(applicationProtocolBase+port);
     */
    public OID applicationProtocol = null;

    /**
     * root OID in String
     */
    public String srootOIDGoldenGate;

    /**
     * root OID
     */
    public OID rootOIDGoldenGate;

    /**
     * Used in Notify
     */
    public OID rootOIDGoldenGateNotif;

    /**
     * Used in Notify Start or Shutdown
     */
    public OID rootOIDGoldenGateNotifStartOrShutdown;

    /**
     * Info static part
     */
    public OID rootOIDGoldenGateInfo;

    /**
     * Info Row access
     */
    public GgMORow rowInfo;

    /**
     * Global dynamic part
     */
    public OID rootOIDGoldenGateGlobal;

    /**
     * Global Row access
     */
    public GgMORow rowGlobal;

    /**
     * Uptime OID
     */
    public OID rootOIDGoldenGateGlobalUptime;

    /**
     * Corresponding UpTime in Mib
     */
    public GgMOScalar scalarUptime = null;

    /**
     * Detailed dynamic part
     */
    public OID rootOIDGoldenGateDetailed;

    /**
     * Detailed Row access
     */
    public GgMORow rowDetailed;

    /**
     * Error dynamic part
     */
    public OID rootOIDGoldenGateError;

    /**
     * Error Row access
     */
    public GgMORow rowError;

    /**
     * New SNMPV2 MIB
     */
    public SNMPv2MIB snmpv2;

    /**
     * Corresponding agent
     */
    public GgSnmpAgent agent;

    /**
     * 
     * @param port
     *            port used by FTP server
     */
    public FtpPrivateMib(int port) {
        srootOIDGoldenGate = rootEnterpriseMib.toString() + "." +
                SnmpPrivateId + "." + SnmpFtpId;
        applicationProtocolBase = srootOIDGoldenGate + ".1.1.4.";
        ggObjectId = new OID(srootOIDGoldenGate);
        applicationProtocol = new OID(applicationProtocolBase + port);
        rootOIDGoldenGate = new OID(srootOIDGoldenGate);
        rootOIDGoldenGateInfo = new OID(srootOIDGoldenGate + ".1");
        rootOIDGoldenGateGlobal = new OID(srootOIDGoldenGate + ".2");
        rootOIDGoldenGateGlobalUptime = new OID(
                rootOIDGoldenGateGlobal.toString() + "." +
                        goldenGateGlobalValuesIndex.applUptime.getOID() + ".0");
        rootOIDGoldenGateDetailed = new OID(srootOIDGoldenGate + ".3");
        rootOIDGoldenGateError = new OID(srootOIDGoldenGate + ".4");
        rootOIDGoldenGateNotif = new OID(srootOIDGoldenGate + ".5.1");
        rootOIDGoldenGateNotifStartOrShutdown = new OID(srootOIDGoldenGate +
                ".5.1.1.1");
    }

    /**
     * Unregister and Register again the SNMPv2MIB with System adapted to this
     * Mib
     * 
     * @throws DuplicateRegistrationException
     */
    protected void agentRegisterSystem() throws DuplicateRegistrationException {
        // Since BaseAgent registers some mibs by default we need to unregister
        // one before we register our own sysDescr. Normally you would
        // override that method and register the mibs that you need

        agent.unregisterManagedObject(agent.getSnmpv2MIB());

        // Register a system description, use one from you product environment
        // to test with
        snmpv2 = new SNMPv2MIB(new OctetString(SnmpName), ggObjectId,
                new Integer32(SnmpService));
        snmpv2.setContact(new OctetString(SnmpDefaultAuthor));
        snmpv2.setLocation(new OctetString(SnmpDefaultLocalization));
        snmpv2.setName(new OctetString(SnmpVersion));
        snmpv2.registerMOs(agent.getServer(), null);
        if (logger.isDebugEnabled()) {
            logger.debug("SNMPV2: " + snmpv2.getContact() + ":" +
                    snmpv2.getDescr() + ":" + snmpv2.getLocation() + ":" +
                    snmpv2.getName() + ":" + snmpv2.getObjectID() + ":" +
                    snmpv2.getServices() + ":" + snmpv2.getUpTime());
        }
        // Save UpTime reference since used everywhere
        upTime = snmpv2.getSysUpTime();
    }

    /**
     * Register this MIB
     * 
     * @throws DuplicateRegistrationException
     */
    protected void agentRegisterGoldenGateMib()
            throws DuplicateRegistrationException {
        logger.debug("registerGGMib");
        // register Static info
        rowInfo = new GgMORow(this, rootOIDGoldenGateInfo,
                goldenGateDefinition, MibLevel.staticInfo.ordinal());
        rowInfo.setValue(goldenGateDefinitionIndex.applName.ordinal(),
                "GoldenGate OpenR66");
        rowInfo.setValue(goldenGateDefinitionIndex.applServerName.ordinal(),
                FileBasedConfiguration.fileBasedConfiguration.HOST_ID);
        rowInfo.setValue(goldenGateDefinitionIndex.applVersion.ordinal(),
                Version.ID);
        rowInfo.setValue(goldenGateDefinitionIndex.applDescription.ordinal(),
                "GoldenGate OpenR66: File Transfer Monitor");
        rowInfo.setValue(goldenGateDefinitionIndex.applURL.ordinal(),
                "http://openr66.free.fr");
        rowInfo.setValue(
                goldenGateDefinitionIndex.applApplicationProtocol.ordinal(),
                applicationProtocol);

        rowInfo.registerMOs(agent.getServer(), null);
        // register General info
        rowGlobal = new GgMORow(this, rootOIDGoldenGateGlobal,
                goldenGateGlobalValues, MibLevel.globalInfo.ordinal());
        GgMOScalar memoryScalar = rowGlobal.row[goldenGateGlobalValuesIndex.memoryTotal
                .ordinal()];
        memoryScalar.setValue(new MemoryGauge32(MemoryType.TotalMemory));
        memoryScalar = rowGlobal.row[goldenGateGlobalValuesIndex.memoryFree
                .ordinal()];
        memoryScalar.setValue(new MemoryGauge32(MemoryType.FreeMemory));
        memoryScalar = rowGlobal.row[goldenGateGlobalValuesIndex.memoryUsed
                .ordinal()];
        memoryScalar.setValue(new MemoryGauge32(MemoryType.UsedMemory));
        rowGlobal.registerMOs(agent.getServer(), null);
        // setup UpTime to SysUpTime and change status
        scalarUptime = rowGlobal.row[goldenGateGlobalValuesIndex.applUptime
                .ordinal()];
        scalarUptime.setValue(new GgUptime(upTime));
        changeStatus(OperStatus.restarting);
        changeStatus(OperStatus.up);
        // register Detailed info
        rowDetailed = new GgMORow(this, rootOIDGoldenGateDetailed,
                goldenGateDetailedValues, MibLevel.detailedInfo.ordinal());
        rowDetailed.registerMOs(agent.getServer(), null);
        // register Error info
        rowError = new GgMORow(this, rootOIDGoldenGateError,
                goldenGateErrorValues, MibLevel.errorInfo.ordinal());
        rowError.registerMOs(agent.getServer(), null);
    }

    /**
     * Unregister this MIB
     */
    protected void agentUnregisterMibs() {
        logger.debug("UnRegisterGoldenGate");
        rowInfo.unregisterMOs(agent.getServer(), agent.getDefaultContext());
        rowGlobal.unregisterMOs(agent.getServer(), agent.getDefaultContext());
        rowDetailed.unregisterMOs(agent.getServer(), agent.getDefaultContext());
        rowError.unregisterMOs(agent.getServer(), agent.getDefaultContext());
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.snmp4j.agent.MOGroup#registerMOs(org.snmp4j.agent.MOServer,
     * org.snmp4j.smi.OctetString)
     */
    @Override
    public void registerMOs(MOServer arg0, OctetString arg1)
            throws DuplicateRegistrationException {
        agentRegisterSystem();
        agentRegisterGoldenGateMib();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.snmp4j.agent.MOGroup#unregisterMOs(org.snmp4j.agent.MOServer,
     * org.snmp4j.smi.OctetString)
     */
    @Override
    public void unregisterMOs(MOServer arg0, OctetString arg1) {
        agentUnregisterMibs();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * goldengate.snmp.interf.GgInterfaceMib#setAgent(goldengate.snmp.GgSnmpAgent
     * )
     */
    @Override
    public void setAgent(GgSnmpAgent agent) {
        this.agent = agent;
    }

    /*
     * (non-Javadoc)
     * 
     * @see goldengate.snmp.interf.GgInterfaceMib#getBaseOidStartOrShutdown()
     */
    @Override
    public OID getBaseOidStartOrShutdown() {
        return rootOIDGoldenGateNotifStartOrShutdown;
    }

    /*
     * (non-Javadoc)
     * 
     * @see goldengate.snmp.interf.GgInterfaceMib#getSNMPv2MIB()
     */
    @Override
    public SNMPv2MIB getSNMPv2MIB() {
        return snmpv2;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * goldengate.snmp.interf.GgInterfaceMib#updateServices(goldengate.snmp.
     * utils.GgMOScalar)
     */
    @Override
    public void updateServices(GgMOScalar scalar) {
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * goldengate.snmp.interf.GgInterfaceMib#updateServices(org.snmp4j.agent
     * .MOScope)
     */
    @Override
    public void updateServices(MOScope range) {
    }

    /**
     * Change the status and the LastChange Timeticks
     * 
     * @param status
     */
    public void changeStatus(OperStatus status) {
        GgMOScalar statusScalar = rowGlobal.row[goldenGateGlobalValuesIndex.applOperStatus
                .ordinal()];
        Integer32 var = (Integer32) statusScalar.getValue();
        if (var.getValue() != status.status) {
            var.setValue(status.status);
            GgMOScalar lastTimeScalar = rowGlobal.row[goldenGateGlobalValuesIndex.applLastChange
                    .ordinal()];
            TimeTicks time = (TimeTicks) lastTimeScalar.getValue();
            time.setValue(upTime.get().getValue());
        }
    }

    /**
     * Send a notification (trap or inform) for Shutdown event
     * 
     * @param message
     * @param message2
     */
    public void notifyStartStop(String message, String message2) {
        if (!TrapLevel.StartStop.isLevelValid(agent.trapLevel)) return;
        notify(NotificationElements.TrapShutdown, message, message2);
    }

    /**
     * Send a notification (trap or inform) for Error event
     * 
     * @param message
     * @param message2
     */
    public void notifyError(String message, String message2) {
        if (!TrapLevel.Alert.isLevelValid(agent.trapLevel)) return;
        notify(NotificationElements.TrapError, message, message2);
    }

    /**
     * Send a notification (trap or inform) for Server Overloaded event
     * 
     * @param message
     * @param message2
     */
    public void notifyOverloaded(String message, String message2) {
        if (!TrapLevel.Warning.isLevelValid(agent.trapLevel)) return;
        notify(NotificationElements.TrapOverloaded, message, message2);
    }

    /**
     * Send a notification (trap or inform) for Warning event
     * 
     * @param message
     * @param message2
     */
    public void notifyWarning(String message, String message2) {
        if (!TrapLevel.Warning.isLevelValid(agent.trapLevel)) return;
        notify(NotificationElements.TrapWarning, message, message2);
    }

    /**
     * Send a notification (trap or inform) for Warning/Error event on a single
     * Transfer Task
     * 
     * @param message
     * @param runner
     */
    public void notifyInfoTask(String message, DbTransferLog runner) {
        if (!TrapLevel.All.isLevelValid(agent.trapLevel)) return;
        if (logger.isDebugEnabled())
            logger.debug("Notify: " + NotificationElements.InfoTask + ":" +
                    message + ":" + runner);
        long delay = (runner.getStart().getTime() - agent.getUptimeSystemTime()) / 10;
        if (delay < 0) delay = 0;
        agent.getNotificationOriginator().notify(
                new OctetString("public"),
                NotificationElements.InfoTask.getOID(rootOIDGoldenGateNotif),
                new VariableBinding[] {
                        new VariableBinding(NotificationElements.InfoTask
                                .getOID(rootOIDGoldenGateNotif, 1),
                                new OctetString(NotificationElements.InfoTask
                                        .name())),
                        new VariableBinding(NotificationElements.InfoTask
                                .getOID(rootOIDGoldenGateNotif, 1),
                                new OctetString(message)),
                        // Start of Task
                        new VariableBinding(
                                NotificationElements.InfoTask
                                        .getOID(rootOIDGoldenGateNotif,
                                                NotificationTasks.filenameInfo
                                                        .getOID()),
                                new OctetString(runner.getFilename())),
                        new VariableBinding(NotificationElements.InfoTask
                                .getOID(rootOIDGoldenGateNotif,
                                        NotificationTasks.modeTransInfo
                                                .getOID()), 
                                new OctetString(runner.getMode())),
                        new VariableBinding(NotificationElements.InfoTask
                                .getOID(rootOIDGoldenGateNotif,
                                        NotificationTasks.startTransInfo
                                                .getOID()),
                                new TimeTicks(delay)),
                        new VariableBinding(NotificationElements.InfoTask
                                .getOID(rootOIDGoldenGateNotif,
                                        NotificationTasks.infoStatusInfo
                                                .getOID()), new OctetString(
                                runner.getErrorInfo().getMesg())),
                        new VariableBinding(NotificationElements.InfoTask
                                .getOID(rootOIDGoldenGateNotif,
                                        NotificationTasks.userIdInfo
                                                .getOID()), new OctetString(
                                runner.getUser())),
                        new VariableBinding(NotificationElements.InfoTask
                                .getOID(rootOIDGoldenGateNotif,
                                        NotificationTasks.accountId
                                                .getOID()), new OctetString(
                                runner.getAccount())),
                        new VariableBinding(NotificationElements.InfoTask
                                .getOID(rootOIDGoldenGateNotif,
                                        NotificationTasks.specialIdInfo
                                                .getOID()), new OctetString("" +
                                runner.getSpecialId())),
                        // End of Task
                        new VariableBinding(SnmpConstants.sysDescr, snmpv2
                                .getDescr()),
                        new VariableBinding(SnmpConstants.sysObjectID, snmpv2
                                .getObjectID()),
                        new VariableBinding(SnmpConstants.sysContact, snmpv2
                                .getContact()),
                        new VariableBinding(SnmpConstants.sysName, snmpv2
                                .getName()),
                        new VariableBinding(SnmpConstants.sysLocation, snmpv2
                                .getLocation()) });
    }

    /**
     * Trap/Notification
     * 
     * @param element
     * @param message
     * @param message2
     */
    private void notify(NotificationElements element, String message,
            String message2) {
        if (logger.isDebugEnabled())
            logger.debug("Notify: " + element + ":" + message + ":" + message2);
        agent.getNotificationOriginator().notify(
                new OctetString("public"),
                element.getOID(rootOIDGoldenGateNotif),
                new VariableBinding[] {
                        new VariableBinding(element.getOID(
                                rootOIDGoldenGateNotif, 1), new OctetString(
                                element.name())),
                        new VariableBinding(element.getOID(
                                rootOIDGoldenGateNotif, 1), new OctetString(
                                message)),
                        new VariableBinding(element.getOID(
                                rootOIDGoldenGateNotif, 1), new OctetString(
                                message2)),
                        new VariableBinding(SnmpConstants.sysDescr, snmpv2
                                .getDescr()),
                        new VariableBinding(SnmpConstants.sysObjectID, snmpv2
                                .getObjectID()),
                        new VariableBinding(SnmpConstants.sysContact, snmpv2
                                .getContact()),
                        new VariableBinding(SnmpConstants.sysName, snmpv2
                                .getName()),
                        new VariableBinding(SnmpConstants.sysLocation, snmpv2
                                .getLocation()) });
    }

    /**
     * MIB entry levels
     * 
     * @author Frederic Bregier
     * 
     */
    public static enum MibLevel {
        staticInfo, globalInfo, detailedInfo, errorInfo, trapInfo
    }

    // From now the MIB definition
    /**
     * Notification Elements
     * 
     * @author Frederic Bregier
     * 
     */
    public static enum NotificationElements {
        TrapShutdown(1),
        TrapError(2),
        TrapWarning(3),
        TrapOverloaded(4),
        InfoTask(5);

        public int[] oid;

        private NotificationElements(int oid) {
            this.oid = new int[] {
                oid };
        }

        public OID getOID(OID oidBase) {
            return new OID(oidBase.getValue(), this.oid);
        }

        public OID getOID(OID oidBase, int rank) {
            int[] ids = new int[] {
                    this.oid[0], rank };
            return new OID(oidBase.getValue(), ids);
        }
    }

    /**
     * Notification for a task trap
     * 
     * @author Frederic Bregier
     * 
     */
    public static enum NotificationTasks {
        filenameInfo, modeTransInfo, startTransInfo, infoStatusInfo, userIdInfo, 
        accountId, specialIdInfo;

        public int getOID() {
            return this.ordinal() + 1;
        }
    }

    /**
     * Definition part
     * 
     * @author Frederic Bregier
     * 
     */
    public static enum goldenGateDefinitionIndex {
        applName,
        applServerName,
        applVersion,
        applDescription,
        applURL,
        applApplicationProtocol;

        public int getOID() {
            return this.ordinal() + 1;
        }
    }

    /**
     * Definition part
     */
    public static GgEntry[] goldenGateDefinition = {
            // applName
            new GgEntry(SMIConstants.SYNTAX_OCTET_STRING,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // applServerName
            new GgEntry(SMIConstants.SYNTAX_OCTET_STRING,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // applVersion
            new GgEntry(SMIConstants.SYNTAX_OCTET_STRING,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // applDescription
            new GgEntry(SMIConstants.SYNTAX_OCTET_STRING,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // applURL
            new GgEntry(SMIConstants.SYNTAX_OCTET_STRING,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // applApplicationProtocol
            new GgEntry(SMIConstants.SYNTAX_OBJECT_IDENTIFIER,
                    MOAccessImpl.ACCESS_READ_ONLY) };

    /**
     * Global part
     * 
     * @author Frederic Bregier
     * 
     */
    public static enum goldenGateGlobalValuesIndex {
        applUptime,
        applOperStatus,
        applLastChange,
        applInboundAssociations,
        applOutboundAssociations,
        applAccumInboundAssociations,
        applAccumOutboundAssociations,
        applLastInboundActivity,
        applLastOutboundActivity,
        applRejectedInboundAssociations,
        applFailedOutboundAssociations,
        applInboundBandwidthKBS,
        applOutboundBandwidthKBS,
        nbInfoUnknown,
        nbInfoNotUpdated,
        nbInfoInterrupted,
        nbInfoToSubmit,
        nbInfoError,
        nbInfoRunning,
        nbInfoDone,
        nbAllTransfer,
        memoryTotal,
        memoryFree,
        memoryUsed,
        nbThreads,
        nbNetworkConnection;

        public int getOID() {
            return this.ordinal() + 1;
        }
    }

    /**
     * Global part
     */
    public static GgEntry[] goldenGateGlobalValues = {
            // applUptime
            new GgEntry(SMIConstants.SYNTAX_TIMETICKS,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // applOperStatus
            new GgEntry(SMIConstants.SYNTAX_INTEGER,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // applLastChange
            new GgEntry(SMIConstants.SYNTAX_TIMETICKS,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // applInboundAssociations
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // applOutboundAssociations
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // applAccumInboundAssociations
            new GgEntry(SMIConstants.SYNTAX_COUNTER32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // applAccumOutboundAssociations
            new GgEntry(SMIConstants.SYNTAX_COUNTER32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // applLastInboundActivity
            new GgEntry(SMIConstants.SYNTAX_TIMETICKS,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // applLastOutboundActivity
            new GgEntry(SMIConstants.SYNTAX_TIMETICKS,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // applRejectedInboundAssociations
            new GgEntry(SMIConstants.SYNTAX_COUNTER32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // applFailedOutboundAssociations
            new GgEntry(SMIConstants.SYNTAX_COUNTER32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // Bandwidth
            // applInboundBandwidthKBS
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // applOutboundBandwidthKBS
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // Overall status including past, future and current transfers
            // nbInfoUnknown
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // nbInfoNotUpdated
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // nbInfoInterrupted
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // nbInfoToSubmit
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // nbInfoError
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // nbInfoRunning
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // nbInfoDone
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // Current situation of all transfers, running or not
            // nbAllTransfer
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // memoryTotal
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // memoryFree
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // memoryUsed
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // nbThreads
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
            // nbNetworkConnection
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY) };

    /**
     * Detailed part
     * 
     * @author Frederic Bregier
     * 
     */
    public static enum goldenGateDetailedValuesIndex {
        reply_000(ReplyCode.REPLY_000_SPECIAL_NOSTATUS),
        reply_110(ReplyCode.REPLY_110_RESTART_MARKER_REPLY),
        reply_120(ReplyCode.REPLY_120_SERVICE_READY_IN_NNN_MINUTES),
        reply_125(ReplyCode.REPLY_125_DATA_CONNECTION_ALREADY_OPEN),
        reply_150(ReplyCode.REPLY_150_FILE_STATUS_OKAY),
        reply_200(ReplyCode.REPLY_200_COMMAND_OKAY),
        reply_202(ReplyCode.REPLY_202_COMMAND_NOT_IMPLEMENTED),
        reply_211(ReplyCode.REPLY_211_SYSTEM_STATUS_REPLY),
        reply_212(ReplyCode.REPLY_212_DIRECTORY_STATUS),
        reply_213(ReplyCode.REPLY_213_FILE_STATUS),
        reply_214(ReplyCode.REPLY_214_HELP_MESSAGE),
        reply_215(ReplyCode.REPLY_215_NAME_SYSTEM_TYPE),
        reply_220(ReplyCode.REPLY_220_SERVICE_READY),
        reply_221(ReplyCode.REPLY_221_CLOSING_CONTROL_CONNECTION),
        reply_225(ReplyCode.REPLY_225_DATA_CONNECTION_OPEN_NO_TRANSFER_IN_PROGRESS),
        reply_226(ReplyCode.REPLY_226_CLOSING_DATA_CONNECTION),
        reply_227(ReplyCode.REPLY_227_ENTERING_PASSIVE_MODE),
        reply_229(ReplyCode.REPLY_229_ENTERING_PASSIVE_MODE),
        reply_230(ReplyCode.REPLY_230_USER_LOGGED_IN),
        reply_250(ReplyCode.REPLY_250_REQUESTED_FILE_ACTION_OKAY),
        reply_257(ReplyCode.REPLY_257_PATHNAME_CREATED),
        reply_331(ReplyCode.REPLY_331_USER_NAME_OKAY_NEED_PASSWORD),
        reply_332(ReplyCode.REPLY_332_NEED_ACCOUNT_FOR_LOGIN),
        reply_350(ReplyCode.REPLY_350_REQUESTED_FILE_ACTION_PENDING_FURTHER_INFORMATION);

        public ReplyCode code;
        
        private goldenGateDetailedValuesIndex(ReplyCode code) {
            this.code = code;
        }
        public int getOID() {
            return this.ordinal() + 1;
        }
    }

    /**
     * Detailed part
     */
    public static GgEntry[] goldenGateDetailedValues = {
            // reply_000,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_110,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_120,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_125,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_150,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_200,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_202,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_211,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_212,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_213,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_214,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_215,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_220,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_221,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_225,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_226,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_227,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_229,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_230,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_250,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_257,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_331,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_332,
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY),
            // reply_350;
        new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                MOAccessImpl.ACCESS_READ_ONLY) };

    /**
     * Error part
     * 
     * @author Frederic Bregier
     * 
     */
    public static enum goldenGateErrorValuesIndex {
        reply_421(ReplyCode.REPLY_421_SERVICE_NOT_AVAILABLE_CLOSING_CONTROL_CONNECTION),
        reply_425(ReplyCode.REPLY_425_CANT_OPEN_DATA_CONNECTION),
        reply_426(ReplyCode.REPLY_426_CONNECTION_CLOSED_TRANSFER_ABORTED),
        reply_450(ReplyCode.REPLY_450_REQUESTED_FILE_ACTION_NOT_TAKEN),
        reply_451(ReplyCode.REPLY_451_REQUESTED_ACTION_ABORTED),
        reply_452(ReplyCode.REPLY_452_REQUESTED_ACTION_NOT_TAKEN),
        reply_500(ReplyCode.REPLY_500_SYNTAX_ERROR_COMMAND_UNRECOGNIZED),
        reply_501(ReplyCode.REPLY_501_SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS),
        reply_502(ReplyCode.REPLY_502_COMMAND_NOT_IMPLEMENTED),
        reply_503(ReplyCode.REPLY_503_BAD_SEQUENCE_OF_COMMANDS),
        reply_504(ReplyCode.REPLY_504_COMMAND_NOT_IMPLEMENTED_FOR_THAT_PARAMETER),
        reply_522(ReplyCode.REPLY_522_EXTENDED_PORT_FAILURE_UNKNOWN_NETWORK_PROTOCOL),
        reply_530(ReplyCode.REPLY_530_NOT_LOGGED_IN),
        reply_532(ReplyCode.REPLY_532_NEED_ACCOUNT_FOR_STORING_FILES),
        reply_550(ReplyCode.REPLY_550_REQUESTED_ACTION_NOT_TAKEN),
        reply_551(ReplyCode.REPLY_551_REQUESTED_ACTION_ABORTED_PAGE_TYPE_UNKNOWN),
        reply_552(ReplyCode.REPLY_552_REQUESTED_FILE_ACTION_ABORTED_EXCEEDED_STORAGE),
        reply_553(ReplyCode.REPLY_553_REQUESTED_ACTION_NOT_TAKEN_FILE_NAME_NOT_ALLOWED);

        public ReplyCode code;
        
        private goldenGateErrorValuesIndex(ReplyCode code) {
            this.code = code;
        }

        public int getOID() {
            return this.ordinal() + 1;
        }
    }

    /**
     * Error part
     */
    public static GgEntry[] goldenGateErrorValues = {
        // reply_421,
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
        // reply_425,
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
        // reply_426,
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
        // reply_450,
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
        // reply_451,
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
        // reply_452,
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
        // reply_500,
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
        // reply_501,
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
        // reply_502,
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
        // reply_503,
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
        // reply_504,
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
        // reply_522,
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
        // reply_530,
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
        // reply_532,
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
        // reply_550,
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
        // reply_551,
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
        // reply_552,
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY),
        // reply_553;
            new GgEntry(SMIConstants.SYNTAX_GAUGE32,
                    MOAccessImpl.ACCESS_READ_ONLY) };

    /**
     * Oper Status (as defined in Net Application SNMP)
     * 
     * @author Frederic Bregier
     * 
     */
    public static enum OperStatus {
        up(1), down(2), halted(3), congested(4), restarting(5), quiescing(6);

        public int status;

        private OperStatus(int status) {
            this.status = status;
        }
    }

}
