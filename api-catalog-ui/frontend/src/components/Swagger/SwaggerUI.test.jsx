/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
import { act } from 'react-dom/test-utils';
import { render } from 'react-dom';
import { shallow } from 'enzyme';
import { describe, expect, it, jest } from '@jest/globals';
import SwaggerUIApiml from './SwaggerUIApiml';

describe('>>> Swagger component tests', () => {
    afterEach(() => {
        document.body.innerHTML = '';
    });

    beforeEach(() => {
        process.env.REACT_APP_API_PORTAL = false;
    });

    it('should not render swagger if apiDoc is null', () => {
        const service = {
            serviceId: 'testservice',
            title: 'Spring Boot Enabler Service',
            description: 'Dummy Service for enabling others',
            status: 'UP',
            secured: false,
            homePageUrl: 'http://localhost:10013/enabler/',
            basePath: '/enabler/api/v1',
            apiDoc: null,
        };
        service.apis = {
            codeSnippet: {
                codeBlock: 'code',
                endpoint: '/test',
                language: 'java',
            },
        };
        const wrapper = shallow(
            <div>
                <SwaggerUIApiml selectedService={service} />
            </div>
        );
        const swaggerDiv = wrapper.find('#swaggerContainer');

        expect(swaggerDiv.length).toEqual(0);
    });

    it('should not render swagger if apis default is provided', () => {
        const service = {
            serviceId: 'testservice',
            title: 'Spring Boot Enabler Service',
            description: 'Dummy Service for enabling others',
            status: 'UP',
            secured: false,
            homePageUrl: 'http://localhost:10013/enabler/',
            basePath: '/enabler/api/v1',
            apiDoc: JSON.stringify({
                openapi: '3.0.0',
            }),
            apis: {
                default: {
                    apiId: 'enabler',
                    codeSnippet: {
                        codeBlock: 'code',
                        endpoint: '/test',
                        language: 'java',
                    },
                },
            },
            defaultApiVersion: 0,
        };
        const wrapper = shallow(
            <div>
                <SwaggerUIApiml selectedService={service} />
            </div>
        );
        const swaggerDiv = wrapper.find('#swaggerContainer');

        expect(swaggerDiv.length).toEqual(0);
    });

    it('should not render swagger if apiDoc is undefined', async () => {
        const service = {
            serviceId: 'testservice',
            title: 'Spring Boot Enabler Service',
            description: 'Dummy Service for enabling others',
            status: 'UP',
            secured: false,
            homePageUrl: 'http://localhost:10013/enabler/',
            basePath: '/enabler/api/v1',
            defaultApiVersion: 0,
        };
        service.apis = [
            {
                default: { apiId: 'enabler' },
                codeSnippet: {
                    codeBlock: 'code',
                    endpoint: '/test',
                    language: 'java',
                },
            },
        ];

        const container = document.createElement('div');
        document.body.appendChild(container);
        await act(async () => render(<SwaggerUIApiml selectedService={service} />, container));
        expect(container.textContent).toContain(`API documentation could not be retrieved`);
    });

    it('should transform swagger server url', async () => {
        const endpoint = '/enabler/api/v1';
        const service = {
            serviceId: 'testservice',
            title: 'Spring Boot Enabler Service',
            description: 'Dummy Service for enabling others',
            status: 'UP',
            secured: false,
            homePageUrl: 'http://localhost:10013/enabler/',
            basePath: '/enabler/api/v1',
            apiDoc: JSON.stringify({
                openapi: '3.0.0',
                servers: [{ url: `https://bad.com${endpoint}` }],
            }),
            apis: {
                default: {
                    apiId: 'enabler',
                    codeSnippet: {
                        codeBlock: 'code',
                        endpoint: '/test',
                        language: 'java',
                    },
                },
            },
            defaultApiVersion: 0,
        };
        service.apis = [
            {
                default: { apiId: 'enabler' },
                codeSnippet: {
                    codeBlock: 'code',
                    endpoint: '/test',
                    language: 'java',
                },
            },
        ];
        const tiles = [{}];
        const container = document.createElement('div');
        document.body.appendChild(container);

        await act(async () => render(<SwaggerUIApiml selectedService={service} tiles={tiles} />, container));
        expect(container.textContent).toContain(`Servershttp://localhost${endpoint}`);
    });

    it('should update swagger', async () => {
        const endpoint1 = '/oldenabler/api/v1';
        const endpoint2 = '/newenabler/api/v2';
        const service1 = {
            serviceId: 'oldservice',
            title: 'Spring Boot Enabler Service',
            description: 'Dummy Service for enabling others',
            status: 'UP',
            secured: false,
            homePageUrl: 'http://localhost:10013/oldenabler/',
            basePath: '/oldenabler/api/v1',
            apiDoc: JSON.stringify({
                openapi: '3.0.0',
                servers: [{ url: `https://bad.com${endpoint1}` }],
            }),
            apis: {
                default: { apiId: 'oldenabler' },
            },
            defaultApiVersion: 0,
        };
        service1.apis = [
            {
                default: { apiId: 'enabler' },
                codeSnippet: {
                    codeBlock: 'code',
                    endpoint: '/test',
                    language: 'java',
                },
            },
        ];
        const service2 = {
            serviceId: 'newservice',
            title: 'Spring Boot Enabler Service',
            description: 'Dummy Service for enabling others',
            status: 'UP',
            secured: false,
            homePageUrl: 'http://localhost:10013/newenabler/',
            basePath: '/newenabler/api/v1',
            apiDoc: JSON.stringify({
                openapi: '3.0.0',
                servers: [{ url: `https://bad.com${endpoint2}` }],
            }),
            apis: {
                default: { apiId: 'oldenabler' },
            },
            defaultApiVersion: 0,
        };

        service2.apis = [
            {
                default: { apiId: 'enabler' },
                codeSnippet: {
                    codeBlock: 'code2',
                    endpoint: '/test2',
                    language: 'python',
                },
            },
        ];
        const container = document.createElement('div');
        document.body.appendChild(container);
        const tiles = [{}];
        await act(async () => render(<SwaggerUIApiml selectedService={service1} tiles={tiles} />, container));
        expect(container.textContent).toContain(`Servershttp://localhost${endpoint1}`);
        await act(async () => render(<SwaggerUIApiml selectedService={service2} tiles={tiles} />, container));
        expect(container.textContent).toContain(`Servershttp://localhost${endpoint2}`);
    });

    it('should get snippet from selectedVersion and render swagger', async () => {
        const endpoint1 = '/oldenabler/api/v1';
        const service1 = {
            serviceId: 'oldservice',
            title: 'Spring Boot Enabler Service',
            description: 'Dummy Service for enabling others',
            status: 'UP',
            secured: false,
            homePageUrl: 'http://localhost:10013/oldenabler/',
            basePath: '/oldenabler/api/v1',
            apiDoc: JSON.stringify({
                openapi: '3.0.0',
                servers: [{ url: `https://bad.com${endpoint1}` }],
            }),
            apis: {
                default: { apiId: 'oldenabler' },
            },
            defaultApiVersion: 0,
        };
        service1.apis = [
            {
                default: { apiId: 'enabler' },
            },
        ];
        service1.apis[0].codeSnippet = {
            codeBlock: 'code',
            endpoint: '/test',
            language: 'java',
        };
        const container = document.createElement('div');
        document.body.appendChild(container);

        await act(async () => render(<SwaggerUIApiml selectedService={service1} selectedVersion="0" />, container));
        expect(container).not.toBeNull();
    });

    const divInfo = {
        appendChild: jest.fn(),
    };

    it('should create element if does not exist', () => {
        process.env.REACT_APP_API_PORTAL = true;
        const service = {
            serviceId: 'testservice',
            title: 'Spring Boot Enabler Service',
            description: 'Dummy Service for enabling others',
            status: 'UP',
            secured: false,
            homePageUrl: 'http://localhost:10013/enabler/',
            basePath: '/enabler/api/v1',
            apiDoc: null,
        };
        service.apis = {
            codeSnippet: {
                codeBlock: 'code',
                endpoint: '/test',
                language: 'java',
            },
        };
        const querySelectorSpy = jest.spyOn(document, 'querySelector').mockImplementation(() => divInfo);
        const querySelectorDivSpy = jest.spyOn(divInfo, 'querySelector').mockImplementation(() => title);
        const querySelectorTitleSpy = jest.spyOn(title, 'querySelector').mockImplementation(() => 'myVersion');
        const titleSpy = jest.spyOn(title, 'appendChild');

        const wrapper = shallow(<SwaggerUIApiml selectedService={service} />);

        wrapper.setProps({ selectedVersion: 'v2' });

        expect(elementsByClassNameSpy).toHaveBeenCalled();
        expect(querySelectorSpy).toHaveBeenCalled();
        expect(querySelectorSpy).toHaveBeenCalledWith('.info');
        expect(createElementSpy).toHaveBeenCalled();
        expect(createElementSpy).toHaveBeenCalledWith('span');
        expect(divInfoSpy).toHaveBeenCalled();
    });

    it('should not create element if span already exists', () => {
        process.env.REACT_APP_API_PORTAL = true;
        const service = {
            serviceId: 'testservice',
            title: 'Spring Boot Enabler Service',
            description: 'Dummy Service for enabling others',
            status: 'UP',
            secured: false,
            homePageUrl: 'http://localhost:10013/enabler/',
            basePath: '/enabler/api/v1',
            apiDoc: null,
        };
        service.apis = {
            codeSnippet: {
                codeBlock: 'code',
                endpoint: '/test',
                language: 'java',
            },
        };
        const querySelectorSpy = jest.spyOn(document, 'querySelector').mockImplementation(() => null);
        const querySelectorDivSpy = jest.spyOn(divInfo, 'querySelector');
        const querySelectorTitleSpy = jest.spyOn(title, 'querySelector');
        const titleSpy = jest.spyOn(title, 'appendChild');

        const wrapper = shallow(<SwaggerUIApiml selectedService={service} />);

        wrapper.setProps({ selectedVersion: 'v2' });

        expect(querySelectorSpy).toHaveBeenCalled();
        expect(querySelectorSpy).toHaveBeenCalledWith('.information-container');
        expect(querySelectorDivSpy).not.toHaveBeenCalled();
        expect(querySelectorTitleSpy).not.toHaveBeenCalled();
        expect(titleSpy).not.toHaveBeenCalled();
    });

    it('should NOT replace inner element in title with version if title missing', () => {
        process.env.REACT_APP_API_PORTAL = true;
        const service = {
            serviceId: 'testservice',
            title: 'Spring Boot Enabler Service',
            description: 'Dummy Service for enabling others',
            status: 'UP',
            secured: false,
            homePageUrl: 'http://localhost:10013/enabler/',
            basePath: '/enabler/api/v1',
            apiDoc: null,
        };
        service.apis = {
            codeSnippet: {
                codeBlock: 'code',
                endpoint: '/test',
                language: 'java',
            },
        };
        const querySelectorSpy = jest.spyOn(document, 'querySelector').mockImplementation(() => divInfo);
        const querySelectorDivSpy = jest.spyOn(divInfo, 'querySelector').mockImplementation(() => null);
        const querySelectorTitleSpy = jest.spyOn(title, 'querySelector');
        const titleSpy = jest.spyOn(title, 'appendChild');

        const wrapper = shallow(<SwaggerUIApiml selectedService={service} />);

        wrapper.setProps({ selectedVersion: 'v2' });

        expect(elementsByClassNameSpy).toHaveBeenCalled();
        expect(getElementByIdSpy).toHaveBeenCalledWith('filter-label');
        expect(querySelectorSpy).not.toHaveBeenCalled();
        expect(createElementSpy).not.toHaveBeenCalled();
        expect(divInfoSpy).not.toHaveBeenCalled();
    });

    it('should not create element if api portal disabled and element does not exist', () => {
        const service = {
            serviceId: 'testservice',
            title: 'Spring Boot Enabler Service',
            description: 'Dummy Service for enabling others',
            status: 'UP',
            secured: false,
            homePageUrl: 'http://localhost:10013/enabler/',
            basePath: '/enabler/api/v1',
            apiDoc: null,
        };
        service.apis = {
            codeSnippet: {
                codeBlock: 'code',
                endpoint: '/test',
                language: 'java',
            },
        };
        const querySelectorSpy = jest.spyOn(document, 'querySelector').mockImplementation(() => divInfo);
        const querySelectorDivSpy = jest.spyOn(divInfo, 'querySelector').mockImplementation(() => title);
        const querySelectorTitleSpy = jest.spyOn(title, 'querySelector').mockImplementation(() => null);
        const titleSpy = jest.spyOn(title, 'appendChild');

        const wrapper = shallow(<SwaggerUIApiml selectedService={service} />);

        wrapper.setProps({ selectedVersion: 'v2' });

        expect(querySelectorSpy).toHaveBeenCalled();
        expect(querySelectorSpy).toHaveBeenCalledWith('.information-container');
        expect(querySelectorDivSpy).toHaveBeenCalled();
        expect(querySelectorDivSpy).toHaveBeenCalledWith('.title');
        expect(querySelectorTitleSpy).toHaveBeenCalled();
        expect(querySelectorTitleSpy).toHaveBeenCalledWith('.version-stamp');
        expect(titleSpy).not.toHaveBeenCalled();
    });

    it('should not create element api portal disabled and span already exists', () => {
        const service = {
            serviceId: 'testservice',
            title: 'Spring Boot Enabler Service',
            description: 'Dummy Service for enabling others',
            status: 'UP',
            secured: false,
            homePageUrl: 'http://localhost:10013/enabler/',
            basePath: '/enabler/api/v1',
            apiDoc: null,
        };
        service.apis = {
            codeSnippet: {
                codeBlock: 'code',
                endpoint: '/test',
                language: 'java',
            },
        };
        jest.spyOn(document, 'getElementById').mockImplementation(() => <span id="filter-label" />);
        const createElement = jest.spyOn(document, 'createElement');
        const wrapper = shallow(
            <div>
                <SwaggerUIApiml selectedService={service} />
            </div>
        );
        const swaggerDiv = wrapper.find('span');

        expect(swaggerDiv.length).toEqual(0);
        expect(createElement).not.toHaveBeenCalled();
    });
});
