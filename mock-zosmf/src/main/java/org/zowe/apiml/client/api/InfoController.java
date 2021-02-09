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

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zowe.apiml.client.services.AparBasedService;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@SuppressWarnings("squid:S1452")
public class InfoController {
    private final AparBasedService info;

    @GetMapping(value = "/zosmf/info", produces = "application/json; charset=utf-8")
    public ResponseEntity<?> info(
        HttpServletResponse response,
        @RequestHeader Map<String, String> headers
    ) {
        return info.process("information", "get", response, headers);
    }
}
