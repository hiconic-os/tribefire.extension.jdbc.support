// ============================================================================
// Copyright BRAINTRIBE TECHNOLOGY GMBH, Austria, 2002-2022
// 
// This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
// 
// This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
// 
// You should have received a copy of the GNU Lesser General Public License along with this library; See http://www.gnu.org/licenses/.
// ============================================================================
package com.braintribe.model.processing.jdbc.support.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import com.braintribe.cfg.Configurable;
import com.braintribe.cfg.Required;
import com.braintribe.exception.Exceptions;
import com.braintribe.gm.model.reason.Maybe;
import com.braintribe.gm.model.reason.Reasons;
import com.braintribe.gm.model.reason.UnsatisfiedMaybeTunneling;
import com.braintribe.gm.model.security.reason.Forbidden;
import com.braintribe.logging.Logger;
import com.braintribe.model.deployment.DeploymentStatus;
import com.braintribe.model.deployment.database.connector.DatabaseConnectionDescriptor;
import com.braintribe.model.deployment.database.connector.GenericDatabaseConnectionDescriptor;
import com.braintribe.model.deployment.database.connector.MssqlConnectionDescriptor;
import com.braintribe.model.deployment.database.connector.OracleConnectionDescriptor;
import com.braintribe.model.deployment.database.pool.ConfiguredDatabaseConnectionPool;
import com.braintribe.model.deployment.database.pool.DatabaseConnectionPool;
import com.braintribe.model.jdbc.support.deployment.db.DatabaseInformationQueries;
import com.braintribe.model.jdbc.suppport.service.AnalyzeDatabase;
import com.braintribe.model.jdbc.suppport.service.CommitStyle;
import com.braintribe.model.jdbc.suppport.service.ConnectorList;
import com.braintribe.model.jdbc.suppport.service.CreateForeignKeyIndices;
import com.braintribe.model.jdbc.suppport.service.DatabaseAnalysis;
import com.braintribe.model.jdbc.suppport.service.DatabaseInformation;
import com.braintribe.model.jdbc.suppport.service.ExecuteSqlStatement;
import com.braintribe.model.jdbc.suppport.service.ForeignIndicesReport;
import com.braintribe.model.jdbc.suppport.service.HasConnectorId;
import com.braintribe.model.jdbc.suppport.service.JdbcSupportRequest;
import com.braintribe.model.jdbc.suppport.service.JdbcSupportServiceResponse;
import com.braintribe.model.jdbc.suppport.service.ListConnectors;
import com.braintribe.model.jdbc.suppport.service.StatementResult;
import com.braintribe.model.jdbc.suppport.service.StatementResultEntry;
import com.braintribe.model.processing.deployment.api.DeployRegistry;
import com.braintribe.model.processing.jdbc.support.service.expert.DatabaseExpert;
import com.braintribe.model.processing.query.fluent.EntityQueryBuilder;
import com.braintribe.model.processing.service.api.ServiceProcessor;
import com.braintribe.model.processing.service.api.ServiceRequestContext;
import com.braintribe.model.processing.service.common.context.UserSessionAspect;
import com.braintribe.model.processing.service.impl.ServiceProcessors;
import com.braintribe.model.processing.session.api.persistence.PersistenceGmSession;
import com.braintribe.model.query.EntityQuery;
import com.braintribe.model.service.api.ServiceRequest;
import com.braintribe.model.usersession.UserSession;
import com.braintribe.utils.IOTools;
import com.braintribe.utils.date.NanoClock;
import com.braintribe.utils.lcd.StringTools;

public class JdbcSupportServiceProcessor implements ServiceProcessor<JdbcSupportRequest, JdbcSupportServiceResponse> {

	private final static Logger logger = Logger.getLogger(JdbcSupportServiceProcessor.class);

	private DeployRegistry deployRegistry;
	private Supplier<PersistenceGmSession> cortexSessionSupplier;

	private Map<String, DatabaseInformationQueries> informationQueries;
	private Map<String, DatabaseExpert> expertMap;

	private Set<String> allowedRoles = Set.of("tf-admin");

	private final ServiceProcessor<ServiceRequest, JdbcSupportServiceResponse> ddsaDispatcher = ServiceProcessors.dispatcher(config -> {
		config.register(AnalyzeDatabase.T, this::analyzeDatabase);
		config.register(CreateForeignKeyIndices.T, this::createForeignKeyIndices);
		config.register(ExecuteSqlStatement.T, this::executeSqlStatement);
		config.register(ListConnectors.T, this::listConnectors);
	});

	@Override
	public JdbcSupportServiceResponse process(ServiceRequestContext requestContext, JdbcSupportRequest request) {
		Instant start = NanoClock.INSTANCE.instant();

		UserSession userSession = requestContext.findAspect(UserSessionAspect.class);
		Set<String> effectiveRoles = new HashSet<>(userSession.getEffectiveRoles());
		effectiveRoles.retainAll(allowedRoles);
		if (effectiveRoles.isEmpty()) {
			logger.info(() -> "User " + userSession.getUser().getName() + " is not allowed to execute this service.");
			Maybe<Object> maybe = Reasons.build(Forbidden.T).text("You are not allowed to use this service.").toMaybe();
			throw new UnsatisfiedMaybeTunneling(maybe);
		}

		JdbcSupportServiceResponse result = ddsaDispatcher.process(requestContext, request);
		result.setDurationInMs(Duration.between(start, NanoClock.INSTANCE.instant()).toMillis());
		return result;
	}

	public ConnectorList listConnectors(@SuppressWarnings("unused") ServiceRequestContext requestContext,
			@SuppressWarnings("unused") ListConnectors request) {

		ConnectorList result = ConnectorList.T.create();

		PersistenceGmSession cortexSession = cortexSessionSupplier.get();
		EntityQuery cpQuery = EntityQueryBuilder.from(DatabaseConnectionPool.T).where().property(DatabaseConnectionPool.deploymentStatus)
				.eq(DeploymentStatus.deployed).done();
		List<DatabaseConnectionPool> list = cortexSession.queryDetached().entities(cpQuery).list();
		result.getPoolList().addAll(list);

		return result;
	}

	public StatementResult executeSqlStatement(@SuppressWarnings("unused") ServiceRequestContext requestContext, ExecuteSqlStatement request) {

		PersistenceGmSession cortexSession = cortexSessionSupplier.get();
		String statements = request.getStatement();
		String externalId = request.getConnectorExternalId();
		final CommitStyle commitStyle = request.getCommitStyle() != null ? request.getCommitStyle() : CommitStyle.NONE;

		DatabaseConnectionPool cp = getConnector(cortexSession, request);

		StatementResult result = StatementResult.T.create();

		List<String> statementList = new ArrayList<>();
		CSVFormat sqlFormat = CSVFormat.Builder.create().setIgnoreEmptyLines(true).setDelimiter(';').setQuote('\'').setSkipHeaderRecord(false)
				.setTrim(true).build();
		CSVParser parser;
		try {
			parser = CSVParser.parse(statements, sqlFormat);
			for (CSVRecord csvRecord : parser) {
				for (int i = 0; i < csvRecord.size(); ++i) {
					statementList.add(csvRecord.get(i));
				}
			}
		} catch (Exception e) {
			logger.warn(() -> "Could not parse statements: " + statements, e);
			statementList.clear();
			statementList.add(statements);
		}

		DataSource dataSource = deployRegistry.resolve(cp, DatabaseConnectionPool.T);
		if (dataSource != null) {

			try (Connection con = dataSource.getConnection()) {

				boolean oldAutoCommit = con.getAutoCommit();
				con.setAutoCommit(false);
				try {
					for (String statement : statementList) {

						if (StringTools.isBlank(statement)) {
							continue;
						}

						try (Statement stmt = con.createStatement()) {

							StatementResultEntry resultEntry = StatementResultEntry.T.create();
							result.getEntries().add(resultEntry);
							resultEntry.setStatement(statement);

							executeStatement(commitStyle, statement, stmt, resultEntry);

						} // try with statement resource

						if (commitStyle == CommitStyle.INDIVIDUAL) {
							con.commit();
						}
					} // for

					if (commitStyle == CommitStyle.COMPLETE) {
						con.commit();
					} else if (commitStyle == CommitStyle.NONE) {
						con.rollback();
					}
				} catch (Exception e) {
					try {
						con.rollback();
					} catch (Exception re) {
						logger.debug(() -> "Error while trying to rollback", re);
						e.addSuppressed(re);
					}
					throw e;
				} finally {
					con.setAutoCommit(oldAutoCommit);
				}

			} catch (Exception e) {
				throw Exceptions.unchecked(e, "Error while executing " + statements + " on " + externalId);
			}

		} else {
			throw new IllegalStateException("Could not resolve DB connector " + externalId);
		}

		return result;
	}

	private void executeStatement(final CommitStyle commitStyle, String statement, Statement jdbcStatement, StatementResultEntry resultEntry)
			throws SQLException, IOException {
		if (statement.toLowerCase().startsWith("select")) {
			logger.debug(() -> "Statement " + statement + " appears to be a query. Wrapping it with a transaction and commit: " + commitStyle);
			try (ResultSet resultSet = jdbcStatement.executeQuery(statement)) {
				resultEntry.setStatementResult(true);

				ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
				int columnCount = resultSetMetaData.getColumnCount();
				List<String> columnNames = new ArrayList<>();
				for (int i = 1; i <= columnCount; ++i) {
					columnNames.add(resultSetMetaData.getColumnLabel(i));
				}

				String[] headers = columnNames.toArray(new String[] {});
				CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader(headers).build();
				try (StringWriter stringWriter = new StringWriter();
						BufferedWriter writer = new BufferedWriter(stringWriter);
						CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat);) {
					int counter = 0;
					while (resultSet.next()) {
						List<Object> values = new ArrayList<>();
						for (int i = 1; i <= columnCount; ++i) {
							values.add(resultSet.getObject(i));
						}
						csvPrinter.printRecord(values);
						counter++;
					}
					csvPrinter.flush();
					csvPrinter.close();
					writer.close();
					stringWriter.close();
					String csvContent = stringWriter.toString();
					logger.debug("Created " + counter + " CSV entries with a total size of " + csvContent.length() + " characters.");
					resultEntry.setResultCsv(csvContent);
				}

			}

		} else {
			logger.debug(
					() -> "Statement " + statement + " does not appear to be a query. Wrapping it with a transaction and commit: " + commitStyle);
			boolean execute = jdbcStatement.execute(statement);
			resultEntry.setStatementResult(execute);

		}
	}

	public DatabaseAnalysis analyzeDatabase(@SuppressWarnings("unused") ServiceRequestContext requestContext, AnalyzeDatabase request) {

		PersistenceGmSession cortexSession = cortexSessionSupplier.get();
		List<DatabaseConnectionPool> connectors = getUniqueDatabases(getConnectors(cortexSession, request));

		DatabaseAnalysis result = DatabaseAnalysis.T.create();
		for (DatabaseConnectionPool connector : connectors) {

			DatabaseInformation dbInfo = DatabaseInformation.T.create();
			result.getInformationPerDatabase().put(connector.getExternalId(), dbInfo);
			StringBuilder info = new StringBuilder();

			info.append("Checking Connection Pool " + connector.getExternalId() + "\n");

			try {
				DataSource dataSource = deployRegistry.resolve(connector, DatabaseConnectionPool.T);
				if (dataSource != null) {

					checkDataSource(connector, dataSource, info, dbInfo);

				} else {
					info.append("Could not resolve DB connector " + connector.getExternalId() + "\n");
				}
			} catch (Exception e) {
				info.append(Exceptions.stringify(e) + "\n");
			}

			dbInfo.setInformation(info.toString());
		}

		return result;
	}

	protected DatabaseConnectionPool getConnector(PersistenceGmSession cortexSession, HasConnectorId hci) {
		String connectorExternalId = hci.getConnectorExternalId();
		if (StringTools.isBlank(connectorExternalId)) {
			throw new IllegalArgumentException("Please provide an external ID of a DB connector.");
		}
		//@formatter:off
		EntityQuery cpQuery = EntityQueryBuilder.from(DatabaseConnectionPool.T)
				.where()
					.conjunction()
						.property(DatabaseConnectionPool.deploymentStatus).eq(DeploymentStatus.deployed)
						.property(DatabaseConnectionPool.externalId).eq(connectorExternalId)
					.close()
				.done();
		//@formatter:on
		DatabaseConnectionPool cp = cortexSession.query().entities(cpQuery).first();
		if (cp == null) {
			throw new IllegalArgumentException("Could not find a deployed DB connector with external ID " + connectorExternalId + ".");
		}
		return cp;
	}
	protected List<DatabaseConnectionPool> getConnectors(PersistenceGmSession cortexSession, HasConnectorId hci) {
		String connectorExternalId = hci.getConnectorExternalId();
		if (StringTools.isBlank(connectorExternalId)) {
			//@formatter:off
			EntityQuery cpQuery = EntityQueryBuilder.from(DatabaseConnectionPool.T)
					.where()
						.property(DatabaseConnectionPool.deploymentStatus).eq(DeploymentStatus.deployed)
					.done();
			//@formatter:on
			return cortexSession.query().entities(cpQuery).list();
		} else {
			//@formatter:off
			EntityQuery cpQuery = EntityQueryBuilder.from(DatabaseConnectionPool.T)
					.where()
						.conjunction()
							.property(DatabaseConnectionPool.deploymentStatus).eq(DeploymentStatus.deployed)
							.property(DatabaseConnectionPool.externalId).eq(connectorExternalId)
						.close()
					.done();
			//@formatter:on
			DatabaseConnectionPool cp = cortexSession.query().entities(cpQuery).first();
			if (cp == null) {
				throw new IllegalArgumentException("Could not find a deployed DB connector with external ID " + connectorExternalId + ".");
			}
			return List.of(cp);
		}
	}

	public ForeignIndicesReport createForeignKeyIndices(@SuppressWarnings("unused") ServiceRequestContext requestContext,
			CreateForeignKeyIndices request) {

		PersistenceGmSession cortexSession = cortexSessionSupplier.get();
		List<DatabaseConnectionPool> connectors = getUniqueDatabases(getConnectors(cortexSession, request));

		ForeignIndicesReport result = ForeignIndicesReport.T.create();

		for (DatabaseConnectionPool connector : connectors) {

			StringBuilder info = new StringBuilder();

			DataSource dataSource = deployRegistry.resolve(connector, DatabaseConnectionPool.T);
			if (dataSource != null) {

				createIndices(connector, dataSource, info, request.getDryMode());

			} else {
				info.append("Could not resolve DB connector " + connector.getExternalId() + "\n");
			}

			result.getReportPerDatabase().put(connector.getExternalId(), info.toString());
		}

		return result;
	}

	private void createIndices(DatabaseConnectionPool connector, DataSource dataSource, StringBuilder info, boolean dryMode) {

		Connection con = null;
		try {
			con = dataSource.getConnection();
			DatabaseMetaData dmd = con.getMetaData();
			String productName = dmd.getDatabaseProductName();
			logger.debug(() -> "Connector " + connector.getExternalId() + " has product name " + productName);
			DatabaseExpert expert = getDatabaseExpert(productName);
			if (expert == null) {
				info.append("Could not find an expert for product name " + productName + "\n");
			} else {
				String report = expert.createMissingIndices(con, dryMode);
				info.append(report + "\n");
			}

		} catch (Exception e) {
			info.append(Exceptions.stringify(e));
		} finally {
			IOTools.closeCloseable(con, logger);
		}
	}

	private void checkDataSource(DatabaseConnectionPool connector, DataSource dataSource, StringBuilder info, DatabaseInformation dbInfo) {

		Connection con = null;
		try {
			con = dataSource.getConnection();
			DatabaseMetaData dmd = con.getMetaData();
			String productName = dmd.getDatabaseProductName();
			logger.debug(() -> "Connector " + connector.getExternalId() + " has product name " + productName);
			DatabaseInformationQueries dbInfoQueries = getDatabaseInformationQueries(productName);
			if (dbInfoQueries == null) {
				info.append("Could not find analytical queries for product name " + productName + "\n");
			} else {
				Map<String, String> queries = dbInfoQueries.getInformationQueries();
				for (Map.Entry<String, String> entry : queries.entrySet()) {

					String desc = entry.getKey();
					String query = entry.getValue();
					StringBuilder sb = new StringBuilder();
					List<String> lines = StringTools.getLines(query);
					for (String line : lines) {
						if (line.trim().length() > 0) {
							sb.append("-- " + line + "\n");
						}
					}
					sb.append("\n\n");

					PreparedStatement st = null;
					ResultSet rs = null;
					try {
						st = con.prepareStatement(query);
						rs = st.executeQuery();

						ResultSetMetaData metadata = rs.getMetaData();
						int columnCount = metadata.getColumnCount();
						for (int i = 1; i <= columnCount; i++) {
							if (i > 1) {
								sb.append(", ");
							}
							sb.append(metadata.getColumnName(i));
						}
						sb.append("\n");

						while (rs.next()) {
							for (int i = 1; i <= columnCount; i++) {
								if (i > 1) {
									sb.append(", ");
								}
								sb.append(rs.getString(i));
							}
							sb.append("\n");
						}
					} finally {
						IOTools.closeCloseable(rs, logger);
						IOTools.closeCloseable(st, logger);
					}

					dbInfo.getQueryResults().put(desc, sb.toString());

				}
			}
		} catch (Exception e) {
			info.append(Exceptions.stringify(e));
		} finally {
			IOTools.closeCloseable(con, logger);
		}

	}

	private DatabaseInformationQueries getDatabaseInformationQueries(String productName) {
		if (informationQueries == null) {
			return null;
		}
		for (Map.Entry<String, DatabaseInformationQueries> entry : informationQueries.entrySet()) {
			String regex = entry.getKey();
			if (productName.matches(regex)) {
				return entry.getValue();
			}
		}
		return null;
	}

	private DatabaseExpert getDatabaseExpert(String productName) {
		if (expertMap == null) {
			return null;
		}
		for (Map.Entry<String, DatabaseExpert> entry : expertMap.entrySet()) {
			String regex = entry.getKey();
			if (productName.matches(regex)) {
				return entry.getValue();
			}
		}
		return null;
	}

	private List<DatabaseConnectionPool> getUniqueDatabases(List<DatabaseConnectionPool> list) {
		if (list == null || list.isEmpty()) {
			return Collections.EMPTY_LIST;
		}
		if (list.size() == 1) {
			return list;
		}
		Map<String, DatabaseConnectionPool> uniquePools = new HashMap<>();
		list.forEach(c -> {
			if (c instanceof ConfiguredDatabaseConnectionPool) {
				ConfiguredDatabaseConnectionPool cc = (ConfiguredDatabaseConnectionPool) c;
				DatabaseConnectionDescriptor connectionDescriptor = cc.getConnectionDescriptor();
				String key = null;
				if (connectionDescriptor instanceof GenericDatabaseConnectionDescriptor) {
					GenericDatabaseConnectionDescriptor gcc = (GenericDatabaseConnectionDescriptor) connectionDescriptor;
					key = gcc.getUrl();
				} else if (connectionDescriptor instanceof MssqlConnectionDescriptor) {
					MssqlConnectionDescriptor mcc = (MssqlConnectionDescriptor) connectionDescriptor;
					key = mcc.getHost() + ":" + mcc.getDatabase() + ":" + mcc.getInstance() + ":" + mcc.getUser();
				} else if (connectionDescriptor instanceof OracleConnectionDescriptor) {
					OracleConnectionDescriptor occ = (OracleConnectionDescriptor) connectionDescriptor;
					key = occ.getHost() + ":" + occ.getServiceName() + ":" + occ.getSid() + ":" + occ.getUser();
				} else {
					key = c.getExternalId();
				}
				if (!uniquePools.containsKey(key)) {
					uniquePools.put(key, c);
				}
			} else {
				uniquePools.put(c.getExternalId(), c);
			}
		});

		return new ArrayList<>(uniquePools.values());
	}

	@Required
	@Configurable
	public void setDeployRegistry(DeployRegistry deployRegistry) {
		this.deployRegistry = deployRegistry;
	}
	@Required
	@Configurable
	public void setInformationQueries(Map<String, DatabaseInformationQueries> informationQueries) {
		this.informationQueries = informationQueries;
	}
	@Required
	@Configurable
	public void setCortexSessionSupplier(Supplier<PersistenceGmSession> cortexSessionSupplier) {
		this.cortexSessionSupplier = cortexSessionSupplier;
	}
	@Required
	@Configurable
	public void setExpertMap(Map<String, DatabaseExpert> expertMap) {
		this.expertMap = expertMap;
	}
	@Configurable
	public void setAllowedRoles(Set<String> allowedRoles) {
		if (allowedRoles != null && !allowedRoles.isEmpty()) {
			this.allowedRoles = allowedRoles;
		}
	}

}
