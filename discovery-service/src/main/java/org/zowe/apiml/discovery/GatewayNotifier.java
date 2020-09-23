/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.discovery;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.EurekaServerContextHolder;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.util.BiConsumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.zowe.apiml.message.core.MessageService;
import org.zowe.apiml.message.log.ApimlLogger;
import org.zowe.apiml.product.constants.CoreService;
import org.zowe.apiml.util.EurekaUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * This bean is used to send a notification to the Gateways. It is sending notification asynchronous (at first they
 * are stored into the queue and then with a delay send to Gateways). If same notification is waiting for sending
 * it send it only once.
 * <p>
 * Purpose of this bean is at first in notification Gateways about new and removed services and process at least
 * evicting of caches there.
 */
@Component
@Slf4j
public class GatewayNotifier implements Runnable {

    public static final String GATEWAY_SERVICE_ID = CoreService.GATEWAY.getServiceId().toUpperCase();

    private static final String DISTRIBUTE_PATH = "/gateway/auth/distribute/";  // NOSONAR: URL is always using / to separate path segments
    private static final String CACHE_PATH = "/gateway/cache/services";  // NOSONAR: URL is always using / to separate path segments

    private final ApimlLogger apimlLogger;

    private final RestTemplate restTemplate;

    private boolean stopped;
    private BlockingQueue<Notification> queue = new LinkedBlockingQueue<>();
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();

    public GatewayNotifier(@Qualifier("restTemplateWithKeystore") RestTemplate restTemplate, MessageService messageService) {
        this.restTemplate = restTemplate;
        this.apimlLogger = ApimlLogger.of(GatewayNotifier.class, messageService);
    }

    @PostConstruct
    public void afterPropertiesSet() {
        threadPoolTaskExecutor.initialize();
        threadPoolTaskExecutor.execute(this);
    }

    @PreDestroy
    public void preDestroy() {
        this.stopped = true;
    }

    private EurekaServerContext getServerContext() {
        return EurekaServerContextHolder.getInstance().getServerContext();
    }

    private PeerAwareInstanceRegistry getRegistry() {
        return getServerContext().getRegistry();
    }

    private List<InstanceInfo> getGatewayInstances() {
        final PeerAwareInstanceRegistry registry = getRegistry();
        final Application application = registry.getApplication(GATEWAY_SERVICE_ID);
        if (application == null) {
            apimlLogger.log("org.zowe.apiml.discovery.errorNotifyingGateway");
            return Collections.emptyList();
        }
        return application.getInstances();
    }

    protected void addToQueue(Notification notification) {
        if (!queue.contains(notification)) {
            queue.add(notification);
        }
    }

    /**
     * Method notifies Gateways about any service's change. This is necessary to cache evicting on Gateway side.
     * If any service was added, through this method all Gateway will clean our cached data about
     * this service. It support using cache about services on Gateway at all.
     * <p>
     * If notification is about a Gateway instance, this instance is not notified itself.
     *
     * @param serviceId  service ID of changed service
     * @param instanceId instance ID of changed service
     */
    public void serviceUpdated(String serviceId, String instanceId) {
        final Notification notification = new Notification(serviceId, instanceId, Type.SERVICE_UPDATED);
        addToQueue(notification);
    }

    /**
     * Method notifies Gateways about any service's registration being cancelled.
     * This is necessary to cache evicting on Gateway side. If any service was removed it will
     * clean our cached data about this service. It support using cache about services on Gateway at all.
     * <p>
     * If notification is about a Gateway instance, this instance is not notified itself.
     *
     * @param serviceId  service ID of changed service
     */
    public void serviceCancelledRegistration(String serviceId) {
        final Notification notification = new Notification(serviceId, null, Type.SERVICE_CANCEL_REGISTRATION);
        addToQueue(notification);
    }

    /**
     * Each Gateway use cache for sorting of invalidated tokens. Purpose of this method is distribute list of invalidated
     * tokens to a Gateway which is new, because meanwhile the Gateway was down, anybody can make logout and this
     * information is stored in other instance of Gateway. After this call those Gateway, which were up, will notify
     * this newly registered gateway.
     *
     * @param instanceId instance ID of newly registered Gateway
     */
    public void distributeInvalidatedCredentials(String instanceId) {
        final Notification notification = new Notification(null, instanceId, Type.DISTRIBUTE_INVALIDATED_CREDENTIALS);
        addToQueue(notification);
    }

    /**
     * Process to send notification to gateways
     *
     * @param instanceId instance ID of notified Gateway to reduce call - don't call itself
     * @param call       function to make a call
     */
    private void notify(String instanceId, Consumer<InstanceInfo> call) {
        final List<InstanceInfo> gatewayInstances = getGatewayInstances();

        for (final InstanceInfo instanceInfo : gatewayInstances) {
            // don't notify service itself, it is not required
            if (StringUtils.equalsIgnoreCase(instanceId, instanceInfo.getInstanceId())) continue;
            call.accept(instanceInfo);
        }
    }

    protected void serviceUpdatedProcess(String serviceId, String instanceId) {
        notify(instanceId, instanceInfo -> {
            final String url = getServiceUrl(serviceId, instanceInfo);
            try {
                restTemplate.delete(url);
            } catch (Exception e) {
                log.debug("Cannot notify the Gateway {} about {}", url, instanceId, e);
                apimlLogger.log("org.zowe.apiml.discovery.registration.gateway.notify", url, instanceId);
            }
        });
    }

    protected void serviceCancelRegistrationProcess(String serviceId) {
        notify(null, instanceInfo -> {
            final String url = getServiceUrl(serviceId, instanceInfo);
            try {
                restTemplate.delete(url);
            } catch (Exception e) {
                log.debug("Cannot notify the Gateway {} about service un-registration", url, e);
                apimlLogger.log("org.zowe.apiml.discovery.unregistration.gateway.notify", url);
            }
        });
    }

    protected void distributeInvalidatedCredentialsProcess(String instanceId) {
        notify(instanceId, instanceInfo -> {
            final StringBuilder url = new StringBuilder();
            url.append(EurekaUtils.getUrl(instanceInfo))
                .append(DISTRIBUTE_PATH)
                .append(instanceId);

            try {
                restTemplate.getForEntity(url.toString(), Void.class);
            } catch (Exception e) {
                log.debug("Cannot notify the Gateway {} about {}", url.toString(), instanceId, e);
                apimlLogger.log("org.zowe.apiml.discovery.registration.gateway.notify", url.toString(), instanceId);
            }
        });
    }

    /**
     * Implementation of asynchronous thread, which checking the queue of notification request and then process them.
     */
    @Override
    public void run() {
        while (!stopped) {
            try {
                Notification notification = queue.poll(10, TimeUnit.SECONDS);
                if (notification != null) {
                    notification.process();
                }
            } catch (InterruptedException e) {
                log.debug("Thread for Gateway notification was interrupted", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.debug("Unexpected exception on gateway notifier", e);
            }
        }
    }

    private String getServiceUrl(String serviceId, InstanceInfo instanceInfo) {
        final StringBuilder url = new StringBuilder();
        url.append(EurekaUtils.getUrl(instanceInfo))
            .append(CACHE_PATH);
        if (serviceId != null) url.append('/').append(serviceId);
        return url.toString();
    }

    /**
     * This class contains information about one notification (required information about instance to notifying and
     * type of notification)
     */
    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode
    protected class Notification {

        private final String serviceId;
        private final String instanceId;
        private final Type type;

        protected void process() {
            type.call.accept(GatewayNotifier.this, this);
        }

    }

    /**
     * All supported types of notification by bean GatewayNotifier
     */
    @AllArgsConstructor
    private enum Type {

        SERVICE_UPDATED((gatewayNotifier, notification) ->
            gatewayNotifier.serviceUpdatedProcess(notification.serviceId, notification.instanceId)
        ),

        SERVICE_CANCEL_REGISTRATION(((gatewayNotifier, notification) ->
            gatewayNotifier.serviceCancelRegistrationProcess(notification.serviceId))
        ),

        DISTRIBUTE_INVALIDATED_CREDENTIALS((gatewayNotifier, notification) ->
            gatewayNotifier.distributeInvalidatedCredentialsProcess(notification.instanceId)
        );

        /**
         * Realize mapping of notification to method which process it
         */
        private final BiConsumer<GatewayNotifier, Notification> call;

    }

}
