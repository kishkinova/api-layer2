/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
import { render } from '@testing-library/react';
import '@testing-library/jest-dom/extend-expect';
import { Provider } from 'react-redux';
import configureStore from 'redux-mock-store';
import { Router } from 'react-router-dom';
import ServiceTabContainer from './ServiceTabContainer';

const mockStore = configureStore();

describe('ServiceTab Container', () => {
    let store;
    let container;
    beforeEach(() => {
        const tiles = [{ title: 'test', id: '2', description: 'test', services: ['service1'] }];
        store = mockStore({
            tilesReducer: {
                tiles,
            },
            selectedServiceReducer: {
                selectedTile: 'tile',
                selectedService: {
                    serviceId: 'service',
                },
            },
        });
        const history = {
            location: {
                pathname: {},
            },
            push: jest.fn(),
            listen: jest.fn(),
        };
        container = render(
            <Router history={history}>
                <Provider store={store}>
                    <ServiceTabContainer />
                </Provider>
            </Router>
        );
    });

    xit('should render the container', () => {
        expect(container).not.toBeNull();
    });
});
