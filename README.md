# oVirt optimizer

[![Build Status](https://travis-ci.org/oVirt/ovirt-optimizer.svg?branch=master)](https://travis-ci.org/oVirt/ovirt-optimizer)

## INSTALL

Make sure you have Oracle Java 7 or OpenJDK >= 1.7 installed.

Install ovirt-optimizer for jboss or jetty using:

yum install ovirt-optimizer-jetty
or
yum install ovirt-optimizer-jboss

Unpack the Optaplanner 6.2.0 to /usr/share/java which should create a directory
/usr/share/java/optaplanner-distribution-6.2.0.Final

You can download Optaplanner from http://download.jboss.org/optaplanner/release/6.2.0.Final/optaplanner-distribution-6.2.0.Final.zip

Or you can use the script ovirt-optimizer-setup that will try to do that
for you.

## HTTP REVERSE PROXY

JBoss is listening on 127.0.0.1 in its default configuration and has no SSL configured. It is common to setup
a reverse proxy using httpd or nginx to forward the traffic to the web container running optimizer.

### Apache

```
cat >/etc/httpd/conf.d/00-optimizer.conf <<EOF
<VirtualHost *:443>
    SSLEngine on
    SSLCertificateFile /etc/pki/tls/certs/optimizer.crt
    SSLCertificateKeyFile /etc/pki/tls/keys/optimizer.key

    <Location /ovirt-optimizer>
        ProxyPassMatch http://127.0.0.1:8080 timeout=3600 retry=5
        <IfModule deflate_module>
            AddOutputFilterByType DEFLATE text/javascript text/css text/html text/xml text/json application/xml application/json application/x-yaml
        </IfModule>
    </Location>
</VirtualHost>
EOF
```

If you are using httpd reverse proxy on RHEL 7.1 with enabled SELinux, you will have to allow
it to connect to the optimizer service:

```
setsebool -P httpd_can_network_relay=true
```

### Nginx

```
server {
    server_name fqdn;
    listen 443;

    ssl on;
    ssl_certificate /etc/nginx/ssl/optimizer.crt;
    ssl_certificate_key /etc/nginx/ssl/optimizer.key;

    location /ovirt-optimizer {
        proxy_pass http://localhost:8080;

        gzip            on;
        gzip_min_length 1000;
        gzip_proxied    expired no-cache no-store private auth;
        gzip_types      text/plain application/xml application/json;
    }
}
```

## RUN (USING RPM DISTRIBUTION)

service ovirt-optimizer-jboss start
systemctl start ovirt-optimizer-jboss

or start Jetty (using its own service files)


## RUN (USING SOURCE DISTRIBUTION)

This method uses the Jetty webserver.

1. quick developer mode -

   OVIRT_OPTIMIZER_CONFIG=/home/ovirt/ovirt-optimizer.properties \
   mvn jetty:run -pl ovirt-optimizer-jetty/

2. service mode (forked the bg, with debug port 5005 and 'stop' port 1353) -

   OVIRT_OPTIMIZER_CONFIG=/home/ovirt/ovirt-optimizer.properties \
   mvn -Prun -pl ovirt-optimizer-jetty/

```
# sample ovirt-optimizer.properties
org.ovirt.optimizer.sdk.protocol=http
org.ovirt.optimizer.sdk.server=dockerhost
org.ovirt.optimizer.sdk.port=8080
org.ovirt.optimizer.sdk.connection.timeout=10
org.ovirt.optimizer.sdk.username=admin@internal
org.ovirt.optimizer.sdk.password=PASSWORD
org.ovirt.optimizer.sdk.ca.store=/etc/ovirt-optimizer/ovirt-optimizer.truststore

org.ovirt.optimizer.solver.steps=20
org.ovirt.optimizer.solver.timeout=30
org.ovirt.optimizer.solver.data.refresh=60
org.ovirt.optimizer.solver.cluster.refresh=300
# end of ovirt-optimizer.properties
```

A sample of the file is also under PROJECT-ROOT/ovirt-optimizer-core/src/main/resources


## RELEASE

How to prepare a release:

1. Run `./release.sh [--major]` from the root directory
2. Confirm the versions
3. Edit the spec file that opens and fill in the changelog entry
4. Run:
   `git archive --format=tgz HEAD >ovirt-optimizer-{version}.tar.gz`
   or
   `sh make-srpm.sh`


## BUILDING RPMs LOCALLY

`rpmbuild --define "_version $VERSION" --define "_release ${VERSION[1]-1}" --nodeps --rebuild ovirt-optimizer-<version>.srpm`

## BUILDING RPMs IN COPR

The following packages have to be always present for EL6 platform:

scl-utils-build maven30-build

The following repositories have to be explicitly added to the project:

copr://rhscl/maven30-el6
copr://rhscl/rh-java-common

Also the internet access has to be enabled for the build so it can retrieve
all maven dependencies.

