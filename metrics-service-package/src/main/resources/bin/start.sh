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

# Optional variables:
# - CMMN_LB
# - LIBPATH
# - LIBRARY_PATH
# - ZWE_components_discovery_port - the port the discovery service will use
# - ZWE_components_gateway_port - the port the api gateway service will use
# - ZWE_components_gateway_server_ssl_enabled
# - ZWE_configs_certificate_keystore_alias - The alias of the key within the keystore
# - ZWE_configs_certificate_keystore_file - The keystore to use for SSL certificates
# - ZWE_configs_certificate_keystore_password - The password to access the keystore supplied by KEYSTORE
# - ZWE_configs_certificate_keystore_type - The keystore type to use for SSL certificates
# - ZWE_configs_certificate_truststore_file
# - ZWE_configs_certificate_truststore_type
# - ZWE_configs_debug
# - ZWE_configs_port - the port the metics service will use
# - ZWE_DISCOVERY_SERVICES_LIST
# - ZWE_haInstance_hostname
# - ZWE_zowe_certificate_keystore_type - The default keystore type to use for SSL certificates
# - ZWE_zowe_verifyCertificates - if we accept only verified certificates

JAR_FILE="$(pwd)/bin/metrics-service-lite.jar"
# script assumes it's in the metrics component directory and common_lib needs to be relative path
if [ -z "${CMMN_LB}" ]
then
    COMMON_LIB="../apiml-common-lib/bin/api-layer-lite-lib-all.jar"
else
    COMMON_LIB=${CMMN_LB}
fi

# API Mediation Layer Debug Mode
export LOG_LEVEL=

if [ "${ZWE_configs_debug}" = "true" ]
then
  export LOG_LEVEL="debug"
fi

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

if [ "$(uname)" = "OS/390" ]
then
    QUICK_START=-Xquickstart
fi

LIBPATH="$LIBPATH":"/lib"
LIBPATH="$LIBPATH":"/usr/lib"
LIBPATH="$LIBPATH":"${JAVA_HOME}"/bin
LIBPATH="$LIBPATH":"${JAVA_HOME}"/bin/classic
LIBPATH="$LIBPATH":"${JAVA_HOME}"/bin/j9vm
LIBPATH="$LIBPATH":"${JAVA_HOME}"/lib/s390/classic
LIBPATH="$LIBPATH":"${JAVA_HOME}"/lib/s390/default
LIBPATH="$LIBPATH":"${JAVA_HOME}"/lib/s390/j9vm
LIBPATH="$LIBPATH":"${LIBRARY_PATH}"

# NOTE: these are moved from below
# -Dapiml.service.ipAddress=${ZOWE_IP_ADDRESS:-127.0.0.1} \
# -Dapiml.service.preferIpAddress=${APIML_PREFER_IP_ADDRESS:-false} \

METRICS_CODE=MS
_BPX_JOBNAME=${ZWE_zowe_job_prefix}${METRICS_CODE} java -Xms16m -Xmx512m \
   ${QUICK_START} \
  -Dibm.serversocket.recover=true \
  -Dfile.encoding=UTF-8 \
  -Djava.io.tmpdir=/tmp \
  -Dspring.profiles.include=$LOG_LEVEL \
  -Dapiml.service.port=${ZWE_configs_port:-7551} \
  -Dapiml.service.hostname=${ZWE_haInstance_hostname:-localhost} \
  -Dapiml.service.discoveryServiceUrls=${ZWE_DISCOVERY_SERVICES_LIST:-"https://${ZWE_haInstance_hostname:-localhost}:${ZWE_components_discovery_port:-7553}/eureka/"} \
  -Dapiml.service.customMetadata.apiml.gatewayPort=${ZWE_components_gateway_port:-7554} \
  -Dapiml.service.ssl.verifySslCertificatesOfServices=${verifySslCertificatesOfServices:-false} \
  -Dapiml.service.ssl.nonStrictVerifySslCertificatesOfServices=${nonStrictVerifySslCertificatesOfServices:-false} \
  -Dserver.address=0.0.0.0 \
  -Dserver.ssl.enabled=${ZWE_components_gateway_server_ssl_enabled:-true} \
  -Dserver.ssl.keyStore="${ZWE_configs_certificate_keystore_file:-${ZWE_zowe_certificate_keystore_file}}" \
  -Dserver.ssl.keyStoreType="${ZWE_configs_certificate_keystore_type:-${ZWE_zowe_certificate_keystore_type:-PKCS12}}" \
  -Dserver.ssl.keyStorePassword="${ZWE_configs_certificate_keystore_password:-${ZWE_zowe_certificate_keystore_password}}" \
  -Dserver.ssl.keyAlias="${ZWE_configs_certificate_keystore_alias:-${ZWE_zowe_certificate_keystore_alias}}" \
  -Dserver.ssl.keyPassword="${ZWE_configs_certificate_keystore_password:-${ZWE_zowe_certificate_keystore_password}}" \
  -Dserver.ssl.trustStore="${ZWE_configs_certificate_truststore_file:-${ZWE_zowe_certificate_truststore_file}}" \
  -Dserver.ssl.trustStoreType="${ZWE_configs_certificate_truststore_type:-${ZWE_zowe_certificate_truststore_type:-PKCS12}}" \
  -Dserver.ssl.trustStorePassword="${ZWE_configs_certificate_keystore_password:-${ZWE_zowe_certificate_keystore_password}}" \
  -Djava.protocol.handler.pkgs=com.ibm.crypto.provider \
  -Dloader.path=${COMMON_LIB} \
  -Djava.library.path=${LIBPATH} \
  -jar ${JAR_FILE} &
pid=$!
echo "pid=${pid}"

wait %1
