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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zowe.apiml.client.service.ZaasClientService;
import org.zowe.apiml.zaasclient.exception.ZaasClientException;

@RestController
@RequestMapping("/api/v1/zaasClient")
@Api(
    value = "/api/v1/zaasClient",
    consumes = "application/json",
    tags = {"Zaas client test call"})
public class ZaasClientTestController {

    private ZaasClientService zaasClientService;

    public ZaasClientTestController(ZaasClientService zaasClientService) {
        this.zaasClientService = zaasClientService;
    }

    @PostMapping
    @ApiOperation(value = "Forward login to gateway service via zaas client")
    public ResponseEntity<String> forwardLogin(@RequestBody LoginRequest loginRequest) {
        try {
            String jwt = zaasClientService.login(loginRequest.getUsername(), loginRequest.getPassword());
            return ResponseEntity.ok().body(jwt);
        } catch (ZaasClientException e) {
            return ResponseEntity.status(e.getErrorCode().getReturnCode()).body(e.getErrorCode().getMessage());
        }

    }
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class LoginRequest {
    private String username;
    private String password;

}
