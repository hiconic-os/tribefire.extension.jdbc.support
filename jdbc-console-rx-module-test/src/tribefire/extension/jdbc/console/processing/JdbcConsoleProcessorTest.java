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

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Types;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.Test;
import org.h2.jdbcx.JdbcDataSource;

import com.braintribe.gm.model.reason.Maybe;
import com.braintribe.gm.model.reason.UnsatisfiedMaybeTunneling;
import com.braintribe.model.generic.eval.Evaluator;
import com.braintribe.model.processing.service.api.ServiceRequestContext;
import com.braintribe.model.processing.service.common.context.UserSessionAspect;
import com.braintribe.model.processing.service.commons.StandardServiceRequestContextBuilder;
import com.braintribe.model.service.api.ServiceRequest;
import com.braintribe.model.usersession.UserSession;

import hiconic.rx.db.model.configuration.Database;
import hiconic.rx.db.module.api.DatabaseContract;

import tribefire.extension.jdbc.console.model.JdbcConsoleConfiguration;
import tribefire.extension.jdbc.console.model.JdbcValueKind;
import tribefire.extension.jdbc.console.model.api.ExecuteJdbcQuery;
import tribefire.extension.jdbc.console.model.api.ExecuteJdbcStatement;

public class JdbcConsoleProcessorTest {
	@Test
	public void mapsJdbcTypesToStableModelKinds() {
		assertThat(JdbcConsoleProcessor.valueKind(Types.BOOLEAN)).isEqualTo(JdbcValueKind.BOOLEAN);
		assertThat(JdbcConsoleProcessor.valueKind(Types.INTEGER)).isEqualTo(JdbcValueKind.INTEGER);
		assertThat(JdbcConsoleProcessor.valueKind(Types.BIGINT)).isEqualTo(JdbcValueKind.LONG);
		assertThat(JdbcConsoleProcessor.valueKind(Types.NUMERIC)).isEqualTo(JdbcValueKind.DECIMAL);
		assertThat(JdbcConsoleProcessor.valueKind(Types.TIMESTAMP_WITH_TIMEZONE)).isEqualTo(JdbcValueKind.INSTANT);
		assertThat(JdbcConsoleProcessor.valueKind(Types.BLOB)).isEqualTo(JdbcValueKind.BINARY);
		assertThat(JdbcConsoleProcessor.valueKind(Types.OTHER)).isEqualTo(JdbcValueKind.STRING);
	}

	@Test
	public void configurationHasSafeAndUsefulDefaults() {
		JdbcConsoleConfiguration configuration = JdbcConsoleConfiguration.T.create();

		assertThat(configuration.getReaderRoles()).isEqualTo(Set.of("jdbc-console-reader", "jdbc-console-writer"));
		assertThat(configuration.getWriterRoles()).isEqualTo(Set.of("jdbc-console-writer"));
		assertThat(configuration.getDefaultRowLimit()).isEqualTo(500);
		assertThat(configuration.getMaximumRowLimit()).isEqualTo(5000);
		assertThat(configuration.getQueryTimeoutSeconds()).isEqualTo(30);
		assertThat(configuration.getMaximumCellLength()).isEqualTo(10000);
	}

	@Test
	public void queriesTypedRowsAndReportsTruncation() throws Exception {
		JdbcDataSource dataSource = database();
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.execute("create table sample(id bigint, amount decimal(10,2), active boolean, title varchar(30))");
			statement.execute("insert into sample values (1, 12.50, true, 'one'), (2, 8.25, false, 'two')");
		}

		JdbcConsoleProcessor processor = processor(dataSource);
		ExecuteJdbcQuery request = ExecuteJdbcQuery.T.create();
		request.setDatabaseName("test-db");
		request.setStatement("select id, amount, active, title from sample order by id");
		request.setRowLimit(1);

		var result = (tribefire.extension.jdbc.console.model.JdbcQueryResult) processor.process(context("jdbc-console-reader"), request);
		assertThat(result.getColumns()).extracting(column -> column.getValueKind()).containsExactly(
				JdbcValueKind.LONG, JdbcValueKind.DECIMAL, JdbcValueKind.BOOLEAN, JdbcValueKind.STRING);
		assertThat(result.getRows()).hasSize(1);
		assertThat(result.getRows().get(0).getValues()).containsExactly(1L, new java.math.BigDecimal("12.50"), true, "one");
		assertThat(result.getTruncated()).isTrue();
	}

	@Test
	public void writerRoleCanCommitStatementsButReaderCannot() throws Exception {
		JdbcDataSource dataSource = database();
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
			statement.execute("create table sample(id integer)");
		}

		JdbcConsoleProcessor processor = processor(dataSource);
		ExecuteJdbcStatement insert = ExecuteJdbcStatement.T.create();
		insert.setDatabaseName("test-db");
		insert.setStatement("insert into sample values (7)");

		assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> processor.process(context("jdbc-console-reader"), insert)))
				.isInstanceOf(UnsatisfiedMaybeTunneling.class);
		processor.process(context("jdbc-console-writer"), insert);
		try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
				var rows = statement.executeQuery("select count(*) from sample")) {
			rows.next();
			assertThat(rows.getInt(1)).isEqualTo(1);
		}
	}

	private static JdbcConsoleProcessor processor(DataSource dataSource) {
		JdbcConsoleConfiguration configuration = JdbcConsoleConfiguration.T.create();
		return new JdbcConsoleProcessor(new StubDatabaseContract(dataSource), configuration);
	}

	private static ServiceRequestContext context(String... roles) {
		UserSession session = UserSession.T.create();
		session.setEffectiveRoles(Set.of(roles));
		return new StandardServiceRequestContextBuilder((Evaluator<ServiceRequest>) null)
				.set(UserSessionAspect.class, session).build();
	}

	private static JdbcDataSource database() {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
		return dataSource;
	}

	private record StubDatabaseContract(DataSource dataSource) implements DatabaseContract {
		@Override public Maybe<DataSource> mainDataSource() { return Maybe.complete(dataSource); }
		@Override public DataSource findDataSource(String name) { return "test-db".equals(name) ? dataSource : null; }
		@Override public Maybe<DataSource> dataSource(String name) { return Maybe.complete(findDataSource(name)); }
		@Override public DataSource deploy(Database database) { return dataSource; }
		@Override public List<Database> allDatabases() { return List.of(); }
	}
}
