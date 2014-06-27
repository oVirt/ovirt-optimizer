%global engine_etc /etc/ovirt-engine
%global engine_data %{_datadir}/ovirt-engine
%global jetty_deployments %{_datadir}/jetty/webapps

%define _jettydir %{_javadir}/jetty

%if 0%{?rhel} && 0%{?rhel} >= 7
%global with_jetty 0
%global with_jboss 1
# oVirt distribution of JBoss
%global jboss_deployments /usr/share/ovirt-engine-jboss-as/standalone/deployments
%endif

%if 0%{?rhel} && 0%{?rhel} < 7
%global with_jetty 0
%global with_jboss 1
# oVirt distribution of JBoss
%global jboss_deployments /usr/share/ovirt-engine-jboss-as/standalone/deployments
%endif

%if 0%{?fedora} && 0%{?fedora} < 20
%global with_jboss 1
%global with_jetty 1
%global jboss_deployments %{_datadir}/jboss-as/standalone/deployments
%endif

%if 0%{?fedora} && 0%{?fedora} >= 20
%global with_jboss 0
%global with_jetty 1
%endif

# The version macro to be redefined by Jenkins build when needed
%define project_version 0.8
%{!?_version: %define _version %{project_version}}
%{!?_release: %define _release 1}

Name:		ovirt-optimizer
Version:	%{_version}
Release:	%{_release}%{?dist}
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
BuildRequires:	unzip
BuildRequires:  symlinks

Requires:	java >= 1:1.7.0
Requires:	jpackage-utils
Requires:   xpp3

%if 0%{?fedora}
Requires:       quartz
%endif

%if 0%{?fedora} || 0%{?rhel} >= 7
Requires:       protobuf-java >= 2.5
%endif

Requires:       slf4j
Requires:       ovirt-engine-sdk-java >= 3.5.2.0

%description
%{name} service collects data from the oVirt engine and proposes
Vm to host assignments to utilize cluster resources better.

%package ui
Summary:        UI for displaying results in oVirt webadmin
Requires:	    ovirt-engine-webadmin-portal >= 3.5

%description ui
This subpackage adds an UI plugin to the oVirt webadmin portal.
This plugin then adds a Tab to the cluster display and presents
the assingment recommendations to the sysadmin there.

%if 0%{?with_jboss}
%package jboss7
Summary:       Integration of the optimization service to Jboss 7.
Requires:      %{name} = %{version}
Requires:      %{name}-dependencies = %{version}

%if 0%{?fedora}
Requires:	   jboss-as >= 7.1.1-9.3
%endif

%if 0%{?rhel}
# CentOS does not ship jboss, use the package provided by oVirt
Requires:	   ovirt-engine-jboss-as >= 7.1.1-1

# antlr3 is provided by JPackage for RHEL 6, but not for RHEL 7
%if 0%{?rhel} < 7
Requires:      antlr3
%endif

%endif #%if 0%{?rhel}


%if 0%{?rhel} && 0%{?rhel} < 7
# Old package names for el6
Requires:      jakarta-commons-lang
Requires:      jakarta-commons-io
%endif

%description jboss7
This subpackage deploys the optimizer service to the standard Jboss 7
application server.
%endif

%if 0%{?with_jetty}
%package jetty
Summary:       Integration of the optimization service to Jetty 9.
Requires:      %{name} = %{version}
Requires:      %{name}-dependencies = %{version}
Requires:      jetty >= 9
Requires:      cdi-api
Requires:      resteasy
Requires:      jackson
Requires:      jboss-annotations-1.1-api
Requires:      jboss-transaction-1.1-api
Requires:      tomcat-jsp-2.2-api
%if 0%{?fedora} || 0%{?rhel} < 7
Requires:      antlr3
%endif
Requires:      apache-commons-math >= 3
Requires:      ecj

%description jetty
This subpackage deploys the optimizer service to Jetty application
server.
%endif

%package dependencies
Summary:       Libraries not currently provided by the system, but necessary for the project.

%description dependencies
This subpackage bundles libraries that are not provided in the form of RPMs, but are necessary
for the project to work. The goal is to drop this subpackage once the dependencies are available.

%prep
%setup -c -q

%build
mvn %{?with_extra_maven_opts} clean install

%install
##
## Core
##

# Copy core jars to the proper place
install -dm 755 %{buildroot}%{_javadir}/%{name}
install -dm 755 %{buildroot}%{_javadir}/%{name}/bundled
mv ovirt-optimizer-core/target/ovirt-optimizer-core.jar %{buildroot}%{_javadir}/%{name}

##
## Jboss7
##

pushd ovirt-optimizer-jboss7

# Remove the bundled jars by moving them elsewhere and then
# copying only the needed files back. The rest is provided
# by this package's dependencies.
mkdir target/lib
mv target/%{name}-jboss7/WEB-INF/lib/* target/lib

JBOSS_SYMLINK="%{_javadir}/%{name}/%{name}-core.jar
%{_javadir}/commons-beanutils.jar
%{_javadir}/commons-codec.jar
%{_javadir}/commons-logging.jar
%{_javadir}/commons-io.jar
%{_javadir}/commons-lang.jar
%if 0%{?fedora} || 0%{?rhel} >= 7
%{_javadir}/commons-math3.jar
%{_javadir}/protobuf.jar
%{_javadir}/guava.jar
%endif
%if 0%{?fedora}
%{_javadir}/quartz.jar
%{_javadir}/antlr3.jar
%{_javadir}/antlr3-runtime.jar
%endif
%if 0%{?rhel} && 0%{?rhel} < 7
%{_javadir}/antlr3.jar
%{_javadir}/antlr3-runtime.jar
%endif
%{_javadir}/httpcomponents/httpcore.jar
%{_javadir}/httpcomponents/httpclient.jar
%{_javadir}/ovirt-engine-sdk-java/ovirt-engine-sdk-java.jar
%{_javadir}/xpp3.jar"

JBOSS_BUNDLE="target/lib/drools-*
target/lib/kie-*
target/lib/optaplanner-*
%if 0%{?rhel} && 0%{?rhel} < 7
target/lib/guava-*
target/lib/protobuf-java-*
target/lib/commons-math3-*
%endif
%if 0%{?rhel}
target/lib/quartz-*
%endif
%if 0%{?rhel} && 0%{?rhel} >= 7
target/lib/antlr-*
%endif
target/lib/mvel2-*
target/lib/xstream-*"



%if 0%{?with_jboss}

# Install the exploded Jboss war to javadir
cp -ar target/%{name}-jboss7 %{buildroot}%{_javadir}/%{name}/jboss7.war

# Symlink libs to %{buildroot}%{_javadir}/%{name}/jboss7/WEB-INF/lib
echo "$JBOSS_SYMLINK" | xargs -d \\n -I@ sh -c "ln -s -t %{buildroot}%{_javadir}/%{name}/jboss7.war/WEB-INF/lib @"

# Copy bundled libs to %{buildroot}%{_javadir}/%{name}/bundled
echo "$JBOSS_BUNDLE" | xargs -d \\n -I@ sh -c "cp -t %{buildroot}%{_javadir}/%{name}/bundled @"

# Symlink the bundled libs to %{buildroot}%{_javadir}/%{name}/jboss7/WEB-INF/lib
cp -Rs %{buildroot}%{_javadir}/%{name}/bundled/* %{buildroot}%{_javadir}/%{name}/jboss7.war/WEB-INF/lib
symlinks -rc %{buildroot}%{_javadir}/%{name}/jboss7.war/WEB-INF/lib

# Symlink it to Jboss war directory and touch the deploy marker
install -dm 755 %{buildroot}%{jboss_deployments}
ln -sf %{_javadir}/%{name}/jboss7.war %{buildroot}%{jboss_deployments}/%{name}.war
touch %{buildroot}%{jboss_deployments}/%{name}.war.dodeploy

%endif

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

JETTY_SYMLINK="%{_javadir}/resteasy/resteasy-cdi.jar
%{_javadir}/resteasy/resteasy-jackson-provider.jar
%{_javadir}/resteasy/resteasy-jaxrs.jar
%{_javadir}/resteasy/jaxrs-api.jar
%{_javadir}/jackson/jackson-core-asl.jar
%{_javadir}/jackson/jackson-jaxrs.jar
%{_javadir}/jackson/jackson-mapper-asl.jar
%{_javadir}/jackson/jackson-xc.jar
%if 0%{?rhel} || 0%{?fedora} < 20
%{_javadir}/cdi-api.jar
%endif
%if 0%{?fedora} && 0%{?fedora}>=20
%{_javadir}/cdi-api/cdi-api.jar
%endif
%{_javadir}/c3p0.jar
%{_javadir}/javassist.jar
%{_javadir}/scannotation.jar
%{_javadir}/slf4j/slf4j-api.jar
%{_javadir}/slf4j/slf4j-log4j12.jar
%{_javadir}/log4j.jar
%{_javadir}/elspec.jar
%{_javadir}/ecj.jar
%{_jettydir}/jetty-jmx.jar
%{_jettydir}/jetty-server.jar
%{_jettydir}/jetty-servlet.jar
%{_jettydir}/jetty-util.jar
%{_javadir}/jboss-annotations-1.1-api.jar
%{_javadir}/jboss-interceptors-1.1-api.jar
%{_javadir}/jsp.jar
%{_javadir}/jcip-annotations.jar"

JETTY_BUNDLE="target/lib/weld-*
target/lib/activation-1.1*
target/lib/javax.inject-1.jar
target/lib/jsr250-*"

%if 0%{?with_jetty}

# Install the exploded Jetty war to javadir
install -dm 755 %{buildroot}%{_javadir}/%{name}/jetty
cp -ar target/%{name}-jetty %{buildroot}%{_javadir}/%{name}/jetty/%{name}

# Symlink libs to %{buildroot}%{_javadir}/%{name}/jetty/%{name}/WEB-INF/lib
echo "$JBOSS_SYMLINK" | xargs -d \\n -I@ sh -c "ln -s -t %{buildroot}%{_javadir}/%{name}/jetty/%{name}/WEB-INF/lib @"
echo "$JETTY_SYMLINK" | xargs -d \\n -I@ sh -c "ln -s -t %{buildroot}%{_javadir}/%{name}/jetty/%{name}/WEB-INF/lib @"

# Copy bundled libs to %{buildroot}%{_javadir}/%{name}/bundled
echo "$JETTY_BUNDLE" | xargs -d \\n -I@ sh -c "cp -t %{buildroot}%{_javadir}/%{name}/bundled @"

# Symlink the bundled libs to %{buildroot}%{_javadir}/%{name}/jetty/%{name}/WEB-INF/lib
cp -Rs %{buildroot}%{_javadir}/%{name}/bundled/* %{buildroot}%{_javadir}/%{name}/jetty/%{name}/WEB-INF/lib
symlinks -rc %{buildroot}%{_javadir}/%{name}/jetty/%{name}/WEB-INF/lib

# Symlink it to Jetty war directory and touch the deploy marker
install -dm 755 %{buildroot}%{jetty_deployments}
ln -sf %{_javadir}/%{name}/jetty/%{name} %{buildroot}%{jetty_deployments}/%{name}

%endif

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
%defattr(644, root, root, 755)
%doc README COPYING
%dir %{_javadir}/%{name}
%{_javadir}/%{name}/*.jar
%config /etc/%{name}/*

%if 0%{?with_jetty}
%files jetty
%defattr(644, root, root, 755)
%dir %{_javadir}/%{name}/jetty/%{name}
%{_javadir}/%{name}/jetty/%{name}/*
%{jetty_deployments}/*
%endif

%if 0%{?with_jboss}
%files jboss7
%defattr(644, root, root, 755)
%dir %{_javadir}/%{name}/jboss7.war
%{_javadir}/%{name}/jboss7.war/*
%{jboss_deployments}/*
%endif

%files ui
%defattr(644, root, root, 755)
%dir %{engine_data}/ui-plugins/ovirt-optimizer-resources
%config %{engine_etc}/ui-plugins/ovirt-optimizer-config.json
%{engine_data}/ui-plugins/ovirt-optimizer.json
%{engine_data}/ui-plugins/ovirt-optimizer-resources/*

%files dependencies
%defattr(644, root, root, 755)
%dir %{_javadir}/%{name}/bundled
%{_javadir}/%{name}/bundled/*

%changelog
* Mon Nov 10 2014 Martin Sivak <msivak@redhat.com> 0.6-1
- Fix uuid matching rules for even distribution policies
  Resolves: rhbz#1156141

- Fix the way we collect cluster policy parameters
  Related: rhbz#1156141

- Use separate subpackage for bundled dependencies

* Mon Sep 29 2014 Martin Sivak <msivak@redhat.com> 0.5-1
- Optimize start button in webadmin UI is now active
  only when used on stopped VM.
  Resolves: rhbz#1140723

* Mon Sep 11 2014 Martin Sivak <msivak@redhat.com> 0.4-1
- Fixed file permissions
- Fixed REST endpoint urls in UI plugin
  Related: rhbz#1140721

* Mon Aug 25 2014 Martin Sivak <msivak@redhat.com> 0.3-3
- Packaging fixed for CentOS 6 with oVirt repos

* Thu Aug 21 2014 Martin Sivak <msivak@redhat.com> 0.3-2
- Packaging fixed for F19 and F20 in Docker
- Source tarball removed from the RPM

* Mon Aug 11 2014 Martin Sivak <msivak@redhat.com> 0.3-1
- Fixed Fedora/RHEL conditionals
  Related: rhbz#1124264
- Configuration allows http vs. https configuration for SDK
  Resolves: rhbz#1124326

* Fri Jul 25 2014 Martin Sivak <msivak@redhat.com> 0.2-2
- Bundle some jars for CentOS6

* Fri Jun 27 2014 Martin Sivak <msivak@redhat.com> 0.2-1
- Support for CPU cores and threads
- Support for VM start optimization
- Partial support for CentOS6 and F20+

* Fri Jun 27 2014 Martin Sivak <msivak@redhat.com> 0.1-1
Initial release

