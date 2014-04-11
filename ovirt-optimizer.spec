%global engine_etc /etc/ovirt-engine
%global engine_data %{_datadir}/ovirt-engine
%global jboss_deployments %{_datadir}/jboss-as/standalone/deployments
%global jetty_deployments %{_datadir}/jetty/webapps

Name:		ovirt-optimizer
Version:	0.1
Release:	1%{?dist}
Summary:	Cluster balance optimization service for oVirt
Group:		%{ovirt_product_group}
License:	ASL 2.0
URL:		http://www.ovirt.org
Source0:	http://ovirt.org/releases/stable/src/%{name}-%{version}.tar.gz

BuildArch:	noarch

BuildRequires:	java-devel
BuildRequires:	jpackage-utils
BuildRequires:	maven-local
BuildRequires:  maven-war-plugin
BuildRequires:  maven-jar-plugin
BuildRequires:  maven-ejb-plugin
BuildRequires:	unzip

Requires:	java >= 1:1.7.0
Requires:	jpackage-utils
Requires:       resteasy
Requires:       jackson
Requires:       slf4j
Requires:       quartz

%description
%{name} service collects data from the oVirt engine and proposes
Vm to host assignments to utilize cluster resources better.

%package ui
Summary:        UI for displaying results in oVirt webadmin
Requires:	%{name}-webadmin-portal >= 3.4

%description ui
This subpackage adds an UI plugin to the oVirt webadmin portal.
This plugin then adds a Tab to the cluster display and presents
the assingment recommendations to the sysadmin there.

%package jboss7
Summary:       Integration of the optimization service to Jboss 7.
Requires:      %{name} = %{version}
Requires:	jboss-as >= 7.1.1-9.3

%description jboss7
This subpackage deploys the optimizer service to the standard Jboss 7
application server.

%package jetty
Summary:       Integration of the optimization service to Jetty 9.
Requires:      %{name} = %{version}
Requires:      jetty >= 9
Requires:      weld-api >= 2.1.2
Requires:      cdi-api

%description jetty
This subpackage deploys the optimizer service to Jetty application
server.

%prep
%setup -c -q

%build
mvn --offline clean install

%install
##
## Core
##

# Copy core jars to the proper place
install -dm 755 %{buildroot}%{_javadir}/%{name}
mv ovirt-optimizer-core/target/*.jar %{buildroot}%{_javadir}/%{name}

##
## Jboss7
##

pushd ovirt-optimizer-jboss7

# Remove the bundled jars by moving them elsewhere and then
# copying only the needed files back. The rest is provided
# by this package's dependencies.
mkdir target/lib
mv target/%{name}-jboss7/WEB-INF/lib/* target/lib

# Install the exploded Jboss war to javadir
cp -ar target/%{name}-jboss7 %{buildroot}%{_javadir}/%{name}/jboss7.war

# Symlink the core lib to %{buildroot}%{_javadir}/%{name}/jboss7/WEB-INF/lib
ln -sf %{_javadir}/%{name}/%{name}-core.jar %{buildroot}%{_javadir}/%{name}/jboss7.war/WEB-INF/lib

# Copy bundled libs to %{buildroot}%{_javadir}/%{name}/jboss7.war/WEB-INF/lib
JBOSS_BUNDLE="target/lib/drools-*
target/lib/kie-*
target/lib/optaplanner-*"
echo "$JBOSS_BUNDLE" | xargs -d \\n -I@ sh -c "mv -t %{buildroot}%{_javadir}/%{name}/jboss7.war/WEB-INF/lib @"

# Symlink it to Jboss war directory and touch the deploy marker
install -dm 755 %{buildroot}%{jboss_deployments}
ln -sf %{_javadir}/%{name}/jboss7.war %{buildroot}%{jboss_deployments}/%{name}.war
touch %{buildroot}%{jboss_deployments}/%{name}.war.dodeploy

popd

##
## Jetty9
##

pushd ovirt-optimizer-jetty

# Remove the bundled jars by moving them elsewhere and then
# copying only the needed files back. The rest is provided
# by this package's dependencies.
mkdir target/lib
mv target/%{name}-jetty/WEB-INF/lib/* target/lib

# Install the exploded Jetty war to javadir
install -dm 755 %{buildroot}%{_javadir}/%{name}
cp -ar target/%{name}-jetty %{buildroot}%{_javadir}/%{name}/jetty.war

# Symlink the core lib to %{buildroot}%{_javadir}/%{name}/jetty.war/WEB-INF/lib
ln -sf %{_javadir}/%{name}/%{name}-core.jar %{buildroot}%{_javadir}/%{name}/jetty.war/WEB-INF/lib

# Copy bundled libs to %{buildroot}%{_javadir}/%{name}/jetty.war/WEB-INF/lib
JETTY_BUNDLE=""
echo "$JBOSS_BUNDLE" | xargs -d \\n -I@ sh -c "mv -t %{buildroot}%{_javadir}/%{name}/jetty.war/WEB-INF/lib @"
#echo "$JETTY_BUNDLE" | xargs -d \\n -I@ sh -c "mv -t %{buildroot}%{_javadir}/%{name}/jetty.war/WEB-INF/lib @"

# Symlink it to Jetty war directory and touch the deploy marker
install -dm 755 %{buildroot}%{jetty_deployments}
ln -sf %{_javadir}/%{name}/jetty.war %{buildroot}%{jetty_deployments}/%{name}.war

popd

##
## Docs
##

# Copy the setup script to documentation
install -dm 755 %{buildroot}%{_docdir}/%{name}-%{version}

##
## UI plugin
##

# Install the UI plugin to the oVirt webadmin
install -dm 755 %{buildroot}%{engine_data}/ui-plugins
install -dm 755 %{buildroot}%{engine_etc}/ui-plugins
install -dm 755 %{buildroot}%{engine_data}/ui-plugins/ovirt-optimizer-resources
install dist/ovirt-optimizer-uiplugin/*.json %{buildroot}%{engine_data}/ui-plugins/
install dist/ovirt-optimizer-uiplugin/ovirt-optimizer-resources/* %{buildroot}%{engine_data}/ui-plugins/ovirt-optimizer-resources
install dist/etc/*.json %{buildroot}%{engine_etc}/ui-plugins/

# Install config file
install -dm 755 %{buildroot}/etc/%{name}
install dist/etc/*.properties %{buildroot}/etc/%{name}

%files
%doc README COPYING
%dir %{_javadir}/%{name}
%{_javadir}/%{name}/*.jar
%config /etc/%{name}/*

%files jetty
%dir %{_javadir}/%{name}/jetty.war
%{_javadir}/%{name}/jetty.war/*
%{jetty_deployments}/*

%files jboss7
%dir %{_javadir}/%{name}/jboss7.war
%{_javadir}/%{name}/jboss7.war/*
%{jboss_deployments}/*

%files ui
%dir %{engine_data}/ui-plugins/ovirt-optimizer-resources
%config %{engine_etc}/ui-plugins/ovirt-optimizer-config.json
%{engine_data}/ui-plugins/ovirt-optimizer.json
%{engine_data}/ui-plugins/ovirt-optimizer-resources/*
