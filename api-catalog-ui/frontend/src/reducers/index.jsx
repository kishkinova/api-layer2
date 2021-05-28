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
import tilesReducer from './fetch-tile-reducer';
import filtersReducer from './filter-reducer';
import loadingReducer from './loading-reducer';
import errorReducer from './error-reducer';
import authenticationReducer from './authentication-reducer';
import selectedServiceReducer from './selected-service-reducer';
import refreshStaticApisReducer from './refresh-static-apis-reducer';
import serviceVersionDiff from './service-version-diff-reducer';

const reducers = {
    filtersReducer,
    tilesReducer,
    loadingReducer,
    errorReducer,
    authenticationReducer,
    selectedServiceReducer,
    refreshStaticApisReducer,
    serviceVersionDiff,
};

// eslint-disable-next-line import/prefer-default-export
export const rootReducer = combineReducers(reducers);
