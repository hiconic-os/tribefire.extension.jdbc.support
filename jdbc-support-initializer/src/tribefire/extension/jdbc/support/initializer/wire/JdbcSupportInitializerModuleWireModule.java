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
package tribefire.extension.jdbc.support.initializer.wire;

import static com.braintribe.wire.api.util.Lists.list;

import java.util.List;

import com.braintribe.wire.api.module.WireModule;
import com.braintribe.wire.api.module.WireTerminalModule;

import tribefire.cortex.initializer.support.integrity.wire.CoreInstancesWireModule;
import tribefire.extension.jdbc.support.initializer.wire.contract.JdbcSupportInitializerMainContract;

/**
 * <p>
 * This is the {@link WireModule Wire module} of the jdbc-support-module-initializer.
 * </p>
 */
public enum JdbcSupportInitializerModuleWireModule implements WireTerminalModule<JdbcSupportInitializerMainContract> {

	INSTANCE;

	/**
	 * <p>
	 * Declares initializer's Wire module dependencies. All external managed instances used within initializer, have
	 * Wire module providing their exposing contract declared here.
	 * </p>
	 */
	@Override
	public List<WireModule> dependencies() {
		return list(CoreInstancesWireModule.INSTANCE);
	}

}
