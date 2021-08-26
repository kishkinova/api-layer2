/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.apicatalog.staticapi;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Controller to handle the request issued from the UI to generate
 * a static definition file from the Wizard interface
 */
@RestController
@RequestMapping("/static-api")
@RequiredArgsConstructor
public class StaticDefinitionController {
    private final StaticDefinitionGenerator staticDefinitionGenerator;

    /**
     * Retrieve the yaml from the request and store it in the file
     *
     * @param payload  the request payload
     * @return the response entity
     */
    @PostMapping(value = "/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> generateStaticDef(@RequestBody String payload) throws IOException {
        StaticAPIResponse staticAPIResponse = staticDefinitionGenerator.generateFile(payload);
        return ResponseEntity
            .status(staticAPIResponse.getStatusCode())
            .body(staticAPIResponse.getBody());
    }

    /**
     * Overwrite the file already created
     *
     * @param payload  the request payload
     * @return the response entity
     */
    @PostMapping(value = "/override", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> overrideStaticDef(@RequestBody String payload) throws IOException {
        StaticAPIResponse staticAPIResponse = staticDefinitionGenerator.overrideFile(payload);
        return ResponseEntity
            .status(staticAPIResponse.getStatusCode())
            .body(staticAPIResponse.getBody());
    }
}
