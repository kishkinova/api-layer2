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
import WizardNavigation from './WizardNavigation';
import { changeWizardCategory, checkFilledInput, nextWizardCategory } from '../../../actions/wizard-actions';

const mapStateToProps = state => ({
    selectedCategory: state.wizardReducer.selectedCategory,
    inputData: state.wizardReducer.inputData,
    navTabArray: state.wizardReducer.navTabArray,
});

const mapDispatchToProps = {
    nextWizardCategory,
    changeWizardCategory,
    checkFilledInput,
};

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(WizardNavigation);
