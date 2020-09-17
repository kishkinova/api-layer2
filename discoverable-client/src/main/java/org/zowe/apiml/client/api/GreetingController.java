/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.client.api;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zowe.apiml.client.model.Greeting;

import java.util.Date;


/**
 * Version 1 of the controller that returns greetings.
 */
@RestController
@Api(tags = {"Other Operations"})
@SwaggerDefinition(tags = {
    @Tag(name = "Other Operations", description = "General Operations")})
public class GreetingController {
    private static final String TEMPLATE = "Hello, %s!";

    /**
     * Gets a greeting for anyone.
     */
    @GetMapping(value = "/api/v1/greeting")
    @ApiOperation(value = "Get a greeting", response = Greeting.class,
        tags = {"Other Operations"})
    public Greeting greeting(@RequestParam(value = "name", defaultValue = "world") String name,
                             @RequestParam(value = "delayMs", defaultValue = "0", required = false) Integer delayMs) {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return new Greeting(new Date(), String.format(TEMPLATE, name));
    }

    /**
     * Gets a custom greeting.
     */
    @GetMapping(value = {"/api/v1/{yourName}/greeting"})
    @ApiOperation(value = "Get a greeting", response = Greeting.class,
        tags = {"Other Operations"})
    public Greeting customGreeting(@PathVariable(value = "yourName") String yourName) {
        return new Greeting(new Date(), String.format(TEMPLATE, yourName));
    }
}

