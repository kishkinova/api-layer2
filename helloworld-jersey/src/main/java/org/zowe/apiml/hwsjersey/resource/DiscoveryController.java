/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.hwsjersey.resource;

import org.zowe.apiml.eurekaservice.model.Health;
import io.swagger.v3.oas.annotations.Hidden;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;

@Hidden
@Path("/api/v1/application")
public class DiscoveryController {

    @GET
    @Path("/info")
    @Produces(MediaType.APPLICATION_JSON)
    public Object getDiscoveryInfo() {
        return new HashMap<String, String>();
    }

    @GET
    @Path("/health")
    @Produces(MediaType.APPLICATION_JSON)
    public Object getHealth() {
        return new Health("UP");
    }
}
