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
package tribefire.extension.jdbc.console.model;

import java.util.Set;

import com.braintribe.model.generic.GenericEntity;
import com.braintribe.model.generic.annotation.Initializer;
import com.braintribe.model.generic.reflection.EntityType;
import com.braintribe.model.generic.reflection.EntityTypes;

public interface JdbcConsoleConfiguration extends GenericEntity {
	EntityType<JdbcConsoleConfiguration> T = EntityTypes.T(JdbcConsoleConfiguration.class);

	@Initializer("{'jdbc-console-reader','jdbc-console-writer'}")
	Set<String> getReaderRoles();
	void setReaderRoles(Set<String> readerRoles);

	@Initializer("{'jdbc-console-writer'}")
	Set<String> getWriterRoles();
	void setWriterRoles(Set<String> writerRoles);

	@Initializer("500")
	int getDefaultRowLimit();
	void setDefaultRowLimit(int defaultRowLimit);

	@Initializer("5000")
	int getMaximumRowLimit();
	void setMaximumRowLimit(int maximumRowLimit);

	@Initializer("30")
	int getQueryTimeoutSeconds();
	void setQueryTimeoutSeconds(int queryTimeoutSeconds);

	@Initializer("10000")
	int getMaximumCellLength();
	void setMaximumCellLength(int maximumCellLength);
}
