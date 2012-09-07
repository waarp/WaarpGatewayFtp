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
package org.waarp.gateway.ftp.database.data;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.TreeSet;

import org.dom4j.Document;
import org.waarp.common.command.ReplyCode;
import org.waarp.common.database.DbPreparedStatement;
import org.waarp.common.database.DbSession;
import org.waarp.common.database.data.AbstractDbData;
import org.waarp.common.database.data.DbValue;
import org.waarp.common.database.exception.WaarpDatabaseException;
import org.waarp.common.database.exception.WaarpDatabaseNoConnectionException;
import org.waarp.common.database.exception.WaarpDatabaseNoDataException;
import org.waarp.common.database.exception.WaarpDatabaseSqlException;
import org.waarp.common.exception.InvalidArgumentException;
import org.waarp.common.logging.WaarpInternalLogger;
import org.waarp.common.logging.WaarpInternalLoggerFactory;
import org.waarp.common.xml.XmlDecl;
import org.waarp.common.xml.XmlType;
import org.waarp.common.xml.XmlUtil;
import org.waarp.common.xml.XmlValue;
import org.waarp.ftp.core.command.FtpCommandCode;
import org.waarp.gateway.ftp.config.FileBasedConfiguration;
import org.waarp.gateway.ftp.database.DbConstant;
import org.waarp.gateway.ftp.database.model.DbModelFactory;

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
	private static final WaarpInternalLogger logger = WaarpInternalLoggerFactory
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

	public static final int[] dbTypes = {
			Types.VARCHAR,
			Types.VARCHAR,
			Types.TIMESTAMP, Types.TIMESTAMP,
			Types.LONGVARCHAR, Types.INTEGER, Types.INTEGER,
			Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BIGINT };

	public static final String table = " TRANSFLOG ";

	public static final String fieldseq = "TRANSSEQ";

	public static final Columns[] indexes = {
			Columns.STARTTRANS, Columns.UPDATEDINFO, Columns.INFOSTATUS
	};

	public static final String XMLRUNNERS = "transferlogs";
	public static final String XMLRUNNER = "log";

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

	/**
	 * Special For DbTransferLog
	 */
	public static final int NBPRKEY = 4;
	// ALL TABLE SHOULD IMPLEMENT THIS

	protected static final String selectAllFields = Columns.FILENAME.name() + "," +
			Columns.MODETRANS.name() + "," +
			Columns.STARTTRANS.name() + "," + Columns.STOPTRANS.name() + "," +
			Columns.TRANSINFO.name() + "," +
			Columns.INFOSTATUS.name() + "," + Columns.UPDATEDINFO.name() + "," +
			Columns.USERID.name() + "," + Columns.ACCOUNTID.name() + "," +
			Columns.HOSTID.name() + "," + Columns.SPECIALID.name();

	protected static final String updateAllFields =
			Columns.FILENAME.name() + "=?," +
					Columns.MODETRANS.name() + "=?," +
					Columns.STARTTRANS.name() + "=?," + Columns.STOPTRANS.name() + "=?," +
					Columns.TRANSINFO.name() + "=?," +
					Columns.INFOSTATUS.name() + "=?," + Columns.UPDATEDINFO.name() + "=?";

	protected static final String insertAllValues = " (?,?,?,?,?,?,?,?,?,?,?) ";

	private static final TreeSet<Long> clientNoDbSpecialId = new TreeSet<Long>();

	/**
	 * Insert into database
	 * 
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
	 * @throws WaarpDatabaseException
	 */
	public DbTransferLog(DbSession dbSession, String user, String account,
			long specialId,
			boolean isSender, String filename, String mode,
			ReplyCode infostatus, String info,
			UpdatedInfo updatedInfo) throws WaarpDatabaseException {
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
	 * 
	 * @param dbSession
	 * @param user
	 * @param account
	 * @param specialId
	 * @throws WaarpDatabaseException
	 */
	public DbTransferLog(DbSession dbSession, String user, String account, long specialId)
			throws WaarpDatabaseException {
		super(dbSession);
		this.user = user;
		this.account = account;
		this.specialId = specialId;
		this.hostid = FileBasedConfiguration.fileBasedConfiguration.HOST_ID;
		select();
	}

	/*
	 * (non-Javadoc)
	 * @see org.waarp.common.database.data.AbstractDbData#initObject()
	 */
	@Override
	protected void initObject() {
		primaryKey = new DbValue[] {
				new DbValue(user, Columns.USERID.name()),
				new DbValue(account, Columns.ACCOUNTID.name()),
				new DbValue(hostid, Columns.HOSTID.name()),
				new DbValue(specialId, Columns.SPECIALID.name()) };
		otherFields = new DbValue[] {
				// FILENAME, MODETRANS,
				// STARTTRANS, STOPTRANS, TRANSINFO
				// INFOSTATUS, UPDATEDINFO
				new DbValue(filename, Columns.FILENAME.name()),
				new DbValue(mode, Columns.MODETRANS.name()),
				new DbValue(start, Columns.STARTTRANS.name()),
				new DbValue(stop, Columns.STOPTRANS.name()),
				new DbValue(infotransf, Columns.TRANSINFO.name()),
				new DbValue(ReplyCode.REPLY_000_SPECIAL_NOSTATUS.getCode(),
						Columns.INFOSTATUS.name()), // infostatus.getCode()
				new DbValue(updatedInfo, Columns.UPDATEDINFO.name()) };
		allFields = new DbValue[] {
				otherFields[0], otherFields[1], otherFields[2], otherFields[3],
				otherFields[4], otherFields[5], otherFields[6],
				primaryKey[0], primaryKey[1], primaryKey[2], primaryKey[3] };
	}

	/*
	 * (non-Javadoc)
	 * @see org.waarp.common.database.data.AbstractDbData#getSelectAllFields()
	 */
	@Override
	protected String getSelectAllFields() {
		return selectAllFields;
	}

	/*
	 * (non-Javadoc)
	 * @see org.waarp.common.database.data.AbstractDbData#getTable()
	 */
	@Override
	protected String getTable() {
		return table;
	}

	/*
	 * (non-Javadoc)
	 * @see org.waarp.common.database.data.AbstractDbData#getInsertAllValues()
	 */
	@Override
	protected String getInsertAllValues() {
		return insertAllValues;
	}

	/*
	 * (non-Javadoc)
	 * @see org.waarp.common.database.data.AbstractDbData#getUpdateAllFields()
	 */
	@Override
	protected String getUpdateAllFields() {
		return updateAllFields;
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
	protected void setFromArray() throws WaarpDatabaseSqlException {
		filename = (String) allFields[Columns.FILENAME.ordinal()].getValue();
		mode = (String) allFields[Columns.MODETRANS.ordinal()].getValue();
		start = (Timestamp) allFields[Columns.STARTTRANS.ordinal()].getValue();
		stop = (Timestamp) allFields[Columns.STOPTRANS.ordinal()].getValue();
		try {
			infostatus = ReplyCode.getReplyCode(((Integer) allFields[Columns.INFOSTATUS
					.ordinal()].getValue()));
		} catch (InvalidArgumentException e) {
			throw new WaarpDatabaseSqlException("Wrong Argument", e);
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
	protected String getWherePrimaryKey() {
		return primaryKey[0].column + " = ? AND " +
				primaryKey[1].column + " = ? AND " +
				primaryKey[2].column + " = ? AND " +
				primaryKey[3].column + " = ? ";
	}

	/**
	 * Set the primary Key as current value
	 */
	protected void setPrimaryKey() {
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
		return " " + Columns.HOSTID + " = '" +
				FileBasedConfiguration.fileBasedConfiguration.HOST_ID + "' ";
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
	 * @see org.waarp.openr66.databaseold.data.AbstractDbData#delete()
	 */
	@Override
	public void delete() throws WaarpDatabaseException {
		if (dbSession == null) {
			removeNoDbSpecialId();
			return;
		}
		super.delete();
	}

	/*
	 * (non-Javadoc)
	 * @see org.waarp.openr66.databaseold.data.AbstractDbData#insert()
	 */
	@Override
	public void insert() throws WaarpDatabaseException {
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
		super.insert();
	}

	/**
	 * As insert but with the ability to change the SpecialId
	 * 
	 * @throws WaarpDatabaseException
	 */
	public void create() throws WaarpDatabaseException {
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
			try {
				int count = preparedStatement.executeUpdate();
				if (count <= 0) {
					throw new WaarpDatabaseNoDataException("No row found");
				}
			} catch (WaarpDatabaseSqlException e) {
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
							throw new WaarpDatabaseSqlException(e1);
						}
						specialId = result + 1;
						DbModelFactory.dbModel.resetSequence(dbSession, specialId + 1);
						setToArray();
						preparedStatement.close();
						setValues(preparedStatement, allFields);
						int count = preparedStatement.executeUpdate();
						if (count <= 0) {
							throw new WaarpDatabaseNoDataException("No row found");
						}
					} else {
						throw new WaarpDatabaseNoDataException("No row found");
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
	 * @throws WaarpDatabaseNoConnectionException
	 * @throws WaarpDatabaseSqlException
	 */
	public static DbTransferLog getFromStatement(
			DbPreparedStatement preparedStatement)
			throws WaarpDatabaseNoConnectionException, WaarpDatabaseSqlException {
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
	 * @param limit
	 *            limit the number of rows
	 * @return the DbPreparedStatement for getting TransferLog according to status ordered by start
	 * @throws WaarpDatabaseNoConnectionException
	 * @throws WaarpDatabaseSqlException
	 */
	public static DbPreparedStatement getStatusPrepareStament(
			DbSession session, ReplyCode status, int limit)
			throws WaarpDatabaseNoConnectionException, WaarpDatabaseSqlException {
		String request = "SELECT " + selectAllFields + " FROM " + table;
		if (status != null) {
			request += " WHERE " + Columns.INFOSTATUS.name() + " = " +
					status.getCode() + " AND " + getLimitWhereCondition();
		} else {
			request += " WHERE " + getLimitWhereCondition();
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
	 * @throws WaarpDatabaseNoConnectionException
	 * @throws WaarpDatabaseSqlException
	 */
	public static DbPreparedStatement getLogPrepareStament(DbSession session,
			Timestamp start, Timestamp stop)
			throws WaarpDatabaseNoConnectionException, WaarpDatabaseSqlException {
		DbPreparedStatement preparedStatement = new DbPreparedStatement(session);
		String request = "SELECT " + selectAllFields + " FROM " + table;
		if (start != null & stop != null) {
			request += " WHERE " + Columns.STARTTRANS.name() + " >= ? AND " +
					Columns.STARTTRANS.name() + " <= ? AND " + getLimitWhereCondition() +
					" ORDER BY " + Columns.SPECIALID.name() + " DESC ";
			preparedStatement.createPrepareStatement(request);
			try {
				preparedStatement.getPreparedStatement().setTimestamp(1, start);
				preparedStatement.getPreparedStatement().setTimestamp(2, stop);
			} catch (SQLException e) {
				preparedStatement.realClose();
				throw new WaarpDatabaseSqlException(e);
			}
		} else if (start != null) {
			request += " WHERE " + Columns.STARTTRANS.name() +
					" >= ? AND " + getLimitWhereCondition() +
					" ORDER BY " + Columns.SPECIALID.name() + " DESC ";
			preparedStatement.createPrepareStatement(request);
			try {
				preparedStatement.getPreparedStatement().setTimestamp(1, start);
			} catch (SQLException e) {
				preparedStatement.realClose();
				throw new WaarpDatabaseSqlException(e);
			}
		} else if (stop != null) {
			request += " WHERE " + Columns.STARTTRANS.name() +
					" <= ? AND " + getLimitWhereCondition() +
					" ORDER BY " + Columns.SPECIALID.name() + " DESC ";
			preparedStatement.createPrepareStatement(request);
			try {
				preparedStatement.getPreparedStatement().setTimestamp(1, stop);
			} catch (SQLException e) {
				preparedStatement.realClose();
				throw new WaarpDatabaseSqlException(e);
			}
		} else {
			request += " WHERE " + getLimitWhereCondition() +
					" ORDER BY " + Columns.SPECIALID.name() + " DESC ";
			preparedStatement.createPrepareStatement(request);
		}
		return preparedStatement;
	}

	/**
	 * 
	 * @param session
	 * @return the DbPreparedStatement for getting Updated Object
	 * @throws WaarpDatabaseNoConnectionException
	 * @throws WaarpDatabaseSqlException
	 */
	public static DbPreparedStatement getCountInfoPrepareStatement(DbSession session)
			throws WaarpDatabaseNoConnectionException, WaarpDatabaseSqlException {
		String request = "SELECT COUNT(" + Columns.SPECIALID.name() +
				") FROM " + table + " WHERE " +
				Columns.STARTTRANS.name() + " >= ? AND " + getLimitWhereCondition() +
				" AND " + Columns.UPDATEDINFO.name() + " = ? ";
		DbPreparedStatement pstt = new DbPreparedStatement(session, request);
		session.addLongTermPreparedStatement(pstt);
		return pstt;
	}

	/**
	 * 
	 * @param pstt
	 * @param info
	 * @param time
	 * @return the number of elements (COUNT) from the statement
	 */
	public static long getResultCountPrepareStatement(DbPreparedStatement pstt, UpdatedInfo info,
			long time) {
		long result = 0;
		try {
			finishSelectOrCountPrepareStatement(pstt, time);
			pstt.getPreparedStatement().setInt(2, info.ordinal());
			pstt.executeQuery();
			if (pstt.getNext()) {
				result = pstt.getResultSet().getLong(1);
			}
		} catch (WaarpDatabaseNoConnectionException e) {
		} catch (WaarpDatabaseSqlException e) {
		} catch (SQLException e) {
		} finally {
			pstt.close();
		}
		return result;
	}

	/**
	 * @param session
	 * @return the DbPreparedStatement for getting Runner according to status ordered by start
	 * @throws WaarpDatabaseNoConnectionException
	 * @throws WaarpDatabaseSqlException
	 */
	public static DbPreparedStatement getCountStatusPrepareStatement(
			DbSession session)
			throws WaarpDatabaseNoConnectionException, WaarpDatabaseSqlException {
		String request = "SELECT COUNT(" + Columns.SPECIALID.name() + ") FROM " + table;
		request += " WHERE " + Columns.STARTTRANS.name() + " >= ? ";
		request += " AND " + Columns.INFOSTATUS.name() + " = ? AND " + getLimitWhereCondition();
		DbPreparedStatement prep = new DbPreparedStatement(session, request);
		session.addLongTermPreparedStatement(prep);
		return prep;
	}

	/**
	 * @param session
	 * @return the DbPreparedStatement for getting All according to status ordered by start
	 * @throws WaarpDatabaseNoConnectionException
	 * @throws WaarpDatabaseSqlException
	 */
	public static DbPreparedStatement getCountAllPrepareStatement(
			DbSession session)
			throws WaarpDatabaseNoConnectionException, WaarpDatabaseSqlException {
		String request = "SELECT COUNT(" + Columns.SPECIALID.name() + ") FROM " + table;
		request += " WHERE " + Columns.STARTTRANS.name() + " >= ? ";
		request += " AND " + getLimitWhereCondition();
		DbPreparedStatement prep = new DbPreparedStatement(session, request);
		session.addLongTermPreparedStatement(prep);
		return prep;
	}

	/**
	 * 
	 * @param pstt
	 * @param error
	 * @param time
	 * @return the number of elements (COUNT) from the statement
	 */
	public static long getResultCountPrepareStatement(DbPreparedStatement pstt, ReplyCode error,
			long time) {
		long result = 0;
		try {
			finishSelectOrCountPrepareStatement(pstt, time);
			pstt.getPreparedStatement().setInt(2, error.getCode());
			pstt.executeQuery();
			if (pstt.getNext()) {
				result = pstt.getResultSet().getLong(1);
			}
		} catch (WaarpDatabaseNoConnectionException e) {
		} catch (WaarpDatabaseSqlException e) {
		} catch (SQLException e) {
		} finally {
			pstt.close();
		}
		return result;
	}

	/**
	 * 
	 * @param pstt
	 * @return the number of elements (COUNT) from the statement
	 */
	public static long getResultCountPrepareStatement(DbPreparedStatement pstt) {
		long result = 0;
		try {
			pstt.executeQuery();
			if (pstt.getNext()) {
				result = pstt.getResultSet().getLong(1);
			}
		} catch (WaarpDatabaseNoConnectionException e) {
		} catch (WaarpDatabaseSqlException e) {
		} catch (SQLException e) {
		} finally {
			pstt.close();
		}
		return result;
	}

	/**
	 * Set the current time in the given updatedPreparedStatement
	 * 
	 * @param pstt
	 * @throws WaarpDatabaseNoConnectionException
	 * @throws WaarpDatabaseSqlException
	 */
	public static void finishSelectOrCountPrepareStatement(DbPreparedStatement pstt)
			throws WaarpDatabaseNoConnectionException, WaarpDatabaseSqlException {
		finishSelectOrCountPrepareStatement(pstt, System.currentTimeMillis());
	}

	/**
	 * Set the current time in the given updatedPreparedStatement
	 * 
	 * @param pstt
	 * @throws WaarpDatabaseNoConnectionException
	 * @throws WaarpDatabaseSqlException
	 */
	public static void finishSelectOrCountPrepareStatement(DbPreparedStatement pstt, long time)
			throws WaarpDatabaseNoConnectionException, WaarpDatabaseSqlException {
		Timestamp startlimit = new Timestamp(time);
		try {
			pstt.getPreparedStatement().setTimestamp(1, startlimit);
		} catch (SQLException e) {
			logger.error("Database SQL Error: Cannot set timestamp", e);
			throw new WaarpDatabaseSqlException("Cannot set timestamp", e);
		}
	}

	/**
	 * Running or not transfers are concerned
	 * 
	 * @param session
	 * @param in
	 *            True for Incoming, False for Outgoing
	 * @return the DbPreparedStatement for getting Runner according to in or out going way and Error
	 * @throws WaarpDatabaseNoConnectionException
	 * @throws WaarpDatabaseSqlException
	 */
	public static DbPreparedStatement getCountInOutErrorPrepareStatement(
			DbSession session, boolean in)
			throws WaarpDatabaseNoConnectionException, WaarpDatabaseSqlException {
		String request = "SELECT COUNT(" + Columns.SPECIALID.name() + ") FROM " + table;
		String inCond = null;
		if (in) {
			inCond = " (" + Columns.MODETRANS.name() + " = '" + FtpCommandCode.APPE.name()
					+ "' OR " +
					Columns.MODETRANS.name() + " = '" + FtpCommandCode.STOR.name() + "' OR " +
					Columns.MODETRANS.name() + " = '" + FtpCommandCode.STOU.name() + "') ";
		} else {
			inCond = " (" + Columns.MODETRANS.name() + " = '" + FtpCommandCode.RETR.name() + "') ";
		}
		request += " WHERE " + inCond;
		request += " AND " + getLimitWhereCondition() + " ";
		request += " AND " + Columns.STARTTRANS.name() + " >= ? ";
		request += " AND " + Columns.UPDATEDINFO.name() + " = " + UpdatedInfo.INERROR.ordinal();
		DbPreparedStatement prep = new DbPreparedStatement(session, request);
		session.addLongTermPreparedStatement(prep);
		return prep;
	}

	/**
	 * Running or not transfers are concerned
	 * 
	 * @param session
	 * @param in
	 *            True for Incoming, False for Outgoing
	 * @param running
	 *            True for Running only, False for all
	 * @return the DbPreparedStatement for getting Runner according to in or out going way
	 * @throws WaarpDatabaseNoConnectionException
	 * @throws WaarpDatabaseSqlException
	 */
	public static DbPreparedStatement getCountInOutRunningPrepareStatement(
			DbSession session, boolean in, boolean running)
			throws WaarpDatabaseNoConnectionException, WaarpDatabaseSqlException {
		String request = "SELECT COUNT(" + Columns.SPECIALID.name() + ") FROM " + table;
		String inCond = null;
		if (in) {
			inCond = " (" + Columns.MODETRANS.name() + " = '" + FtpCommandCode.APPE.name()
					+ "' OR " +
					Columns.MODETRANS.name() + " = '" + FtpCommandCode.STOR.name() + "' OR " +
					Columns.MODETRANS.name() + " = '" + FtpCommandCode.STOU.name() + "') ";
		} else {
			inCond = " (" + Columns.MODETRANS.name() + " = '" + FtpCommandCode.RETR.name() + "') ";
		}
		request += " WHERE " + inCond;
		request += " AND " + getLimitWhereCondition() + " ";
		request += " AND " + Columns.STARTTRANS.name() + " >= ? ";
		if (running) {
			request += " AND " + Columns.UPDATEDINFO.name() + " = " + UpdatedInfo.RUNNING.ordinal();
		}
		DbPreparedStatement prep = new DbPreparedStatement(session, request);
		session.addLongTermPreparedStatement(prep);
		return prep;
	}

	/*
	 * (non-Javadoc)
	 * @see org.waarp.openr66.databaseold.data.AbstractDbData#changeUpdatedInfo(UpdatedInfo)
	 */
	@Override
	public void changeUpdatedInfo(UpdatedInfo info) {
		updatedInfo = info.ordinal();
		allFields[Columns.UPDATEDINFO.ordinal()].setValue(updatedInfo);
		isSaved = false;
	}

	/**
	 * Set the ReplyCode for the UpdatedInfo
	 * 
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
	 * @param infotransf
	 *            the infotransf to set
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
	 * @param stop
	 *            the stop to set
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
	 * @throws WaarpDatabaseException
	 */
	public void saveStatus() throws WaarpDatabaseException {
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
				specialId + " Mode: " + mode + " isSender: " + isSender +
				" User: " + user + " Account: " + account +
				" Start: " + start + " Stop: " + stop +
				" Internal: " + UpdatedInfo.values()[updatedInfo].name() +
				":" + infostatus.getMesg() +
				" TransferInfo: " + infotransf;
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
	 * XXXIDXXX XXXUSERXXX XXXACCTXXX XXXFILEXXX XXXMODEXXX XXXSTATUSXXX XXXINFOXXX XXXUPINFXXX
	 * XXXSTARTXXX XXXSTOPXXX
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
	private static final XmlDecl[] logDecls = {
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
			new XmlDecl(XML_ENTRY, XmlType.XVAL, XML_ROOT + "/" + XML_ENTRY,
					logDecls, true)
	};

	/**
	 * 
	 * @return the associated XmlValue
	 */
	private XmlValue[] saveIntoXmlValue() {
		XmlValue[] values = new XmlValue[logDecls.length];
		for (int i = 0; i < logDecls.length; i++) {
			values[i] = new XmlValue(logDecls[i]);
		}
		try {
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
		} catch (InvalidArgumentException e) {
			return null;
		}
		return values;
	}

	/**
	 * Save the current DbTransferLog to a file
	 * 
	 * @param filename
	 * @return The message for the HTTPS interface
	 */
	public String saveDbTransferLog(String filename) {
		Document document = XmlUtil.createEmptyDocument();
		XmlValue[] roots = new XmlValue[1];
		XmlValue root = new XmlValue(logsElements[0]);
		roots[0] = root;
		String message = null;
		XmlValue[] values = saveIntoXmlValue();
		if (values == null) {
			return "Error during export";
		}
		try {
			root.addValue(values);
		} catch (InvalidObjectException e) {
			logger.error("Error during Write DbTransferLog file", e);
			return "Error during purge";
		}
		try {
			delete();
		} catch (WaarpDatabaseException e) {
			message = "Error during purge";
		}
		message = "Purge Correct Logs successful";
		XmlUtil.write(document, roots);
		try {
			XmlUtil.saveDocument(filename, document);
		} catch (IOException e1) {
			logger.error("Cannot write to file: " + filename + " since {}", e1.getMessage());
			return message + " but cannot save file as export";
		}
		return message;
	}

	/**
	 * Export DbTransferLogs to a file and purge the corresponding DbTransferLogs
	 * 
	 * @param preparedStatement
	 *            the DbTransferLog as SELECT command to export (and purge)
	 * @param filename
	 *            the filename where the DbLogs will be exported
	 * @return The message for the HTTPS interface
	 */
	public static String saveDbTransferLogFile(DbPreparedStatement preparedStatement,
			String filename) {
		Document document = XmlUtil.createEmptyDocument();
		XmlValue[] roots = new XmlValue[1];
		XmlValue root = new XmlValue(logsElements[0]);
		roots[0] = root;
		String message = null;
		try {
			try {
				preparedStatement.executeQuery();
				while (preparedStatement.getNext()) {
					DbTransferLog log = DbTransferLog.getFromStatement(preparedStatement);
					XmlValue[] values = log.saveIntoXmlValue();
					if (values == null) {
						return "Error during export";
					}
					try {
						root.addValue(values);
					} catch (InvalidObjectException e) {
						logger.error("Error during Write DbTransferLog file", e);
						return "Error during purge";
					}
					log.delete();
				}
				message = "Purge Correct Logs successful";
			} catch (WaarpDatabaseNoConnectionException e) {
				message = "Error during purge";
			} catch (WaarpDatabaseSqlException e) {
				message = "Error during purge";
			} catch (WaarpDatabaseException e) {
				message = "Error during purge";
			}
		} finally {
			preparedStatement.realClose();
		}
		XmlUtil.write(document, roots);
		try {
			XmlUtil.saveDocument(filename, document);
		} catch (IOException e1) {
			logger.error("Cannot write to file: " + filename + " since {}", e1.getMessage());
			return message + " but cannot save file as export";
		}
		return message;
	}
}
