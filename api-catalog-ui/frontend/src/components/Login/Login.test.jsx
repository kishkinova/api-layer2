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
import jest from 'jest-mock';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom/extend-expect';
import Login from './Login';

describe('>>> Login page component tests', () => {
    it('should display password update form', () => {
        render(
            <Login
                authentication={{
                    error: {
                        messageNumber: 'ZWEAT412E',
                        messageType: 'ERROR',
                    },
                    expired: true,
                }}
            />
        );
        const newPassInput = screen.getByTestId('newPassword');
        const repeatNewPassword = screen.getByTestId('repeatNewPassword');
        expect(newPassInput).toBeInTheDocument();
        expect(repeatNewPassword).toBeInTheDocument();
    });

    it('should display password validation error', () => {
        render(
            <Login
                authentication={{
                    error: {
                        messageNumber: 'ZWEAT604E',
                        messageType: 'ERROR',
                    },
                    expired: true,
                }}
            />
        );
        const newPassInput = screen.getByText('Passwords do not match');
        expect(newPassInput).toBeInTheDocument();
    });

    it('should display account suspended warning ', () => {
        render(
            <Login
                authentication={{
                    error: {
                        messageNumber: 'ZWEAT414E',
                        messageType: 'ERROR',
                    },
                }}
            />
        );
        const suspendedBtn = screen.getByTestId('suspendedBackToLogin');
        expect(suspendedBtn).toBeInTheDocument();
    });

    it('should return to login ', () => {
        const backToLoginMock = jest.fn();
        render(
            <Login
                returnToLogin={backToLoginMock}
                authentication={{
                    error: {
                        messageNumber: 'ZWEAT414E',
                        messageType: 'ERROR',
                    },
                }}
            />
        );
        const suspendedBtn = screen.getByTestId('suspendedBackToLogin');
        fireEvent.click(suspendedBtn);
        expect(backToLoginMock).toHaveBeenCalled();
    });

    it('should submit username and password input', () => {
        const loginMock = jest.fn();

        const page = enzyme.shallow(<Login login={loginMock} />);
        page.find('[data-testid="username"]')
            .first()
            .simulate('change', { target: { name: 'username', value: 'user' } });

        page.find('[data-testid="password"]')
            .last()
            .simulate('change', { target: { name: 'password', value: 'password' } });

        page.find('form').simulate('submit', {
            preventDefault: () => {},
        });

        expect(loginMock).toHaveBeenCalled();
    });

    it('should display message if username and password are empty and submited', () => {
        const page = enzyme.shallow(<Login />);

        page.find('[data-testid="username"]')
            .first()
            .simulate('change', { target: { name: 'username', value: '' } });

        page.find('[data-testid="password"]')
            .last()
            .simulate('change', { target: { name: 'password', value: '' } });

        const button = page.find('[data-testid="submit"]');
        button.simulate('click');
        const errorMessage = page.find('p.error-message-content');
        expect(button).toBeDefined();
        expect(errorMessage).toBeDefined();
    });

    it('should enable login button if username and password are populated', () => {
        const page = enzyme.shallow(<Login />);

        page.find('[data-testid="username"]')
            .first()
            .simulate('change', { target: { name: 'username', value: 'user' } });

        page.find('[data-testid="password"]')
            .last()
            .simulate('change', { target: { name: 'password', value: 'password' } });

        const button = page.find('[data-testid="submit"]');
        expect(button).toBeDefined();
        expect(button.props().disabled).toBeFalsy();
    });

    it('should render form', () => {
        const page = enzyme.shallow(<Login />);

        expect(page.find('form')).toBeDefined();
    });

    it('should display a credentials failure message', () => {
        render(
            <Login
                authentication={{
                    error: {
                        messageType: 'ERROR',
                        messageNumber: 'ZWEAS120E',
                        messageContent:
                            "Authentication problem: 'Invalid Credentials' for URL '/apicatalog/auth/login'",
                        messageKey: 'org.zowe.apiml.security.invalidUsername',
                    },
                }}
            />
        );
        expect(screen.getByText('Invalid Credentials')).toBeInTheDocument();
    });

    it('should disable button and show spinner when request is being resolved', () => {
        render(<Login isFetching />);

        expect(screen.getByTestId('submit')).toBeInTheDocument();
        expect(screen.getByTestId('spinner')).toBeInTheDocument();
    });

    it('should display UI errorMessage', () => {
        const page = enzyme.shallow(<Login errorMessage="Cus bus" />);
        const errorMessage = page.find('p.error-message-content').first();

        expect(errorMessage).toBeDefined();
    });

    it('should track keydown event on capslock', () => {
        const page = enzyme.shallow(<Login />);
        page.find('[data-testid="password"]').last().simulate('keydown', { keyCode: 20 });
        const label = page.find('#capslock');
        expect(label.text()).toEqual('Caps Lock is ON!');
    });

    it('should track keydown event on shift', () => {
        const page = enzyme.shallow(<Login />);
        page.find('[data-testid="password"]').last().simulate('keydown', { keyCode: 16 });
        const label = page.find('#capslock');
        expect(label.text()).toEqual('Caps Lock is ON!');
    });

    it('should track keyup event on capslock', () => {
        const page = enzyme.shallow(<Login />);
        page.find('[data-testid="password"]').last().simulate('keyup', { keyCode: 20 });
        const label = page.find('#capslock');
        expect(label).toEqual({});
    });

    it('should track keyup event on shift', () => {
        const page = enzyme.shallow(<Login />);
        page.find('[data-testid="password"]').last().simulate('keyup', { keyCode: 16 });
        const label = page.find('#capslock');
        expect(label).toEqual({});
    });
});
