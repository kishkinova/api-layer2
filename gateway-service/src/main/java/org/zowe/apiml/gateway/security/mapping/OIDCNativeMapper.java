/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.security.mapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.zowe.apiml.gateway.security.service.schema.source.AuthSource;
import org.zowe.apiml.gateway.security.service.schema.source.OIDCAuthSource;
import org.zowe.apiml.message.core.MessageType;
import org.zowe.apiml.message.log.ApimlLogger;
import org.zowe.apiml.product.logging.annotations.InjectApimlLogger;
import org.zowe.commons.usermap.MapperResponse;

import static org.zowe.apiml.gateway.security.mapping.model.MapperResponse.OIDC_FAILED_MESSAGE_KEY;

@RequiredArgsConstructor
@Component("oidcMapper")
@ConditionalOnExpression("'${apiml.security.oidc.enabled:false}' == 'true' && '${apiml.security.useInternalMapper:false}' == 'true'")
public class OIDCNativeMapper implements AuthenticationMapper {

    private final NativeMapperWrapper nativeMapper;

    @Value("${apiml.security.oidc.registry:}")
    protected String registry;

    @InjectApimlLogger
    private final ApimlLogger apimlLog = ApimlLogger.empty();

    @Override
    public String mapToMainframeUserId(AuthSource authSource) {
        if (!(authSource instanceof OIDCAuthSource)) {
            apimlLog.log(MessageType.DEBUG, "The used authentication source type is {} and not OIDC", authSource.getType());
            return null;
        }
        if (StringUtils.isEmpty(registry)) {
            apimlLog.log(OIDC_FAILED_MESSAGE_KEY,
                "Missing registry name configuration. Make sure that " +
                    "'components.gateway.apiml.security.oidc.registry' is correctly set in 'zowe.yaml'.");
            return null;
        }

        final String distributedId = ((OIDCAuthSource) authSource).getDistributedId();
        if (StringUtils.isEmpty(distributedId)) {
            apimlLog.log(OIDC_FAILED_MESSAGE_KEY,
                "OIDC token is missing the distributed ID. Make sure your distributed identity provider is" +
                    " properly configured.");
            return null;
        }

        MapperResponse response = nativeMapper.getUserIDForDN(distributedId, registry);
        if (response.getRc() == 0 && StringUtils.isNotEmpty(response.getUserId())) {
            return response.getUserId();
        }

        return null;
    }
}
