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
    return {
        type: SELECT_ENABLER,
        payload: { enablerName },
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
    keys.forEach(currKey => {
        if (parent[currKey] === undefined) {
            parent[currKey] = content[currKey];
        } else {
            insert(parent[currKey], content[currKey]);
        }
    });
};

/**
 * Receives a single category and translates it to an object the yaml library can correctly convert to yaml
 * @param category a category
 * @param parent parent object which the yaml-ready category should be added to
 * @returns result the updated parent
 */
export const addCategoryToYamlObject = (category, parent) => {
    const result = { ...parent };
    let content = {};
    // load user's answer into content object
    if (!Array.isArray(category.content)) {
        Object.keys(category.content).forEach(key => {
            content[key] = category.content[key].value;
        });
    } else {
        content = [];
        let index = 0;
        if (category.noKey) {
            category.content.forEach(o => {
                Object.keys(o).forEach(key => {
                    content[index] = category.content[index][key].value;
                });
                index += 1;
            });
        } else {
            category.content.forEach(o => {
                content[index] = {};
                Object.keys(o).forEach(key => {
                    content[index][key] = category.content[index][key].value;
                });
                index += 1;
            });
        }
    }
    // handle indentation, if any
    if (!category.indentation) {
        insert(result, content);
    } else {
        const indent = category.indentation;
        const arr = indent.split('/');
        arr.reverse().forEach(key => {
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
    inputData.forEach(category => {
        result = addCategoryToYamlObject(category, result);
    });
    return {
        type: READY_YAML_OBJECT,
        payload: { yaml: result },
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
