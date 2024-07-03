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

import com.braintribe.wire.api.space.WireSpace;

import tribefire.cortex.initializer.support.integrity.wire.contract.CoreInstancesContract;
import tribefire.module.wire.contract.TribefirePlatformContract;

/**
 * <p>
 * This is the main {@link WireSpace Wire contract} of the initializer which exposes all the relevant contracts to the
 * "outside" world (i.e. initializer contracts and the ones used from wire module dependencies).
 * </p>
 */
public interface JdbcSupportInitializerMainContract extends WireSpace {

	JdbcSupportInitializerContract initializerContract();

	JdbcSupportInitializerModelsContract initializerModelsContract();

	RuntimePropertiesContract propertiesContract();

	ExistingInstancesContract existingInstancesContract();

	CoreInstancesContract coreInstancesContract();

	JdbcSupportInitializerMetaDataContract metadata();

	TribefirePlatformContract tfPlatform();

}
