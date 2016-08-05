#!/bin/sh

rm -r tomcat/webapps/nrtmosaic*
mvn clean package -DskipTests
mv target/nrtmosaic*.war target/nrtmosaic.war

cp -r gui/ tomcat/webapps/
cp target/nrtmosaic.war tomcat/webapps/
sleep 2
#mkdir -p tomcat/webapps/nrtmosaic/WEB-INF/classes/
tomcat/bin/shutdown.sh
tomcat/bin/startup.sh
tomcat/bin/shutdown.sh
sleep 1
cp setups/local/nrtmosaic.properties tomcat/webapps/nrtmosaic/WEB-INF/classes/
tomcat/bin/startup.sh

echo "nrtmosaic deployed to local tomcat, available at http://localhost:8080/gui/"

