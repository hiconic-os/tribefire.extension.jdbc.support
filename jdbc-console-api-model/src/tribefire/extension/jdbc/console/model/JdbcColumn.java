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

import com.braintribe.model.generic.GenericEntity;
import com.braintribe.model.generic.reflection.EntityType;
import com.braintribe.model.generic.reflection.EntityTypes;

public interface JdbcColumn extends GenericEntity {
	EntityType<JdbcColumn> T = EntityTypes.T(JdbcColumn.class);

	int getIndex();
	void setIndex(int index);

	String getName();
	void setName(String name);

	String getLabel();
	void setLabel(String label);

	int getJdbcType();
	void setJdbcType(int jdbcType);

	String getDatabaseTypeName();
	void setDatabaseTypeName(String databaseTypeName);

	JdbcValueKind getValueKind();
	void setValueKind(JdbcValueKind valueKind);

	boolean getNullable();
	void setNullable(boolean nullable);
}
