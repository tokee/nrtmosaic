#!/bin/sh

rm -r tomcat/webapps/nrtmosaic*
mvn clean package -DskipTests
mv target/nrtmosaic*.war target/nrtmosaic.war
cp target/nrtmosaic.war tomcat/webapps/

echo "nrtmosaic deployed to localtomcat"

