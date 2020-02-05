/* eslint-disable no-undef */
import * as React from 'react';
// tslint:disable-next-line:no-implicit-dependencies
import * as enzyme from 'enzyme';
import jest from 'jest-mock';
import Login from './Login';

describe('>>> Login page component tests', () => {
    it('should submit username and password input', () => {
        const loginMock = jest.fn();

        const page = enzyme.shallow(<Login login={loginMock} />);
        page.find('TextInput')
            .first()
            .simulate('change', { target: { name: 'username', value: 'user' } });

        page.find('TextInput')
            .last()
            .simulate('change', { target: { name: 'password', value: 'password' } });

        page.find('form').simulate('submit', {
            preventDefault: () => {},
        });

        expect(loginMock).toHaveBeenCalled();
    });

    it('should display message if username and password are empty and submited', () => {
        const page = enzyme.shallow(<Login />);

        page.find('TextInput')
            .first()
            .simulate('change', { target: { name: 'username', value: '' } });

        page.find('TextInput')
            .last()
            .simulate('change', { target: { name: 'password', value: '' } });

        const button = page.find('Button');
        button.simulate('click');
        const errorMessage = page.find('p.error-message-content');
        expect(button).toBeDefined();
        expect(errorMessage).toBeDefined();
    });

    it('should enable login button if username and password are populated', () => {
        const page = enzyme.shallow(<Login />);

        page.find('TextInput')
            .first()
            .simulate('change', { target: { name: 'username', value: 'user' } });

        page.find('TextInput')
            .last()
            .simulate('change', { target: { name: 'password', value: 'password' } });

        const button = page.find('Button');
        expect(button).toBeDefined();
        expect(button.props().disabled).toBeFalsy();
    });

    it('should render form', () => {
        const page = enzyme.shallow(<Login />);

        expect(page.find('form')).toBeDefined();
    });

    it('should display a credentials failure message', () => {
        const wrapper = enzyme.shallow(<Login />);
        const instance = wrapper.instance();
        const messageText = instance.handleError({
            messageType: 'ERROR',
            messageNumber: 'ZWEAS120E',
            messageContent:
                "Authentication problem: 'Username or password are invalid.' for URL '/apicatalog/auth/login'",
            messageKey: 'org.zowe.apiml.security.invalidUsername',
        });
        expect(messageText).toEqual('(ZWEAS120E) Invalid username or password');
    });

    it('should display authetication service not available message', () => {
        const wrapper = enzyme.shallow(<Login />);
        const instance = wrapper.instance();
        const messageText = instance.handleError({
            messageType: 'ERROR',
            messageNumber: 'ZWEAS104E',
            messageContent:
                "Authentication service is not available by URL",
            messageKey: 'org.zowe.apiml.security.authenticationRequired',
        });
        expect(messageText).toEqual('(ZWEAS104E) Authentication service not available, please try again later');
    });

    it('should display session has expired', () => {
        const wrapper = enzyme.shallow(<Login />);
        const instance = wrapper.instance();
        const messageText = instance.handleError({
            messageType: 'ERROR',
            messageNumber: 'ZWEAS102E',
            messageContent:
                "Token is expired for URL",
            messageKey: 'org.zowe.apiml.security.expiredToken',
        });
        expect(messageText).toEqual('(ZWEAS102E) Session has expired, please login again');
    });

    it('should display generic failure message', () => {
        const wrapper = enzyme.shallow(<Login />);
        const instance = wrapper.instance();
        const messageText = instance.handleError({
            messageType: 'ERROR',
            messageNumber: 'ZWEAS100E',
            messageContent:
                "Authentication exception for URL",
            messageKey: 'org.zowe.apiml.security.generic',
        });
        expect(messageText).toEqual('(ZWEAS100E) A generic failure occurred while authenticating');
    });

    it('should display request timeout message', () => {
        const wrapper = enzyme.shallow(<Login />);
        const instance = wrapper.instance();
        const messageText = instance.handleError({
            messageType: 'ERROR',
            messageNumber: 'ZWEAM700E',
            messageContent:
                "No response received within the allowed time",
            messageKey: 'org.zowe.apiml.common.serviceTimeout',
        });
        expect(messageText).toEqual(`(ZWEAM700E) Request timeout, please try again later`);
    });

    it('should disable button and show spinner when request is being resolved', () => {
        const wrapper = enzyme.shallow(<Login isFetching />);

        const submitButton = wrapper.find('Button');
        const spinner = wrapper.find('Spinner');

        expect(submitButton).toBeDefined();
        expect(spinner).toBeDefined();
        expect(submitButton.props().disabled).toBeTruthy();
    });

    it('should display UI errorMessage', () => {
        const page = enzyme.shallow(<Login errorMessage="Cus bus" />);
        const errorMessage = page.find('p.error-message-content').first();

        expect(errorMessage).toBeDefined();
    });
});
