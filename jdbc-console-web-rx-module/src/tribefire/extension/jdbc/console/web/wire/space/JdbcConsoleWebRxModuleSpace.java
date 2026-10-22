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
package tribefire.extension.jdbc.console.web.wire.space;

import static com.braintribe.gm.model.reason.UnsatisfiedMaybeTunneling.getOrTunnel;

import java.util.LinkedHashSet;
import java.util.Set;

import com.braintribe.wire.api.annotation.Import;
import com.braintribe.wire.api.annotation.Managed;

import hiconic.rx.module.api.wire.RxModuleContract;
import hiconic.rx.module.api.wire.RxPlatformContract;
import hiconic.rx.web.server.api.WebAppNavigationEntry;
import hiconic.rx.web.server.api.WebServerContract;
import tribefire.extension.jdbc.console.model.JdbcConsoleConfiguration;

@Managed
public class JdbcConsoleWebRxModuleSpace implements RxModuleContract {
	public static final String WEBAPP_PATH = "jdbc-console";

	@Import private WebServerContract webServer;
	@Import private RxPlatformContract platform;

	@Override
	public void onDeploy() {
		webServer.addWebAppRuntimeConfiguration(WEBAPP_PATH, java.util.Map::of);
		Set<String> roles = visibleRoles();
		if (!roles.isEmpty())
			webServer.addWebAppNavigation(new WebAppNavigationEntry(WEBAPP_PATH, "JDBC Console",
					"Inspect and, when authorized, modify configured databases.", roles, 110));
	}

	private Set<String> visibleRoles() {
		JdbcConsoleConfiguration config = configuration();
		Set<String> roles = new LinkedHashSet<>();
		if (config.getReaderRoles() != null)
			roles.addAll(config.getReaderRoles());
		if (config.getWriterRoles() != null)
			roles.addAll(config.getWriterRoles());
		return roles;
	}

	@Managed
	private JdbcConsoleConfiguration configuration() {
		return getOrTunnel(platform.configuration().readConfig(JdbcConsoleConfiguration.T));
	}
}
