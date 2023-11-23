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
package tribefire.extension.jdbc.support.initializer;

import com.braintribe.logging.Logger;
import com.braintribe.model.jdbc.suppport.service.DatabaseInformation;
import com.braintribe.model.jdbc.suppport.service.JdbcSupportRequest;
import com.braintribe.model.meta.data.prompt.Outline;
import com.braintribe.model.processing.meta.editor.BasicModelMetaDataEditor;
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
		ModelMetaDataEditor modelEditor = BasicModelMetaDataEditor
				.create(initializerMainContract.initializerModelsContract().configuredServiceModel()).withEtityFactory(context.getSession()::create)
				.done();
		modelEditor.onEntityType(JdbcSupportRequest.T).addMetaData(initializerMainContract.initializerContract().serviceProcessWith());

		Outline outline = context.getSession().create(Outline.T);

		modelEditor.onEntityType(DatabaseInformation.T).addPropertyMetaData(DatabaseInformation.information, outline);
		modelEditor.onEntityType(DatabaseInformation.T).addPropertyMetaData(DatabaseInformation.queryResults, outline);
	}

}
