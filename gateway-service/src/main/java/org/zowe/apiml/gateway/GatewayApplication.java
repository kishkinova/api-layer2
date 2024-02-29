/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.netflix.ribbon.RibbonClients;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.zowe.apiml.extension.ExtensionConfigReader;
import org.zowe.apiml.extension.ExtensionsLoader;
import org.zowe.apiml.gateway.ribbon.GatewayRibbonConfig;
import org.zowe.apiml.product.monitoring.LatencyUtilsConfigInitializer;
import org.zowe.apiml.product.version.BuildInfo;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import static org.zowe.apiml.extension.ZoweRuntimeEnvironment.defaultEnv;

@EnableZuulProxy
@EnableWebSecurity
@SpringBootApplication
@EnableCircuitBreaker
@ComponentScan(
    value = {
        "org.zowe.apiml.gateway",
        "org.zowe.apiml.product",
        "org.zowe.apiml.security.common"
    },
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*RibbonConfig")
    }
)
@RibbonClients(defaultConfiguration = GatewayRibbonConfig.class)
@EnableEurekaClient
@EnableWebSocket
@EnableAspectJAutoProxy
public class GatewayApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(GatewayApplication.class);
        app.addInitializers(new LatencyUtilsConfigInitializer());
        app.addListeners(new ExtensionsLoader(new ExtensionConfigReader(defaultEnv())));
        app.setLogStartupInfo(false);
        new BuildInfo().logBuildInfo();
        app.run(args);
    }

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        Config config = ConfigProvider.getConfig();
        servletContext.setInitParameter("spring.config.additional-location", config.getValue("configYmlLocation", String.class));
        super.onStartup(servletContext);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(GatewayApplication.class);
    }
}
