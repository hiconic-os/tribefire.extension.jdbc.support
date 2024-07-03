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
package tribefire.extension.jdbc.support.initializer;

import com.braintribe.logging.Logger;
import com.braintribe.model.jdbc.suppport.service.DatabaseInformation;
import com.braintribe.model.jdbc.suppport.service.JdbcSupportRequest;
import com.braintribe.model.meta.data.prompt.Outline;
import com.braintribe.model.processing.meta.editor.ModelMetaDataEditor;
import com.braintribe.model.processing.session.api.collaboration.PersistenceInitializationContext;
import com.braintribe.wire.api.module.WireTerminalModule;

import tribefire.cortex.initializer.support.api.WiredInitializerContext;
import tribefire.cortex.initializer.support.impl.AbstractInitializer;
import tribefire.extension.jdbc.support.initializer.wire.JdbcSupportInitializerModuleWireModule;
import tribefire.extension.jdbc.support.initializer.wire.contract.JdbcSupportInitializerContract;
import tribefire.extension.jdbc.support.initializer.wire.contract.JdbcSupportInitializerMainContract;

/**
 * <p>
 * This {@link AbstractInitializer initializer} initializes targeted accesses with our custom instances available from
 * initializer's contracts.
 * </p>
 */
public class JdbcSupportInitializer extends AbstractInitializer<JdbcSupportInitializerMainContract> {

	private final static Logger logger = Logger.getLogger(JdbcSupportInitializer.class);

	@Override
	public WireTerminalModule<JdbcSupportInitializerMainContract> getInitializerWireModule() {
		return JdbcSupportInitializerModuleWireModule.INSTANCE;
	}

	@Override
	public void initialize(PersistenceInitializationContext context, WiredInitializerContext<JdbcSupportInitializerMainContract> initializerContext,
			JdbcSupportInitializerMainContract initializerMainContract) {

		JdbcSupportInitializerContract initializer = initializerMainContract.initializerContract();
		if (initializerMainContract.propertiesContract().JDBC_SUPPORT_ENABLE()) {
			logger.debug(() -> "JDBC Support module is enabled.");
			initializer.apiServiceDomain();
			initializer.serviceRequestProcessor();
			addMetaDataToModelsCommon(context, initializerMainContract);

			initializerMainContract.metadata().configureDdraMappings();
		} else {
			logger.debug(() -> "JDBC Support module is not enabled.");
		}

	}

	private void addMetaDataToModelsCommon(PersistenceInitializationContext context, JdbcSupportInitializerMainContract initializerMainContract) {

		ModelMetaDataEditor modelEditor = initializerMainContract.tfPlatform().modelApi()
				.newMetaDataEditor(initializerMainContract.initializerModelsContract().configuredServiceModel()).done();

		modelEditor.onEntityType(JdbcSupportRequest.T).addMetaData(initializerMainContract.initializerContract().serviceProcessWith());

		Outline outline = context.getSession().create(Outline.T);

		modelEditor.onEntityType(DatabaseInformation.T).addPropertyMetaData(DatabaseInformation.information, outline);
		modelEditor.onEntityType(DatabaseInformation.T).addPropertyMetaData(DatabaseInformation.queryResults, outline);
	}

}
