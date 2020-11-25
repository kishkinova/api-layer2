
export const REQUEST_VERSION_DIFF = 'REQUEST_VERSION_DIFF';
export const RECEIVE_VERSION_DIFF = 'RECEIVE_VERSION_DIFF';

export function getDiff(serviceId, oldVersion, newVersion) {
    function request(serviceId, oldVersion, newVersion) {
        return {
            type: REQUEST_VERSION_DIFF,
            serviceId,
            oldVersion,
            newVersion,
        }
    }

    function receive(diffText) {
        return {
            type: RECEIVE_VERSION_DIFF,
            diffText,
        }
    }

    return dispatch => {
        dispatch(request(serviceId, oldVersion, newVersion));

        return fetch(process.env.REACT_APP_GATEWAY_URL +
            process.env.REACT_APP_CATALOG_HOME +
            process.env.REACT_APP_APIDOC_UPDATE + 
            `/${serviceId}/${oldVersion}/${newVersion}`)
            .then(response => {
                return response.text();
            })
            .then(text => {
                return dispatch(receive(text))
            })
            .catch(e => {
                console.log(e);
            });
    }
}