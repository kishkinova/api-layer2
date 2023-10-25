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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.Data;
import org.apache.http.HttpHeaders;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.zowe.apiml.cloudgatewayservice.service.InstanceInfoService;
import org.zowe.apiml.message.core.MessageService;
import org.zowe.apiml.ticket.TicketRequest;
import org.zowe.apiml.ticket.TicketResponse;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class PassticketFilterFactory extends AbstractAuthSchemeFactory<PassticketFilterFactory.Config, TicketResponse, String> {

    private final String ticketUrl = "%s://%s:%s/%s/api/v1/auth/ticket";
    private final ObjectWriter writer = new ObjectMapper().writer();

    public PassticketFilterFactory(WebClient webClient, InstanceInfoService instanceInfoService, MessageService messageService) {
        super(Config.class, webClient, instanceInfoService, messageService);
    }

    @Override
    protected Class<TicketResponse> getResponseClass() {
        return TicketResponse.class;
    }

    @Override
    protected WebClient.RequestHeadersSpec<?> createRequest(ServerWebExchange exchange, ServiceInstance instance, String requestBody) {
        return webClient.post()
            .uri(String.format(ticketUrl, instance.getScheme(), instance.getHost(), instance.getPort(), instance.getServiceId().toLowerCase()))
            .headers(headers -> headers.addAll(exchange.getRequest().getHeaders()))
            .bodyValue(requestBody);
    }

    @Override
    protected Mono<Void> processResponse(ServerWebExchange exchange, GatewayFilterChain chain, TicketResponse response) {
        if (response.getTicket() == null) {
            ServerHttpRequest request = updateHeadersForError(exchange, "Invalid or missing authentication.");
            return chain.filter(exchange.mutate().request(request).build());
        }
        String encodedCredentials = Base64.getEncoder().encodeToString((response.getUserId() + ":" + response.getTicket()).getBytes(StandardCharsets.UTF_8));
        final String headerValue = "Basic " + encodedCredentials;
        ServerHttpRequest request = setRequestHeader(exchange, HttpHeaders.AUTHORIZATION, headerValue);
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public GatewayFilter apply(Config config) {
        try {
            return createGatewayFilter(writer.writeValueAsString(new TicketRequest(config.getApplicationName())));
        } catch (JsonProcessingException e) {
            return ((exchange, chain) -> {
                ServerHttpRequest request = updateHeadersForError(exchange, e.getMessage());
                return chain.filter(exchange.mutate().request(request).build());
            });
        }
    }

    @Data
    public static class Config {

        private String applicationName;

    }
}
