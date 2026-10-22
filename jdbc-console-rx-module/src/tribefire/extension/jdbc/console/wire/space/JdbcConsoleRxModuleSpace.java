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
package tribefire.extension.jdbc.console.wire.space;

import static com.braintribe.gm.model.reason.UnsatisfiedMaybeTunneling.getOrTunnel;

import java.util.LinkedHashSet;
import java.util.Set;

import com.braintribe.wire.api.annotation.Import;
import com.braintribe.wire.api.annotation.Managed;

import hiconic.rx.db.module.api.DatabaseContract;
import hiconic.rx.module.api.service.ServiceDomainConfigurations;
import hiconic.rx.module.api.wire.RxModuleContract;
import hiconic.rx.module.api.wire.RxPlatformContract;
import tribefire.extension.jdbc.console.model.JdbcConsoleConfiguration;
import tribefire.extension.jdbc.console.model.JdbcConsoleConstants;
import tribefire.extension.jdbc.console.model.api.JdbcConsoleRequest;
import tribefire.extension.jdbc.console.processing.JdbcConsoleProcessor;

@Managed
public class JdbcConsoleRxModuleSpace implements RxModuleContract {
	@Import private RxPlatformContract platform;
	@Import private DatabaseContract databases;

	@Override
	public void configureServiceDomains(ServiceDomainConfigurations configurations) {
		JdbcConsoleConfiguration config = configuration();
		var domain = configurations.byId(JdbcConsoleConstants.SERVICE_DOMAIN);
		domain.setDisplayName("JDBC Console");
		domain.allowRoles(allRoles(config));
		domain.bindRequest(JdbcConsoleRequest.T, this::processor);
	}

	private static Set<String> allRoles(JdbcConsoleConfiguration config) {
		Set<String> result = new LinkedHashSet<>();
		if (config.getReaderRoles() != null)
			result.addAll(config.getReaderRoles());
		if (config.getWriterRoles() != null)
			result.addAll(config.getWriterRoles());
		return result;
	}

	@Managed
	private JdbcConsoleConfiguration configuration() {
		return getOrTunnel(platform.configuration().readConfig(JdbcConsoleConfiguration.T));
	}

	@Managed
	private JdbcConsoleProcessor processor() {
		return new JdbcConsoleProcessor(databases, configuration());
	}
}
