/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.apiml.caching.service.vsam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.zowe.apiml.caching.config.VsamConfig;
import org.zowe.apiml.caching.model.KeyValue;

import java.io.UnsupportedEncodingException;

public class VsamRecord {

    private final VsamConfig config;

    private String serviceId;

    private VsamKey key;
    private KeyValue keyValue;

    private ObjectMapper mapper = new ObjectMapper();

    private static final String UNSUPPORTED_ENCODING_MESSAGE = "Unsupported encoding: ";

    public VsamRecord(VsamConfig config, String serviceId, KeyValue kv) {
        this.config = config;
        this.serviceId = serviceId;
        this.keyValue = kv;
        this.key = new VsamKey(config);
    }

    public VsamRecord(VsamConfig config, String serviceId, byte[] recordData) throws VsamRecordException {
        this.config = config;
        this.serviceId = serviceId;
        this.key = new VsamKey(config);

        try {
            String recordString = new String(recordData, config.getEncoding());
            this.keyValue = mapper.readValue(recordString.substring(config.getKeyLength()).trim(), KeyValue.class);
        } catch (UnsupportedEncodingException e) {
            throw new VsamRecordException(UNSUPPORTED_ENCODING_MESSAGE + config.getEncoding(), e);
        } catch (JsonProcessingException e) {
            throw new VsamRecordException("Failure deserializing the record value to KeyValue object", e);
        }

    }

    public byte[] getBytes() throws VsamRecordException {
        try {
            byte[] bytes = VsamUtils.padToLength(key.getKey(serviceId, keyValue.getKey()) + mapper.writeValueAsString(keyValue), config.getRecordLength())
                .getBytes(config.getEncoding());
            if (bytes.length > config.getRecordLength()) {
                throw new VsamRecordException("Record length exceeds the configured Vsam record length: ");
            }

            return bytes;


        } catch (UnsupportedEncodingException e) {
            throw new VsamRecordException(UNSUPPORTED_ENCODING_MESSAGE + config.getEncoding(), e);
        } catch (JsonProcessingException e) {
            throw new VsamRecordException("Failure serializing KeyValue object to Json: " + config.getEncoding(), e);
        }
    }

    public byte[] getKeyBytes() throws VsamRecordException {
        try {
            return key.getKeyBytes(serviceId, keyValue);
        } catch (UnsupportedEncodingException e) {
            throw new VsamRecordException(UNSUPPORTED_ENCODING_MESSAGE + config.getEncoding(), e);
        }
    }

    public String getServiceId() {
        return serviceId;
    }

    public KeyValue getKeyValue() {
        return keyValue;
    }

    @Override
    public String toString() {
        return "VsamRecord{" +
            "config=" + config +
            ", serviceId='" + serviceId + '\'' +
            ", key=" + key.getKey(serviceId, keyValue.getKey()) +
            ", keyValue=" + keyValue +
            '}';
    }
}
