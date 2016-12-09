#!/bin/bash
export PGP_SECRET_KEY_RING_FILE=/Users/user/.gnupg/secring.gpg
export PGP_KEY_ID=01BCE012
export NEXUS_USERNAME=ph4r05
export NEXUS_PASSWORD=password

export EBUILDER_VERSION=1.0.0

PGP_PASSWORD='password' ./gradlew :signArchives --info --stacktrace
PGP_PASSWORD='password' ./gradlew :uploadArchives --info --stacktrace
./gradlew :closeAndPromoteRepository


