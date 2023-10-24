/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

import { combineReducers } from 'redux';
import tilesReducer from './tiles-reducer';
import filtersReducer from './filters-reducer';
import loadingReducer from './loading-reducer';
import errorReducer from './error-reducer';
import authenticationReducer from './authentication-reducer';
import selectedServiceReducer from './selected-service-reducer';
import refreshStaticApisReducer from './refresh-static-apis-reducer';
import serviceVersionDiff from './service-version-diff-reducer';
import wizardReducer from './wizard-reducer';

const reducers = {
    filtersReducer,
    tilesReducer,
    loadingReducer,
    errorReducer,
    authenticationReducer,
    selectedServiceReducer,
    refreshStaticApisReducer,
    serviceVersionDiff,
    wizardReducer,
};

if ('serviceWorker' in navigator) {
    navigator.serviceWorker.ready.then((registration) => {
        registration.unregister();

        if (caches) {
            // Service worker cache should be cleared with caches.delete()
            caches.keys().then(async (names) => {
                await Promise.all(names.map((name) => caches.delete(name)));
            });
        }
    });
}
// eslint-disable-next-line import/prefer-default-export
export const rootReducer = combineReducers(reducers);
