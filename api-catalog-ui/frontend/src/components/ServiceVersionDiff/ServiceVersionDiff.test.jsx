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
import ServiceVersionDiff from './ServiceVersionDiff';

describe('>>> ServiceVersionDiff component tests', () => {
    it('Should display service version diff with no data', () => {
        const serviceVersionDiff = shallow(<ServiceVersionDiff serviceId="service" versions={['v1', 'v2']} />);

        expect(serviceVersionDiff.find('.api-diff-container').exists()).toEqual(true);
        expect(serviceVersionDiff.find('.api-diff-form').exists()).toEqual(true);

        expect(serviceVersionDiff.find('Text').first().prop('children')).toEqual('Compare');

        expect(serviceVersionDiff.find('Select').first().exists()).toEqual(true);
        expect(serviceVersionDiff.find('Select').first().prop('selectedItem')).toEqual(undefined);
        expect(serviceVersionDiff.find('Select').first().prop('data')).toEqual([{ text: 'v1' }, { text: 'v2' }]);

        expect(serviceVersionDiff.find('Text').at(1).prop('children')).toEqual('with');

        expect(serviceVersionDiff.find('Select').at(1).exists()).toEqual(true);
        expect(serviceVersionDiff.find('Select').at(1).prop('selectedItem')).toEqual(undefined);
        expect(serviceVersionDiff.find('Select').at(1).prop('data')).toEqual([{ text: 'v1' }, { text: 'v2' }]);

        expect(serviceVersionDiff.find('[data-testid="diff-button"]').first().exists()).toEqual(true);
        expect(serviceVersionDiff.find('[data-testid="diff-button"]').first().prop('children')).toEqual('Go');
    });

    it('Should preselect versions for compare when state is set', () => {
        const serviceVersionDiff = shallow(<ServiceVersionDiff serviceId="service" versions={['v1', 'v2']} />);
        serviceVersionDiff.setState({ selectedVersion1: { text: 'v1' }, selectedVersion2: { text: 'v2' } });

        expect(serviceVersionDiff.find('Select').first().prop('selectedItem')).toEqual({ text: 'v1' });
        expect(serviceVersionDiff.find('Select').at(1).prop('selectedItem')).toEqual({ text: 'v2' });
    });

    it('Should preselect versions for compare when props are passed', () => {
        const serviceVersionDiff = shallow(
            <ServiceVersionDiff serviceId="service" versions={['v1', 'v2']} version1="v1" version2="v2" />
        );

        expect(serviceVersionDiff.find('Select').first().prop('selectedItem')).toEqual({ text: 'v1' });
        expect(serviceVersionDiff.find('Select').at(1).prop('selectedItem')).toEqual({ text: 'v2' });
    });

    it('Should call getDiff when button pressed', () => {
        const getDiff = jest.fn();
        const serviceVersionDiff = shallow(
            <ServiceVersionDiff
                getDiff={getDiff}
                serviceId="service"
                versions={['v1', 'v2']}
                version1="v1"
                version2="v2"
            />
        );

        serviceVersionDiff.find('[data-testid="diff-button"]').first().simulate('click');
        expect(getDiff.mock.calls.length).toBe(1);
    });
});
