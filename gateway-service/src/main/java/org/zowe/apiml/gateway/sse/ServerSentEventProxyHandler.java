/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.sse;

import reactor.core.publisher.Flux;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ArrayList;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@Component("ServerSentEventProxyHandler")
public class ServerSentEventProxyHandler {

    private static final String SEPARATOR = "/";
    private final DiscoveryClient discovery;
    private static SseEmitter emitter;
    private Map<String, Flux<ServerSentEvent<String>>> sseEventStreams = new ConcurrentHashMap<>();

    @Autowired
    public ServerSentEventProxyHandler(DiscoveryClient discovery) {
        this.discovery = discovery;
    }

    @RequestMapping("/sse/**")
    public SseEmitter getEmitter(HttpServletRequest request, HttpServletResponse response) throws Exception {
        emitter = new SseEmitter(-1L);
        
        String[] uriParts = getUriParts(request, response);
        if (uriParts != null && uriParts.length >= 5) {
            String majorVersion = uriParts[2];
            String serviceId = uriParts[3];
            String path = uriParts[4];
            String params[] = Arrays.copyOfRange(uriParts, 5, uriParts.length);
            ServiceInstance serviceInstance = findServiceInstance(serviceId);

            if (serviceInstance == null) {
                response.getWriter().print(String.format("Service '%s' could not be discovered", serviceId));
                return null;
            }

            String targetUrl = getTargetUrl(serviceId, serviceInstance, path, params);
            if (!sseEventStreams.containsKey(targetUrl)) {
                addStream(targetUrl);
            }
            forwardEvents(sseEventStreams.get(targetUrl));
        }
        return emitter;
    }

    public void forwardEvents(Flux<ServerSentEvent<String>> stream) {
        stream.subscribe(
            (content) -> {
                try {
                    emitter.send(content.data());
                } catch (IOException e) {
                    System.out.println("Error encounter sending SSE event");
                    e.printStackTrace();
                    emitter.complete();
                }
            },
            (error) -> {
                System.out.println("Error receiving SSE");
                System.out.println(error);
                emitter.complete();
            },
            () -> {
                emitter.complete();
            });
    }

    private void addStream(String sseStreamUrl) {
        WebClient client = WebClient.create(sseStreamUrl);
        ParameterizedTypeReference<ServerSentEvent<String>> type
        = new ParameterizedTypeReference<ServerSentEvent<String>>() {};
        Flux<ServerSentEvent<String>> eventStream = client.get()
        .retrieve()
        .bodyToFlux(type);
        sseEventStreams.put(sseStreamUrl, eventStream);
    }

    private String[] getUriParts(HttpServletRequest request, HttpServletResponse response) {
        String uriPath = request.getRequestURI();
        Map<String, String[]> parameters = request.getParameterMap();
        String arr[] = null;
        if (uriPath != null) {
            List<String> uriParts = new ArrayList<String>(Arrays.asList(uriPath.split("/", 5)));
            Iterator<Map.Entry<String, String[]>> it = (parameters.entrySet()).iterator();
            while (it.hasNext()) {
                Map.Entry<String, String[]> entry = (Map.Entry<String, String[]>)it.next();
                uriParts.add(entry.getKey() + "=" + entry.getValue()[0].toString());
            }
            arr = uriParts.toArray(new String[uriParts.size()]);
        }
        return arr;
    }

    private ServiceInstance findServiceInstance(String serviceId) {
        List<ServiceInstance> serviceInstances = this.discovery.getInstances(serviceId);
        if (!serviceInstances.isEmpty()) {
            // TODO: This just a simple implementation that will be replaced by more sophisticated mechanism in future
            return serviceInstances.get(0);
        } else {
            return null;
        }
    }

    private String getTargetUrl(String serviceId, ServiceInstance serviceInstance, String path, String params[]) {
        return "https" + "://" + serviceInstance.getHost() + ":"
            + serviceInstance.getPort() +
            SEPARATOR + serviceId + SEPARATOR + path + "?" + String.join("&", params);
    }
}
