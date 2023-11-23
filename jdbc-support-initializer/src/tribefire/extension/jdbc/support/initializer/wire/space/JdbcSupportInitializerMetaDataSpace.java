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

import java.util.Set;

import com.braintribe.model.ddra.DdraConfiguration;
import com.braintribe.model.ddra.DdraMapping;
import com.braintribe.model.ddra.DdraUrlMethod;
import com.braintribe.model.generic.reflection.EntityType;
import com.braintribe.model.jdbc.suppport.service.AnalyzeDatabase;
import com.braintribe.model.jdbc.suppport.service.CreateForeignKeyIndices;
import com.braintribe.model.jdbc.suppport.service.ExecuteSqlStatement;
import com.braintribe.model.jdbc.suppport.service.JdbcSupportConstants;
import com.braintribe.model.jdbc.suppport.service.ListConnectors;
import com.braintribe.model.meta.GmEntityType;
import com.braintribe.model.service.api.ServiceRequest;
import com.braintribe.wire.api.annotation.Import;
import com.braintribe.wire.api.annotation.Managed;

import tribefire.cortex.initializer.support.integrity.wire.contract.CoreInstancesContract;
import tribefire.cortex.initializer.support.wire.space.AbstractInitializerSpace;
import tribefire.extension.jdbc.support.initializer.wire.contract.ExistingInstancesContract;
import tribefire.extension.jdbc.support.initializer.wire.contract.JdbcSupportInitializerMetaDataContract;
import tribefire.extension.jdbc.support.initializer.wire.contract.RuntimePropertiesContract;

@Managed
public class JdbcSupportInitializerMetaDataSpace extends AbstractInitializerSpace implements JdbcSupportInitializerMetaDataContract {

	private static final String REACHABLE = "reachable";
	public static final String TAG_ANALYSIS = "Analysis";
	public static final String TAG_ADAPTATIONS = "Adaptations";
	public static final String TAG_SQL = "SQL";

	@Import
	private ExistingInstancesContract existingInstances;

	@Import
	private CoreInstancesContract coreInstances;

	@Import
	private RuntimePropertiesContract properties;

	@Override
	public void configureDdraMappings() {
		DdraConfiguration config = lookup("ddra:config");
		Set<DdraMapping> mappings = config.getMappings();

		//@formatter:off
		mappings.add(ddraMapping(
				"analysis/analyse-db",
				DdraUrlMethod.GET,
				AnalyzeDatabase.T,
				TAG_ANALYSIS,
				null));

		mappings.add(ddraMapping(
				"analysis/connectors",
				DdraUrlMethod.GET,
				ListConnectors.T,
				TAG_ANALYSIS,
				null));

		mappings.add(ddraMapping(
				"adapt/create-foreign-key-indices",
				DdraUrlMethod.POST,
				CreateForeignKeyIndices.T,
				TAG_ADAPTATIONS,
				null));

		mappings.add(ddraMapping(
				"sql/execute-statement",
				DdraUrlMethod.POST,
				ExecuteSqlStatement.T,
				TAG_SQL,
				null));
//@formatter:on

	}

	protected DdraMapping ddraMapping(String relativePath, DdraUrlMethod method, EntityType<? extends ServiceRequest> reflectionType, String tag) {
		return ddraMapping(relativePath, method, reflectionType, tag, 1, null, false, null);
	}

	protected DdraMapping ddraMapping(String relativePath, DdraUrlMethod method, EntityType<? extends ServiceRequest> reflectionType, String tag,
			String projection) {
		return ddraMapping(relativePath, method, reflectionType, tag, projection, null);
	}

	protected DdraMapping ddraMapping(String relativePath, DdraUrlMethod method, EntityType<? extends ServiceRequest> reflectionType, String tag,
			String projection, String depth) {
		return ddraMapping(relativePath, method, reflectionType, tag, 1, projection, method == DdraUrlMethod.POST, depth);
	}

	protected DdraMapping ddraMapping(String relativePath, DdraUrlMethod method, EntityType<? extends ServiceRequest> reflectionType, String tag,
			int version, String projection, boolean announceAsMultipart, String depth) {
		GmEntityType type = lookup("type:" + reflectionType.getTypeSignature());
		String path = "/" + JdbcSupportConstants.SERVICE_DOMAIN + "/v" + version + "/" + relativePath;

		DdraMapping bean = create(DdraMapping.T);
		bean.setGlobalId("ddra:/" + method + "/" + path + "/" + JdbcSupportConstants.SERVICE_DOMAIN);
		bean.setPath(path);
		bean.setRequestType(type);
		bean.setMethod(method);
		bean.setDefaultServiceDomain(JdbcSupportConstants.SERVICE_DOMAIN);
		bean.setDefaultMimeType("application/json");
		bean.setHideSerializedRequest(true);
		bean.setAnnounceAsMultipart(announceAsMultipart);
		if (projection != null) {
			bean.setDefaultProjection(projection);
		}

		if (tag != null) {
			bean.getTags().add(tag);
		}

		if (depth != null) {
			bean.setDefaultDepth(depth);
		}

		bean.setDefaultUseSessionEvaluation(false);
		bean.setDefaultEntityRecurrenceDepth(-1);

		return bean;
	}

	@SuppressWarnings("unused")
	private DdraMapping ddraDownloadMapping(String relativePath, EntityType<? extends ServiceRequest> reflectionType, String tag, String projection) {
		DdraMapping downloadMapping = ddraMapping(relativePath, DdraUrlMethod.GET, reflectionType, tag);
		downloadMapping.setDefaultProjection(projection);
		downloadMapping.setDefaultDownloadResource(true);
		downloadMapping.setDefaultSaveLocally(true);
		return downloadMapping;
	}

	@SuppressWarnings("unused")
	private DdraMapping ddraInlineMapping(String relativePath, EntityType<? extends ServiceRequest> reflectionType, String tag, String projection) {
		DdraMapping downloadMapping = ddraMapping(relativePath, DdraUrlMethod.GET, reflectionType, tag);
		downloadMapping.setDefaultProjection(projection);
		downloadMapping.setDefaultDownloadResource(true);
		downloadMapping.setDefaultSaveLocally(false);
		return downloadMapping;
	}

}