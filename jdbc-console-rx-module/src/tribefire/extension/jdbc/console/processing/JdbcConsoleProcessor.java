// ============================================================================
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ============================================================================
package tribefire.extension.jdbc.console.processing;

import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import com.braintribe.gm.model.reason.Maybe;
import com.braintribe.gm.model.reason.Reasons;
import com.braintribe.gm.model.reason.UnsatisfiedMaybeTunneling;
import com.braintribe.gm.model.reason.essential.InvalidArgument;
import com.braintribe.gm.model.reason.essential.NotFound;
import com.braintribe.gm.model.security.reason.Forbidden;
import com.braintribe.model.processing.service.api.ServiceRequestContext;
import com.braintribe.model.processing.service.common.context.UserSessionAspect;
import com.braintribe.model.processing.service.impl.AbstractDispatchingServiceProcessor;
import com.braintribe.model.processing.service.impl.DispatchConfiguration;
import com.braintribe.model.usersession.UserSession;

import hiconic.rx.db.model.configuration.Database;
import hiconic.rx.db.module.api.DatabaseContract;
import tribefire.extension.jdbc.console.model.JdbcColumn;
import tribefire.extension.jdbc.console.model.JdbcCell;
import tribefire.extension.jdbc.console.model.JdbcConsoleCapabilities;
import tribefire.extension.jdbc.console.model.JdbcConsoleConfiguration;
import tribefire.extension.jdbc.console.model.JdbcDatabase;
import tribefire.extension.jdbc.console.model.JdbcQueryResult;
import tribefire.extension.jdbc.console.model.JdbcRow;
import tribefire.extension.jdbc.console.model.JdbcStatementResult;
import tribefire.extension.jdbc.console.model.JdbcValueKind;
import tribefire.extension.jdbc.console.model.api.ExecuteJdbcQuery;
import tribefire.extension.jdbc.console.model.api.ExecuteJdbcStatement;
import tribefire.extension.jdbc.console.model.api.GetJdbcConsoleCapabilities;
import tribefire.extension.jdbc.console.model.api.JdbcConsoleRequest;
import tribefire.extension.jdbc.console.model.api.JdbcDatabases;
import tribefire.extension.jdbc.console.model.api.ListJdbcDatabases;

public class JdbcConsoleProcessor extends AbstractDispatchingServiceProcessor<JdbcConsoleRequest, Object> {
	private final DatabaseContract databases;
	private final JdbcConsoleConfiguration configuration;

	public JdbcConsoleProcessor(DatabaseContract databases, JdbcConsoleConfiguration configuration) {
		this.databases = databases;
		this.configuration = configuration;
		validate(configuration);
	}

	@Override
	protected void configureDispatching(DispatchConfiguration<JdbcConsoleRequest, Object> dispatching) {
		dispatching.register(GetJdbcConsoleCapabilities.T, this::capabilities);
		dispatching.register(ListJdbcDatabases.T, this::listDatabases);
		dispatching.register(ExecuteJdbcQuery.T, this::executeQuery);
		dispatching.register(ExecuteJdbcStatement.T, this::executeStatement);
	}

	private JdbcConsoleCapabilities capabilities(ServiceRequestContext context, GetJdbcConsoleCapabilities request) {
		JdbcConsoleCapabilities result = JdbcConsoleCapabilities.T.create();
		result.setCanRead(hasAnyRole(context, readRoles()));
		result.setCanWrite(hasAnyRole(context, writerRoles()));
		return result;
	}

	private JdbcDatabases listDatabases(ServiceRequestContext context, ListJdbcDatabases request) {
		requireRead(context);
		JdbcDatabases result = JdbcDatabases.T.create();
		result.setDatabases(databases.allDatabases().stream()
				.sorted(Comparator.comparing(Database::getName))
				.map(database -> {
					JdbcDatabase item = JdbcDatabase.T.create();
					item.setName(database.getName());
					return item;
				}).toList());
		return result;
	}

	private JdbcQueryResult executeQuery(ServiceRequestContext context, ExecuteJdbcQuery request) {
		requireRead(context);
		validate(request.getDatabaseName(), request.getStatement());
		int limit = rowLimit(request.getRowLimit());
		long started = System.nanoTime();

		try (Connection connection = dataSource(request.getDatabaseName()).getConnection()) {
			prepareReadOnly(connection);
			try (Statement statement = connection.createStatement()) {
				statement.setQueryTimeout(configuration.getQueryTimeoutSeconds());
				statement.setMaxRows(limit + 1);
				try (ResultSet resultSet = statement.executeQuery(request.getStatement())) {
					JdbcQueryResult result = read(resultSet, limit);
					result.setWarnings(warnings(statement.getWarnings()));
					result.setDurationInMs(elapsedMillis(started));
					return result;
				} finally {
					rollback(connection);
				}
			}
		} catch (UnsatisfiedMaybeTunneling e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Could not execute JDBC query on database '" + request.getDatabaseName() + "'", e);
		}
	}

	private JdbcStatementResult executeStatement(ServiceRequestContext context, ExecuteJdbcStatement request) {
		requireWrite(context);
		validate(request.getDatabaseName(), request.getStatement());
		long started = System.nanoTime();

		try (Connection connection = dataSource(request.getDatabaseName()).getConnection()) {
			connection.setAutoCommit(false);
			try (Statement statement = connection.createStatement()) {
				statement.setQueryTimeout(configuration.getQueryTimeoutSeconds());
				boolean returnedResultSet = statement.execute(request.getStatement());
				if (returnedResultSet) {
					rollback(connection);
					throw invalid("The statement returned rows. Use ExecuteJdbcQuery for result-producing SQL.");
				}
				long count = statement.getLargeUpdateCount();
				connection.commit();
				JdbcStatementResult result = JdbcStatementResult.T.create();
				result.setUpdateCount(count);
				result.setWarnings(warnings(statement.getWarnings()));
				result.setDurationInMs(elapsedMillis(started));
				return result;
			} catch (Exception e) {
				rollback(connection);
				throw e;
			}
		} catch (UnsatisfiedMaybeTunneling e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Could not execute JDBC statement on database '" + request.getDatabaseName() + "'", e);
		}
	}

	private JdbcQueryResult read(ResultSet resultSet, int limit) throws Exception {
		ResultSetMetaData metadata = resultSet.getMetaData();
		int count = metadata.getColumnCount();
		List<JdbcColumn> columns = new ArrayList<>(count);
		for (int index = 1; index <= count; index++)
			columns.add(column(metadata, index));

		List<JdbcRow> rows = new ArrayList<>();
		boolean truncated = false;
		while (resultSet.next()) {
			if (rows.size() == limit) {
				truncated = true;
				break;
			}
			List<JdbcCell> cells = new ArrayList<>(count);
			for (int index = 1; index <= count; index++) {
				Object value = value(resultSet, index, columns.get(index - 1).getValueKind());
				JdbcCell cell = JdbcCell.T.create();
				cell.setNullValue(value == null);
				cell.setValue(value);
				cells.add(cell);
			}
			JdbcRow row = JdbcRow.T.create();
			row.setCells(cells);
			rows.add(row);
		}

		JdbcQueryResult result = JdbcQueryResult.T.create();
		result.setColumns(columns);
		result.setRows(rows);
		result.setTruncated(truncated);
		return result;
	}

	private JdbcColumn column(ResultSetMetaData metadata, int index) throws Exception {
		JdbcColumn column = JdbcColumn.T.create();
		column.setIndex(index);
		column.setName(metadata.getColumnName(index));
		column.setLabel(metadata.getColumnLabel(index));
		column.setJdbcType(metadata.getColumnType(index));
		column.setDatabaseTypeName(metadata.getColumnTypeName(index));
		column.setValueKind(valueKind(metadata.getColumnType(index)));
		column.setNullable(metadata.isNullable(index) != ResultSetMetaData.columnNoNulls);
		return column;
	}

	private Object value(ResultSet resultSet, int index, JdbcValueKind kind) throws Exception {
		Object value = resultSet.getObject(index);
		if (value == null)
			return null;
		return switch (kind) {
			case BOOLEAN -> resultSet.getBoolean(index);
			case INTEGER -> resultSet.getInt(index);
			case LONG -> resultSet.getLong(index);
			case DECIMAL -> decimal(value);
			case DOUBLE -> resultSet.getDouble(index);
			case DATE -> date(resultSet, index);
			case INSTANT -> instant(value);
			case BINARY -> binary(value);
			case STRING -> string(value);
		};
	}

	private static BigDecimal decimal(Object value) {
		return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
	}

	private static Date date(ResultSet resultSet, int index) throws Exception {
		java.sql.Date date = resultSet.getDate(index);
		return date == null ? null : new Date(date.getTime());
	}

	private Object instant(Object value) {
		if (value instanceof java.sql.Timestamp timestamp)
			return Date.from(timestamp.toInstant());
		if (value instanceof java.time.Instant instant)
			return Date.from(instant);
		if (value instanceof OffsetDateTime dateTime)
			return Date.from(dateTime.toInstant());
		if (value instanceof ZonedDateTime dateTime)
			return Date.from(dateTime.toInstant());
		if (value instanceof OffsetTime || value instanceof java.time.LocalDate || value instanceof java.time.LocalDateTime
				|| value instanceof java.time.LocalTime)
			return truncate(value.toString());
		return value instanceof Date date ? date : truncate(value.toString());
	}

	private String binary(Object value) throws Exception {
		byte[] bytes;
		if (value instanceof byte[] array)
			bytes = array;
		else if (value instanceof Blob blob) {
			int maximumBytes = Math.max(1, configuration.getMaximumCellLength() * 3 / 4);
			bytes = blob.getBytes(1, (int) Math.min(blob.length(), maximumBytes + 1L));
		} else
			bytes = value.toString().getBytes(StandardCharsets.UTF_8);
		return truncate(Base64.getEncoder().encodeToString(bytes));
	}

	private String string(Object value) throws Exception {
		if (value instanceof Clob clob) {
			try (Reader reader = clob.getCharacterStream()) {
				int maximum = configuration.getMaximumCellLength();
				StringBuilder text = new StringBuilder(Math.min(maximum + 1, 8192));
				char[] chars = new char[Math.min(maximum + 1, 4096)];
				while (text.length() <= maximum) {
					int length = reader.read(chars, 0, Math.min(chars.length, maximum + 1 - text.length()));
					if (length < 0)
						break;
					text.append(chars, 0, length);
				}
				return truncate(text.toString());
			}
		}
		if (value instanceof SQLXML xml)
			return truncate(xml.getString());
		return truncate(value.toString());
	}

	private String truncate(String value) {
		int maximum = configuration.getMaximumCellLength();
		return value.length() <= maximum ? value : value.substring(0, maximum) + "…";
	}

	static JdbcValueKind valueKind(int jdbcType) {
		return switch (jdbcType) {
			case Types.BOOLEAN, Types.BIT -> JdbcValueKind.BOOLEAN;
			case Types.TINYINT, Types.SMALLINT, Types.INTEGER -> JdbcValueKind.INTEGER;
			case Types.BIGINT -> JdbcValueKind.LONG;
			case Types.NUMERIC, Types.DECIMAL -> JdbcValueKind.DECIMAL;
			case Types.FLOAT, Types.REAL, Types.DOUBLE -> JdbcValueKind.DOUBLE;
			case Types.DATE -> JdbcValueKind.DATE;
			case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> JdbcValueKind.INSTANT;
			case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> JdbcValueKind.BINARY;
			default -> JdbcValueKind.STRING;
		};
	}

	private DataSource dataSource(String name) {
		DataSource result = databases.findDataSource(name);
		if (result == null)
			throw new UnsatisfiedMaybeTunneling(Reasons.build(NotFound.T).text("No configured database named '" + name + "'.").toMaybe());
		return result;
	}

	private int rowLimit(Integer requested) {
		int result = requested == null ? configuration.getDefaultRowLimit() : requested;
		if (result < 1 || result > configuration.getMaximumRowLimit())
			throw invalid("Row limit must be between 1 and " + configuration.getMaximumRowLimit() + ".");
		return result;
	}

	private static void validate(String database, String statement) {
		if (database == null || database.isBlank())
			throw invalid("Database name must not be empty.");
		if (statement == null || statement.isBlank())
			throw invalid("SQL statement must not be empty.");
	}

	private static void validate(JdbcConsoleConfiguration configuration) {
		if (configuration.getDefaultRowLimit() < 1)
			throw new IllegalArgumentException("JDBC console default row limit must be positive.");
		if (configuration.getMaximumRowLimit() < configuration.getDefaultRowLimit())
			throw new IllegalArgumentException("JDBC console maximum row limit must not be smaller than its default.");
		if (configuration.getQueryTimeoutSeconds() < 1)
			throw new IllegalArgumentException("JDBC console query timeout must be positive.");
		if (configuration.getMaximumCellLength() < 1)
			throw new IllegalArgumentException("JDBC console maximum cell length must be positive.");
	}

	private void requireRead(ServiceRequestContext context) {
		if (!hasAnyRole(context, readRoles()))
			throw forbidden("A JDBC console reader role is required.");
	}

	private void requireWrite(ServiceRequestContext context) {
		if (!hasAnyRole(context, writerRoles()))
			throw forbidden("A JDBC console writer role is required.");
	}

	private Set<String> readRoles() {
		Set<String> readers = configuration.getReaderRoles();
		Set<String> writers = configuration.getWriterRoles();
		if (writers == null || writers.isEmpty())
			return readers == null ? Set.of() : readers;
		var result = new java.util.LinkedHashSet<String>();
		if (readers != null)
			result.addAll(readers);
		result.addAll(writers);
		return result;
	}

	private Set<String> writerRoles() {
		return configuration.getWriterRoles() == null ? Set.of() : configuration.getWriterRoles();
	}

	private static boolean hasAnyRole(ServiceRequestContext context, Set<String> required) {
		UserSession session = context.findOrNull(UserSessionAspect.class);
		Set<String> effective = session == null ? null : session.getEffectiveRoles();
		return effective != null && required != null && effective.stream().anyMatch(required::contains);
	}

	private static UnsatisfiedMaybeTunneling forbidden(String message) {
		Maybe<Object> maybe = Reasons.build(Forbidden.T).text(message).toMaybe();
		return new UnsatisfiedMaybeTunneling(maybe);
	}

	private static UnsatisfiedMaybeTunneling invalid(String message) {
		return new UnsatisfiedMaybeTunneling(Reasons.build(InvalidArgument.T).text(message).toMaybe());
	}

	private static void prepareReadOnly(Connection connection) throws Exception {
		connection.setAutoCommit(false);
		try {
			connection.setReadOnly(true);
		} catch (Exception ignored) {
			// Some drivers do not support this hint. The database account remains the security boundary.
		}
	}

	private static void rollback(Connection connection) {
		try {
			connection.rollback();
		} catch (Exception ignored) {
			// Preserve the original result or exception.
		}
	}

	private static List<String> warnings(SQLWarning warning) {
		List<String> result = new ArrayList<>();
		for (SQLWarning current = warning; current != null; current = current.getNextWarning())
			result.add(current.getMessage());
		return result;
	}

	private static long elapsedMillis(long started) {
		return (System.nanoTime() - started) / 1_000_000;
	}
}
