/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
import * as enzyme from 'enzyme';
import { IconDanger } from 'mineral-ui-icons';
import WizardNavigation from './WizardNavigation';

describe('>>> Wizard navigation tests', () => {
    it('should handle category change', () => {
        const next = jest.fn();
        const changeWizardCategory = jest.fn();
        const validateInput = jest.fn();
        const wrapper = enzyme.shallow(
            <WizardNavigation
                selectedCategory={0}
                inputData={[]}
                navsObj={{ Nav1: {} }}
                nextWizardCategory={next}
                changeWizardCategory={changeWizardCategory}
                validateInput={validateInput}
            />
        );
        const instance = wrapper.instance();
        instance.handleChange(2);
        expect(changeWizardCategory).toHaveBeenCalled();
        expect(validateInput).toHaveBeenCalled();
    });
    it('should validate all tabs on YAML tab click', () => {
        const next = jest.fn();
        const changeWizardCategory = jest.fn();
        const validateInput = jest.fn();
        const wrapper = enzyme.shallow(
            <WizardNavigation
                selectedCategory={0}
                inputData={[]}
                navsObj={{ Nav1: {}, Nav2: {} }}
                nextWizardCategory={next}
                changeWizardCategory={changeWizardCategory}
                validateInput={validateInput}
                assertAuthorization={jest.fn()}
            />
        );
        const instance = wrapper.instance();
        instance.handleChange(2);
        expect(validateInput).toHaveBeenCalledTimes(3);
    });
    it('should not validate upon accessing something else', () => {
        const next = jest.fn();
        const changeWizardCategory = jest.fn();
        const validateInput = jest.fn();
        const wrapper = enzyme.shallow(
            <WizardNavigation
                selectedCategory={3}
                inputData={[]}
                navsObj={{ Nav1: {}, Nav2: {} }}
                nextWizardCategory={next}
                changeWizardCategory={changeWizardCategory}
                validateInput={validateInput}
                assertAuthorization={jest.fn()}
            />
        );
        const instance = wrapper.instance();
        instance.handleChange(1);
        expect(validateInput).toHaveBeenCalledTimes(0);
    });
    it('should ignore certain events', () => {
        const next = jest.fn();
        const changeWizardCategory = jest.fn();
        const wrapper = enzyme.shallow(
            <WizardNavigation
                selectedCategory={0}
                inputData={[]}
                nextWizardCategory={next}
                changeWizardCategory={changeWizardCategory}
            />
        );
        const instance = wrapper.instance();
        instance.handleChange('go');
        expect(changeWizardCategory).toHaveBeenCalledTimes(0);
    });
    it('should load the tabs', () => {
        const next = jest.fn();
        const changeWizardCategory = jest.fn();
        const checkFilledInput = jest.fn();
        const dummyData = [
            {
                text: 'Some Enabler',
                nav: 'Nav #1',
                categories: [
                    { name: 'Category 1', indentation: false },
                    { name: 'Category 2', indentation: false },
                ],
                help: 'Some additional information',
                helpUrl: {
                    title: 'Help',
                    link: 'https://docs.zowe.org/stable/extend/extend-apiml/onboard-plain-java-enabler/#api-catalog-information',
                },
            },
            {
                text: 'Other Enabler',
                nav: 'Nav #1',
                categories: [
                    { name: 'Category 1', indentation: false },
                    { name: 'Category 2', indentation: false },
                ],
            },
        ];
        const wrapper = enzyme.shallow(
            <WizardNavigation
                selectedCategory={0}
                inputData={dummyData}
                navsObj={{ 'Nav #1': {} }}
                nextWizardCategory={next}
                changeWizardCategory={changeWizardCategory}
                checkFilledInput={checkFilledInput}
            />
        );
        expect(wrapper.find('Tab').length).toEqual(2);
        expect(wrapper.find('Card').length).toEqual(1);
        expect(wrapper.find('Link').length).toEqual(1);
    });

    it('should add a class name for the problematic tabs', () => {
        const next = jest.fn();
        const changeWizardCategory = jest.fn();
        const checkFilledInput = jest.fn();
        const dummyData = [
            {
                text: 'Category 1',
                content: [
                    {
                        test: { value: 'val', question: 'Why?' },
                    },
                ],
                help: 'Some additional information',
                nav: 'Nav #1',
            },
        ];
        const wrapper = enzyme.shallow(
            <WizardNavigation
                selectedCategory={0}
                inputData={dummyData}
                navsObj={{ 'Nav #1': { warn: true } }}
                nextWizardCategory={next}
                changeWizardCategory={changeWizardCategory}
                checkFilledInput={checkFilledInput}
            />
        );
        expect(wrapper.instance().loadTabs()[0].props.icon).toEqual(<IconDanger style={{ color: 'red' }} />);
    });
});
