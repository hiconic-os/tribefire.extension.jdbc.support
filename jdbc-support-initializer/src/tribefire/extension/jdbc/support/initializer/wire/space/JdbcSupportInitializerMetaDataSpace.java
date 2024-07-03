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