/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

import { ofType } from 'redux-observable';
import { of, throwError, Observable } from 'rxjs';
// eslint-disable-next-line import/no-extraneous-dependencies
import { TestScheduler } from 'rxjs/testing';
// eslint-disable-next-line import/no-extraneous-dependencies
import { AjaxError } from 'rxjs/ajax';
import { fetchTilesPollingEpic } from './fetch-tiles';
import { fetchTilesFailed, fetchTilesStart, fetchTilesStop, fetchTilesSuccess } from '../actions/catalog-tile-actions';

class ActionsObservable extends Observable {
    constructor(actionsSubject) {
        super();
        this.source = actionsSubject;
    }

    lift(operator) {
        const observable = new ActionsObservable(this);
        observable.operator = operator;
        return observable;
    }

    ofType(...keys) {
        return ofType(...keys)(this);
    }
}

const mockResponse = [
    {
        version: '1.0.0',
        id: 'apicatalog',
        title: 'API Mediation Layer API',
        status: 'UP',
        description: 'The API Mediation Layer for z/OS internal API services.',
        services: [],
        totalServices: 0,
        activeServices: 0,
        lastUpdatedTimestamp: '2018-08-22T08:32:03.110+0000',
        createdTimestamp: '2018-08-22T08:31:22.948+0000',
    },
];

const ajaxResponse = {
    response: mockResponse,
};

const ajax500Error = new AjaxError(
    'API Error',
    {
        status: 500,
        response: { message: 'Fetch Failure' },
        responseType: 'json',
    },
    null
);

const deepEquals = (actual, expected) => {
    expect(actual).toEqual(expected);
};

const createTestScheduler = () => new TestScheduler(deepEquals);

test('it should return a successful result then stop after one cycle', () => {
    const marbles1 = 'a-c|';
    const marbles2 = '---b-|';
    const values = {
        a: fetchTilesStart(''),
        b: fetchTilesSuccess(mockResponse),
        c: fetchTilesStop(),
    };

    process.env.REACT_APP_STATUS_UPDATE_DEBOUNCE = '10';
    process.env.REACT_APP_STATUS_UPDATE_PERIOD = '50';
    process.env.REACT_APP_STATUS_UPDATE_SCALING_DURATION = '10';
    const ts = createTestScheduler();
    const dependencies = {
        ajax: jest.fn(() => of(ajaxResponse)),
        scheduler: ts,
    };

    const source = new ActionsObservable(ts.createColdObservable(marbles1, values));
    const actual = fetchTilesPollingEpic(source, null, dependencies);
    ts.expectObservable(actual).toBe(marbles2, values);
    ts.flush();
    expect(dependencies.ajax).toHaveBeenCalledTimes(1);
});

test('it should return a successful result then stop after one cycle for a explicit container search', () => {
    const marbles1 = 'ac|';
    const marbles2 = '--b|';
    const values = {
        a: fetchTilesStart('apicatalog'),
        b: fetchTilesSuccess([mockResponse[0]]),
        c: fetchTilesStop(),
    };

    process.env.REACT_APP_STATUS_UPDATE_DEBOUNCE = '10';
    process.env.REACT_APP_STATUS_UPDATE_PERIOD = '50';
    process.env.REACT_APP_STATUS_UPDATE_SCALING_DURATION = '10';
    const ts = createTestScheduler();
    const dependencies = {
        ajax: jest.fn(() => of(ajaxResponse)),
        scheduler: ts,
    };

    const source = new ActionsObservable(ts.createColdObservable(marbles1, values));
    const actual = fetchTilesPollingEpic(source, null, dependencies);
    ts.expectObservable(actual).toBe(marbles2, values);
    ts.flush();
    expect(dependencies.ajax).toHaveBeenCalledTimes(1);
});

test('it should request, fail with a terminating FAILED action with an enclosed Ajax Error', () => {
    const marbles1 = '-ac';
    const marbles2 = '-------------------------------c';
    const values = {
        a: fetchTilesStart(''),
        c: fetchTilesFailed(ajax500Error),
    };

    const ts = createTestScheduler();
    const dependencies = {
        ajax: () => throwError(ajax500Error),
        scheduler: ts,
    };

    process.env.REACT_APP_STATUS_UPDATE_DEBOUNCE = '10';
    process.env.REACT_APP_STATUS_UPDATE_PERIOD = '50';
    process.env.REACT_APP_STATUS_UPDATE_SCALING_DURATION = '10';
    const source = new ActionsObservable(ts.createColdObservable(marbles1, values));
    const actual = fetchTilesPollingEpic(source, null, dependencies);
    ts.expectObservable(actual).toBe(marbles2, values);
    ts.flush();
});
