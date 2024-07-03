// ============================================================================
// Copyright BRAINTRIBE TECHNOLOGY GMBH, Austria, 2002-2022
//
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
