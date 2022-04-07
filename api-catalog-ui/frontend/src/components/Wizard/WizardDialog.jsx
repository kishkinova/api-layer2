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
import { Component } from 'react';
import { Dialog, DialogContent, DialogContentText, DialogTitle, DialogActions, IconButton } from '@material-ui/core';
import './wizard.css';
import WizardNavigationContainer from './WizardComponents/WizardNavigationContainer';

export default class WizardDialog extends Component {
    constructor(props) {
        super(props);
        this.nextSave = this.nextSave.bind(this);
        this.showFile = this.showFile.bind(this);
        this.fillInputs = this.fillInputs.bind(this);
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

    /**
     * Convert an uploaded yaml file to JSON and save in redux
     */
    showFile = (e) => {
        e.preventDefault();
        const reader = new FileReader();
        const filepath = e.target.value.split('\\');
        const filename = filepath[2];
        reader.onload = (event) => {
            const text = event.target.result;
            try {
                const obj = yaml.load(text);
                this.fillInputs(obj);
                this.props.updateUploadedYamlTitle(filename);
            } catch {
                document.getElementById('yaml-browser').value = null;
                this.props.notifyInvalidYamlUpload();
            }
        };
        reader.readAsText(e.target.files[0]);
    };

    /**
     * Fills in the field specified by the indentation dependency
     * @param inputData inputData array with all categories (already filled by user)
     * @param indentationDependency the name of the input on which it depends
     * @param indentationDependencyValue the value for the input
     */
    fillIndentationDependency = (inputData, indentationDependency, indentationDependencyValue) => {
        inputData.forEach((category) => {
            const objResult = { ...category };
            if (Array.isArray(category.content)) {
                category.content.forEach((inpt, j) => {
                    Object.keys(inpt).forEach((k) => {
                        if (k === indentationDependency) {
                            objResult.content[j][k].value = indentationDependencyValue;
                        }
                    });
                });
            } else {
                Object.keys(category.content).forEach((k) => {
                    if (k === indentationDependency) {
                        objResult.content[k].value = indentationDependencyValue;
                    }
                });
            }
            this.props.updateWizardData(objResult);
        });
    };

    /**
     * Check the non-applicable restrictions
     * @param inputObject one input object
     * @param value user's input
     * @param regexRestriction restriction in regex expression
     * @param validUrl whether the value should be a valid URL
     * @returns {boolean} true if there's a problem
     */
    checkRestrictions(inputObject, value, regexRestriction, validUrl) {
        let problem = false;
        if (regexRestriction !== undefined) {
            regexRestriction.forEach((regex) => {
                const restriction = new RegExp(regex.value);
                if (!restriction.test(value)) {
                    inputObject.tooltip = regex.tooltip;
                    problem = true;
                }
            });
        }
        if (validUrl) {
            try {
                // eslint-disable-next-line no-new
                new URL(value);
                problem = problem || false;
                return problem;
            } catch {
                inputObject.tooltip = 'The URL has to be valid, example: https://localhost:10014';
                return true;
            }
        }
        return problem;
    }

    /**
     * Add to the inputData content
     * @param obj the item of inputData being searched through
     * @param objResult the current changes to the inputData item
     * @param index the index of the inputData content item
     * @param propertyKey the current value's key
     * @param value the value to be set from the yaml
     */
    addToContent = (obj, objResult, index, propertyKey, value) => {
        // If this path exists in the uploaded yaml and its a field that has potential replicas
        if (obj.multiple && obj.multiple === true) {
            value.forEach((individualValue, individualIndex) => {
                if (objResult.content.length <= individualIndex) {
                    objResult.content.push(JSON.parse(JSON.stringify(obj.content[0])));
                }
                let valueToSet = null;
                if (obj.noKey && obj.noKey === true && individualValue) {
                    valueToSet = individualValue;
                } else if (obj.arrIndent && individualValue[obj.arrIndent][propertyKey]) {
                    valueToSet = individualValue[obj.arrIndent][propertyKey];
                } else if (individualValue[propertyKey]) {
                    valueToSet = individualValue[propertyKey];
                }
                if (valueToSet != null) {
                    objResult.content[individualIndex][propertyKey].value = valueToSet;
                    objResult.content[individualIndex][propertyKey].interactedWith = true;
                    objResult.content[individualIndex][propertyKey].empty = false;
                    objResult.content[index][propertyKey].problem = this.checkRestrictions(
                        objResult.content[index][propertyKey],
                        valueToSet,
                        obj.content[index][propertyKey].regexRestriction,
                        obj.content[index][propertyKey].validUrl
                    );
                }
            });
        } else if (value[propertyKey]) {
            objResult.content[index][propertyKey].value = value[propertyKey];
            objResult.content[index][propertyKey].interactedWith = true;
            objResult.content[index][propertyKey].empty = false;
            objResult.content[index][propertyKey].problem = this.checkRestrictions(
                objResult.content[index][propertyKey],
                value[propertyKey],
                obj.content[index][propertyKey].regexRestriction,
                obj.content[index][propertyKey].validUrl
            );
        }
        return objResult;
    };

    /**
     * Find each value from the yaml to fill the fields
     * @param obj the item of inputData being searched through
     * @param objResultIn the current changes to the inputData item
     * @param uploadedYaml the yaml object uploaded by the user
     */
    findValue = (obj, objResultIn, uploadedYaml) => {
        let objResult = { ...objResultIn };
        let path = [];
        // Get indentation to mimic hierarchy of yaml
        if (obj.indentation) {
            path = obj.indentation.split('/');
        }
        let value = uploadedYaml;
        let found = true;
        // Set value if it is in an array
        if (obj.inArr) {
            [value] = value.services;
        }
        // Navigate down Yaml hierarchy to find desired value to change to
        if (path.length > 0) {
            path.forEach((indent) => {
                if (found && value[indent]) {
                    value = value[indent];
                } else {
                    found = false;
                }
            });
        }
        if (obj.indentationDependency) {
            this.fillIndentationDependency(this.props.inputData, obj.indentationDependency, Object.keys(value)[0]);
            value = value[Object.keys(value)[0]];
        }
        if (found) {
            // Loop through all possible replicas of an input group (e.g. Routes 1 (field1, field2), Routes 2 (field1, field2))
            obj.content.forEach((property, index) => {
                // Loop through each input in an input group replica
                Object.keys(property).forEach((propertyKey) => {
                    objResult = this.addToContent(obj, objResult, index, propertyKey, value);
                });
            });
        }
        return objResult;
    };

    /**
     * Go through the uploaded yaml file and fill the corresponding input fields
     * @param uploadedYaml the yaml object uploaded by the user
     */
    fillInputs = (uploadedYaml) => {
        if (this.props.inputData) {
            // Loop through all input groups (tabs/sections)
            this.props.inputData.forEach((obj) => {
                let objResult = { ...obj };
                if (obj.content) {
                    objResult = this.findValue(obj, objResult, uploadedYaml);
                }
                this.props.updateWizardData(objResult);
            });
        }
        // Validate all fields
        const navNamesArr = Object.keys(this.props.navsObj);
        navNamesArr.forEach((navName) => {
            this.props.validateInput(navName, false);
        });
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
                        <DialogContentText>
                            Select your YAML configuration file to prefill the fields:
                        </DialogContentText>
                        <div id="yaml-upload-container">
                            <label id="yaml-upload" nesting="true" htmlFor="yaml-browser">
                                <input id="yaml-browser" type="file" onChange={this.showFile} />
                                Choose File
                            </label>
                            {this.props.uploadedYamlTitle ? (
                                <span id="yaml-file-text">{this.props.uploadedYamlTitle}</span>
                            ) : null}
                        </div>
                        <DialogContentText>Or fill the fields:</DialogContentText>
                        <WizardNavigationContainer />
                    </DialogContent>
                    <DialogActions>
                        <IconButton
                            id="wizard-cancel-button"
                            size="medium"
                            onClick={() => {
                                this.closeWizard();
                                this.props.updateUploadedYamlTitle('');
                            }}
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
