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
package tribefire.extension.jdbc.support.initializer.wire.contract;

import com.braintribe.model.meta.GmMetaModel;
import com.braintribe.wire.api.space.WireSpace;

import tribefire.cortex.initializer.support.impl.lookup.GlobalId;
import tribefire.cortex.initializer.support.impl.lookup.InstanceLookup;

/**
 * <p>
 * This {@link WireSpace Wire contract} provides lookups on already existing instances. <br>
 * It exposes instances like:
 * <ul>
 * <li>Models which are coming from ModelPriming assets</li>
 * <li>Resources coming from ResourcePriming assets</li>
 * </ul>
 * </p>
 */
@InstanceLookup(lookupOnly = true)
public interface ExistingInstancesContract extends WireSpace {

	@GlobalId("model:tribefire.extension.jdbc.support:jdbc-support-deployment-model")
	GmMetaModel deploymentModel();

	@GlobalId("model:tribefire.extension.jdbc.support:jdbc-support-service-model")
	GmMetaModel serviceModel();

	@GlobalId("model:tribefire.extension.jdbc.support:jdbc-support-model")
	GmMetaModel dataModel();

	@GlobalId("module://tribefire.extension.jdbc.support:jdbc-support-module")
	com.braintribe.model.deployment.Module module();

}
