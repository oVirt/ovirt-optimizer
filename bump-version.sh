#!/bin/sh

if [ $# -lt 1 ]; then
  echo "Usage: $0 <version>"
  exit 1
fi

mvn versions:set -DnewVersion=$1
sed -i -e "s/%define project_version .*/%define project_version $1/g" ovirt-optimizer.spec

DATE=$(date "+%a %b %d %Y")
NAME=$(git config user.name)
EMAIL=$(git config user.email)

ENTRY="* $DATE $NAME <$EMAIL> $1-1"

sed -i "/%changelog/a $ENTRY" ovirt-optimizer.spec

echo
echo "You should now open ovirt-optimizer.spec and add content to the added"
echo "changelog entry: $ENTRY"

