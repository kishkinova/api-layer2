/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.gateway.ws;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.springframework.http.HttpHeaders;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.jetty.JettyWebSocketClient;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * Represents a connection in the proxying chain, establishes 'client' to
 * 'server' communication with the next server, with a
 * {@link WebSocketProxyClientHandler} to copy data from the 'client' to the
 * supplied 'server' session.
 */
@Slf4j
public class WebSocketRoutedSession {
    private static final int DEFAULT_TIMEOUT = 30000;

    private final WebSocketSession webSocketClientSession;
    private final WebSocketSession webSocketServerSession;

    public WebSocketRoutedSession(WebSocketSession webSocketServerSession, String targetUrl, SslContextFactory.Server jettySslContextFactory) {
        log.debug("Creating WebSocketRoutedSession jettySslContextFactory={}", jettySslContextFactory);
        this.webSocketServerSession = webSocketServerSession;
        this.webSocketClientSession = createWebSocketClientSession(webSocketServerSession, targetUrl, jettySslContextFactory);
    }

    public WebSocketRoutedSession(WebSocketSession webSocketServerSession, WebSocketSession webSocketClientSession) {
        log.debug("Creating WebSocketRoutedSession with provided server and client session.");
        this.webSocketClientSession = webSocketClientSession;
        this.webSocketServerSession = webSocketServerSession;
    }

    private WebSocketHttpHeaders getWebSocketHttpHeaders(WebSocketSession webSocketServerSession) {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        HttpHeaders browserHeaders = webSocketServerSession.getHandshakeHeaders();
        browserHeaders.forEach((key, value) -> {
            String header = String.join(" ", value);
            headers.add(key, header);
        });

        return headers;
    }

    public WebSocketSession getWebSocketClientSession() {
        return webSocketClientSession;
    }

    public WebSocketSession getWebSocketServerSession() {
        return webSocketServerSession;
    }

    private WebSocketSession createWebSocketClientSession(WebSocketSession webSocketServerSession, String targetUrl, SslContextFactory.Server sslContextFactory) {
        try {
            log.debug("createWebSocketClientSession(session={},targetUrl={},jettySslContextFactory={})",
                webSocketClientSession, targetUrl, sslContextFactory);
            JettyWebSocketClient client = new JettyWebSocketClient(new WebSocketClient(new HttpClient(sslContextFactory)));
            client.start();
            URI targetURI = new URI(targetUrl);
            WebSocketHttpHeaders headers = getWebSocketHttpHeaders(webSocketServerSession);
            ListenableFuture<WebSocketSession> futureSession = client
                .doHandshake(new WebSocketProxyClientHandler(webSocketServerSession), headers, targetURI);
            return futureSession.get(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (IllegalStateException e) {
            throw webSocketProxyException(targetUrl, e, webSocketServerSession, true);
        } catch (Exception e) {
            throw webSocketProxyException(targetUrl, e, webSocketServerSession, false);
        }
    }

    private WebSocketProxyError webSocketProxyException(String targetUrl, Exception cause, WebSocketSession webSocketServerSession, boolean logError) {
        String message = String.format("Error opening session to WebSocket service at %s: %s", targetUrl, cause.getMessage());
        if (logError) {
            log.debug(message);
        }
        return new WebSocketProxyError(message, cause, webSocketServerSession);
    }

    public void sendMessageToServer(WebSocketMessage<?> webSocketMessage) throws IOException {
        log.debug("sendMessageToServer(session={},message={})", webSocketClientSession, webSocketMessage);
        webSocketClientSession.sendMessage(webSocketMessage);
    }

    public void close(CloseStatus status) throws IOException {
        if (webSocketClientSession.isOpen()) {
            webSocketClientSession.close(status);
        }
    }

    public String getServerRemoteAddress() {
        return getWebSocketServerSession().getRemoteAddress().toString();
    }

    public String getServerUri() {
        return getWebSocketServerSession().getUri().toString();
    }

    public String getClientUri() {
        return getWebSocketClientSession().getUri().toString();
    }

    public String getClientId() {
        return getWebSocketClientSession().getId();
    }
}
