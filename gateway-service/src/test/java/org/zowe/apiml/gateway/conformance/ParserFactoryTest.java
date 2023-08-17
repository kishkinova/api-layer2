/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.gateway.conformance;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParserFactoryTest {

    @Nested
    class givenSwaggerDocumentation {

        final String DUMMY_SERVICE_ID = "dummy";


        public String swaggerFromPath(String path) throws IOException {
            File file = new File(path);
            return new String(Files.readAllBytes(file.getAbsoluteFile().toPath()));
        }


        @ParameterizedTest
        @ValueSource(strings = {"src/test/resources/api-doc-v2.json", "src/test/resources/api-doc.json"})
        void whenCorrectSwagger(String path) throws IOException {
            String sampleSwagger = swaggerFromPath(path);

            List<String> result;
            result = ParserFactory.parseSwagger(sampleSwagger, null, null, DUMMY_SERVICE_ID).getMessages();
            assertEquals(0, result.size());

        }


        @Test
        void whenWrongVersioningV2() throws IOException {

            String sampleSwagger2 = swaggerFromPath("src/test/resources/api-doc-v2.json");


            String brokenSwagger = sampleSwagger2.replace("2.0", "42");

            Exception e = assertThrows(SwaggerParsingException.class, () -> ParserFactory.parseSwagger(brokenSwagger, null, null, DUMMY_SERVICE_ID));

            assertTrue(e.getMessage().contains("Swagger documentation is not conformant to either OpenAPI V2 nor V3"));
        }

        @Test
        void whenWrongVersioningV3() throws IOException {

            String sampleSwagger3 = swaggerFromPath("src/test/resources/api-doc.json");

            String brokenSwagger = sampleSwagger3.replace("3.0", "42");

            Exception e = assertThrows(SwaggerParsingException.class, () -> ParserFactory.parseSwagger(brokenSwagger, null, null, DUMMY_SERVICE_ID));

            assertTrue(e.getMessage().contains("Swagger documentation is not conformant to either OpenAPI V2 nor V3"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"src/test/resources/api-doc-v2.json", "src/test/resources/api-doc.json"})
        void whenBrokenSwagger(String path) throws IOException {

            String sampleSwagger = swaggerFromPath(path);

            String brokenSwagger = sampleSwagger.substring(0, 250);

            Exception e = assertThrows(SwaggerParsingException.class, () -> ParserFactory.parseSwagger(brokenSwagger, null, null, DUMMY_SERVICE_ID));

            assertTrue(e.getMessage().contains("Could not parse Swagger documentation"));
        }

    }
}
