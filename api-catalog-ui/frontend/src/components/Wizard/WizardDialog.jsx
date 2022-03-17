/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */
import yaml from 'js-yaml';
import * as YAML from 'yaml';
import { toast } from 'react-toastify';
import { Component } from 'react';
import { Dialog, DialogContent, DialogContentText, DialogTitle, DialogActions, IconButton } from '@material-ui/core';
import './wizard.css';
import WizardNavigationContainer from './WizardComponents/WizardNavigationContainer';

export default class WizardDialog extends Component {
    constructor(props) {
        super(props);
        this.nextSave = this.nextSave.bind(this);
        this.showFile = this.showFile.bind(this);
        this.renderDoneButtonText = this.renderDoneButtonText.bind(this);
    }

    closeWizard = () => {
        const { wizardToggleDisplay } = this.props;
        wizardToggleDisplay();
    };

    doneWizard = () => {
        const { sendYAML, navsObj, notifyError, yamlObject, serviceId, enablerName } = this.props;

        /**
         * Check that all mandatory fields are filled.
         * @param navs navsObj contains information about all unfilled fields in each nav tab.
         * @returns {boolean} true if no mandatory fields are empty
         */
        const presenceIsSufficient = (navs) => {
            let sufficient = true;
            Object.keys(navs).forEach((nav) => {
                Object.keys(navs[nav]).forEach((category) => {
                    if (Array.isArray(navs[nav][category])) {
                        navs[nav][category].forEach((set) => {
                            if (set.length > 0) {
                                sufficient = false;
                            }
                        });
                    }
                });
            });
            return sufficient;
        };
        if (enablerName !== 'Static Onboarding' || !this.props.userCanAutoOnboard) {
            this.closeWizard();
        } else if (presenceIsSufficient(navsObj)) {
            sendYAML(YAML.stringify(yamlObject), serviceId);
        } else {
            notifyError('Fill all mandatory fields first!');
        }
    };

    /**
     * Displays either Next or Save, depending whether the user is at the last stage or not.
     */
    nextSave = () => {
        const { selectedCategory, navsObj, nextWizardCategory, validateInput } = this.props;
        if (selectedCategory < Object.keys(navsObj).length) {
            validateInput(Object.keys(navsObj)[selectedCategory], false);
            nextWizardCategory();
            if (selectedCategory === Object.keys(navsObj).length - 1) {
                const navNamesArr = Object.keys(this.props.navsObj);
                navNamesArr.forEach((navName) => {
                    this.props.validateInput(navName, false);
                });
            }
        } else {
            this.doneWizard();
        }
    };

    // Convert an uploaded yaml file to JSON and save in local storage
    showFile = (e) => {
        e.preventDefault();
        const reader = new FileReader();
        reader.onload = (event) => {
            const text = event.target.result;
            try {
                const obj = yaml.load(text);
                this.props.storeUploadedYaml(obj);
            } catch {
                document.getElementById('yaml-browser').value = null;
                toast.warn('Please make sure the file you are uploading is in valid YAML format!', {
                    closeOnClick: true,
                    autoClose: 4000,
                });
            }
        };
        reader.readAsText(e.target.files[0]);
    };

    renderDoneButtonText() {
        if (this.props.enablerName === 'Static Onboarding' && this.props.userCanAutoOnboard) {
            return 'Save';
        }
        return 'Done';
    }

    render() {
        const { wizardIsOpen, enablerName, selectedCategory, navsObj } = this.props;
        const size = selectedCategory === Object.keys(navsObj).length ? 'large' : 'medium';
        const disable = selectedCategory !== Object.keys(navsObj).length;
        return (
            <div className="dialog">
                <Dialog id="wizard-dialog" open={wizardIsOpen} size={size}>
                    <DialogTitle>Onboard a New API Using {enablerName}</DialogTitle>
                    <DialogContent>
                        <DialogContentText>
                            This wizard will guide you through creating a correct YAML for your application.
                        </DialogContentText>
                        <div className="yaml-file-browser">
                            <div>Select your YAML configuration file to prefill the fields:</div>
                            <input id="yaml-browser" type="file" onChange={this.showFile} />
                        </div>
                        <WizardNavigationContainer />
                    </DialogContent>
                    <DialogActions>
                        <IconButton
                            id="wizard-cancel-button"
                            size="medium"
                            onClick={this.closeWizard}
                            style={{ borderRadius: '0.1875em' }}
                        >
                            Cancel
                        </IconButton>
                        <IconButton
                            id="wizard-done-button"
                            size="medium"
                            onClick={this.nextSave}
                            disabled={disable}
                            style={{ borderRadius: '0.1875em' }}
                        >
                            {this.renderDoneButtonText()}
                        </IconButton>
                    </DialogActions>
                </Dialog>
            </div>
        );
    }
}
