/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
import { Component, Suspense } from 'react';
import { Redirect, Route, Router, Switch } from 'react-router-dom';
import { ToastContainer } from 'react-toastify';
import BigShield from '../ErrorBoundary/BigShield/BigShield';
import ErrorContainer from '../Error/ErrorContainer';
import '../../assets/css/APIMReactToastify.css';
import PageNotFound from '../PageNotFound/PageNotFound';
import HeaderContainer from '../Header/HeaderContainer';
import Spinner from '../Spinner/Spinner';
import { closeMobileMenu, isAPIPortal } from '../../utils/utilFunctions';
import { AsyncDashboardContainer, AsyncDetailPageContainer } from './AsyncModules'; // eslint-disable-line import/no-cycle
import HomepageContainer from '../Dashboard/HomepageContainer';

class App extends Component {
    componentDidMount() {
        // workaround for missing process polyfill in webpack 5
        window.process = { ...window.process };
        window.onresize = () => {
            if (document.body.offsetWidth > 767) {
                closeMobileMenu();
            }
        };
    }

    render() {
        const { history } = this.props;
        const isLoading = true;
        const headerPath = '/(dashboard|homepage|service/.*)/';
        const dashboardPath = '/dashboard';
        const homepagePath = '/homepage';
        return (
            <div className="App">
                <BigShield history={history}>
                    <ToastContainer />
                    <ErrorContainer />
                    <Suspense fallback={<Spinner isLoading={isLoading} />}>
                        <Router history={history}>
                            {/* eslint-disable-next-line react/jsx-no-useless-fragment */}
                            <>
                                <div className="content">
                                    <Route path={headerPath} component={HeaderContainer} />

                                    {isAPIPortal() && (
                                        <div className="dashboard-mobile-menu mobile-view">
                                            <Route path={headerPath} component={HeaderContainer} />
                                        </div>
                                    )}

                                    <Switch>
                                        <Route path="/" exact render={() => <Redirect replace to={homepagePath} />} />
                                        <Route
                                            exact
                                            path={homepagePath}
                                            render={(props, state) => (
                                                <BigShield>
                                                    <HomepageContainer {...props} {...state} />
                                                </BigShield>
                                            )}
                                        />
                                        <Route
                                            exact
                                            path={dashboardPath}
                                            render={(props, state) => (
                                                <BigShield>
                                                    <AsyncDashboardContainer {...props} {...state} />
                                                </BigShield>
                                            )}
                                        />
                                        <Route
                                            path="/service"
                                            render={(props, state) => (
                                                <BigShield history={history}>
                                                    <AsyncDetailPageContainer {...props} {...state} />
                                                </BigShield>
                                            )}
                                        />
                                        <Route
                                            render={(props, state) => (
                                                <BigShield history={history}>
                                                    <PageNotFound {...props} {...state} />
                                                </BigShield>
                                            )}
                                        />
                                    </Switch>
                                </div>
                            </>
                        </Router>
                    </Suspense>
                </BigShield>
            </div>
        );
    }
}

export default App;
