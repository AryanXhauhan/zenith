#!/bin/sh
# ----------------------------------------------------------------------------
# Simplified Maven Wrapper for Zenith
# ----------------------------------------------------------------------------

# Resolve project base directory
PRG="$0"
while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG="`dirname "$PRG"`/$link"
  fi
done
BASEDIR=`dirname "$PRG"`
BASEDIR=`cd "$BASEDIR" && pwd`

# Check for Java
if [ -z "$JAVA_HOME" ] ; then
  JAVA_EXE="java"
else
  JAVA_EXE="$JAVA_HOME/bin/java"
fi

# Run Maven Wrapper
exec "$JAVA_EXE" \
  $MAVEN_OPTS \
  -classpath "$BASEDIR/.mvn/wrapper/maven-wrapper.jar" \
  "-Dmaven.multiModuleProjectDirectory=$BASEDIR" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
