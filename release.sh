#!/bin/bash

if [ "x$1" == "x--help" ]; then
  echo "Release and version bump script for Maven based projects"
  echo " with X.Y[-SNAPSHOT] versioning scheme."
  echo "Usage: $0 [--major]"
  echo
  echo "  --major  This is a major release. Bump the X version"
  echo "           and reset Y to 0 prior to release."
  echo "           example: 1.5-SNAPSHOT -> 2.0 -> 2.1-SNAPSHOT"
  exit 0
fi

function relhook_failed() {
  echo "Release hook $1 failed."
  exit 2
}

BRANCH=$(git rev-parse --abbrev-ref HEAD)
echo "Current branch is $BRANCH"

echo "Verifying git status"
GIT_STATUS=$(git status --porcelain | grep -v '^?? ')

if [[ $GIT_STATUS ]]; then
  git status
  echo
  echo "Your git tree is not clean, aborting."
  exit 1
fi

CURRENT=$(mvn help:evaluate -Dexpression=project.version | grep -v '\[')

if [ "x$1" == "x--major" ]; then
  echo "Performing MAJOR release version bump!"
  BUMP_MAJOR="+1"
  BUMP_MINOR="-\$2"
elif [[ "$CURRENT" != *SNAPSHOT ]]; then
  echo "Performing full minor release version bump"
  BUMP_MAJOR="+0"
  BUMP_MINOR="+1"
else
  echo "Performing minor release version bump"
  BUMP_MAJOR="+0"
  BUMP_MINOR="+0"
fi

CURRENT_RELEASE=$(echo $CURRENT | awk -F'[.-]' "{print (\$1$BUMP_MAJOR)\".\"(\$2$BUMP_MINOR)}")
NEXT_VERSION=$(echo $CURRENT | awk -F'[.-]' "{print (\$1$BUMP_MAJOR)\".\"(\$2$BUMP_MINOR+1)\"-SNAPSHOT\"}")
TAG="v$CURRENT_RELEASE"

echo
echo "Verify the following:"
echo "Current version: $CURRENT"
echo
echo "Release: $CURRENT_RELEASE"
echo "Tag: $TAG"
echo
echo "Next development version: $NEXT_VERSION"
echo
echo -n "Is everything correct? [yes/no]: "
read ok

if [ "x$ok" != "xyes" ]; then
  echo "Negative answer. Aborting."
  exit 1
fi

echo "Preparing release"
mvn versions:set -DnewVersion=$CURRENT_RELEASE | grep -v '\['

echo "Executing custom release scripts for $TAG"
if [ -d release.d ]; then
  for SCRIPT in release.d/*
  do
    if [ -f $SCRIPT -a -x $SCRIPT ]; then
      $SCRIPT $CURRENT_RELEASE $TAG || relhook_failed $SCRIPT
    fi
  done
fi

NAME=$(git config user.name)
EMAIL=$(git config user.email)

git commit -a -m "Releasing new version $CURRENT_RELEASE

Signed-off-by: $NAME <$EMAIL>"
git tag "$TAG"

echo "Preparing development version"
mvn versions:set -DnewVersion=$NEXT_VERSION | grep -v '\['

echo "Executing custom release scripts for $NEXT_VERSION"
if [ -d release.d ]; then
  for SCRIPT in release.d/*
  do
    if [ -f $SCRIPT -a -x $SCRIPT ]; then
      $SCRIPT $NEXT_VERSION latest || relhook_failed $SCRIPT
    fi
  done
fi
git commit -a -m "Preparing for next development iteration

Signed-off-by: $NAME <$EMAIL>"

echo "Perform the following command to push everything to the server:"
echo "git push origin $BRANCH $TAG"

