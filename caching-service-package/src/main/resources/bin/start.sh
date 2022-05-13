#!/bin/sh

################################################################################
# This program and the accompanying materials are made available under the terms of the
# Eclipse Public License v2.0 which accompanies this distribution, and is available at
# https://www.eclipse.org/legal/epl-v20.html
#
# SPDX-License-Identifier: EPL-2.0
#
# Copyright IBM Corporation 2019, 2020
################################################################################

# Variables required on shell:
# - ZOWE_PREFIX
# - DISCOVERY_PORT - the port the discovery service will use
# - CATALOG_PORT - the port the api catalog service will use
# - GATEWAY_PORT - the port the api gateway service will use
# - VERIFY_CERTIFICATES - boolean saying if we accept only verified certificates
# - DISCOVERY_PORT - The port the data sets server will use
# - KEY_ALIAS
# - KEYSTORE - The keystore to use for SSL certificates
# - KEYSTORE_TYPE - The keystore type to use for SSL certificates
# - KEYSTORE_PASSWORD - The password to access the keystore supplied by KEYSTORE
# - KEY_ALIAS - The alias of the key within the keystore
# - ALLOW_SLASHES - Allows encoded slashes on on URLs through gateway
# - ZOWE_MANIFEST - The full path to Zowe's manifest.json file

if [[ -z "${LAUNCH_COMPONENT}" ]]
then
  # component should be started from component home directory
  LAUNCH_COMPONENT=$(pwd)/bin
fi

JAR_FILE="${LAUNCH_COMPONENT}/caching-service.jar"

# API Mediation Layer Debug Mode
export LOG_LEVEL=

if [[ ! -z ${APIML_DEBUG_MODE_ENABLED} && ${APIML_DEBUG_MODE_ENABLED} == true ]]
then
  export LOG_LEVEL="debug"
fi

if [[ -z ${LIBRARY_PATH} ]]
then
    LIBRARY_PATH="../common-java-lib/bin/"
fi

if [ ! -z ${ZWE_CACHING_SERVICE_VSAM_DATASET} ]
then
    VSAM_FILE_NAME=//\'${ZWE_CACHING_SERVICE_VSAM_DATASET}\'
fi

EXPLORER_HOST=${ZOWE_EXPLORER_HOST:-localhost}

if [ `uname` = "OS/390" ]
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

CACHING_CODE=CS
_BPX_JOBNAME=${ZOWE_PREFIX}${CACHING_CODE} java -Xms16m -Xmx512m \
   ${QUICK_START} \
  -Dibm.serversocket.recover=true \
  -Dfile.encoding=UTF-8 \
  -Djava.io.tmpdir=/tmp \
  -Dapiml.logs.location=${WORKSPACE_DIR}/api-mediation/logs \
  -Dspring.profiles.active=${APIML_SPRING_PROFILES:-} \
  -Dspring.profiles.include=$LOG_LEVEL \
  -Dapiml.service.port=${ZWE_CACHING_SERVICE_PORT:-7555} \
  -Dapiml.service.hostname=${EXPLORER_HOST} \
  -Dapiml.service.discoveryServiceUrls=${ZWE_DISCOVERY_SERVICES_LIST:-"https://${EXPLORER_HOST}:${DISCOVERY_PORT:-7553}/eureka/"} \
  -Dapiml.service.ipAddress=${ZOWE_IP_ADDRESS:-127.0.0.1} \
  -Dapiml.service.customMetadata.apiml.gatewayPort=${GATEWAY_PORT:-7554} \
  -Dapiml.service.ssl.verifySslCertificatesOfServices=${VERIFY_CERTIFICATES:-false} \
  -Dapiml.service.ssl.nonStrictVerifySslCertificatesOfServices=${NONSTRICT_VERIFY_CERTIFICATES:-false} \
  -Dcaching.storage.evictionStrategy=${ZWE_CACHING_EVICTION_STRATEGY:-reject} \
  -Dcaching.storage.size=${ZWE_CACHING_STORAGE_SIZE:-10000} \
  -Dcaching.storage.mode=${ZWE_CACHING_SERVICE_PERSISTENT:-inMemory} \
  -Dcaching.storage.vsam.name=${VSAM_FILE_NAME} \
  -Djgroups.bind.address=${ZOWE_EXPLORER_HOST} \
  -Dapiml.service.preferIpAddress=${APIML_PREFER_IP_ADDRESS:-false} \
  -Dserver.address=0.0.0.0 \
  -Dserver.ssl.enabled=${APIML_SSL_ENABLED:-true}  \
  -Dserver.ssl.keyStore="${KEYSTORE}" \
  -Dserver.ssl.keyStoreType="${KEYSTORE_TYPE:-PKCS12}" \
  -Dserver.ssl.keyStorePassword="${KEYSTORE_PASSWORD}" \
  -Dserver.ssl.keyAlias="${KEY_ALIAS}" \
  -Dserver.ssl.keyPassword="${KEYSTORE_PASSWORD}" \
  -Dserver.ssl.trustStore="${TRUSTSTORE}" \
  -Dserver.ssl.trustStoreType="${KEYSTORE_TYPE:-PKCS12}" \
  -Dserver.ssl.trustStorePassword="${KEYSTORE_PASSWORD}" \
  -Djava.protocol.handler.pkgs=com.ibm.crypto.provider \
  -Djava.library.path=${LIBPATH} \
  -jar "${JAR_FILE}" &
pid=$!
echo "pid=${pid}"

wait %1
