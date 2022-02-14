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

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zowe.apiml.client.model.Greeting;

import java.util.Date;

@RestController
@Api(tags = {"Web Service"})
@SwaggerDefinition(tags = {
    @Tag(name = "Web Service", description = "Test handling of URL containing \"ws\"")})
@RequestMapping("/api/v1")
public class WebServiceController {

    private static final String TEMPLATE = "Hello, %s!";

    /**
     * Gets a custom greeting from Web Service.
     */
    @GetMapping(value = {"/ws", "/sse"})
    @ApiOperation(value = "Get a greeting", response = Greeting.class,
        tags = {"Web Service"})
    @HystrixCommand()
    public Greeting weServiceGreet() {

        return new Greeting(new Date(), String.format(TEMPLATE, "Web service"));
    }
}
