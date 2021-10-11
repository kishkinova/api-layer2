/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.gateway.filters.post;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.*;

/**
 * This filter adds headers to the response as configured by the responding service's metadata.
 */
@Component
@RequiredArgsConstructor
public class PerServiceAddResponseHeadersFilter extends ZuulFilter {

    private final DiscoveryClient discoveryClient;

    @Override
    public String filterType() {
        return POST_TYPE;
    }

    @Override
    public int filterOrder() {
        return SEND_RESPONSE_FILTER_ORDER - 1;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Override
    public Object run() throws ZuulException {
        RequestContext context = RequestContext.getCurrentContext();
        String serviceId = (String) context.get(SERVICE_ID_KEY);
        List<ServiceInstance> serviceInstances = discoveryClient.getInstances(serviceId);

        if (serviceInstances != null && !serviceInstances.isEmpty()) {
            ServiceInstance serviceInstance = serviceInstances.get(0);
            Map<String, String> metadata = serviceInstance.getMetadata();

            String headersToAdd = metadata.get("apiml.response.headers");
            if (headersToAdd != null && !headersToAdd.trim().isEmpty()) {
                String[] headerValuePairs = StringUtils.stripAll(headersToAdd.split(","));

                for (String headerValuePair : headerValuePairs) {
                    String[] headerValue = StringUtils.stripAll(headerValuePair.split(":", 2)); // separate header name and header value
                    String header = headerValue[0];
                    String value = headerValue.length > 1 ? headerValue[1] : "";

                    context.addZuulResponseHeader(header, value);
                }
            }
        }

        return null;
    }
}
