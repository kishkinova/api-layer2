/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

import {
    SELECT_ENABLER,
    TOGGLE_DISPLAY,
    INPUT_UPDATED,
    NEXT_CATEGORY,
    CHANGE_CATEGORY,
    READY_YAML_OBJECT,
    REMOVE_INDEX,
    VALIDATE_INPUT,
    UPDATE_SERVICE_ID,
    UPDATE_UPLOADED_YAML_TITLE,
} from '../constants/wizard-constants';

/**
 * Update the object containing user's input with any new input received
 * @param category the entire object, but with already updated values
 */
export function updateWizardData(category) {
    return {
        type: INPUT_UPDATED,
        payload: { category },
    };
}

/**
 * Notify the reducer to change the value of wizardIsOpen
 */
export function wizardToggleDisplay() {
    return {
        type: TOGGLE_DISPLAY,
        payload: null,
    };
}

/**
 * Notify which onboarding method the user has selected
 * @param enablerName the type/name of the onboarding method
 */
export function selectEnabler(enablerName) {
    return (dispatch, getState) => {
        const { tiles } = getState().tilesReducer;
        dispatch({
            type: SELECT_ENABLER,
            payload: { enablerName, tiles: tiles.map((tile) => tile.title) },
        });
    };
}

/**
 * Notifies the WizardNavigation component to move to the next nav.
 */
export function nextWizardCategory() {
    return {
        type: NEXT_CATEGORY,
        payload: null,
    };
}

/**
 * Notifies the WizardNavigation component to move to a specific nav.
 * @param num the index of the nav the user wants to move to.
 */
export function changeWizardCategory(num) {
    return {
        type: CHANGE_CATEGORY,
        payload: { category: num },
    };
}

/**
 * Insert an object in a parent object(if key already exists in parent, content is added, nothing is overwritten)
 * @param parent parent object
 * @param content object that should be inserted into parent
 */
export const insert = (parent, content) => {
    const keys = Object.keys(content);
    keys.forEach((currKey) => {
        if (parent[currKey] === undefined) {
            parent[currKey] = content[currKey];
        } else {
            insert(parent[currKey], content[currKey]);
        }
    });
};

/**
 * Adds the value of
 * @param inputData inputData array with all categories (already filled by user)
 * @param indentationDependency the name of the input on which it depends
 * @param indentation the predefined indentation of the category
 * @returns result the updated indentation
 */
export function handleIndentationDependency(inputData, indentationDependency, indentation) {
    let result = indentation;
    inputData.forEach((category) => {
        if (Array.isArray(category.content)) {
            category.content.forEach((inpt) => {
                Object.keys(inpt).forEach((k) => {
                    if (k === indentationDependency) {
                        result = result.concat('/', inpt[k].value);
                        return result;
                    }
                });
            });
        } else {
            Object.keys(category.content).forEach((k) => {
                if (k === indentationDependency) {
                    result = result.concat('/', category.content[k].value);
                    return result;
                }
            });
        }
    });
    return result;
}

export function handleArrayIndentation(arrIndent, content) {
    let finalContent = [];
    if (arrIndent !== undefined) {
        let index = 0;
        content.forEach((set) => {
            finalContent[index] = { [arrIndent]: set };
            index += 1;
        });
    } else {
        finalContent = content;
    }
    return finalContent;
}

/**
 * Checks if the current key/value pair should be placed in the yaml
 * @param valueObj object containing the value and the properties of the field
 * @returns {boolean} should be placed
 */
const shouldBePlacedInYAML = (valueObj) => {
    if (valueObj.show !== false && valueObj.hidden !== true) {
        return valueObj.value !== '' || valueObj.optional !== true;
    }
    return false;
};

/**
 * Updates the YAML with a category that has a multiple property set to true
 * @param category category object
 * @returns {*} an array containing the sets of data ready to be presented as YAML
 */
function handleCategoryMultiple(category) {
    let content = [];
    let index = 0;
    if (category.noKey) {
        category.content.forEach((o) => {
            Object.keys(o).forEach((key) => {
                const valueObj = category.content[index][key];
                if (shouldBePlacedInYAML(valueObj)) {
                    content[index] = category.content[index][key].value;
                }
            });
            index += 1;
        });
    } else {
        category.content.forEach((o) => {
            content[index] = {};
            Object.keys(o).forEach((key) => {
                const valueObj = category.content[index][key];
                if (shouldBePlacedInYAML(valueObj)) {
                    content[index][key] = category.content[index][key].value;
                }
            });
            content = handleArrayIndentation(category.arrIndent, content);
            index += 1;
        });
    }
    return content;
}

/**
 * Receives a single category and translates it to an object the yaml library can correctly convert to yaml
 * @param category a category
 * @param parent parent object which the yaml-ready category should be added to
 * @param inputData array with all categories (already filled by user)
 * @returns result the updated parent
 */
export const addCategoryToYamlObject = (category, parent, inputData) => {
    const result = { ...parent };
    let content = {};

    // load user's answer into content object
    if (!category.multiple) {
        const contentElement = category.content[0];
        Object.keys(contentElement).forEach((key) => {
            const valueObj = contentElement[key];
            if (shouldBePlacedInYAML(valueObj)) {
                content[key] = contentElement[key].value;
            }
        });
    } else {
        content = handleCategoryMultiple(category);
    }
    // handle indentation, if any
    if (!category.indentation) {
        insert(result, content);
    } else {
        let indent = category.indentation;
        if (category.indentationDependency !== undefined) {
            indent = handleIndentationDependency(inputData, category.indentationDependency, category.indentation);
        }
        const arr = indent.split('/');
        arr.reverse().forEach((key) => {
            if (key.length > 0)
                content = {
                    [key]: content,
                };
        });
        insert(result, content);
    }
    // return result
    return result;
};

/**
 * Receives the array containing all of user's input divided into categories and calls addCategoryToYamlObject for each.
 * @param inputData array with all categories (already filled by user)
 */
export function createYamlObject(inputData) {
    let result = {};
    const arr = [];
    inputData.forEach((category) => {
        if (category.inArr) {
            result = addCategoryToYamlObject(category, result, inputData);
        }
    });
    if (Object.keys(result).length > 0) {
        arr.push(result);
    }
    let finalResult;
    if (arr.length > 0) {
        finalResult = { services: arr };
    }
    inputData.forEach((cat) => {
        if (cat.inArr === undefined) {
            finalResult = addCategoryToYamlObject(cat, finalResult, inputData);
        }
    });
    return {
        type: READY_YAML_OBJECT,
        payload: { yaml: finalResult },
    };
}

/**
 * Relates to categories that can have multiple sets - deletes a set with a given id.
 * @param index index of the set to be deleted
 * @param text name of the category the set should be deleted from
 */
export function deleteCategoryConfig(index, text) {
    return {
        type: REMOVE_INDEX,
        payload: { index, text },
    };
}

/**
 * Validate input of a nav
 * @param navName name of the nav to be checked
 * @param silent respect/override interactedWith
 */
export function validateInput(navName, silent) {
    return {
        type: VALIDATE_INPUT,
        payload: { navName, silent },
    };
}

/**
 * Store serviceId's value, because it's needed for saving staatic definitons
 * @param value value of serviceId
 */
export function updateServiceId(value) {
    return {
        type: UPDATE_SERVICE_ID,
        payload: { value },
    };
}

/**
 * Store the title of the uploaded yaml file
 * @param value the title to be stored
 */
export function updateUploadedYamlTitle(value) {
    return {
        type: UPDATE_UPLOADED_YAML_TITLE,
        payload: { value },
    };
}
