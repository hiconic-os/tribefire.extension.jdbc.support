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
package tribefire.extension.jdbc.support.wire.space;

import java.util.HashMap;
import java.util.Map;

import com.braintribe.model.processing.deployment.api.ExpertContext;
import com.braintribe.model.processing.jdbc.support.service.JdbcSupportServiceProcessor;
import com.braintribe.model.processing.jdbc.support.service.expert.DatabaseExpert;
import com.braintribe.model.processing.jdbc.support.service.expert.PostgresqlExpert;
import com.braintribe.wire.api.annotation.Import;
import com.braintribe.wire.api.annotation.Managed;
import com.braintribe.wire.api.space.WireSpace;

import tribefire.module.wire.contract.ResourceProcessingContract;
import tribefire.module.wire.contract.TribefireWebPlatformContract;

@Managed
public class JdbcSupportDeployablesSpace implements WireSpace {

	@Import
	private TribefireWebPlatformContract tfPlatform;

	@Import
	private ResourceProcessingContract resourceProcessing;

	@Managed
	public JdbcSupportServiceProcessor jdbcSupportServiceProcessor(
			ExpertContext<com.braintribe.model.jdbc.support.deployment.JdbcSupportServiceProcessor> context) {
		JdbcSupportServiceProcessor bean = new JdbcSupportServiceProcessor();

		com.braintribe.model.jdbc.support.deployment.JdbcSupportServiceProcessor deployable = context.getDeployable();

		bean.setDeployRegistry(tfPlatform.deployment().deployRegistry());
		bean.setInformationQueries(deployable.getInformationQueries());
		bean.setCortexSessionSupplier(tfPlatform.systemUserRelated().cortexSessionSupplier());
		bean.setExpertMap(expertMap());
		bean.setAllowedRoles(deployable.getAllowedRoles());

		return bean;
	}

	private Map<String, DatabaseExpert> expertMap() {
		Map<String, DatabaseExpert> map = new HashMap<>();
		map.put("(?i)postgre.*", postgresqlExpert());
		return map;
	}

	private DatabaseExpert postgresqlExpert() {
		PostgresqlExpert bean = new PostgresqlExpert();
		return bean;
	}

}
