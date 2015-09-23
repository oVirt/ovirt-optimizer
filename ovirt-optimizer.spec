# The version macro to be redefined by Jenkins build when needed
%define project_version 0.9.1
%{!?_version: %define _version %{project_version}}
%{!?_release: %define _release 2}

%global engine_etc /etc/ovirt-engine
%global engine_data %{_datadir}/ovirt-engine
%global jetty_deployments %{_datadir}/jetty/webapps
%global _optaplanner %{_javadir}/%{name}/optaplanner
%global _optaplanner_archive %{_javadir}/optaplanner-distribution-6.2.0.Final/binaries

%define _jettydir %{_javadir}/jetty

%if 0%{?rhel} && 0%{?rhel} >= 7
%global with_jetty 0
%global with_jboss 1
%global with_systemd 1
%global with_sysv 0

# oVirt distribution of JBoss
%global jboss_deployments %{_javadir}/%{name}/jboss-conf/deployments
%endif

%if 0%{?rhel} && 0%{?rhel} < 7
%global with_jetty 0
%global with_jboss 1
%global with_systemd 0
%global with_sysv 1

# oVirt distribution of JBoss
%global jboss_deployments %{_javadir}/%{name}/jboss-conf/deployments
%endif

%if 0%{?fedora} && 0%{?fedora} < 20
%global with_jboss 1
%global with_jetty 1
%global with_systemd 1
%global with_sysv 0

%global jboss_deployments %{_datadir}/jboss-as/standalone/deployments
%endif

%if 0%{?fedora} && 0%{?fedora} >= 20
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

BuildRequires:	java-devel
BuildRequires:	jpackage-utils

# Enable SCL maven when available, but only on EL6
%if 0%{?scl:1} && 0%{?rhel} && 0%{?rhel} <= 6
BuildRequires:  maven30
BuildRequires:  maven30-maven
BuildRequires:  rh-java-common-maven-local
%else
BuildRequires:  maven
BuildRequires:	maven-local
%endif

BuildRequires:	unzip
BuildRequires:  symlinks

Requires:	java >= 1:1.7.0
Requires:	jpackage-utils

Requires:       curl
Requires:       unzip

%if 0%{?fedora}
Requires:       antlr3
Requires:       quartz
%endif

Requires:       xpp3

%if 0%{?fedora} || 0%{?rhel} >= 7
Requires:       protobuf-java >= 2.5
Requires:       guava >= 13
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
%package jboss
Summary:       Integration of the optimization service to JBoss 7 and Wildfly.
Provides:      %{name}-jboss7 = %{version}
Provides:      %{name}-wildfly = %{version}
Requires:      %{name} = %{version}
Requires:      %{name}-dependencies = %{version}

%if %{?with_systemd}
Requires(post): systemd
Requires(preun): systemd
Requires(postun): systemd
BuildRequires: systemd
%endif

%if %{?with_sysv}
Requires:      sudo
%endif

Requires:      log4j

%if 0%{?fedora} && 0%{?fedora} >= 20
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
Requires:      jetty >= 9
Requires:      cdi-api
Requires:      resteasy
Requires:      jackson
Requires:      jboss-annotations-1.1-api
Requires:      jboss-transaction-1.1-api
%if 0%{?fedora} >= 23
Requires:      tomcat-jsp-2.3-api
%else
Requires:      tomcat-jsp-2.2-api
%endif
Requires:      apache-commons-math >= 3
Requires:      ecj

%description jetty
This subpackage deploys the optimizer service to Jetty application
server.
%endif

%package dependencies
Summary:       Libraries not currently provided by the system, but necessary for the project.
Requires:      %{name} = %{version}

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
%{_javadir}/slf4j/slf4j-api.jar
%{_javadir}/slf4j/slf4j-log4j12.jar
%{_javadir}/log4j.jar
%{_javadir}/commons-beanutils.jar
%{_javadir}/commons-codec.jar
%{_javadir}/commons-logging.jar
%{_optaplanner}/drools-compiler-6.2.0.Final.jar
%{_optaplanner}/drools-core-6.2.0.Final.jar
%{_optaplanner}/kie-api-6.2.0.Final.jar
%{_optaplanner}/kie-internal-6.2.0.Final.jar
%{_optaplanner}/mvel2-2.2.4.Final.jar
%{_optaplanner}/optaplanner-core-6.2.0.Final.jar
%{_optaplanner}/xmlpull-1.1.3.1.jar
%{_optaplanner}/xstream-1.4.7.jar
%if 0%{?fedora} || 0%{?rhel} >= 7
%{_javadir}/guava.jar
%{_javadir}/protobuf.jar
%endif
%if 0%{?fedora}
%{_javadir}/antlr3.jar
%{_javadir}/antlr3-runtime.jar
%{_javadir}/commons-io.jar
%{_javadir}/commons-lang.jar
%{_javadir}/commons-math3.jar
%{_javadir}/quartz.jar
%endif
%if 0%{?rhel}
%{_optaplanner}/antlr-runtime-3.5.jar
%{_optaplanner}/commons-io-2.1.jar
%{_optaplanner}/commons-lang-2.6.jar
%{_optaplanner}/commons-math3-3.2.jar
%endif
%if 0%{?rhel} && 0%{?rhel} < 7
%{_optaplanner}/guava-13.0.1.jar
%{_optaplanner}/protobuf-java-2.5.0.jar
%endif
%{_javadir}/httpcomponents/httpcore.jar
%{_javadir}/httpcomponents/httpclient.jar
%{_javadir}/ovirt-engine-sdk-java/ovirt-engine-sdk-java.jar
%{_javadir}/xpp3.jar"

%if 0%{?rhel}
JBOSS_BUNDLE="target/lib/quartz-*"
%else
JBOSS_BUNDLE=""
%endif



%if 0%{?with_jboss}

# Install the exploded Jboss war to javadir
cp -ar target/%{name}-jboss7 %{buildroot}%{_javadir}/%{name}/jboss.war

# Symlink libs to %{buildroot}%{_javadir}/%{name}/jboss/WEB-INF/lib
echo "$JBOSS_SYMLINK" | xargs -d \\n -I@ sh -c "ln -s -t %{buildroot}%{_javadir}/%{name}/jboss.war/WEB-INF/lib @"

# Copy bundled libs to %{buildroot}%{_javadir}/%{name}/bundled
if [ "x$JBOSS_BUNDLE" != "x" ]; then
    echo "$JBOSS_BUNDLE" | xargs -d \\n -I@ sh -c "cp -t %{buildroot}%{_javadir}/%{name}/bundled @"
fi

# Symlink the bundled libs to %{buildroot}%{_javadir}/%{name}/jboss/WEB-INF/lib
cp -Rs %{buildroot}%{_javadir}/%{name}/bundled/* %{buildroot}%{_javadir}/%{name}/jboss.war/WEB-INF/lib || true
symlinks -rc %{buildroot}%{_javadir}/%{name}/jboss.war/WEB-INF/lib

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

# Install config file
install -dm 755 %{buildroot}/etc/%{name}
install dist/etc/*.properties %{buildroot}/etc/%{name}

%files
%defattr(644, root, root, 755)
%doc README COPYING
%attr(755, root, root) /usr/bin/ovirt-optimizer-setup
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
%files jboss
%defattr(644, root, root, 755)
%dir %{_javadir}/%{name}/jboss.war
%{_javadir}/%{name}/jboss.war/*
%dir %{_javadir}/%{name}/jboss-conf
%{_javadir}/%{name}/jboss-conf/*
%{jboss_deployments}/*
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
%{engine_data}/ui-plugins/ovirt-optimizer-resources/*

%files dependencies
%defattr(644, root, root, 755)
%dir %{_javadir}/%{name}/bundled
%{_javadir}/%{name}/bundled/*
%{_optaplanner}

%post dependencies
/usr/bin/ovirt-optimizer-setup || echo "Optaplanner 6.2.0 could not be installed. Please see the README file for %{name}-%{version} and install it manually"

# Systemd scripts
%if %{?with_systemd} && %{?with_jboss}
%post jboss
%systemd_post ovirt-optimizer-jboss.service

%preun jboss
%systemd_preun ovirt-optimizer-jboss.service

%postun jboss
%systemd_postun_with_restart ovirt-optimizer-jboss.service
%endif

%changelog
* Wed Dec 23 2015 Martin Sivak <msivak@redhat.com> 0.9.1-2
- Fix dependency for Fedora 23

* Thu Oct  1 2015 Martin Sivak <msivak@redhat.com> 0.9.1-1
- Reuse the oVirt API instance to avoid login/logout noise

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

