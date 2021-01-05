/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
package org.zowe.apiml.caching.service.inmemory;

import lombok.extern.slf4j.Slf4j;
import org.zowe.apiml.caching.model.KeyValue;
import org.zowe.apiml.caching.service.Messages;
import org.zowe.apiml.caching.service.Storage;
import org.zowe.apiml.caching.service.StorageException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class InMemoryStorage implements Storage {
    private Map<String, Map<String, KeyValue>> storage = new ConcurrentHashMap<>();

    public InMemoryStorage() {
    }

    protected InMemoryStorage(Map<String, Map<String, KeyValue>> storage) {
        this.storage = storage;
    }

    @Override
    public KeyValue create(String serviceId, KeyValue toCreate) {
        log.info("Writing record: {}|{}|{}", serviceId, toCreate.getKey(), toCreate.getValue());

        storage.computeIfAbsent(serviceId, k -> new HashMap<>());
        Map<String, KeyValue> serviceStorage = storage.get(serviceId);
        if (serviceStorage.containsKey(toCreate.getKey())) {
            throw new StorageException(Messages.DUPLICATE_KEY.getKey(), Messages.DUPLICATE_KEY.getStatus(), toCreate.getKey());
        }
        serviceStorage.put(toCreate.getKey(), toCreate);
        return toCreate;
    }

    @Override
    public KeyValue read(String serviceId, String key) {
        log.info("Reading Record: {}|{}|{}", serviceId, key, "-");

        Map<String, KeyValue> serviceSpecificStorage = storage.get(serviceId);
        if (serviceSpecificStorage == null || !serviceSpecificStorage.containsKey(key)) {
            throw new StorageException(Messages.KEY_NOT_IN_CACHE.getKey(), Messages.KEY_NOT_IN_CACHE.getStatus(), key, serviceId);
        }

        return serviceSpecificStorage.get(key);
    }

    @Override
    public KeyValue update(String serviceId, KeyValue toUpdate) {
        log.info("Updating Record: {}|{}|{}", serviceId, toUpdate.getKey(), toUpdate.getValue());

        String key = toUpdate.getKey();
        if (isKeyNotInCache(serviceId, key)) {
            throw new StorageException(Messages.KEY_NOT_IN_CACHE.getKey(), Messages.KEY_NOT_IN_CACHE.getStatus(), key, serviceId);
        }

        Map<String, KeyValue> serviceStorage = storage.get(serviceId);
        serviceStorage.put(key, toUpdate);
        return toUpdate;
    }

    @Override
    public KeyValue delete(String serviceId, String key) {
        log.info("Deleting Record: {}|{}|{}", serviceId, key, "-");

        if (isKeyNotInCache(serviceId, key)) {
            throw new StorageException(Messages.KEY_NOT_IN_CACHE.getKey(), Messages.KEY_NOT_IN_CACHE.getStatus(), key, serviceId);
        }

        Map<String, KeyValue> serviceSpecificStorage = storage.get(serviceId);
        return serviceSpecificStorage.remove(key);
    }

    @Override
    public Map<String, KeyValue> readForService(String serviceId) {
        return storage.get(serviceId);
    }

    private boolean isKeyNotInCache(String serviceId, String keyToTest) {
        Map<String, KeyValue> serviceSpecificStorage = storage.get(serviceId);
        return serviceSpecificStorage == null || serviceSpecificStorage.get(keyToTest) == null;
    }
}
