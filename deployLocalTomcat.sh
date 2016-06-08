#!/bin/sh

rm  /home/teg/Desktop/apache-tomcat-8.0.23/webapps/nrtmosaic -r
mvn clean package -DskipTests
mv target/nrtmosaic*.war target/nrtmosaic.war
cp target/nrtmosaic.war /home/teg/Desktop/apache-tomcat-8.0.23/webapps/

echo "nrtmosaic deployed to localtomcat"

