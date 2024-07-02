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

import com.braintribe.wire.api.annotation.Import;
import com.braintribe.wire.api.annotation.Managed;

import tribefire.cortex.initializer.support.integrity.wire.contract.CoreInstancesContract;
import tribefire.cortex.initializer.support.wire.space.AbstractInitializerSpace;
import tribefire.extension.jdbc.support.initializer.wire.contract.ExistingInstancesContract;
import tribefire.extension.jdbc.support.initializer.wire.contract.JdbcSupportInitializerContract;
import tribefire.extension.jdbc.support.initializer.wire.contract.JdbcSupportInitializerMainContract;
import tribefire.extension.jdbc.support.initializer.wire.contract.JdbcSupportInitializerMetaDataContract;
import tribefire.extension.jdbc.support.initializer.wire.contract.JdbcSupportInitializerModelsContract;
import tribefire.extension.jdbc.support.initializer.wire.contract.RuntimePropertiesContract;
import tribefire.module.wire.contract.TribefirePlatformContract;

/**
 * @see JdbcSupportInitializerMainContract
 */
@Managed
public class JdbcSupportInitializerMainSpace extends AbstractInitializerSpace implements JdbcSupportInitializerMainContract {

	@Import
	private JdbcSupportInitializerContract initializer;

	@Import
	private JdbcSupportInitializerModelsContract models;

	@Import
	private ExistingInstancesContract existingInstances;

	@Import
	private CoreInstancesContract coreInstances;

	@Import
	private RuntimePropertiesContract properties;

	@Import
	private JdbcSupportInitializerMetaDataContract metadata;

	@Import
	private TribefirePlatformContract tfPlatform;

	@Override
	public JdbcSupportInitializerContract initializerContract() {
		return initializer;
	}

	@Override
	public JdbcSupportInitializerModelsContract initializerModelsContract() {
		return models;
	}

	@Override
	public ExistingInstancesContract existingInstancesContract() {
		return existingInstances;
	}

	@Override
	public CoreInstancesContract coreInstancesContract() {
		return coreInstances;
	}

	@Override
	public RuntimePropertiesContract propertiesContract() {
		return properties;
	}

	@Override
	public JdbcSupportInitializerMetaDataContract metadata() {
		return metadata;
	}

	@Override
	public TribefirePlatformContract tfPlatform() {
		return tfPlatform;
	}

}
