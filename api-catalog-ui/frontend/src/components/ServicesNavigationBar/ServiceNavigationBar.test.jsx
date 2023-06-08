/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
import { shallow } from 'enzyme';
import ServicesNavigationBar from './ServicesNavigationBar';

const tile = {
    version: '1.0.0',
    id: 'apicatalog',
    title: 'API Mediation Layer for z/OS internal API services',
    status: 'UP',
    description: 'lkajsdlkjaldskj',
    services: [
        {
            serviceId: 'apicatalog',
            title: 'API Catalog',
            description:
                'API ML Microservice to locate and display API documentation for API ML discovered microservices',
            status: 'UP',
            secured: false,
            homePageUrl: '/ui/v1/apicatalog',
        },
    ],
    totalServices: 1,
    activeServices: 1,
    lastUpdatedTimestamp: '2018-08-22T08:32:03.110+0000',
    createdTimestamp: '2018-08-22T08:31:22.948+0000',
};

describe('>>> ServiceNavigationBar component tests', () => {
    it('should clear when unmounting', () => {
        const clear = jest.fn();
        const serviceNavigationBar = shallow(
            <ServicesNavigationBar clear={clear} services={[tile]} currentTileId="apicatalog" />
        );
        const instance = serviceNavigationBar.instance();
        instance.componentWillUnmount();
        expect(clear).toHaveBeenCalled();
    });

    it('should clear when unmounting', () => {
        const clear = jest.fn();
        const serviceNavigationBar = shallow(
            <ServicesNavigationBar clear={clear} services={[tile]} currentTileId="apicatalog" />
        );
        const instance = serviceNavigationBar.instance();
        instance.componentWillUnmount();
        expect(clear).toHaveBeenCalled();
    });

    it('should display no results if search fails', () => {
        const clear = jest.fn();
        const serviceNavigationBar = shallow(
            <ServicesNavigationBar
                searchCriteria=" Supercalafragalisticexpialadoshus"
                services={[]}
                currentTileId="apicatalog"
                clear={clear}
            />
        );
        expect(serviceNavigationBar.find('[data-testid="search-bar"]')).toExist();
        expect(serviceNavigationBar.find('#search_no_results').children().text()).toEqual(
            'No services found matching search criteria'
        );
    });

    it('should trigger filterText on handleSearch', () => {
        const filterText = jest.fn();
        const wrapper = shallow(
            <ServicesNavigationBar filterText={filterText} services={[]} currentTileId="apicatalog" clear={jest.fn()} />
        );
        const instance = wrapper.instance();
        instance.handleSearch();
        expect(filterText).toHaveBeenCalled();
    });

    it('should display label', () => {
        const clear = jest.fn();
        const serviceNavigationBar = shallow(
            <ServicesNavigationBar services={[]} currentTileId="apicatalog" clear={clear} />
        );
        expect(serviceNavigationBar.find('#serviceIdTabs')).toExist();
        expect(serviceNavigationBar.find('#serviceIdTabs').text()).toEqual('Product APIs');
    });
});
