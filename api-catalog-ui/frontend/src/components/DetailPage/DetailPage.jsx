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
import { Container, IconButton, Link, Typography } from '@material-ui/core';
import ChevronLeftIcon from '@material-ui/icons/ChevronLeft';
import { Redirect, Route, Router, Switch } from 'react-router-dom';
import Spinner from '../Spinner/Spinner';
import formatError from '../Error/ErrorFormatter';
import ServiceTabContainer from '../ServiceTab/ServiceTabContainer';
import PageNotFound from '../PageNotFound/PageNotFound';
import BigShield from '../ErrorBoundary/BigShield/BigShield';
import ServicesNavigationBarContainer from '../ServicesNavigationBar/ServicesNavigationBarContainer';
import Shield from '../ErrorBoundary/Shield/Shield';

export default class DetailPage extends Component {
    componentDidMount() {
        const { fetchTilesStart, currentTileId, fetchNewTiles } = this.props;
        fetchNewTiles();
        if (currentTileId) {
            fetchTilesStart(currentTileId);
        }
    }

    componentWillUnmount() {
        const { fetchTilesStop } = this.props;
        fetchTilesStop();
    }

    // eslint-disable-next-line react/sort-comp
    handleGoBack = () => {
        const { history } = this.props;
        history.push('/dashboard');
    };

    render() {
        const {
            tiles,
            isLoading,
            clearService,
            fetchTilesStop,
            fetchTilesError,
            selectedTile,
            services,
            match,
            fetchTilesStart,
            history,
            currentTileId,
            fetchNewTiles,
        } = this.props;
        const iconBack = <ChevronLeftIcon />;
        let error = null;
        if (fetchTilesError !== undefined && fetchTilesError !== null) {
            fetchTilesStop();
            error = formatError(fetchTilesError);
        } else if (selectedTile !== null && selectedTile !== undefined && selectedTile !== currentTileId) {
            clearService();
            fetchTilesStop();
            fetchNewTiles();
            fetchTilesStart(currentTileId);
        }
        return (
            <div className="main-content2 detail-content">
                <Spinner isLoading={isLoading} />
                {fetchTilesError && (
                    <div className="no-tiles-container">
                        <br />
                        <IconButton id="go-back-button" onClick={this.handleGoBack} size="medium">
                            {iconBack}
                            Back
                        </IconButton>
                        <br />
                        <br />
                        <br />
                        <br />
                        <Typography style={{ color: '#de1b1b' }} data-testid="detail-page-error" variant="subtitle2">
                            Tile details for "{currentTileId}" could not be retrieved, the following error was returned:
                        </Typography>
                        {error}
                    </div>
                )}
                <div className="nav-bar">
                    {services !== undefined && services.length > 0 && (
                        <Shield>
                            <ServicesNavigationBarContainer services={services} match={match} />
                        </Shield>
                    )}
                </div>
                {!isLoading && !fetchTilesError && (
                    <div className="api-description-container">
                        <IconButton
                            id="go-back-button"
                            data-testid="go-back-button"
                            color="primary"
                            onClick={this.handleGoBack}
                            size="medium"
                        >
                            {iconBack}
                            Back
                        </IconButton>
                        <div className="detailed-description-container">
                            <div className="title-api-container">
                                {tiles !== undefined && tiles.length === 1 && (
                                    <h2 id="title" className="text-block-11">
                                        {tiles[0].title}
                                    </h2>
                                )}
                            </div>
                            <div className="paragraph-description-container">
                                {tiles !== undefined && tiles.length > 0 && (
                                    <h4 id="description" className="text-block-12">
                                        {tiles[0].description}
                                    </h4>
                                )}
                            </div>
                        </div>
                        {process.env.REACT_APP_API_PORTAL !== undefined && process.env.REACT_APP_API_PORTAL === 'true' && (
                            <div id="right-resources-menu">
                                <Typography variant="subtitle1">On this page</Typography>
                                <Container>
                                    <Link className="links">Swagger</Link>
                                    <Link className="links">Use cases</Link>
                                    <Link className="links">Tutorials</Link>
                                    <Link className="links">Videos</Link>
                                </Container>
                            </div>
                        )}
                    </div>
                )}
                <div className="content-description-container">
                    {tiles !== undefined && tiles.length === 1 && (
                        <Suspense>
                            <Router history={history}>
                                <Switch>
                                    <Route
                                        exact
                                        path={`${match.path}`}
                                        render={() => (
                                            <Redirect replace to={`${match.url}/${tiles[0].services[0].serviceId}`} />
                                        )}
                                    />
                                    <Route
                                        exact
                                        path={`${match.path}/:serviceId`}
                                        render={() => (
                                            <div className="tabs-swagger">
                                                <ServiceTabContainer tiles={tiles} />
                                            </div>
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
                            </Router>
                        </Suspense>
                    )}
                </div>
            </div>
        );
    }
}
