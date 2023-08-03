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

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.zowe.apiml.gateway.security.login.Providers;
import org.zowe.apiml.product.service.ServiceStartupEventHandler;

import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;

@Component
@RequiredArgsConstructor
public class GatewayStartupListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final long PERIOD = Duration.ofSeconds(15).toMillis(); // should be configurable

    private final Providers providers;

    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (providers.isZosfmUsed()) {
            new Timer().scheduleAtFixedRate(new TimerTask() {

                @Override
                public void run() {
                    if (providers.isZosmfAvailableAndOnline()) {
                        cancel();
                        notifyStartup();
                    }
                }

            }, 0, PERIOD);
        } else {
            notifyStartup();
        }
    }

    private void notifyStartup() {
        new ServiceStartupEventHandler().onServiceStartup("Gateway Service",
            ServiceStartupEventHandler.DEFAULT_DELAY_FACTOR);
    }
}
