#!/bin/bash

#
# Script for easy deployment. At least easy for Toke.
# But maybe it can be used for inspiration?
#

SETUP="$1"
if [ "." == ".$SETUP" ]; then
    echo "Usage: ./deploySetup.sh setup"
    echo "Sample: ./deploySetup.sh setups/local"
    exit 2
fi

# Clean up and build the code
tomcat/bin/shutdown.sh
rm -r tomcat/webapps/nrtmosaic* tomcat/webapps/gui 
mvn clean package -DskipTests
mv target/nrtmosaic*.war target/nrtmosaic.war

# Add the setup-specific properties to the war
pushd "$SETUP/war" > /dev/null
zip -r ../../../target/nrtmosaic.war WEB-INF
popd  > /dev/null

# Copy the default gui to tomcat
cp -r gui/ tomcat/webapps/

# Add specific gui files and the updated WAR
cp -r "$SETUP"/gui/* tomcat/webapps/gui/
cp -r target/nrtmosaic.war tomcat/webapps/

tomcat/bin/startup.sh

echo "nrtmosaic deployed to local tomcat, available at http://localhost:8080/gui/"

