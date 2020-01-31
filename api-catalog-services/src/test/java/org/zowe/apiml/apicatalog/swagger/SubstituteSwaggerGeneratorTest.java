/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.apicatalog.swagger;

import org.zowe.apiml.eurekaservice.client.util.EurekaMetadataParser;
import org.zowe.apiml.config.ApiInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.PortType;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.zowe.apiml.constants.EurekaMetadataDefinition.*;
import static org.junit.Assert.assertTrue;

public class SubstituteSwaggerGeneratorTest {

    @Test
    public void testParseApiInfo() {
        String gatewayScheme = "https";
        String gatewayHost = "localhost:8080";

        Map<String, String> metadata = new HashMap<>();
        metadata.put(API_INFO + ".1." + API_INFO_GATEWAY_URL, "api/v1");
        metadata.put(API_INFO + ".1." + API_INFO_DOCUMENTATION_URL, "https://doc.ca.com/api");

        List<ApiInfo> info = new EurekaMetadataParser().parseApiInfo(metadata);

        InstanceInfo service = InstanceInfo.Builder.newBuilder().setAppName("serviceId").setHostName("localhost")
            .setSecurePort(8080).enablePort(PortType.SECURE, true).setMetadata(metadata).build();

        String result = new SubstituteSwaggerGenerator().generateSubstituteSwaggerForService(service,
            info.get(0), gatewayScheme, gatewayHost);
        assertTrue(result.contains("https://doc.ca.com/api"));
    }
}
