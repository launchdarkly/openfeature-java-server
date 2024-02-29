#!/bin/bash

set -ue

echo "Publishing to Sonatype"
if [ "${LD_RELEASE_IS_PRERELEASE}" == "true" ]; then
  echo "PRERELEASE"
  ./gradlew publishToSonatype -Psigning.keyId="${SIGNING_KEY_ID}" -Psigning.secretKeyRingFile="${SIGNING_SECRET_KEY_RING_FILE}" -PossrhUsername="${SONATYPE_USER_NAME}" -PossrhPassword="${SONATYPE_PASSWORD}" || {
    echo "Gradle publish/release faile" >&2
    exit 1
  }
else
  echo "RELEASE"
  ./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository || {
    echo "Gradle publish/release failed" >&2
    exit 1
  }
fi
