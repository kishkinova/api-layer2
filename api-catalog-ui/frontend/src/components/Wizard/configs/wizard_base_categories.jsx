import { wizRegex } from './wizard_regex_restrictions';

// eslint-disable-next-line import/prefer-default-export
export const baseCategories = [
    {
        text: 'Basic info',
        content: {
            serviceId: {
                value: '',
                question: 'A unique identifier for the API (service ID):',
                maxLength: 40,
                lowercase: true,
            },
            title: {
                value: '',
                question: 'The name of the service (human readable):',
            },
        },
    },
    {
        text: 'Description',
        content: {
            description: {
                value: '',
                question: 'A concise description of the service:',
            },
        },
    },
    {
        text: 'Base URL',
        content: {
            baseUrl: {
                value: '',
                question: 'The base URL of the service (the consistent part of the web address):',
                validUrl: true,
            },
        },
    },
    {
        text: 'Prefer IP address',
        content: {
            preferIpAddress: {
                value: false,
                question: 'Advertise service IP address instead of its hostname',
            },
        },
    },
    {
        text: 'Scheme info',
        content: {
            scheme: {
                value: 'https',
                question: 'Service scheme:',
            },
            hostname: {
                value: '',
                question: 'Service hostname:',
            },
            port: {
                value: '',
                question: 'Service port:',
            },
            contextPath: {
                value: '',
                question: 'Context path:',
            },
        },
    },
    {
        text: 'IP address info',
        content: {
            serviceIpAddress: {
                value: '',
                question: 'The service IP address:',
                optional: true,
                regexRestriction: [wizRegex.ipAddress],
            },
        },
    },
    {
        text: 'URL',
        content: {
            homePageRelativeUrl: {
                value: '',
                question: 'The relative path to the home page of the service:',
                optional: true,
                regexRestriction: [wizRegex.validRelativeUrl],
            },
            statusPageRelativeUrl: {
                value: '',
                question: 'The relative path to the status page of the service:',
                optional: true,
                regexRestriction: [wizRegex.validRelativeUrl],
            },
            healthCheckRelativeUrl: {
                value: '',
                question: 'The relative path to the health check endpoint of the service:',
                optional: true,
                regexRestriction: [wizRegex.validRelativeUrl],
            },
        },
    },
    {
        text: 'Discovery Service URL',
        content: {
            discoveryServiceUrls: {
                value: '',
                question: 'Discovery Service URL:',
                validUrl: true,
            },
        },
        multiple: false,
        noKey: true,
    },
    {
        text: 'Routes',
        content: {
            gatewayUrl: {
                value: '',
                question: 'Expose the Service API on Gateway under context path:',
                tooltip: 'Format: /api/vX, Example: /api/v1',
                regexRestriction: [wizRegex.gatewayUrl],
            },
            serviceUrl: {
                value: '',
                question: 'Service API common context path:',
            },
        },
        help: 'For service: <service>/allOfMyEndpointsAreHere/** exposed on Gateway under <gateway>/<serviceid>/api/v1/**\nFill in:\ngatewayUrl: /api/v1\nserviceUrl: /allOfMyEndpointsAreHere',
        multiple: true,
    },

    {
        text: 'Authentication',
        content: {
            scheme: {
                value: 'bypass',
                question: 'Authentication:',
                options: ['bypass', 'zoweJwt', 'httpBasicPassTicket', 'zosmf', 'x509'],
            },
            applid: {
                value: '',
                question: 'A service APPLID (valid only for the httpBasicPassTicket authentication scheme ):',
                dependencies: { scheme: 'httpBasicPassTicket' },
            },
            headers: {
                value: 'X-Certificate-Public',
                question: 'For the x509 scheme use the headers parameter to select which values to send to a service',
                dependencies: { scheme: 'x509' },
                options: ['X-Certificate-Public', 'X-Certificate-DistinguishedName', 'X-Certificate-CommonName'],
            },
        },
        help: 'The following service authentication schemes are supported by the API Gateway: bypass, zoweJwt, httpBasicPassTicket, zosmf, x509. ',
        helpUrl: {
            title: 'More information about the authentication parameters',
            link: 'https://docs.zowe.org/stable/extend/extend-apiml/onboard-plain-java-enabler/#api-catalog-information',
        },
    },
    {
        text: 'API Info',
        content: {
            apiId: {
                value: '',
                question: 'A unique identifier to the API in the API ML:',
            },
            version: {
                value: '',
                question: 'API version:',
                tooltip: 'API version',
                regexRestriction: [wizRegex.version],
            },
            gatewayUrl: {
                value: '',
                question: 'The base path at the API Gateway where the API is available:',
                tooltip: 'Format: api/vX, Example: api/v1',
                regexRestriction: [wizRegex.gatewayUrl],
            },
            swaggerUrl: {
                value: '',
                question: 'The Http or Https address where the Swagger JSON document is available:',
                optional: true,
            },
            documentationUrl: {
                value: '',
                question: 'Link to the external documentation:',
                optional: true,
            },
        },
    },
    {
        text: 'Catalog',
        content: {
            type: {
                value: 'Custom',
                question: 'Choose existing catalog tile or create a new one:',
                options: ['Custom'],
                hidden: true,
            },
            id: {
                value: '',
                question: 'The unique identifier for the product family of API services:',
                tooltip: 'reverse domain name notation, e.g. org.zowe.apiml',
            },
            title: {
                value: '',
                question: 'The title of the product family of the API service:',
            },
            description: {
                value: '',
                question: 'A description of the API service product family:',
            },
            version: {
                value: '',
                question: 'The semantic version of this API Catalog tile (increase when adding changes):',
            },
        },
        interference: 'catalog',
    },
    {
        text: 'SSL',
        content: {
            verifySslCertificatesOfServices: {
                value: false,
                question: 'Verify SSL certificates of services:',
            },
            protocol: {
                value: 'TLSv1.2',
                question: 'The TLS protocol version used by Zowe API ML Discovery Service:',
            },
            keyAlias: {
                value: '',
                question: 'The alias used to address the private key in the keystore',
            },
            keyPassword: {
                value: '',
                question: 'The password associated with the private key:',
                type: 'password',
            },
            keyStore: {
                value: '',
                question: 'The keystore file used to store the private key (keyring: set to SAF keyring location):',
            },
            keyStorePassword: {
                value: '',
                question: 'The password used to unlock the keystore:',
                type: 'password',
            },
            keyStoreType: {
                value: 'PKCS12',
                question: 'Type of the keystore:',
                options: ['PKCS12', 'JKS', 'JCERACFKS'],
            },
            trustStore: {
                value: '',
                question: 'The truststore file used to keep other parties public keys and certificates:',
            },
            trustStorePassword: {
                value: '',
                question: 'The password used to unlock the truststore:',
                type: 'password',
            },
            trustStoreType: {
                value: 'PKCS12',
                question: 'Truststore type:',
                options: ['PKCS12', 'JKS', 'JCERACFKS'],
            },
        },
    },
];
