# The version macro to be redefined by Jenkins build when needed
%define project_version 0.15-SNAPSHOT
%define project_release 1
%define optaplanner_version 6.4.0.Final

%{!?_version: %define _version %{project_version}}
%{!?_release: %define _release %{project_release}}
%define mvn_sed sed -e 's/mvn(\\([^)]*\\))\\( [>=<]\\{1,2\\} [^ ]\\{1,\\}\\)\\{0,1\\}/\\1/g'
%{!?_licensedir:%global license %doc}

%global engine_etc /etc/ovirt-engine
%global engine_data %{_datadir}/ovirt-engine
%global jetty_deployments %{_datadir}/jetty/webapps
%global _optaplanner %{_javadir}/%{name}/optaplanner
%global _optaplanner_archive %{_javadir}/optaplanner-distribution-%{optaplanner_version}/binaries
%global log_config_file log4j.properties

%define _jettydir %{_javadir}/jetty

%if 0%{?rhel}
%global with_jetty 0
%global with_jboss 1
%global with_systemd 1
%global with_sysv 0

# oVirt distribution of JBoss
%global jboss_deployments %{_javadir}/%{name}/jboss-conf/deployments
%endif

%if 0%{?fedora}
%global with_jboss 1
%global with_jetty 1
%global with_systemd 1
%global with_sysv 0

# oVirt distribution of JBoss
%global jboss_deployments %{_javadir}/%{name}/jboss-conf/deployments
%endif

Name:		ovirt-optimizer
Version:	%{_version}
Release:	%{_release}%{?dist}
Summary:	Cluster balance optimization service for oVirt
Group:		%{ovirt_product_group}
License:	ASL 2.0
URL:		http://www.ovirt.org
Source0:	http://ovirt.org/releases/stable/src/%{name}-%{version}.tar.gz

BuildArch:	noarch

BuildRequires:  java-1.8.0-openjdk-devel
BuildRequires:	jpackage-utils

BuildRequires:  maven
BuildRequires:	maven-local

BuildRequires:	unzip
BuildRequires:  symlinks

Requires:	java >= 1:1.8.0
Requires:	jpackage-utils
Requires:       xmvn

# Needed for xmvn, probably a packaging bug
Requires:       maven-local

Requires:       curl
Requires:       unzip

%if 0%{?fedora}
Requires:       mvn(org.antlr:antlr) >= 3
Requires:       mvn(org.antlr:antlr-runtime) >= 3
%endif

Requires:       mvn(org.apache.httpcomponents:httpclient)
Requires:       mvn(org.apache.httpcomponents:httpcore)
Requires:       mvn(com.google.guava:guava) >= 13

Requires:       mvn(org.slf4j:slf4j-api)
Requires:       mvn(org.ovirt.engine.sdk:ovirt-engine-sdk-java) >= 3.6.0.4
Requires:       mvn(org.ovirt.engine.api:sdk) >= 4.0.0.alpha13

%description
%{name} service collects data from the oVirt engine and proposes
Vm to host assignments to utilize cluster resources better.

%package ui
Summary:        UI for displaying results in oVirt webadmin
Requires:       ovirt-engine-webadmin-portal >= 4.0

%description ui
This subpackage adds an UI plugin to the oVirt webadmin portal.
This plugin then adds a Tab to the cluster display and presents
the assingment recommendations to the sysadmin there.

%if 0%{?with_jboss}
%package jboss
Summary:       Integration of the optimization service to JBoss 7 and Wildfly.
Provides:      %{name}-jboss7 = %{version}
Provides:      %{name}-wildfly = %{version}
Requires:      %{name} = %{version}
Requires:      %{name}-dependencies = %{version}

Requires(post): %{name} = %{version}
Requires(post): %{name}-dependencies = %{version}
Requires(post): findutils

%if %{?with_systemd}
Requires(post): systemd
Requires(preun): systemd
Requires(postun): systemd
BuildRequires: systemd
%endif

%if %{?with_sysv}
Requires:      sudo
%endif

%if 0%{?fedora}
Requires:	   ovirt-engine-wildfly >= 8.2
%endif

%if 0%{?rhel}
# CentOS does not ship jboss, use the package provided by oVirt
Requires:	   ovirt-engine-wildfly >= 8.2
%endif #%if 0%{?rhel}

%description jboss
This subpackage deploys the optimizer service to the standard Wildfly
application server.
%endif

%if 0%{?with_jetty}
%package jetty
Summary:       Integration of the optimization service to Jetty 9.
Requires:      %{name} = %{version}
Requires:      %{name}-dependencies = %{version}

Requires(post): findutils
Requires(post): %{name} = %{version}
Requires(post): %{name}-dependencies = %{version}

Requires:      jetty >= 9

Requires:      mvn(org.jboss.spec.javax.annotation:jboss-annotations-api_1.1_spec)
Requires:      mvn(org.jboss.spec.javax.transaction:jboss-transaction-api_1.1_spec)
Requires:      mvn(org.apache.tomcat:tomcat-jsp-api)
Requires:      mvn(org.jboss.resteasy:jaxrs-api)
Requires:      mvn(org.jboss.resteasy:resteasy-jackson-provider)
Requires:      mvn(org.jboss.resteasy:resteasy-cdi)
Requires:      mvn(org.codehaus.jackson:jackson-core-asl)
Requires:      mvn(org.codehaus.jackson:jackson-jaxrs)
Requires:      mvn(org.codehaus.jackson:jackson-mapper-asl)
Requires:      mvn(org.codehaus.jackson:jackson-xc)
Requires:      mvn(javax.enterprise:cdi-api)
Requires:      mvn(org.scannotation:scannotation)
Requires:      mvn(c3p0:c3p0)
Requires:      mvn(net.jcip:jcip-annotations)
Requires:      mvn(org.jboss.weld.servlet:weld-servlet) >= 2.2

%description jetty
This subpackage deploys the optimizer service to Jetty application
server.
%endif

%package dependencies
Summary:       Libraries not currently provided by the system, but necessary for the project.
Requires:      %{name} = %{version}

Requires:      mvn(commons-beanutils:commons-beanutils)
Requires:      mvn(commons-logging:commons-logging)

%if 0%{?fedora}
Requires:      mvn(log4j:log4j:12)
%endif

%if 0%{?rhel}
Requires:      mvn(log4j:log4j)
%endif

Requires:      mvn(org.slf4j:slf4j-log4j12)


%description dependencies
This subpackage bundles libraries that are not provided in the form of RPMs, but are necessary
for the project to work. The goal is to drop this subpackage once the dependencies are available.

%prep
%setup -c -q

%build
%{?scl:scl enable maven30 "}
mvn %{?with_extra_maven_opts} clean install
%{?scl:"}

%install
##
## Core
##

# Install config file
install -dm 755 %{buildroot}/etc/%{name}
install dist/etc/*.properties %{buildroot}/etc/%{name}

# Copy the setup script to the proper place
install -dm 755 %{buildroot}/usr/bin
mv dist/bin/ovirt-optimizer-setup %{buildroot}/usr/bin/

# Copy core jars to the proper place
install -dm 755 %{buildroot}%{_javadir}/%{name}
install -dm 755 %{buildroot}%{_javadir}/%{name}/bundled
mv ovirt-optimizer-core/target/ovirt-optimizer-core.jar %{buildroot}%{_javadir}/%{name}

# Optaplanner symlink
ln -sf %{_optaplanner_archive} %{buildroot}%{_optaplanner}

##
## Wildfly
##

%if 0%{?with_jboss}

# Install the start script
mv dist/bin/ovirt-optimizer-jboss %{buildroot}/usr/bin/

%if %{?with_sysv}
install -dm 755 %{buildroot}/etc/init.d
mv dist/initscript/ovirt-optimizer-jboss %{buildroot}/etc/init.d
%endif

%if %{?with_systemd}
install -dm 755 %{buildroot}%{_unitdir}
mv dist/initscript/ovirt-optimizer-jboss.service %{buildroot}%{_unitdir}
%endif

# Create the JBoss config directory structure for standalone run
cp -ar dist/jboss-conf %{buildroot}%{_javadir}/%{name}

# Create the log file directory and link it
mkdir -p %{buildroot}/var/log/%{name}/jboss
ln -sf /var/log/%{name}/jboss %{buildroot}%{_javadir}/%{name}/jboss-conf/log

%endif

pushd ovirt-optimizer-jboss7

# Remove the bundled jars by moving them elsewhere and then
# copying only the needed files back. The rest is provided
# by this package's dependencies.
mkdir target/lib
mv target/%{name}-jboss7/WEB-INF/lib/* target/lib

JBOSS_SYMLINK="%{_javadir}/%{name}/%{name}-core.jar
%{_optaplanner}/annotations-2.0.1.jar
%{_optaplanner}/antlr-runtime-3.5.jar
%{_optaplanner}/commons-codec-1.4.jar
%{_optaplanner}/commons-io-2.1.jar
%{_optaplanner}/commons-lang3-3.1.jar
%{_optaplanner}/commons-math3-3.4.1.jar
%{_optaplanner}/drools-compiler-%{optaplanner_version}.jar
%{_optaplanner}/drools-core-%{optaplanner_version}.jar
%{_optaplanner}/ecj-4.4.2.jar
%{_optaplanner}/javassist-3.18.1-GA.jar
%{_optaplanner}/kie-api-%{optaplanner_version}.jar
%{_optaplanner}/kie-internal-%{optaplanner_version}.jar
%{_optaplanner}/mvel2-2.2.8.Final.jar
%{_optaplanner}/optaplanner-core-%{optaplanner_version}.jar
%{_optaplanner}/optaplanner-persistence-common-%{optaplanner_version}.jar
%{_optaplanner}/optaplanner-persistence-xstream-%{optaplanner_version}.jar
%{_optaplanner}/protobuf-java-2.6.0.jar
%{_optaplanner}/reflections-0.9.10.jar
%{_optaplanner}/xmlpull-1.1.3.1.jar
%{_optaplanner}/xpp3_min-1.1.4c.jar
%{_optaplanner}/xstream-1.4.7.jar"

%if 0%{?with_jboss}

# Install the exploded Jboss war to javadir
cp -ar target/%{name}-jboss7 %{buildroot}%{_javadir}/%{name}/jboss.war

# Move config file to etc and symlink it to the right place
mv %{buildroot}%{_javadir}/%{name}/jboss.war/WEB-INF/classes/%{log_config_file} %{buildroot}/etc/%{name}/jboss-%{log_config_file}
ln -sf /etc/%{name}/jboss-%{log_config_file} %{buildroot}%{_javadir}/%{name}/jboss.war/WEB-INF/classes/%{log_config_file}

# Symlink libs to %{buildroot}%{_javadir}/%{name}/jboss/WEB-INF/lib
echo "$JBOSS_SYMLINK" | xargs -d \\n -I@ sh -c "ln -s -t %{buildroot}%{_javadir}/%{name}/jboss.war/WEB-INF/lib @"

# Symlink it to Jboss war directory and touch the deploy marker
install -dm 755 %{buildroot}%{jboss_deployments}
ln -sf %{_javadir}/%{name}/jboss.war %{buildroot}%{jboss_deployments}/%{name}.war
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

JETTY_SYMLINK="%{_jettydir}/jetty-jmx.jar
%{_jettydir}/jetty-server.jar
%{_jettydir}/jetty-servlet.jar
%{_jettydir}/jetty-util.jar"

JETTY_BUNDLE="target/lib/activation-1.1*
target/lib/javax.inject-1.jar
target/lib/jsr250-*"

%if 0%{?with_jetty}

# Install the exploded Jetty war to javadir
install -dm 755 %{buildroot}%{_javadir}/%{name}/jetty
cp -ar target/%{name}-jetty %{buildroot}%{_javadir}/%{name}/jetty/%{name}

# Move config file to etc and symlink it to the right place
mv %{buildroot}%{_javadir}/%{name}/jetty/%{name}/WEB-INF/classes/%{log_config_file} %{buildroot}/etc/%{name}/jetty-%{log_config_file}
ln -sf /etc/%{name}/jetty-%{log_config_file} %{buildroot}%{_javadir}/%{name}/jetty/%{name}/WEB-INF/classes/%{log_config_file}

# Symlink libs to %{buildroot}%{_javadir}/%{name}/jetty/%{name}/WEB-INF/lib
echo "$JBOSS_SYMLINK" | xargs -d \\n -I@ sh -c "ln -s -t %{buildroot}%{_javadir}/%{name}/jetty/%{name}/WEB-INF/lib @"
echo "$JETTY_SYMLINK" | xargs -d \\n -I@ sh -c "ln -s -t %{buildroot}%{_javadir}/%{name}/jetty/%{name}/WEB-INF/lib @"

# Copy bundled libs to %{buildroot}%{_javadir}/%{name}/bundled
echo "$JETTY_BUNDLE" | xargs -d \\n -I@ sh -c "cp -t %{buildroot}%{_javadir}/%{name}/bundled @"

# Symlink the bundled libs to %{buildroot}%{_javadir}/%{name}/jetty/%{name}/WEB-INF/lib
cp -Rs %{buildroot}%{_javadir}/%{name}/bundled/* %{buildroot}%{_javadir}/%{name}/jetty/%{name}/WEB-INF/lib || true
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


%files
%defattr(644, root, root, 755)
%doc README.md
%license COPYING
%attr(755, root, root) /usr/bin/ovirt-optimizer-setup
%dir %{_javadir}/%{name}
%{_javadir}/%{name}/*.jar
%config /etc/%{name}/*

%if 0%{?with_jetty}
%files jetty
%defattr(644, root, root, 755)
%config(noreplace) /etc/%{name}/jetty*
%dir %{_javadir}/%{name}/jetty/%{name}
%{_javadir}/%{name}/jetty/%{name}/
%{jetty_deployments}/*
%endif

%if 0%{?with_jboss}
%files jboss
%defattr(644, root, root, 755)
%config(noreplace) /etc/%{name}/jboss*
%dir %{_javadir}/%{name}/jboss.war
%{_javadir}/%{name}/jboss.war/
%dir %{_javadir}/%{name}/jboss-conf
%{_javadir}/%{name}/jboss-conf/
%{jboss_deployments}/
%dir /var/log/%{name}/jboss
%attr(755, root, root) /usr/bin/ovirt-optimizer-jboss

# Initscripts
%if %{?with_sysv}
%attr(755, root, root) /etc/init.d/ovirt-optimizer-jboss
%endif
%if %{?with_systemd}
%{_unitdir}/ovirt-optimizer-jboss.service
%endif
%endif

%files ui
%defattr(644, root, root, 755)
%dir %{engine_data}/ui-plugins/ovirt-optimizer-resources
%config %{engine_etc}/ui-plugins/ovirt-optimizer-config.json
%{engine_data}/ui-plugins/ovirt-optimizer.json
%{engine_data}/ui-plugins/ovirt-optimizer-resources/

%files dependencies
%defattr(644, root, root, 755)
%dir %{_javadir}/%{name}/bundled
%{_javadir}/%{name}/bundled/
%{_optaplanner}

%post dependencies
/usr/bin/ovirt-optimizer-setup || echo "Optaplanner %{optaplanner_version} could not be installed. Please see the README file for %{name}-%{version} and install it manually"

%if %{?with_jetty}
%post jetty
OPTIMIZER_MVN=$(rpm -qR %{name} | grep '^mvn(')

echo ${OPTIMIZER_MVN} | %{mvn_sed} | xargs build-jar-repository %{_javadir}/%{name}/jetty/%{name}/WEB-INF/lib

JETTY_MVN=$(rpm -qR %{name}-jetty | grep '^mvn(')

echo ${JETTY_MVN} | %{mvn_sed} | xargs build-jar-repository %{_javadir}/%{name}/jetty/%{name}/WEB-INF/lib

DEPS_MVN=$(rpm -qR %{name}-dependencies | grep '^mvn(')

echo ${DEPS_MVN} | %{mvn_sed} | xargs build-jar-repository %{_javadir}/%{name}/jetty/%{name}/WEB-INF/lib
%endif

%if %{?with_jboss}
%post jboss
OPTIMIZER_MVN=$(rpm -qR %{name} | grep '^mvn(')

echo ${OPTIMIZER_MVN} | %{mvn_sed} | xargs build-jar-repository %{_javadir}/%{name}/jboss.war/WEB-INF/lib

DEPS_MVN=$(rpm -qR %{name}-dependencies | grep '^mvn(')

echo ${DEPS_MVN} | %{mvn_sed} | xargs build-jar-repository %{_javadir}/%{name}/jboss.war/WEB-INF/lib

JBOSS_MVN=$(rpm -qR %{name}-jboss | grep '^mvn(')

echo ${JBOSS_MVN} | %{mvn_sed} | xargs build-jar-repository %{_javadir}/%{name}/jboss.war/WEB-INF/lib

%if %{?with_systemd}
# Systemd scripts
%systemd_post ovirt-optimizer-jboss.service
%endif
%endif

# Systemd scripts
%if %{?with_systemd} && %{?with_jboss}
%preun jboss
%systemd_preun ovirt-optimizer-jboss.service

%postun jboss
%systemd_postun_with_restart ovirt-optimizer-jboss.service
%endif

%changelog
* Mon Nov 21 2016 Martin Sivak <msivak@redhat.com> 0.14-1
- Fix (add) handling of SDKv4 errors

* Tue Oct 25 2016 Martin Sivak <msivak@redhat.com> 0.13-1
- Fix issues the UI plugin had with engine's v4 REST

* Wed Oct 19 2016 Martin Sivak <msivak@redhat.com> 0.12-1
- Fix the optimize start endpoint deserialization
- Require the fixed oVirt SDKv4

* Wed Aug 31 2016 Martin Sivak <msivak@redhat.com> 0.11-1
- Support for Affinity Labels in oVirt 4
- Optaplanner 6.4 support
- oVirt 4 REST authentication mode for UI plugin
- Rule updates to match some oVirt changes

* Tue Apr 12 2016 Martin Sivak <msivak@redhat.com> 0.10-1
- Configuration file can be specified on command line
- Better release procedure
- Extensive internal refactoring
- Java 8 support
- Optaplanner 6.3 support
- Bugfixes

* Wed May 27 2015 Martin Sivak <msivak@redhat.com> 0.9-1
- Proper service files for SysV and systemd
- ovirt-optimizer-setup tool
- Optaplanner is no longer bundled but the setup tool downloads
  it from the official web page (download is md5/sha224 checksum
  protected)
- Support for Wildfly

* Thu May 7 2015 Martin Sivak <msivak@redhat.com> 0.8-1
- UI improvements and bug fixes
- Optimizer no longer forgets half of the solution
  after fact update
- Support for hosted engine VM and hosts

* Fri Mar 6 2015 Martin Sivak <msivak@redhat.com> 0.5-1
- Support for custom scheduling rules
- Proper login procedure to ovirt-engine's REST API
- UI improvements and bug fixes
- PinToHost fixes
- Do not propose starting a VM that was not requested

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

* Thu Sep 11 2014 Martin Sivak <msivak@redhat.com> 0.4-1
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
