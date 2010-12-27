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
package goldengate.ftp.exec.database.data;

import goldengate.common.command.ReplyCode;
import goldengate.common.database.DbPreparedStatement;
import goldengate.common.database.DbSession;
import goldengate.common.database.data.AbstractDbData;
import goldengate.common.database.data.DbValue;
import goldengate.common.database.exception.GoldenGateDatabaseException;
import goldengate.common.database.exception.GoldenGateDatabaseNoConnectionError;
import goldengate.common.database.exception.GoldenGateDatabaseNoDataException;
import goldengate.common.database.exception.GoldenGateDatabaseSqlError;
import goldengate.common.exception.InvalidArgumentException;
import goldengate.common.logging.GgInternalLogger;
import goldengate.common.logging.GgInternalLoggerFactory;
import goldengate.common.xml.XmlDecl;
import goldengate.common.xml.XmlType;
import goldengate.common.xml.XmlUtil;
import goldengate.common.xml.XmlValue;
import goldengate.ftp.exec.config.FileBasedConfiguration;
import goldengate.ftp.exec.database.DbConstant;
import goldengate.ftp.exec.database.model.DbModelFactory;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.TreeSet;

import org.dom4j.Document;

/**
 * Transfer Log for FtpExec
 *
 * @author Frederic Bregier
 *
 */
public class DbTransferLog extends AbstractDbData {
    /**
     * Internal Logger
     */
    private static final GgInternalLogger logger = GgInternalLoggerFactory
            .getLogger(DbTransferLog.class);

    public static enum Columns {
        FILENAME,
        MODETRANS,
        STARTTRANS,
        STOPTRANS,
        TRANSINFO,
        INFOSTATUS,
        UPDATEDINFO,
        USERID,
        ACCOUNTID,
        HOSTID,
        SPECIALID;
    }

    public static int[] dbTypes = {
            Types.VARCHAR,
            Types.VARCHAR, 
            Types.TIMESTAMP, Types.TIMESTAMP, 
            Types.LONGVARCHAR, Types.INTEGER, Types.INTEGER,
            Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BIGINT };

    public static String table = " TRANSFLOG ";

    public static String fieldseq = "TRANSSEQ";

    public static Columns [] indexes = {
        Columns.STARTTRANS, Columns.UPDATEDINFO
    };

    public static final String XMLRUNNERS = "transferlogs";
    public static final String XMLRUNNER = "log";
    /**
     * GlobalStep Value
     */
    public static enum TASKSTEP {
        NOTASK, PRETASK, TRANSFERTASK, POSTTASK, ALLDONETASK, ERRORTASK;
    }

    // Values
    private String user;
    
    private String account; 
    
    private long specialId;
    
    private boolean isSender;

    private String filename;

    private String mode;

    private Timestamp start;

    private Timestamp stop;
    
    private String infotransf;
    
    private String hostid;

    /**
     * Info status error code
     */
    private ReplyCode infostatus = ReplyCode.REPLY_000_SPECIAL_NOSTATUS;

    /**
     * The global status for running
     */
    private int updatedInfo = UpdatedInfo.UNKNOWN.ordinal();

    private boolean isSaved = false;

    /**
     * Special For DbTransferLog
     */
    public static final int NBPRKEY = 4;
    // ALL TABLE SHOULD IMPLEMENT THIS
    private final DbValue primaryKey[] = {
            new DbValue(user, Columns.USERID.name()),
            new DbValue(account, Columns.ACCOUNTID.name()),
            new DbValue(hostid, Columns.HOSTID.name()),
            new DbValue(specialId, Columns.SPECIALID.name()) };
    private final DbValue[] otherFields = {
            // FILENAME, MODETRANS,
            // STARTTRANS, STOPTRANS, TRANSINFO
            // INFOSTATUS, UPDATEDINFO
            new DbValue(filename, Columns.FILENAME.name()),
            new DbValue(mode, Columns.MODETRANS.name()),
            new DbValue(start, Columns.STARTTRANS.name()),
            new DbValue(stop, Columns.STOPTRANS.name()),
            new DbValue(infotransf, Columns.TRANSINFO.name()),
            new DbValue(infostatus.getCode(), Columns.INFOSTATUS.name()),
            new DbValue(updatedInfo, Columns.UPDATEDINFO.name()) };

    private final DbValue[] allFields = {
            otherFields[0], otherFields[1], otherFields[2], otherFields[3],
            otherFields[4], otherFields[5], otherFields[6],  
            primaryKey[0], primaryKey[1], primaryKey[2], primaryKey[3] };

    public static final String selectAllFields = Columns.FILENAME.name() + "," +
            Columns.MODETRANS.name() + "," +
            Columns.STARTTRANS.name() + "," + Columns.STOPTRANS.name() + "," +
            Columns.TRANSINFO.name() + "," +
            Columns.INFOSTATUS.name() + "," + Columns.UPDATEDINFO.name() + "," +
            Columns.USERID.name() + "," + Columns.ACCOUNTID.name() + "," +
            Columns.HOSTID.name() + "," +Columns.SPECIALID.name();

    private static final String updateAllFields = 
            Columns.FILENAME.name() + "=?," + 
            Columns.MODETRANS.name() + "=?," +
            Columns.STARTTRANS.name() + "=?," + Columns.STOPTRANS.name() + "=?," + 
            Columns.TRANSINFO.name() + "=?," +
            Columns.INFOSTATUS.name() + "=?," + Columns.UPDATEDINFO.name() + "=?";

    private static final String insertAllValues = " (?,?,?,?,?,?,?,?,?,?,?) ";

    private static final TreeSet<Long> clientNoDbSpecialId = new TreeSet<Long>();
    

    /**
     * Insert into database
     * @param dbSession
     * @param user
     * @param account
     * @param specialId
     * @param isSender
     * @param filename
     * @param mode
     * @param infostatus
     * @param info
     * @param updatedInfo
     * @throws GoldenGateDatabaseException 
     */
    public DbTransferLog(DbSession dbSession, String user, String account, 
            long specialId,
            boolean isSender, String filename, String mode,
            ReplyCode infostatus, String info,
            UpdatedInfo updatedInfo) throws GoldenGateDatabaseException {
        super(dbSession);
        this.user = user;
        this.account = account;
        this.specialId = specialId;
        this.isSender = isSender;
        this.filename = filename;
        this.mode = mode;
        start = new Timestamp(System.currentTimeMillis());
        this.infostatus = infostatus;
        this.infotransf = info;
        this.updatedInfo = updatedInfo.ordinal();
        this.hostid = FileBasedConfiguration.fileBasedConfiguration.HOST_ID;
        setToArray();
        isSaved = false;
        insert();
    }
    /**
     * Load from database
     * @param dbSession
     * @param user
     * @param account
     * @param specialId
     * @throws GoldenGateDatabaseException 
     */
    public DbTransferLog(DbSession dbSession, String user, String account, long specialId) 
    throws GoldenGateDatabaseException {
        super(dbSession);
        this.user = user;
        this.account = account;
        this.specialId = specialId;
        this.hostid = FileBasedConfiguration.fileBasedConfiguration.HOST_ID;
        select();
    }

    @Override
    protected void setToArray() {
        // FILENAME, MODETRANS,
        // STARTTRANS, STOPTRANS, TRANSINFO
        // INFOSTATUS, UPDATEDINFO
        // USERID, ACCOUNTID, SPECIALID
        allFields[Columns.FILENAME.ordinal()].setValue(filename);
        allFields[Columns.MODETRANS.ordinal()].setValue(mode);
        allFields[Columns.STARTTRANS.ordinal()].setValue(start);
        stop = new Timestamp(System.currentTimeMillis());
        allFields[Columns.STOPTRANS.ordinal()].setValue(stop);
        allFields[Columns.TRANSINFO.ordinal()].setValue(infotransf);
        allFields[Columns.INFOSTATUS.ordinal()].setValue(infostatus.getCode());
        allFields[Columns.UPDATEDINFO.ordinal()].setValue(updatedInfo);
        allFields[Columns.USERID.ordinal()].setValue(user);
        allFields[Columns.ACCOUNTID.ordinal()].setValue(account);
        allFields[Columns.HOSTID.ordinal()].setValue(hostid);
        allFields[Columns.SPECIALID.ordinal()].setValue(specialId);
    }

    @Override
    protected void setFromArray() throws GoldenGateDatabaseSqlError {
        filename = (String) allFields[Columns.FILENAME.ordinal()].getValue();
        mode = (String) allFields[Columns.MODETRANS.ordinal()].getValue();
        start = (Timestamp) allFields[Columns.STARTTRANS.ordinal()].getValue();
        stop = (Timestamp) allFields[Columns.STOPTRANS.ordinal()].getValue();
        try {
            infostatus = ReplyCode.getReplyCode(((Integer) allFields[Columns.INFOSTATUS
                                                              .ordinal()].getValue()));
        } catch (InvalidArgumentException e) {
            throw new GoldenGateDatabaseSqlError("Wrong Argument", e);
        }
        infotransf = (String) allFields[Columns.TRANSINFO.ordinal()]
                                  .getValue();
        updatedInfo = (Integer) allFields[Columns.UPDATEDINFO.ordinal()]
                .getValue();
        user = (String) allFields[Columns.USERID.ordinal()]
                .getValue();
        account = (String) allFields[Columns.ACCOUNTID.ordinal()]
                .getValue();
        hostid = (String) allFields[Columns.HOSTID.ordinal()]
                                     .getValue();
        specialId = (Long) allFields[Columns.SPECIALID.ordinal()].getValue();
    }
    /**
     *
     * @return The Where condition on Primary Key
     */
    private String getWherePrimaryKey() {
        return primaryKey[0].column + " = ? AND " +
            primaryKey[1].column + " = ? AND " +
            primaryKey[2].column + " = ? AND " +
            primaryKey[3].column + " = ? ";
    }
    /**
     * Set the primary Key as current value
     */
    private void setPrimaryKey() {
        primaryKey[0].setValue(user);
        primaryKey[1].setValue(account);
        primaryKey[2].setValue(hostid);
        primaryKey[3].setValue(specialId);
    }
    /**
     *
     * @return the condition to limit access to the row concerned by the Host
     */
    private static String getLimitWhereCondition() {
        return " "+Columns.HOSTID + " = '"+
            FileBasedConfiguration.fileBasedConfiguration.HOST_ID+"' ";
    }
    /**
     * Create a Special Id for NoDb client
     */
    private void createNoDbSpecialId() {
        synchronized (clientNoDbSpecialId) {
            // New SpecialId is not possible with No Database Model
            specialId = System.currentTimeMillis();
            Long newOne = specialId;
            while (clientNoDbSpecialId.contains(newOne)) {
                newOne = specialId++;
            }
            clientNoDbSpecialId.add(newOne);
        }
    }
    /**
     * Remove a Special Id for NoDb Client
     */
    private void removeNoDbSpecialId() {
        synchronized (clientNoDbSpecialId) {
            Long oldOne = specialId;
            clientNoDbSpecialId.remove(oldOne);
        }
    }
    /*
     * (non-Javadoc)
     *
     * @see openr66.databaseold.data.AbstractDbData#delete()
     */
    @Override
    public void delete() throws GoldenGateDatabaseException {
        if (dbSession == null) {
            removeNoDbSpecialId();
            return;
        }
        DbPreparedStatement preparedStatement = new DbPreparedStatement(
                dbSession);
        try {
            preparedStatement.createPrepareStatement("DELETE FROM " + table +
                    " WHERE " + getWherePrimaryKey());
            setPrimaryKey();
            setValues(preparedStatement, primaryKey);
            int count = preparedStatement.executeUpdate();
            if (count <= 0) {
                throw new GoldenGateDatabaseNoDataException("No row found");
            }
            isSaved = false;
        } finally {
            preparedStatement.realClose();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see openr66.databaseold.data.AbstractDbData#insert()
     */
    @Override
    public void insert() throws GoldenGateDatabaseException {
        if (isSaved) {
            return;
        }
        if (dbSession == null) {
            if (specialId == DbConstant.ILLEGALVALUE) {
                // New SpecialId is not possible with No Database Model
                createNoDbSpecialId();
            }
            isSaved = true;
            return;
        }
        // First need to find a new id if id is not ok
        if (specialId == DbConstant.ILLEGALVALUE) {
            specialId = DbModelFactory.dbModel.nextSequence(dbSession);
            logger.debug("Try Insert create a new Id from sequence: " +
                    specialId);
            setPrimaryKey();
        }
        setToArray();
        DbPreparedStatement preparedStatement = new DbPreparedStatement(
                dbSession);
        try {
            preparedStatement.createPrepareStatement("INSERT INTO " + table +
                    " (" + selectAllFields + ") VALUES " + insertAllValues);
            setValues(preparedStatement, allFields);
            int count = preparedStatement.executeUpdate();
            if (count <= 0) {
                throw new GoldenGateDatabaseNoDataException("No row found");
            }
            isSaved = true;
        } finally {
            preparedStatement.realClose();
        }
    }

    /**
     * As insert but with the ability to change the SpecialId
     *
     * @throws GoldenGateDatabaseException
     */
    public void create() throws GoldenGateDatabaseException {
        if (isSaved) {
            return;
        }
        if (dbSession == null) {
            if (specialId == DbConstant.ILLEGALVALUE) {
                // New SpecialId is not possible with No Database Model
                createNoDbSpecialId();
            }
            isSaved = true;
            return;
        }
        // First need to find a new id if id is not ok
        if (specialId == DbConstant.ILLEGALVALUE) {
            specialId = DbModelFactory.dbModel.nextSequence(dbSession);
            logger.info("Try Insert create a new Id from sequence: " +
                    specialId);
            setPrimaryKey();
        }
        setToArray();
        DbPreparedStatement preparedStatement = new DbPreparedStatement(
                dbSession);
        try {
            preparedStatement.createPrepareStatement("INSERT INTO " + table +
                    " (" + selectAllFields + ") VALUES " + insertAllValues);
            setValues(preparedStatement, allFields);
            try {
                int count = preparedStatement.executeUpdate();
                if (count <= 0) {
                    throw new GoldenGateDatabaseNoDataException("No row found");
                }
            } catch (GoldenGateDatabaseSqlError e) {
                logger.error("Problem while inserting", e);
                DbPreparedStatement find = new DbPreparedStatement(dbSession);
                try {
                    find.createPrepareStatement("SELECT MAX(" +
                            primaryKey[3].column + ") FROM " + table + " WHERE " +
                            primaryKey[0].column + " = ? AND " +
                            primaryKey[1].column + " = ? AND " +
                            primaryKey[2].column + " = ? AND " +
                            primaryKey[3].column + " != ? ");
                    setPrimaryKey();
                    setValues(find, primaryKey);
                    find.executeQuery();
                    if (find.getNext()) {
                        long result;
                        try {
                            result = find.getResultSet().getLong(1);
                        } catch (SQLException e1) {
                            throw new GoldenGateDatabaseSqlError(e1);
                        }
                        specialId = result + 1;
                        DbModelFactory.dbModel.resetSequence(dbSession, specialId + 1);
                        setToArray();
                        preparedStatement.close();
                        setValues(preparedStatement, allFields);
                        int count = preparedStatement.executeUpdate();
                        if (count <= 0) {
                            throw new GoldenGateDatabaseNoDataException("No row found");
                        }
                    } else {
                        throw new GoldenGateDatabaseNoDataException("No row found");
                    }
                } finally {
                    find.realClose();
                }
            }
            isSaved = true;
        } finally {
            preparedStatement.realClose();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see openr66.databaseold.data.AbstractDbData#exist()
     */
    @Override
    public boolean exist() throws GoldenGateDatabaseException {
        if (dbSession == null) {
            return false;
        }
        DbPreparedStatement preparedStatement = new DbPreparedStatement(
                dbSession);
        try {
            preparedStatement.createPrepareStatement("SELECT " +
                    primaryKey[3].column + " FROM " + table + " WHERE " +
                    getWherePrimaryKey());
            setPrimaryKey();
            setValues(preparedStatement, primaryKey);
            preparedStatement.executeQuery();
            return preparedStatement.getNext();
        } finally {
            preparedStatement.realClose();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see openr66.databaseold.data.AbstractDbData#select()
     */
    @Override
    public void select() throws GoldenGateDatabaseException {
        if (dbSession == null) {
            throw new GoldenGateDatabaseNoDataException("No row found");
        }
        DbPreparedStatement preparedStatement = new DbPreparedStatement(
                dbSession);
        try {
            preparedStatement.createPrepareStatement("SELECT " + selectAllFields +
                    " FROM " + table + " WHERE " +
                    getWherePrimaryKey());
            setPrimaryKey();
            setValues(preparedStatement, primaryKey);
            preparedStatement.executeQuery();
            if (preparedStatement.getNext()) {
                getValues(preparedStatement, allFields);
                setFromArray();
                isSaved = true;
            } else {
                throw new GoldenGateDatabaseNoDataException("No row found: " +
                        primaryKey[1].getValueAsString() + ":" +
                        primaryKey[2].getValueAsString() + ":" +
                        primaryKey[3].getValueAsString());
            }
        } finally {
            preparedStatement.realClose();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see openr66.databaseold.data.AbstractDbData#update()
     */
    @Override
    public void update() throws GoldenGateDatabaseException {
        if (isSaved) {
            return;
        }
        if (dbSession == null) {
            isSaved = true;
            return;
        }
        setToArray();
        DbPreparedStatement preparedStatement = new DbPreparedStatement(
                dbSession);
        try {
            preparedStatement.createPrepareStatement("UPDATE " + table +
                    " SET " + updateAllFields + " WHERE " +
                    getWherePrimaryKey());
            setValues(preparedStatement, allFields);
            int count = preparedStatement.executeUpdate();
            if (count <= 0) {
                throw new GoldenGateDatabaseNoDataException("No row found");
            }
            isSaved = true;
        } finally {
            preparedStatement.realClose();
        }
    }

    /**
     * Private constructor
     *
     * @param session
     */
    private DbTransferLog(DbSession dBsession) {
        super(dBsession);
    }

    /**
     * For instance when getting updated information
     *
     * @param preparedStatement
     * @return the next updated DbTaskRunner
     * @throws GoldenGateDatabaseNoConnectionError
     * @throws GoldenGateDatabaseSqlError
     */
    public static DbTransferLog getFromStatement(
            DbPreparedStatement preparedStatement)
            throws GoldenGateDatabaseNoConnectionError, GoldenGateDatabaseSqlError {
        DbTransferLog dbTaskRunner = new DbTransferLog(preparedStatement
                .getDbSession());
        dbTaskRunner.getValues(preparedStatement, dbTaskRunner.allFields);
        dbTaskRunner.setFromArray();
        dbTaskRunner.isSaved = true;
        return dbTaskRunner;
    }

    /**
     * @param session
     * @param status
     * @param limit limit the number of rows
     * @return the DbPreparedStatement for getting TransferLog according to status ordered by start
     * @throws GoldenGateDatabaseNoConnectionError
     * @throws GoldenGateDatabaseSqlError
     */
    public static DbPreparedStatement getStatusPrepareStament(
            DbSession session, ReplyCode status, int limit)
            throws GoldenGateDatabaseNoConnectionError, GoldenGateDatabaseSqlError {
        String request = "SELECT " + selectAllFields + " FROM " + table;
        if (status != null) {
            request += " WHERE " + Columns.INFOSTATUS.name() + " = " +
                    status.getCode() + " AND "+getLimitWhereCondition();
        } else {
            request += " WHERE "+getLimitWhereCondition();
        }
        request += " ORDER BY " + Columns.STARTTRANS.name() + " DESC ";
        if (limit > 0) {
            request = DbModelFactory.dbModel.limitRequest(selectAllFields, request, limit);
        }
        return new DbPreparedStatement(session, request);
    }
    /**
     *
     * @param session
     * @param start
     * @param stop
     * @return the DbPreparedStatement for getting Selected Object, whatever their status
     * @throws GoldenGateDatabaseNoConnectionError
     * @throws GoldenGateDatabaseSqlError
     */
    public static DbPreparedStatement getLogPrepareStament(DbSession session,
            Timestamp start, Timestamp stop)
            throws GoldenGateDatabaseNoConnectionError, GoldenGateDatabaseSqlError {
        DbPreparedStatement preparedStatement = new DbPreparedStatement(session);
        String request = "SELECT " + selectAllFields + " FROM " + table;
        if (start != null & stop != null) {
            request += " WHERE " + Columns.STARTTRANS.name() + " >= ? AND " +
                    Columns.STARTTRANS.name() + " <= ? AND "+getLimitWhereCondition()+
                    " ORDER BY " + Columns.SPECIALID.name() + " DESC ";
            preparedStatement.createPrepareStatement(request);
            try {
                preparedStatement.getPreparedStatement().setTimestamp(1, start);
                preparedStatement.getPreparedStatement().setTimestamp(2, stop);
            } catch (SQLException e) {
                preparedStatement.realClose();
                throw new GoldenGateDatabaseSqlError(e);
            }
        } else if (start != null) {
            request += " WHERE " + Columns.STARTTRANS.name() +
                    " >= ? AND "+getLimitWhereCondition()+
                    " ORDER BY " + Columns.SPECIALID.name() + " DESC ";
            preparedStatement.createPrepareStatement(request);
            try {
                preparedStatement.getPreparedStatement().setTimestamp(1, start);
            } catch (SQLException e) {
                preparedStatement.realClose();
                throw new GoldenGateDatabaseSqlError(e);
            }
        } else if (stop != null) {
            request += " WHERE " + Columns.STARTTRANS.name() +
                    " <= ? AND "+getLimitWhereCondition()+
                    " ORDER BY " + Columns.SPECIALID.name() + " DESC ";
            preparedStatement.createPrepareStatement(request);
            try {
                preparedStatement.getPreparedStatement().setTimestamp(1, stop);
            } catch (SQLException e) {
                preparedStatement.realClose();
                throw new GoldenGateDatabaseSqlError(e);
            }
        } else {
            request += " WHERE "+getLimitWhereCondition()+
                " ORDER BY " + Columns.SPECIALID.name() + " DESC ";
            preparedStatement.createPrepareStatement(request);
        }
        return preparedStatement;
    }
    /*
     * (non-Javadoc)
     *
     * @see openr66.databaseold.data.AbstractDbData#changeUpdatedInfo(UpdatedInfo)
     */
    @Override
    public void changeUpdatedInfo(UpdatedInfo info) {
        updatedInfo = info.ordinal();
        allFields[Columns.UPDATEDINFO.ordinal()].setValue(updatedInfo);
        isSaved = false;
    }
    /**
     * Set the ReplyCode for the UpdatedInfo
     * @param code
     */
    public void setReplyCodeExecutionStatus(ReplyCode code) {
        if (infostatus != code) {
            infostatus = code;
            allFields[Columns.INFOSTATUS.ordinal()].setValue(infostatus.getCode());
            isSaved = false;
        }
    }
    /**
     *
     * @return The current UpdatedInfo value
     */
    public UpdatedInfo getUpdatedInfo() {
        return UpdatedInfo.values()[updatedInfo];
    }
    /**
     *
     * @return the ReplyCode code associated with the Updated Info
     */
    public ReplyCode getErrorInfo() {
        return infostatus;
    }
    /**
     * @param filename
     *            the filename to set
     */
    public void setFilename(String filename) {
        if (!this.filename.equals(filename)) {
            this.filename = filename;
            allFields[Columns.FILENAME.ordinal()].setValue(this.filename);
            isSaved = false;
        }
    }
    /**
     * @return the isSender
     */
    public boolean isSender() {
        return isSender;
    }
    /**
     * @return the filename
     */
    public String getFilename() {
        return filename;
    }
    /**
     * @return the specialId
     */
    public long getSpecialId() {
        return specialId;
    }
    
    /**
     * @return the infotransf
     */
    public String getInfotransf() {
        return infotransf;
    }
    /**
     * @param infotransf the infotransf to set
     */
    public void setInfotransf(String infotransf) {
        this.infotransf = infotransf;
    }
    /**
     * @return the user
     */
    public String getUser() {
        return user;
    }
    /**
     * @return the account
     */
    public String getAccount() {
        return account;
    }
    /**
     * @param stop the stop to set
     */
    public void setStop(Timestamp stop) {
        this.stop = stop;
    }
    /**
     * @return the mode
     */
    public String getMode() {
        return mode;
    }
    /**
     * This method is to be called each time an operation is happening on Runner
     *
     * @throws GoldenGateDatabaseException
     */
    public void saveStatus() throws GoldenGateDatabaseException {
        update();
    }

    /**
     * Clear the runner
     */
    public void clear() {

    }
    @Override
    public String toString() {
        return "Transfer: on " +
                filename + " SpecialId: " +
                specialId+" Mode: "+mode + " isSender: " + isSender +
                " User: "+user+" Account: "+account+
                " Start: " + start + " Stop: " + stop +
                " Internal: " + UpdatedInfo.values()[updatedInfo].name()+
                ":"+infostatus.getMesg()+
                " TransferInfo: "+infotransf;
    }
    /**
     * @return the start
     */
    public Timestamp getStart() {
        return start;
    }

    /**
     * @return the stop
     */
    public Timestamp getStop() {
        return stop;
    }
    /*
        XXXIDXXX XXXUSERXXX XXXACCTXXX XXXFILEXXX XXXMODEXXX XXXSTATUSXXX XXXINFOXXX 
        XXXUPINFXXX XXXSTARTXXX XXXSTOPXXX
     */
    private static final String XML_IDX = "IDX";
    private static final String XML_USER = "USER";
    private static final String XML_ACCT = "ACCT";
    private static final String XML_FILE = "FILE";
    private static final String XML_MODE = "MODE";
    private static final String XML_STATUS = "STATUS";
    private static final String XML_INFO = "INFO";
    private static final String XML_UPDINFO = "UPDINFO";
    private static final String XML_START = "START";
    private static final String XML_STOP = "STOP";
    private static final String XML_ROOT = "LOGS";
    private static final String XML_ENTRY = "LOG";
    /**
     * Structure of the Configuration file
     *
     */
    private static final XmlDecl [] logDecls = {
        // identity
        new XmlDecl(XmlType.STRING, XML_IDX), 
        new XmlDecl(XmlType.STRING, XML_USER),
        new XmlDecl(XmlType.STRING, XML_ACCT),
        new XmlDecl(XmlType.STRING, XML_FILE),
        new XmlDecl(XmlType.STRING, XML_MODE),
        new XmlDecl(XmlType.STRING, XML_STATUS),
        new XmlDecl(XmlType.STRING, XML_INFO),
        new XmlDecl(XmlType.STRING, XML_UPDINFO),
        new XmlDecl(XmlType.STRING, XML_START),
        new XmlDecl(XmlType.STRING, XML_STOP),
    };
    /**
     * Global Structure for Server Configuration
     */
    private static final XmlDecl[] logsElements = {
        new XmlDecl(XML_ENTRY, XmlType.XVAL, XML_ROOT+"/"+XML_ENTRY, 
                logDecls, true)
    };
    /**
     * 
     * @return the associated XmlValue
     */
    private XmlValue[] saveIntoXmlValue() {
        XmlValue []values = new XmlValue[logDecls.length];
        for (int i = 0; i < logDecls.length; i++) {
            values[i] = new XmlValue(logDecls[i]);
        }
        values[0].setFromString(Long.toString(specialId));
        values[1].setFromString(user);
        values[2].setFromString(account);
        values[3].setFromString(filename);
        values[4].setFromString(mode);
        values[5].setFromString(getErrorInfo().getMesg());
        values[6].setFromString(infotransf);
        values[7].setFromString(getUpdatedInfo().name());
        values[8].setFromString(start.toString());
        values[9].setFromString(stop.toString());
        return values;
    }
    /**
     * Save the current DbTransferLog to a file
     * @param filename
     * @return The message for the HTTPS interface
     */
    public String saveDbTransferLog(String filename) {
        Document document = XmlUtil.createEmptyDocument();
        XmlValue [] roots = new XmlValue[1];
        XmlValue root = new XmlValue(logsElements[0]);
        roots[0] = root;
        String message = null;
        XmlValue []values = saveIntoXmlValue();
        try {
            root.addValue(values);
        } catch (InvalidObjectException e) {
            logger.error("Error during Write DbTransferLog file", e);
            return "Error during purge";
        }
        try {
            delete();
        } catch (GoldenGateDatabaseException e) {
            message = "Error during purge";
        }
        message = "Purge Correct Logs successful";
        XmlUtil.write(document, roots);
        try {
            XmlUtil.saveDocument(filename, document);
        } catch (IOException e1) {
            logger.error("Cannot write to file: "+filename+" since {}", e1.getMessage());
            return message+" but cannot save file as export";
        }
        return message;
    }
    /**
     * Export DbTransferLogs to a file and purge the corresponding DbTransferLogs
     * @param preparedStatement the DbTransferLog as SELECT command to export (and purge)
     * @param filename the filename where the DbLogs will be exported
     * @return The message for the HTTPS interface
     */
    public static String saveDbTransferLogFile(DbPreparedStatement preparedStatement, String filename) {
        Document document = XmlUtil.createEmptyDocument();
        XmlValue [] roots = new XmlValue[1];
        XmlValue root = new XmlValue(logsElements[0]);
        roots[0] = root;
        String message = null;
        try {
            try {
                preparedStatement.executeQuery();
                while (preparedStatement.getNext()) {
                    DbTransferLog log = DbTransferLog.getFromStatement(preparedStatement);
                    XmlValue []values = log.saveIntoXmlValue();
                    try {
                        root.addValue(values);
                    } catch (InvalidObjectException e) {
                        logger.error("Error during Write DbTransferLog file", e);
                        return "Error during purge";
                    }
                    log.delete();
                }
                message = "Purge Correct Logs successful";
            } catch (GoldenGateDatabaseNoConnectionError e) {
                message = "Error during purge";
            } catch (GoldenGateDatabaseSqlError e) {
                message = "Error during purge";
            } catch (GoldenGateDatabaseException e) {
                message = "Error during purge";
            }
        } finally {
            preparedStatement.realClose();
        }
        XmlUtil.write(document, roots);
        try {
            XmlUtil.saveDocument(filename, document);
        } catch (IOException e1) {
            logger.error("Cannot write to file: "+filename+" since {}", e1.getMessage());
            return message+" but cannot save file as export";
        }
        return message;
    }
}
