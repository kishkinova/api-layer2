/* eslint-disable no-undef */
import * as React from 'react';
// tslint:disable-next-line:no-implicit-dependencies
import {shallow} from 'enzyme';
import PageNotFound from './PageNotFound';

describe('>>> Detailed Page component tests', () => {
    it('should handle a dashboard button click', () => {
        const historyMock = { push: jest.fn() };
        const wrapper = shallow(<PageNotFound history={historyMock} />);
        wrapper.find('[data-testid="go-home-button"]').simulate('click');
        expect(historyMock.push.mock.calls[0]).toEqual(['/dashboard']);
    });
});
