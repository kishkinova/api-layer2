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

import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Retryable;
import org.zowe.apiml.caching.model.KeyValue;
import org.zowe.apiml.caching.service.*;
import org.zowe.apiml.caching.service.vsam.config.VsamConfig;
import org.zowe.apiml.util.ObjectUtil;

import java.util.*;

/**
 * Class handles requests from controller and orchestrates operations on the low level VSAM File class
 */

@Slf4j
public class VsamStorage implements Storage {

    private final VsamConfig vsamConfig;

    public VsamStorage(VsamConfig vsamConfig, VsamInitializer vsamInitializer) {

        log.info("Using VSAM storage for the cached data");

        ObjectUtil.requireNotNull(vsamConfig.getFileName(), "Vsam filename cannot be null"); //TODO bean validation
        ObjectUtil.requireNotEmpty(vsamConfig.getFileName(), "Vsam filename cannot be empty");
        this.vsamConfig = vsamConfig;
        log.info("Using Vsam configuration: {}", vsamConfig);
        vsamInitializer.storageWarmup(vsamConfig);
    }


    @Override
    @Retryable(value = {IllegalStateException.class, UnsupportedOperationException.class})
    public KeyValue create(String serviceId, KeyValue toCreate) {
        log.info("Writing record: {}|{}|{}", serviceId, toCreate.getKey(), toCreate.getValue());
        KeyValue result = null;

        try (VsamFile file = new VsamFile(vsamConfig, VsamConfig.VsamOptions.WRITE)) {

            VsamRecord record = new VsamRecord(vsamConfig, serviceId, toCreate);

            Optional<VsamRecord> returned = file.create(record);
            if (returned.isPresent()) {
                result = returned.get().getKeyValue();
            }

        }

        if (result == null) {
            throw new StorageException(Messages.DUPLICATE_KEY.getKey(), Messages.DUPLICATE_KEY.getStatus(), toCreate.getKey(), serviceId);
        }

        return result;
    }

    @Override
    public KeyValue read(String serviceId, String key) {
        log.info("Reading Record: {}|{}|{}", serviceId, key, "-");
        KeyValue result = null;

        try (VsamFile file = new VsamFile(vsamConfig, VsamConfig.VsamOptions.READ)) {

            VsamRecord record = new VsamRecord(vsamConfig, serviceId, new KeyValue(key, "", serviceId));

            Optional<VsamRecord> returned = file.read(record);
            if (returned.isPresent()) {
                result = returned.get().getKeyValue();
            }
        }

        if (result == null) {
            throw new StorageException(Messages.KEY_NOT_IN_CACHE.getKey(), Messages.KEY_NOT_IN_CACHE.getStatus(), key, serviceId);
        }

        return result;
    }

    @Override
    @Retryable (value = {IllegalStateException.class, UnsupportedOperationException.class})
    public KeyValue update(String serviceId, KeyValue toUpdate) {
        log.info("Updating Record: {}|{}|{}", serviceId, toUpdate.getKey(), toUpdate.getValue());
        KeyValue result = null;

        try (VsamFile file = new VsamFile(vsamConfig, VsamConfig.VsamOptions.WRITE)) {

            VsamRecord record = new VsamRecord(vsamConfig, serviceId, toUpdate);

            Optional<VsamRecord> returned = file.update(record);
            if (returned.isPresent()) {
                result = returned.get().getKeyValue();
            }
        }

        if (result == null) {
            throw new StorageException(Messages.KEY_NOT_IN_CACHE.getKey(), Messages.KEY_NOT_IN_CACHE.getStatus(), toUpdate.getKey(), serviceId);
        }

        return result;
    }

    @Override
    @Retryable(value = {IllegalStateException.class, UnsupportedOperationException.class})
    public KeyValue delete(String serviceId, String toDelete) {

        log.info("Deleting Record: {}|{}|{}", serviceId, toDelete, "-");
        KeyValue result = null;

        try (VsamFile file = new VsamFile(vsamConfig, VsamConfig.VsamOptions.WRITE)) {

            VsamRecord record = new VsamRecord(vsamConfig, serviceId, new KeyValue(toDelete, "", serviceId));

            Optional<VsamRecord> returned = file.delete(record);
            if (returned.isPresent()) {
                result = returned.get().getKeyValue();
            }
        }

        if (result == null) {
            throw new StorageException(Messages.KEY_NOT_IN_CACHE.getKey(), Messages.KEY_NOT_IN_CACHE.getStatus(), toDelete, serviceId);
        }

        return result;
    }

    @Override
    public Map<String, KeyValue> readForService(String serviceId) {

        log.info("Reading All Records: {}|{}|{}", serviceId, "-", "-");
        Map<String, KeyValue> result = new HashMap<>();
        List<VsamRecord> returned;

        try (VsamFile file = new VsamFile(vsamConfig, VsamConfig.VsamOptions.READ)) {
            returned = file.readForService(serviceId);
        }

        returned.forEach(vsamRecord -> result.put(vsamRecord.getKeyValue().getKey(), vsamRecord.getKeyValue()));

        return result;
    }

}
