const path = require('path');

// * Tile info
let containers = require('../assets/containers');
const apicatalog = require('../assets/services/apicatalog');
const testinson = require('../assets/services/testinson');
const cademoapps = require('../assets/services/cademoapps');
const loginSuccess = require('../assets/services/login_success');
const invalidCredentials = require('../assets/services/user-name-invalid.json');
const passwordExpired = require('../assets/services/password-expired.json');
const apiCatalog = require('../assets/apidoc/apicatalog.json');
const discoverableClient = require('../assets/apidoc/discoverableclient');
const sampleClient = require('../assets/apidoc/sample');
const userSuspended = require('../assets/services/user-suspended.json');
let allUP = false;

function validateCredentials({ username, password }) {
    return username === 'user' && password === 'user';
}
function isUserSuspended({ username, password }) {
    return username === 'susp' && password === 'user';
}
function validateExpiredCredentials({ username, password }) {
    return username === 'user' && password === 'exp';
}

function validatePasswordUpdate({ username, password, newPassword }) {
    return username === 'user' && password === 'exp' && newPassword !== undefined && newPassword !== password;
}

const appRouter = app => {
    // NOTE: The root route
    app.get('/', (req, res) => {
        res.sendFile(path.join(`${__dirname}/../assets/hello/hello.html`));
    });

    app.post('/apicatalog/api/v1/auth/login', async (req, res) => {
        const credentials = req.body;
        if (validatePasswordUpdate(credentials)) {
            console.log('PASSWORD UPDATE');
            setTimeout(() => res.status(204).send(loginSuccess), 2000);
        } else if (validateCredentials(credentials)) {
            console.log('LOGIN');
            setTimeout(() => res.status(204).send(loginSuccess), 2000);
        } else if(validateExpiredCredentials(credentials)){
            res.status(401).send(passwordExpired);
        } else if(isUserSuspended(credentials)){
            res.status(401).send(userSuspended);
        }
        else {
            console.log(invalidCredentials);
            res.status(401).send(invalidCredentials);
        }
    });

    app.post('/apicatalog/api/v1/auth/logout', (req, res) => {
        res.status(200).send(loginSuccess);
    });

    app.get('/apicatalog/api/v1/containers', async (req, res) => {
        const data = containers;
        // await setTimeout(() => {res.status(200).send(data);}, 2000); // TODO can we externalise timeouts to dynamically simulate long running processes ?
        res.status(200).send(data);
    });

    /**
     * Toggle containers json
     */
    app.get('/apicatalog/api/v1/containers/some-down', (req, res) => {
        if (allUP) {
            containers = require('../assets/containers.json'); // eslint-disable-line global-require
            res.status(200).send({ message: 'Some tiles are down now' });
        } else {
            containers = require('../assets/containers-all-up.json'); // eslint-disable-line global-require
            res.status(200).send({ message: 'All tiles are up now' });
        }
        allUP = !allUP;
    });

    app.get('/apicatalog/api/v1/containers/:serviceID', (req, res) => {
        const ApiCatalog = apicatalog;
        const caDemoApps = cademoapps;
        const Testinson = testinson;
        console.log(`Fetching:${req.params.serviceID}`);
        switch (req.params.serviceID) {
            case 'apicatalog':
                res.status(200).send(ApiCatalog);
                break;
            case 'cademoapps':
                res.status(200).send(caDemoApps);
                break;
            case 'testinson':
                res.status(200).send(Testinson);
                break;
            default:
                res.status(400).send({ message: 'invalid serviceID' });
                break;
        }
    });

    app.get('/apicatalog/api/v1/apidoc/:serviceID/:version', (req, res) => {
        switch (req.params.serviceID) {
            case 'apicatalog':
                res.status(200).send(apiCatalog);
                break;
            case 'discoverableclient':
                res.status(200).send(discoverableClient);
                break;
            case 'otherclient':
                res.status(200).send(sampleClient);
                break;
            default:
                res.status(400).send({ message: 'invalid serviceID' });
                break;
        }
    });
};

module.exports = {
    router: appRouter,
};
