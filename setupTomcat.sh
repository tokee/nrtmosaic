#!/bin/bash

#
# Downloads and installs tomcat 8 as a subfolder to the current folder
#

VERSION=8.0.36
MEM=1000m

URL="http://ftp.download-by.net/apache/tomcat/tomcat-${VERSION:0:1}/v${VERSION}/bin/apache-tomcat-${VERSION}.tar.gz"
rm -rf tomcat apache-tomcat-${VERSION}
curl "$URL" > apache-tomcat-${VERSION}.tar.gz
if [ "100000" -gt `cat apache-tomcat-${VERSION}.tar.gz | wc -c` ]; then
    echo "Unable to download tomcat 8 from $URL"
    echo "Maybe that version is no longer available?"
    exit 2
fi
echo "tar xzovf apache-tomcat-${VERSION}.tar.gz"
tar xzovf apache-tomcat-${VERSION}.tar.gz
ln -s apache-tomcat-${VERSION} tomcat
echo "export JAVA_OPTS=\"-Xmx$MEM $JAVA_OPTS\"" > tomcat/bin/setenv.sh
