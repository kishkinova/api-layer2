#!/usr/bin/env sh

set -ex

./gradle/bootstrap/bootstrap_gradlew.sh
AUTH="-Pzowe.deploy.username=$USERNAME -Pzowe.deploy.password=$PASSWORD -Partifactory_user=$USERNAME -Partifactory_password=$PASSWORD"
DIST_REGISTRY="https://registry.npmjs.org/"

case $RELEASE_TYPE in
   "SNAPSHOT_RELEASE")
   echo "Make SNAPSHOT release"
   ./gradlew publishAllVersions $AUTH
   git archive --format tar.gz -9 --output api-layer.tar.gz HEAD~1
   ;;
   "PATCH_RELEASE")
   echo "Make PATCH release"
   ./gradlew release -Prelease.useAutomaticVersion=true -Prelease.scope=patch $AUTH
   git archive --format tar.gz -9 --output api-layer.tar.gz HEAD~1
   ;;
   "MINOR_RELEASE")
   echo "Make MINOR release"
   ./gradlew release -Prelease.useAutomaticVersion=true -Prelease.scope=minor $AUTH
   git archive --format tar.gz -9 --output api-layer.tar.gz HEAD~1
   ;;
   "MAJOR_RELEASE")
   echo "Make MAJOR release"
   ./gradlew release -Prelease.useAutomaticVersion=true -Prelease.scope=major $AUTH
   git archive --format tar.gz -9 --output api-layer.tar.gz HEAD~1
   ;;
   "SPECIFIC_RELEASE")
   echo "Make specific release"
   ./gradlew release -Prelease.useAutomaticVersion=true -Prelease.releaseVersion=$RELEASE_VERSION -Prelease.newVersion=$NEW_VERSION $AUTH
   git archive --format tar.gz -9 --output api-layer.tar.gz "v$RELEASE_VERSION"
   ;;
   "NODEJS_ENABLER_RELEASE")
   cd onboarding-enabler-nodejs
   echo \"//registry.npmjs.org/:_authToken=$TOKEN\" > ~/.npmrc
   echo \"registry=$DIST_REGISTRY\" >> ~/.npmrc
   npm version $RELEASE_VERSION
   npm publish @zowe/apiml-onboarding-enabler-nodejs --access public

esac

echo "End of publish and release"
