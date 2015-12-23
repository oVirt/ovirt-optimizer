TMPDIR=.rpmbuild
mkdir -p .rpmbuild/SPECS .rpmbuild/SOURCES

cp *.spec $TMPDIR/SPECS
sed -i -e 's/mvn --offline /mvn /g' $TMPDIR/SPECS/ovirt-optimizer.spec

# downloads and installs the necessary jars
mvn help:evaluate -Dexpression=project.version

# Prepare the version string (with support for SNAPSHOT versioning)
# $VERSION contains the version and ${VERSION[1]} the release if it is needed
VERSION=$(mvn help:evaluate -Dexpression=project.version 2>/dev/null | grep -v "^\[")
VERSION=${VERSION/-SNAPSHOT/-0.$(git rev-list HEAD --count).$(date +%04Y%02m%02d%02H%02M)}
IFS='-' read -ra VERSION <<< "$VERSION"

git archive --format=tar HEAD | gzip -9 >$TMPDIR/SOURCES/ovirt-optimizer-$VERSION.tar.gz

if [ ${VERSION[1]-""} != "" ]; then
DEFRELEASE="--define \"_release ${VERSION[1]-1}\""
else
DEFRELEASE=""
fi

rpmbuild --define "_topdir $TMPDIR" --define "_version $VERSION" $DEFRELEASE -bs --nodeps $TMPDIR/SPECS/ovirt-optimizer.spec

