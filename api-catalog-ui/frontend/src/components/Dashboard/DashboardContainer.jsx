/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
import { connect } from 'react-redux';
import Dashboard from './Dashboard';
import {
    fetchTilesFailed,
    fetchTilesStart,
    fetchTilesSuccess,
    fetchTilesStop,
    storeOriginalTiles,
} from '../../actions/catalog-tile-actions';
import { clearService } from '../../actions/selected-service-actions';
import { filterText, clear } from '../../actions/filter-actions';
import { createLoadingSelector, getVisibleTiles } from '../../selectors/selectors';
import { clearError, refreshedStaticApi } from '../../actions/refresh-static-apis-actions';
import { selectEnabler, wizardToggleDisplay } from '../../actions/wizard-actions';
import { userActions } from '../../actions/user-actions';

const loadingSelector = createLoadingSelector(['FETCH_TILES']);

const mapStateToProps = (state) => ({
    searchCriteria: state.filtersReducer.text,
    tiles: getVisibleTiles(state.tilesReducer.tiles, state.filtersReducer.text),
    fetchTilesError: state.tilesReducer.error,
    originalTiles: state.tilesReducer.originalTiles,
    isLoading: loadingSelector(state),
    refreshedStaticApisError: state.refreshStaticApisReducer.error,
    refreshTimestamp: state.refreshStaticApisReducer.refreshTimestamp,
    authentication: state.authenticationReducer,
});

const mapDispatchToProps = {
    clearService,
    fetchTilesStart,
    fetchTilesSuccess,
    fetchTilesFailed,
    fetchTilesStop,
    filterText,
    clear,
    refreshedStaticApi,
    clearError,
    wizardToggleDisplay,
    selectEnabler,
    closeAlert: () => userActions.closeAlert(),
    storeOriginalTiles: (tiles) => storeOriginalTiles(tiles),
};

export default connect(mapStateToProps, mapDispatchToProps)(Dashboard);
