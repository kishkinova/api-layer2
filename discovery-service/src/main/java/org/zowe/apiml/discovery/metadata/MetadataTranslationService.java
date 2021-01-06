/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.discovery.metadata;

import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.zowe.apiml.auth.AuthenticationScheme;

import java.util.Map;
import java.util.stream.Collectors;

import static org.zowe.apiml.constants.EurekaMetadataDefinition.*;

/**
 * Translation service for Eureka metadata
 */
@Service
public class MetadataTranslationService {

    /**
     * Translates service instance Eureka metadata from older versions to the current version
     *
     * @param metadata to be translated
     */
    public void translateMetadata(String serviceId, Map<String, String> metadata) {
        // Version check
        String version = metadata.get(VERSION);
        if (version == null) {
            translateV1toV2(metadata);
        }

        updateZosmfAuthentication(serviceId, metadata);
    }

    private void translateV1toV2(Map<String, String> metadata) {
        // Routing
        translateRoutes(metadata);

        // Catalog
        translateParameter(CATALOG_ID_V1, CATALOG_ID, metadata);
        translateParameter(CATALOG_VERSION_V1, CATALOG_VERSION, metadata);
        translateParameter(CATALOG_TITLE_V1, CATALOG_TITLE, metadata);
        translateParameter(CATALOG_DESCRIPTION_V1, CATALOG_DESCRIPTION, metadata);

        // Service
        translateParameter(SERVICE_TITLE_V1, SERVICE_TITLE, metadata);
        translateParameter(SERVICE_DESCRIPTION_V1, SERVICE_DESCRIPTION, metadata);

        // Api-version
        metadata.remove(API_VERSION_PROPERTIES_BASE_PACKAGE_V1);
        metadata.remove(API_VERSION_PROPERTIES_TITLE_V1);
        metadata.remove(API_VERSION_PROPERTIES_VERSION_V1);
        metadata.remove(API_VERSION_PROPERTIES_DESCRIPTION_V1);

        // Other
        metadata.remove(ENABLE_APIDOC_V1);
    }

    private void translateRoutes(Map<String, String> metadata) {
        Map<String, String> newRoutes = metadata.entrySet().stream()
            .filter(
                entry -> entry.getKey().contains(ROUTES_V1)
            )
            .collect(
                Collectors.toMap(this::translateRouteMapKey, Map.Entry::getValue)
            );

        metadata.putAll(newRoutes);
        metadata.keySet().removeIf(key -> key.contains(ROUTES_V1));
    }

    private String translateRouteMapKey(Map.Entry<String, String> map) {
        return map.getKey()
            .replace(ROUTES_V1, ROUTES)
            .replace(ROUTES_GATEWAY_URL_V1, ROUTES_GATEWAY_URL)
            .replace(ROUTES_SERVICE_URL_V1, ROUTES_SERVICE_URL);
    }

    private void translateParameter(String oldParameter, String newParameter, Map<String, String> metadata) {
        String parameterValue = metadata.get(oldParameter);
        if (parameterValue != null) {
            metadata.remove(oldParameter);
            metadata.put(newParameter, parameterValue);
        }
    }

    /**
     * This method support automatically mapping of z/OSMF's authentication scheme. It means, this method set the
     * right authentication scheme to z/OSMF on registration if value is missing in the static service definition.
     *
     * @param serviceId Id of service to check (if contains zosmf ignoring case it will be applied)
     * @param metadata metadata of service
     */
    protected void updateZosmfAuthentication(String serviceId, Map<String, String> metadata) {
        if (!StringUtils.containsIgnoreCase(serviceId, "zosmf")) return;
        if (metadata.containsKey(AUTHENTICATION_SCHEME)) return;

        metadata.put(AUTHENTICATION_SCHEME, AuthenticationScheme.ZOSMF.getScheme());
    }

}
