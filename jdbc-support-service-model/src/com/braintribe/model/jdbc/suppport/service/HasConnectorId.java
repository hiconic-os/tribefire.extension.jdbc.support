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
package com.braintribe.model.jdbc.suppport.service;

import com.braintribe.model.generic.GenericEntity;
import com.braintribe.model.generic.annotation.meta.Description;
import com.braintribe.model.generic.annotation.meta.Name;
import com.braintribe.model.generic.reflection.EntityType;
import com.braintribe.model.generic.reflection.EntityTypes;

public interface HasConnectorId extends GenericEntity {

	EntityType<HasConnectorId> T = EntityTypes.T(HasConnectorId.class);

	@Name("External ID")
	@Description("The external ID of the connector to be used. If left blank, some request may iterate over all connectors.")
	String getConnectorExternalId();
	void setConnectorExternalId(String connectorExternalId);

}
