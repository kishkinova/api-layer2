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

import org.zowe.apiml.client.model.UploadFileResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultipartControllerTest {
    private MultipartController multipartController;

    @BeforeEach
    void setup() {
        multipartController = new MultipartController();
    }

    @Test
    void shouldSubmitWithPostAndReturnString() {
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", MediaType.TEXT_PLAIN_VALUE, "Hello, World!".getBytes());
        UploadFileResponse uploadFileResponse = multipartController.uploadFileWithPost(file);

        assertEquals("hello.txt", uploadFileResponse.getFileName());
        assertEquals("text/plain", uploadFileResponse.getFileType());
        assertEquals(13, uploadFileResponse.getSize());
    }

    @Test
    void shouldSubmitWithPutAndReturnString() {
        MockMultipartFile file = new MockMultipartFile("file", "hello.txt", MediaType.TEXT_PLAIN_VALUE, "Hello, World!".getBytes());
        UploadFileResponse uploadFileResponse = multipartController.uploadFileWithPut(file);

        assertEquals("hello.txt", uploadFileResponse.getFileName());
        assertEquals("text/plain", uploadFileResponse.getFileType());
        assertEquals(13, uploadFileResponse.getSize());
    }
}
