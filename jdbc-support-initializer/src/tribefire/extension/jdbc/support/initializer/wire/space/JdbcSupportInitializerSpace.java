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
package tribefire.extension.jdbc.support.initializer.wire.space;

import java.util.HashMap;
import java.util.Map;

import com.braintribe.model.extensiondeployment.meta.ProcessWith;
import com.braintribe.model.jdbc.support.deployment.JdbcSupportServiceProcessor;
import com.braintribe.model.jdbc.support.deployment.db.DatabaseInformationQueries;
import com.braintribe.model.jdbc.suppport.service.JdbcSupportConstants;
import com.braintribe.model.service.domain.ServiceDomain;
import com.braintribe.wire.api.annotation.Import;
import com.braintribe.wire.api.annotation.Managed;

import tribefire.cortex.initializer.support.wire.space.AbstractInitializerSpace;
import tribefire.extension.jdbc.support.initializer.wire.contract.ExistingInstancesContract;
import tribefire.extension.jdbc.support.initializer.wire.contract.JdbcSupportInitializerContract;
import tribefire.extension.jdbc.support.initializer.wire.contract.JdbcSupportInitializerModelsContract;
import tribefire.extension.jdbc.support.initializer.wire.contract.RuntimePropertiesContract;

/**
 * @see JdbcSupportInitializerContract
 */
@Managed
public class JdbcSupportInitializerSpace extends AbstractInitializerSpace implements JdbcSupportInitializerContract {

	@Import
	private RuntimePropertiesContract properties;

	@Import
	private ExistingInstancesContract existingInstances;

	@Import
	private JdbcSupportInitializerModelsContract models;

	@Override
	@Managed
	public ServiceDomain apiServiceDomain() {
		ServiceDomain bean = create(ServiceDomain.T);
		bean.setExternalId(JdbcSupportConstants.SERVICE_DOMAIN);
		bean.setName("Database Support");
		bean.setServiceModel(models.configuredServiceModel());
		return bean;
	}

	@Override
	@Managed
	public JdbcSupportServiceProcessor serviceRequestProcessor() {
		JdbcSupportServiceProcessor bean = create(JdbcSupportServiceProcessor.T);
		bean.setModule(existingInstances.module());
		bean.setExternalId("jdbc.support.serviceProcessor");
		bean.setName("JDBC Support Service Processor");
		bean.setInformationQueries(informationQueries());
		bean.setAllowedRoles(properties.JDBC_SUPPORT_ALLOWED_ROLES());
		return bean;
	}

	private Map<String, DatabaseInformationQueries> informationQueries() {

		Map<String, DatabaseInformationQueries> queriesPerDb = new HashMap<>();
		queriesPerDb.put("(?i)postgre.*", postgresqlInformationQueries());

		return queriesPerDb;
	}

	private DatabaseInformationQueries postgresqlInformationQueries() {

		DatabaseInformationQueries queries = DatabaseInformationQueries.T.create();
		Map<String, String> map = queries.getInformationQueries();

		//@formatter:off
		map.put("Current Activity", "SELECT pid, age(clock_timestamp(), query_start), usename, query\n" + 
				"FROM pg_stat_activity\n" + 
				"WHERE query != '<IDLE>' AND query NOT ILIKE '%pg_stat_activity%'\n" + 
				"ORDER BY query_start desc;");
		
		map.put("Locks", "SELECT * FROM pg_locks pl LEFT JOIN pg_stat_activity psa ON pl.pid = psa.pid;");
		
		map.put("Size of DBs", "SELECT d.datname AS Name, pg_catalog.pg_get_userbyid(d.datdba) AS Owner,\n" + 
				"  CASE WHEN pg_catalog.has_database_privilege(d.datname, 'CONNECT')\n" + 
				"    THEN pg_catalog.pg_size_pretty(pg_catalog.pg_database_size(d.datname))\n" + 
				"    ELSE 'No Access'\n" + 
				"  END AS SIZE\n" + 
				"FROM pg_catalog.pg_database d\n" + 
				"ORDER BY\n" + 
				"  CASE WHEN pg_catalog.has_database_privilege(d.datname, 'CONNECT')\n" + 
				"    THEN pg_catalog.pg_database_size(d.datname)\n" + 
				"    ELSE NULL\n" + 
				"  END;");
		
		map.put("Size of tables", "SELECT nspname || '.' || relname AS \"relation\",\n" + 
				"   pg_size_pretty(pg_total_relation_size(C.oid)) AS \"total_size\"\n" + 
				" FROM pg_class C\n" + 
				" LEFT JOIN pg_namespace N ON (N.oid = C.relnamespace)\n" + 
				" WHERE nspname NOT IN ('pg_catalog', 'information_schema')\n" + 
				"   AND C.relkind <> 'i'\n" + 
				"   AND nspname !~ '^pg_toast'\n" + 
				" ORDER BY pg_total_relation_size(C.oid) DESC;");
		
		map.put("Table Scans", "select schemaname, relname, seq_scan from pg_stat_user_tables order by seq_scan desc;");
		
		map.put("Number of Rows", "SELECT schemaname,relname,n_live_tup \n" + 
				"  FROM pg_stat_user_tables \n" + 
				"  ORDER BY n_live_tup DESC;");
		//@formatter:on

		return queries;
	}

	@Override
	@Managed
	public ProcessWith serviceProcessWith() {
		ProcessWith bean = create(ProcessWith.T);
		bean.setProcessor(serviceRequestProcessor());
		return bean;
	}
}
