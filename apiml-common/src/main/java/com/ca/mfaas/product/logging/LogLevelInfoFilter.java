/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package com.ca.mfaas.product.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

import java.util.Optional;

//TODO unit tests

/**
 * This filter's purpose is to hide or show the info level messages
 * There are info level messages that are meant for debug mode only (originally @Slf4j)
 * There are info level messages that are meant to be displayed (like service startup messages)
 *
 * Because ApimlLogger is using Slg4j in the background, there is conflict of interest.
 * Solution is that ApimlLogger is enhancing it's logs with Marker instances and this filter is providing
 * adequate filtering.
 *
 * The filter is normally enabled (filtering), or disabled when the service is started with debug profile included
 * in system variable spring.profiles.include
 */
public class LogLevelInfoFilter extends TurboFilter {

    private static final String APIML_MARKER = "APIML-LOGGER";
    private boolean isFilterActive = true;

    public LogLevelInfoFilter() {
        Optional<String> profiles = Optional.ofNullable(System.getProperties().getProperty("spring.profiles.include"));
        if(profiles.isPresent() && profiles.get().toLowerCase().contains("debug")) {
            isFilterActive = false;
        }
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {

        if(isFilterActive && isLevelOfOrLower(level, Level.INFO) && isInternalLogger(logger)) {
            if (marker != null && marker.getName().equals(APIML_MARKER)) {
                return FilterReply.ACCEPT;
            } else {
                return FilterReply.DENY;
            }
        }
        return FilterReply.NEUTRAL;
    }

    private boolean isLevelOfOrLower(Level messageLevel, Level thresholdLevel) {
        return messageLevel.levelInt <= thresholdLevel.levelInt;
    }

    private boolean isInternalLogger(Logger logger) {
        return logger.getName().startsWith("com.ca.mfaas");
    }
}
