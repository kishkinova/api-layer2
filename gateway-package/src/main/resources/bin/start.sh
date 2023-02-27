#!/bin/sh

################################################################################
# This program and the accompanying materials are made available under the terms of the
# Eclipse Public License v2.0 which accompanies this distribution, and is available at
# https://www.eclipse.org/legal/epl-v20.html
#
# SPDX-License-Identifier: EPL-2.0
#
# Copyright IBM Corporation 2021
################################################################################

# Variables required on shell:
# - JAVA_HOME
# - ZWE_STATIC_DEFINITIONS_DIR
# - ZWE_zowe_certificate_keystore_alias - The default alias of the key within the keystore
# - ZWE_zowe_certificate_keystore_file - The default keystore to use for SSL certificates
# - ZWE_zowe_certificate_keystore_password - The default password to access the keystore supplied by KEYSTORE
# - ZWE_zowe_certificate_truststore_file
# - ZWE_zowe_job_prefix
# - ZWE_zowe_logDirectory
# - ZWE_zowe_runtimeDirectory
# - ZWE_zowe_workspaceDirectory

# Optional variables:
# - CMMN_LB
# - LIBPATH
# - LIBRARY_PATH
# - ZWE_components_discovery_port - the port the discovery service will use
# - ZWE_configs_apiml_catalog_serviceId
# - ZWE_configs_apiml_gateway_timeoutMillis
# - ZWE_configs_apiml_security_auth_provider
# - ZWE_configs_apiml_security_auth_zosmf_jwtAutoconfiguration
# - ZWE_configs_apiml_security_auth_zosmf_serviceId
# - ZWE_configs_apiml_security_authorization_endpoint_enabled
# - ZWE_configs_apiml_security_authorization_endpoint_url
# - ZWE_configs_apiml_security_authorization_provider
# - ZWE_configs_apiml_security_authorization_resourceClass
# - ZWE_configs_apiml_security_authorization_resourceNamePrefix
# - ZWE_configs_apiml_security_jwtInitializerTimeout
# - ZWE_configs_apiml_security_x509_enabled
# - ZWE_configs_apiml_security_x509_externalMapperUrl
# - ZWE_configs_apiml_security_x509_externalMapperUser
# - ZWE_configs_apiml_security_zosmf_applid
# - ZWE_configs_apiml_service_allowEncodedSlashes - Allows encoded slashes on on URLs through gateway
# - ZWE_configs_apiml_service_corsEnabled
# - ZWE_configs_certificate_keystore_alias - The alias of the key within the keystore
# - ZWE_configs_certificate_keystore_file - The keystore to use for SSL certificates
# - ZWE_configs_certificate_keystore_password - The password to access the keystore supplied by KEYSTORE
# - ZWE_configs_certificate_keystore_type - The keystore type to use for SSL certificates
# - ZWE_configs_certificate_truststore_file
# - ZWE_configs_certificate_truststore_type
# - ZWE_configs_debug
# - ZWE_configs_port - the port the api gateway service will use
# - ZWE_configs_server_internal_ssl_certificate_keystore_alias
# - ZWE_configs_server_internal_ssl_certificate_keystore_file
# - ZWE_configs_server_internal_enabled
# - ZWE_configs_server_internal_port
# - ZWE_configs_server_internal_ssl_enabled
# - ZWE_configs_server_maxConnectionsPerRoute
# - ZWE_configs_server_maxTotalConnections
# - ZWE_configs_server_ssl_enabled
# - ZWE_configs_spring_profiles_active
# - ZWE_DISCOVERY_SERVICES_LIST
# - ZWE_GATEWAY_SHARED_LIBS
# - ZWE_haInstance_hostname
# - ZWE_zowe_certificate_keystore_type - The default keystore type to use for SSL certificates
# - ZWE_zowe_verifyCertificates - if we accept only verified certificates

if [ -n "${LAUNCH_COMPONENT}" ]
then
    JAR_FILE="${LAUNCH_COMPONENT}/gateway-service-lite.jar"
else
    JAR_FILE="$(pwd)/bin/gateway-service-lite.jar"
fi
echo "jar file: "${JAR_FILE}
# script assumes it's in the gateway component directory and common_lib needs to be relative path

if [ -z "${CMMN_LB}" ]
then
    COMMON_LIB="../apiml-common-lib/bin/api-layer-lite-lib-all.jar"
else
    COMMON_LIB=${CMMN_LB}
fi

if [ -z "${LIBRARY_PATH}" ]
then
    LIBRARY_PATH="../common-java-lib/bin/"
fi

# API Mediation Layer Debug Mode
export LOG_LEVEL=

if [ "${ZWE_configs_debug}" = "true" ]
then
  export LOG_LEVEL="debug"
fi

# FIXME: APIML_DIAG_MODE_ENABLED is not officially mentioned. We can still use it behind the scene,
# or we can define configs.diagMode in manifest, then use "$ZWE_configs_diagMode".
# DIAG_MODE=${APIML_DIAG_MODE_ENABLED}
# if [ ! -z "$DIAG_MODE" ]
# then
#     LOG_LEVEL=$DIAG_MODE
# fi

# how to verifyCertificates
verify_certificates_config=$(echo "${ZWE_zowe_verifyCertificates}" | tr '[:lower:]' '[:upper:]')
if [ "${verify_certificates_config}" = "DISABLED" ]; then
  verifySslCertificatesOfServices=false
  nonStrictVerifySslCertificatesOfServices=false
elif [ "${verify_certificates_config}" = "NONSTRICT" ]; then
  verifySslCertificatesOfServices=false
  nonStrictVerifySslCertificatesOfServices=true
else
  # default value is STRICT
  verifySslCertificatesOfServices=true
  nonStrictVerifySslCertificatesOfServices=true
fi

if [ -z "${ZWE_configs_apiml_catalog_serviceId}" ]
then
    APIML_GATEWAY_CATALOG_ID="apicatalog"
fi

if [ "${ZWE_configs_apiml_catalog_serviceId}" = "none" ]
then
    APIML_GATEWAY_CATALOG_ID=""
fi

if [ "$(uname)" = "OS/390" ]
then
    QUICK_START=-Xquickstart
    GATEWAY_LOADER_PATH=${COMMON_LIB},/usr/include/java_classes/IRRRacf.jar
else
    GATEWAY_LOADER_PATH=${COMMON_LIB}
fi

# Check if the directory containing the Gateway shared JARs was set and append it to the GW loader path
if [ -n "${ZWE_GATEWAY_SHARED_LIBS}" ]
then
    GATEWAY_LOADER_PATH=${ZWE_GATEWAY_SHARED_LIBS},${GATEWAY_LOADER_PATH}
fi

echo "Setting loader path: "${GATEWAY_LOADER_PATH}

LIBPATH="$LIBPATH":"/lib"
LIBPATH="$LIBPATH":"/usr/lib"
LIBPATH="$LIBPATH":"${JAVA_HOME}"/bin
LIBPATH="$LIBPATH":"${JAVA_HOME}"/bin/classic
LIBPATH="$LIBPATH":"${JAVA_HOME}"/bin/j9vm
LIBPATH="$LIBPATH":"${JAVA_HOME}"/lib/s390/classic
LIBPATH="$LIBPATH":"${JAVA_HOME}"/lib/s390/default
LIBPATH="$LIBPATH":"${JAVA_HOME}"/lib/s390/j9vm
LIBPATH="$LIBPATH":"${LIBRARY_PATH}"

if [ -n "${ZWE_GATEWAY_LIBRARY_PATH}" ]
then
    LIBPATH="$LIBPATH":"${ZWE_GATEWAY_LIBRARY_PATH}"
fi

keystore_type="${ZWE_configs_certificate_keystore_type:-${ZWE_zowe_certificate_keystore_type:-PKCS12}}"
keystore_pass="${ZWE_configs_certificate_keystore_password:-${ZWE_zowe_certificate_keystore_password}}"
key_pass="${ZWE_configs_certificate_key_password:-${ZWE_zowe_certificate_key_password:-${keystore_pass}}}"
truststore_type="${ZWE_configs_certificate_truststore_type:-${ZWE_zowe_certificate_truststore_type:-PKCS12}}"
truststore_pass="${ZWE_configs_certificate_truststore_password:-${ZWE_zowe_certificate_truststore_password}}"

# Workaround for Java desiring safkeyring://// instead of just ://
# We can handle both cases of user input by just adding extra "//" if we detect its missing.
ensure_keyring_slashes() {
  keyring_string="${1}"
  only_two_slashes=$(echo "${keyring_string}" | grep "^safkeyring://[^//]")
  if [ -n "${only_two_slashes}" ]; then
    keyring_string=$(echo "${keyring_string}" | sed "s#safkeyring://#safkeyring:////#")
  fi
  # else, unmodified, perhaps its even p12
  echo $keyring_string
}

keystore_location=$(ensure_keyring_slashes "${ZWE_configs_certificate_keystore_file:-${ZWE_zowe_certificate_keystore_file}}")
truststore_location=$(ensure_keyring_slashes "${ZWE_configs_certificate_truststore_file:-${ZWE_zowe_certificate_truststore_file}}")

# NOTE: these are moved from below
#    -Dapiml.service.preferIpAddress=${APIML_PREFER_IP_ADDRESS:-false} \
#    -Dapiml.service.ipAddress=${ZOWE_IP_ADDRESS:-127.0.0.1} \
#    -Dapiml.security.auth.jwtKeyAlias=${PKCS11_TOKEN_LABEL:-jwtsecret} \

GATEWAY_CODE=AG
_BPX_JOBNAME=${ZWE_zowe_job_prefix}${GATEWAY_CODE} java \
    -Xms32m -Xmx256m \
    ${QUICK_START} \
    -Dibm.serversocket.recover=true \
    -Dfile.encoding=UTF-8 \
    -Djava.io.tmpdir=${TMPDIR:-/tmp} \
    -Dspring.profiles.active=${ZWE_configs_spring_profiles_active:-} \
    -Dspring.profiles.include=$LOG_LEVEL \
    -Dapiml.service.hostname=${ZWE_haInstance_hostname:-localhost} \
    -Dapiml.service.port=${ZWE_configs_port:-7554} \
    -Dapiml.service.discoveryServiceUrls=${ZWE_DISCOVERY_SERVICES_LIST:-"https://${ZWE_haInstance_hostname:-localhost}:${ZWE_components_discovery_port:-7553}/eureka/"} \
    -Dapiml.service.allowEncodedSlashes=${ZWE_configs_apiml_service_allowEncodedSlashes:-true} \
    -Dapiml.service.corsEnabled=${ZWE_configs_apiml_service_corsEnabled:-false} \
    -Dapiml.catalog.serviceId=${APIML_GATEWAY_CATALOG_ID:-apicatalog} \
    -Dapiml.cache.storage.location=${ZWE_zowe_workspaceDirectory}/api-mediation/${ZWE_PARAMETER_HA_INSTANCE:-localhost} \
    -Dapiml.logs.location=${ZWE_zowe_logDirectory} \
    -Dapiml.gateway.timeoutMillis=${ZWE_configs_apiml_gateway_timeoutMillis:-600000} \
    -Dapiml.security.ssl.verifySslCertificatesOfServices=${verifySslCertificatesOfServices:-false} \
    -Dapiml.security.ssl.nonStrictVerifySslCertificatesOfServices=${nonStrictVerifySslCertificatesOfServices:-false} \
    -Dapiml.security.auth.zosmf.serviceId=${ZWE_configs_apiml_security_auth_zosmf_serviceId:-zosmf} \
    -Dapiml.security.auth.provider=${ZWE_configs_apiml_security_auth_provider:-zosmf} \
    -Dapiml.security.auth.jwt.customAuthHeader=${ZWE_configs_apiml_security_auth_jwt_customAuthHeader:-} \
    -Dapiml.security.auth.passticket.customUserHeader=${ZWE_configs_apiml_security_auth_passticket_customUserHeader:-} \
    -Dapiml.security.auth.passticket.customAuthHeader=${ZWE_configs_apiml_security_auth_passticket_customAuthHeader:-} \
    -Dapiml.security.personalAccessToken.enabled=${ZWE_configs_apiml_security_personalAccessToken_enabled:-false} \
    -Dapiml.zoweManifest=${ZWE_zowe_runtimeDirectory}/manifest.json \
    -Dserver.address=0.0.0.0 \
    -Dserver.maxConnectionsPerRoute=${ZWE_configs_server_maxConnectionsPerRoute:-100} \
    -Dserver.maxTotalConnections=${ZWE_configs_server_maxTotalConnections:-1000} \
    -Dserver.ssl.enabled=${ZWE_configs_server_ssl_enabled:-true} \
    -Dserver.ssl.keyStore="${keystore_location}" \
    -Dserver.ssl.keyStoreType="${ZWE_configs_certificate_keystore_type:-${ZWE_zowe_certificate_keystore_type:-PKCS12}}" \
    -Dserver.ssl.keyStorePassword="${keystore_pass}" \
    -Dserver.ssl.keyAlias="${ZWE_configs_certificate_keystore_alias:-${ZWE_zowe_certificate_keystore_alias}}" \
    -Dserver.ssl.keyPassword="${key_pass}" \
    -Dserver.ssl.trustStore="${truststore_location}" \
    -Dserver.ssl.trustStoreType="${ZWE_configs_certificate_truststore_type:-${ZWE_zowe_certificate_truststore_type:-PKCS12}}" \
    -Dserver.ssl.trustStorePassword="${truststore_pass}" \
    -Dserver.internal.enabled=${ZWE_configs_server_internal_enabled:-false} \
    -Dserver.internal.ssl.enabled=${ZWE_configs_server_internal_ssl_enabled:-true} \
    -Dserver.internal.port=${ZWE_configs_server_internal_port:-10017} \
    -Dserver.internal.ssl.keyAlias=${ZWE_configs_server_internal_ssl_certificate_keystore_alias:-localhost-multi} \
    -Dserver.internal.ssl.keyStore=${ZWE_configs_server_internal_ssl_certificate_keystore_file:-keystore/localhost/localhost-multi.keystore.p12} \
    -Dapiml.security.auth.zosmf.jwtAutoconfiguration=${ZWE_configs_apiml_security_auth_zosmf_jwtAutoconfiguration:-auto} \
    -Dapiml.security.jwtInitializerTimeout=${ZWE_configs_apiml_security_jwtInitializerTimeout:-5} \
    -Dapiml.security.x509.enabled=${ZWE_configs_apiml_security_x509_enabled:-false} \
    -Dapiml.security.x509.externalMapperUrl=${ZWE_configs_apiml_security_x509_externalMapperUrl:-"https://${ZWE_haInstance_hostname:-localhost}:${ZWE_configs_port:-7554}/zss/api/v1/certificate"} \
    -Dapiml.security.x509.externalMapperUser=${ZWE_configs_apiml_security_x509_externalMapperUser:-${ZWE_zowe_setup_security_users_zowe:-ZWESVUSR}} \
    -Dapiml.security.authorization.provider=${ZWE_configs_apiml_security_authorization_provider:-} \
    -Dapiml.security.authorization.endpoint.enabled=${ZWE_configs_apiml_security_authorization_endpoint_enabled:-false} \
    -Dapiml.security.authorization.endpoint.url=${ZWE_configs_apiml_security_authorization_endpoint_url:-"https://${ZWE_haInstance_hostname:-localhost}:${ZWE_configs_port:-7554}/zss/api/v1/saf-auth"} \
    -Dapiml.security.saf.provider=${ZWE_configs_apiml_security_saf_provider:-"rest"} \
    -Dapiml.security.saf.urls.authenticate=${ZWE_configs_apiml_security_saf_urls_authenticate:-"https://${ZWE_haInstance_hostname:-localhost}:${ZWE_configs_port:-7554}/zss/api/v1/saf/authenticate"} \
    -Dapiml.security.saf.urls.verify=${ZWE_configs_apiml_security_saf_urls_verify:-"https://${ZWE_haInstance_hostname:-localhost}:${ZWE_configs_port:-7554}/zss/api/v1/saf/verify"} \
    -Dapiml.security.authorization.resourceClass=${ZWE_configs_apiml_security_authorization_resourceClass:-ZOWE} \
    -Dapiml.security.authorization.resourceNamePrefix=${ZWE_configs_apiml_security_authorization_resourceNamePrefix:-APIML.} \
    -Dapiml.security.zosmf.applid=${ZWE_configs_apiml_security_zosmf_applid:-IZUDFLT} \
    -Djava.protocol.handler.pkgs=com.ibm.crypto.provider \
    -Dloader.path=${GATEWAY_LOADER_PATH} \
    -Djava.library.path=${LIBPATH} \
    -jar ${JAR_FILE} &

pid=$!
echo "pid=${pid}"

wait %1
