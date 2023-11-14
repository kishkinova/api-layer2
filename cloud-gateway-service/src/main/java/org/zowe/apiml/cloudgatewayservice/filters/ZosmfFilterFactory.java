/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.cloudgatewayservice.filters;

import lombok.EqualsAndHashCode;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.zowe.apiml.cloudgatewayservice.service.InstanceInfoService;
import org.zowe.apiml.message.core.MessageService;
import org.zowe.apiml.zaas.ZaasResponse;
import reactor.core.publisher.Mono;

import java.net.HttpCookie;


@Service
public class ZosmfFilterFactory extends AbstractAuthSchemeFactory<ZosmfFilterFactory.Config, ZaasResponse, Object> {

    private static final String ZOSMF_URL = "%s://%s:%d/%s/zaas/zosmf";

    public ZosmfFilterFactory(WebClient webClient, InstanceInfoService instanceInfoService, MessageService messageService) {
        super(Config.class, webClient, instanceInfoService, messageService);
    }

    @Override
    public GatewayFilter apply(Config config) {
        try {
            return createGatewayFilter(config, null);
        } catch (Exception e) {
            return ((exchange, chain) -> {
                ServerHttpRequest request = updateHeadersForError(exchange, e.getMessage());
                return chain.filter(exchange.mutate().request(request).build());
            });
        }
    }

    @Override
    protected Class<ZaasResponse> getResponseClass() {
        return ZaasResponse.class;
    }

    @Override
    protected ZaasResponse getResponseFor401() {
        return new ZaasResponse();
    }

    @Override
    protected WebClient.RequestHeadersSpec<?> createRequest(ServiceInstance instance, Object data) {
        String zosmfTokensUrl = String.format(ZOSMF_URL, instance.getScheme(), instance.getHost(), instance.getPort(), instance.getServiceId().toLowerCase());
        return webClient.post()
            .uri(zosmfTokensUrl);
    }

    @Override
    @SuppressWarnings("squid:S2092")    // the internal API cannot define generic more specifically
    protected Mono<Void> processResponse(ServerWebExchange exchange, GatewayFilterChain chain, ZaasResponse response) {
        ServerHttpRequest request;
        if (response.getToken() != null) {
            request = exchange.getRequest().mutate().headers(headers ->
                headers.add(HttpHeaders.COOKIE, new HttpCookie(response.getCookieName(), response.getToken()).toString())
            ).build();
        } else {
            request = updateHeadersForError(exchange, "Invalid or missing authentication.");
        }

        exchange = exchange.mutate().request(request).build();
        return chain.filter(exchange);
    }

    @EqualsAndHashCode(callSuper = true)
    public static class Config extends AbstractAuthSchemeFactory.AbstractConfig {

    }

}
