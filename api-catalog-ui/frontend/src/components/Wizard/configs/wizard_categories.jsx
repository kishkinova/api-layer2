/**
 * each new category:
 * 1. must contain properties:
 *  1.1. text - the name of the category
 *  1.2. content - object containing sub-objects (each with a value and a question key)
 * 2. can contain properties:
 *  2.1. multiple - boolean, if true, allows for multiple sets of configuration
 *  2.2. indentation - string, nests object like so: 'a/b' - { a:{ b:your_object } }
 */
// eslint-disable-next-line import/prefer-default-export
export const categoryData = [
    {
        text: 'Basic info',
        content: {
            serviceId: {
                value: '',
                question: 'A unique identifier for the API:',
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
            },
            statusPageRelativeUrl: {
                value: '',
                question: 'The relative path to the status page of the service:',
            },
            healthCheckRelativeUrl: {
                value: '',
                question: 'The relative path to the health check endpoint of the service:',
            },
        },
    },
    {
        text: 'URL for Static',
        content: {
            instanceBaseUrls: {
                value: '',
                question: 'The base URL of the instance (the consistent part of the web address):',
            },
        },
        multiple: false,
        noKey: true,
    },
    {
        text: 'Discovery Service URL',
        content: {
            discoveryServiceHost: {
                value: '',
                question: 'Discovery Service URL:',
            },
        },
        multiple: true,
        noKey: true,
    },
    {
        text: 'Routes',
        content: {
            gatewayUrl: {
                value: '',
                question: 'The portion of the gateway URL which is replaced by the serviceUrl path part:',
            },
            serviceUrl: {
                value: '',
                question: 'A portion of the service instance URL path which replaces the gatewayUrl part:',
            },
        },
        multiple: true,
    },
    {
        text: 'Routes for Static & Node',
        content: {
            gatewayUrl: {
                value: '',
                question: 'The portion of the gateway URL which is replaced by the serviceUrl path part:',
            },
            serviceRelativeUrl: {
                value: '',
                question: 'A portion of the service instance URL path which replaces the gatewayUrl part:',
            },
        },
        multiple: true,
    },
    {
        text: 'Authentication',
        content: {
            scheme: {
                value: 'bypass',
                question: 'Authentication:',
                options: ['bypass', 'zoweJwt', 'httpBasicPassTicket', 'zosmf', 'x509', 'headers'],
            },
            applid: {
                value: '',
                question: 'A service APPLID (valid only for the httpBasicPassTicket authentication scheme ):',
                dependencies: { scheme: 'httpBasicPassTicket' },
            },
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
            },
            gatewayUrl: {
                value: '',
                question: 'The base path at the API Gateway where the API is available:',
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
        text: 'API Info shorter',
        content: {
            apiId: {
                value: '',
                question: 'A unique identifier to the API in the API ML:',
            },
            gatewayUrl: {
                value: '',
                question: 'The base path at the API Gateway where the API is available:',
            },
            swaggerUrl: {
                value: '',
                question: 'The Http or Https address where the Swagger JSON document is available:',
                optional: true,
            },
        },
    },
    {
        text: 'Catalog',
        content: {
            id: {
                value: '',
                question: 'The unique identifier for the product family of API services:',
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
    },
    {
        text: 'SSL',
        content: {
            verifySslCertificatesOfServices: {
                value: '',
                question: 'Set this parameter to true in production environments:',
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
            },
            keyStore: {
                value: '',
                question: 'The keystore file used to store the private key (keyring: set to SAF keyring location):',
            },
            keyStorePassword: {
                value: '',
                question: 'The password used to unlock the keystore:',
            },
            keyStoreType: {
                value: '',
                question: 'Type of the keystore:',
            },
            trustStore: {
                value: '',
                question: 'The truststore file used to keep other parties public keys and certificates:',
            },
            trustStorePassword: {
                value: '',
                question: 'The password used to unlock the truststore:',
            },
            trustStoreType: {
                value: 'PKCS12',
                question: 'Truststore type:',
            },
        },
    },
    {
        text: 'SSL for Node',
        content: {
            certificate: {
                value: '',
                question: 'Certificate:',
            },
            keyStore: {
                value: '',
                question: 'The keystore file used to store the private key (keyring: set to SAF keyring location):',
            },
            caFile: {
                value: '',
                question: 'Certificate Authority file:',
            },
            keyPassword: {
                value: '',
                question: 'The password associated with the private key:',
            },
        },
    },
    {
        text: 'Enable',
        content: {
            enabled: {
                value: false,
                question: 'Service should automatically register with API ML discovery service',
            },
            enableUrlEncodedCharacters: {
                value: false,
                question: 'Service requests the API ML GW to receive encoded characters in the URL',
            },
        },
    },
    {
        text: 'Spring',
        content: {
            name: {
                value: '',
                question: 'This parameter has to be the same as the service ID you are going to provide',
            },
        },
    },
    {
        text: 'Catalog info',
        content: {
            catalogUiTileId: {
                value: '',
                question: 'The id of the catalog tile:',
            },
        },
    },
    {
        text: 'Catalog UI Tiles',
        content: {
            title: {
                value: '',
                question: 'The title of the API services product family:',
            },
            description: {
                value: '',
                question: 'The detailed description of the API Catalog UI dashboard tile:',
            },
        },
        indentationDependency: 'catalogUiTileId',
    },
    {
        text: 'Eureka',
        content: {
            ssl: {
                value: false,
                question: 'Turn SSL on for Eureka',
            },
            host: {
                value: '',
                question: 'The host to be used:',
            },
            ipAddress: {
                value: '',
                question: 'The IP address to be used:',
            },
            port: {
                value: '',
                question: 'The port to be used:',
            },
            servicePath: {
                value: '',
                question: 'The service path:',
            },
            maxRetries: {
                value: '',
                question: 'The maximum number of retries:',
            },
            requestRetryDelay: {
                value: '',
                question: 'The request retry delay:',
            },
            registryFetchInterval: {
                value: '',
                question: 'The interval for registry interval:',
            },
        },
    },
    {
        text: 'Instance',
        content: {
            app: {
                value: '',
                question: 'App ID:',
            },
            vipAddress: {
                value: '',
                question: 'Virtual IP address:',
            },
            instanceId: {
                value: '',
                question: 'Instance ID:',
            },
            homePageUrl: {
                value: '',
                question: 'The URL of the home page:',
            },
            hostname: {
                value: '',
                question: 'Host name:',
            },
            ipAddr: {
                value: '',
                question: 'IP address:',
            },
            secureVipAddress: {
                value: '',
                question: 'Secure virtual IP address:',
            },
        },
    },
    {
        text: 'Instance port',
        content: {
            $: {
                value: '',
                question: 'Port:',
            },
            '@enabled': {
                value: false,
                question: 'Enable?',
            },
        },
    },
    {
        text: 'Instance security port',
        content: {
            $: {
                value: '',
                question: 'Security port:',
            },
            '@enabled': {
                value: true,
                question: 'Enable?',
            },
        },
    },
    {
        text: 'Data center info',
        content: {
            '@class': {
                value: '',
                question: 'Class:',
            },
            name: {
                value: '',
                question: 'Name:',
            },
        },
    },
    {
        text: 'Metadata',
        content: {
            'apiml.catalog.tile.id': {
                value: '',
                question: 'Tile ID for the API ML catalog:',
            },
            'apiml.catalog.tile.title': {
                value: '',
                question: 'Tile title for the API ML catalog:',
            },
            'apiml.catalog.tile.description': {
                value: '',
                question: 'Tile description for the API ML catalog:',
            },
            'apiml.catalog.tile.version': {
                value: '',
                question: 'Tile version for the API ML catalog:',
            },
            'apiml.routes.api_v1.gatewayUrl': {
                value: '',
                question: 'API gateway URL:',
            },
            'apiml.routes.api_v1.serviceUrl': {
                value: '',
                question: 'API service URL:',
            },
            'apiml.apiInfo.0.apiId': {
                value: '',
                question: 'A unique identifier to the API in the API ML:',
            },
            'apiml.apiInfo.0.gatewayUrl': {
                value: '',
                question: 'The base path at the API Gateway where the API is available:',
            },
            'apiml.apiInfo.0.swaggerUrl': {
                value: '',
                question: 'The base path at the API Gateway where the API is available:',
            },
            'apiml.service.title': {
                value: '',
                question: 'Service title:',
            },
            'apiml.service.description': {
                value: '',
                question: 'Service description:',
            },
        },
    },
];
