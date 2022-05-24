/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.product.compatibility;

import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.appinfo.InstanceInfo;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.*;
import org.springframework.cloud.client.discovery.health.DiscoveryCompositeHealthContributor;
import org.springframework.cloud.netflix.eureka.EurekaHealthIndicator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Configuration
public class ActuatorConfig {

    @Bean
    public HealthCheckHandler healthCheckHandler(@Autowired StatusAggregator statusAggregator) {
        return new ApimlHealthCheckHandler(statusAggregator);
    }

    /**
     * This class is a replacement for EurekaHealthCheckHandler in spring-cloud-netflix-eureka-client:2.2.10.RELEASE, which is incompatible with Spring Boot 2.5.
     * EurekaHealthCheckHandler in 2.2.10.RELEASE relies on a few classes that are replaced in Spring Boot 2.5.
     * <p>
     * This code is almost entirely copied from the 3.x version of spring-cloud-netflix-eureka-client.
     * https://github.com/spring-cloud/spring-cloud-netflix/blob/3.0.x/spring-cloud-netflix-eureka-client/src/main/java/org/springframework/cloud/netflix/eureka/EurekaHealthCheckHandler.java
     * <p>
     * There are minor changes (e.g. making a variable final), and using classes in the spring-cloud-commons 2.2.9.RELEASE instead of
     * spring-cloud-commons for Spring Cloud 3.x.
     * <p>
     * NOTE: This should be removed when the APIML upgrades to Spring Cloud 3.x.
     */
    private static final class ApimlHealthCheckHandler implements HealthCheckHandler, ApplicationContextAware, InitializingBean, Ordered, Lifecycle {
        private static final Map<Status, InstanceInfo.InstanceStatus> STATUS_MAPPING = new HashMap<>();

        static {
            STATUS_MAPPING.put(Status.UNKNOWN, InstanceInfo.InstanceStatus.UNKNOWN);
            STATUS_MAPPING.put(Status.OUT_OF_SERVICE, InstanceInfo.InstanceStatus.OUT_OF_SERVICE);
            STATUS_MAPPING.put(Status.DOWN, InstanceInfo.InstanceStatus.DOWN);
            STATUS_MAPPING.put(Status.UP, InstanceInfo.InstanceStatus.UP);
        }

        private final StatusAggregator statusAggregator;
        private ApplicationContext applicationContext;
        private final Map<String, HealthContributor> healthContributors = new HashMap<>();

        /**
         * {@code true} until the context is stopped.
         */
        private boolean running = true;

        private final Map<String, ReactiveHealthContributor> reactiveHealthContributors = new HashMap<>();

        public ApimlHealthCheckHandler(StatusAggregator statusAggregator) {
            this.statusAggregator = statusAggregator;
            Assert.notNull(statusAggregator, "StatusAggregator must not be null");

        }

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
            this.applicationContext = applicationContext;
        }

        @Override
        public void afterPropertiesSet() {
            populateHealthContributors(applicationContext.getBeansOfType(HealthContributor.class));
            reactiveHealthContributors.putAll(applicationContext.getBeansOfType(ReactiveHealthContributor.class));
        }

        private void populateHealthContributors(Map<String, HealthContributor> healthContributors) {
            for (Map.Entry<String, HealthContributor> entry : healthContributors.entrySet()) {
                // ignore EurekaHealthIndicator and flatten the rest of the composite
                // otherwise there is a never ending cycle of down. See gh-643
                if (entry.getValue() instanceof DiscoveryCompositeHealthContributor) {

                    // Changed from source code to reconcile spring-cloud-commons differences
                    DiscoveryCompositeHealthContributor indicator = (DiscoveryCompositeHealthContributor) entry.getValue();
                    for (NamedContributor<HealthContributor> namedContributor : indicator) {
                        if (!(namedContributor.getContributor() instanceof EurekaHealthIndicator)) {
                            this.healthContributors.put(namedContributor.getName(), namedContributor.getContributor());
                        }
                    }

                } else {
                    this.healthContributors.put(entry.getKey(), entry.getValue());
                }
            }
        }

        @Override
        public InstanceInfo.InstanceStatus getStatus(InstanceInfo.InstanceStatus instanceStatus) {
            if (running) {
                return getHealthStatus();
            } else {
                // Return nothing if the context is not running, so the status held by the
                // InstanceInfo remains unchanged.
                // See gh-1571
                return null;
            }
        }

        private InstanceInfo.InstanceStatus getHealthStatus() {
            Status status = getStatus(statusAggregator);
            return mapToInstanceStatus(status);
        }

        private Status getStatus(StatusAggregator statusAggregator) {
            Set<Status> statusSet = new HashSet<>();
            for (HealthContributor contributor : healthContributors.values()) {
                processContributor(statusSet, contributor);
            }
            for (ReactiveHealthContributor contributor : reactiveHealthContributors.values()) {
                processContributor(statusSet, contributor);
            }
            return statusAggregator.getAggregateStatus(statusSet);
        }

        private void processContributor(Set<Status> statusSet, HealthContributor contributor) {
            if (contributor instanceof CompositeHealthContributor) {
                for (NamedContributor<HealthContributor> contrib : (CompositeHealthContributor) contributor) {
                    processContributor(statusSet, contrib.getContributor());
                }
            } else if (contributor instanceof HealthIndicator) {
                statusSet.add(((HealthIndicator) contributor).health().getStatus());
            }
        }

        private void processContributor(Set<Status> statusSet, ReactiveHealthContributor contributor) {
            if (contributor instanceof CompositeReactiveHealthContributor) {
                for (NamedContributor<ReactiveHealthContributor> contrib : (CompositeReactiveHealthContributor) contributor) {
                    processContributor(statusSet, contrib.getContributor());
                }
            } else if (contributor instanceof ReactiveHealthIndicator) {
                Health health = ((ReactiveHealthIndicator) contributor).health().block();
                if (health != null) {
                    statusSet.add(health.getStatus());
                }
            }
        }

        private InstanceInfo.InstanceStatus mapToInstanceStatus(Status status) {
            if (!STATUS_MAPPING.containsKey(status)) {
                return InstanceInfo.InstanceStatus.UNKNOWN;
            }
            return STATUS_MAPPING.get(status);
        }

        @Override
        public int getOrder() {
            // registered with a high order priority so the close() method is invoked early
            // and *BEFORE* EurekaAutoServiceRegistration
            // (must be in effect when the registration is closed and the eureka replication
            // triggered -> health check handler is
            // consulted at that moment)
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public void start() {
            running = true;
        }

        @Override
        public void stop() {
            running = false;
        }

        @Override
        public boolean isRunning() {
            return true;
        }
    }
}
