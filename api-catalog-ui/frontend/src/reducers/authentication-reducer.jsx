/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

import userConstants from '../constants/user-constants';

const sessionDefaultState = {
    sessionOn: false,
};

function authenticationReducer(state = sessionDefaultState, action = {}) {
    switch (action.type) {
        case userConstants.USERS_LOGIN_REQUEST:
            return {
                ...state,
                user: action.user,
            };
        case userConstants.USERS_LOGIN_SUCCESS:
            sessionDefaultState.sessionOn = true;
            return {
                error: null,
                user: action.user,
                showHeader: true,
            };
        case userConstants.USERS_LOGIN_FAILURE:
            return {
                error: action.error,
            };
        case userConstants.AUTHENTICATION_FAILURE:
            return {
                error: action.error,
                sessionOn: sessionDefaultState.sessionOn,
                onCompleteHandling: () => {
                    sessionDefaultState.sessionOn = false;
                },
            };
        case userConstants.USERS_LOGIN_INVALIDPASSWORD:
            return {
                error: action.error,
                expired: true,
            };
        case userConstants.USERS_LOGIN_INIT:
            return {};
        case userConstants.USERS_LOGIN_VALIDATE:
            if (action.credentials.newPassword !== action.credentials.repeatNewPassword) {
                return {
                    error: {
                        messageText: 'Passwords do not match',
                        messageKey: 'org.zowe.apiml.security.common.passwordUpdate',
                        messageNumber: 'ZWEAT604E',
                        messageType: 'ERROR',
                    },
                    expired: true,
                };
            }
            return {
                error: {},
                expired: true,
            };
        case userConstants.USERS_LOGOUT_REQUEST:
        case userConstants.USERS_LOGOUT_SUCCESS:
            return {
                error: null,
                showHeader: false,
                onCompleteHandling: () => {
                    sessionDefaultState.sessionOn = false;
                },
            };
        case userConstants.USERS_LOGOUT_FAILURE:
            return {
                error: action.error,
                showHeader: false,
            };
        default:
            return state;
    }
}

export default authenticationReducer;
