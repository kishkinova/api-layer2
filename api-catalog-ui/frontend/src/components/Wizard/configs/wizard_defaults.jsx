/**
 * Define default values for different enablers
 */
// eslint-disable-next-line import/prefer-default-export
export const defaultSpring = {
    Spring: { name: '${apiml.service.serviceId}' },
    'Scheme info': { scheme: 'https', contextPath: '/${apiml.service.serviceId}' },
    'IP address info': { baseUrl: '${apiml.service.scheme}://${apiml.service.hostname}:${apiml.service.port}' },
    URL: {
        homePageRelativeUrl: '${apiml.service.contextPath}',
        statusPageRelativeUrl: '${apiml.service.contextPath}',
        healthCheckRelativeUrl: '${apiml.service.contextPath}',
    },
    Routes: {
        serviceUrl: '${apiml.service.contextPath}',
    },
    'API Info': {
        swaggerUrl:
            '${apiml.service.scheme}://${apiml.service.hostname}:${apiml.service.port}${apiml.service.contextPath}',
    },
};
