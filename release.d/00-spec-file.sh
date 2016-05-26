#!/bin/sh
sed -i -e "s/%define project_version .*/%define project_version $1/g" ovirt-optimizer.spec

# Do not create changelog entry for the development version
if [ "x$2" == "xlatest" ]; then
  exit 0
fi

DATE=$(date "+%a %b %d %Y")
NAME=$(git config user.name)
EMAIL=$(git config user.email)

ENTRY="* $DATE $NAME <$EMAIL> $1-1"

sed -i "/%changelog/a $ENTRY" ovirt-optimizer.spec
vim ovirt-optimizer.spec

