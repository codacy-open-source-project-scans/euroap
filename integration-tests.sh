#!/bin/bash

#
# Copyright The WildFly Authors
# SPDX-License-Identifier: Apache-2.0
#

# Shell script to run the integration tests
BASH_INTERPRETER=${BASH_INTERPRETER:-${SHELL}}

PROGNAME=`basename $0`
DIRNAME=`dirname "${BASH_SOURCE[0]}"`
DIRNAME=`cd "$DIRNAME" && pwd`
GREP="grep"
ROOT="/"

# Ignore user's MAVEN_HOME if it is set
M2_HOME=""
MAVEN_HOME=""

# MAVEN_OPTS now live in .mvn/jvm.config and .mvn/maven.config
# MAVEN_OPTS="$MAVEN_OPTS -Xmx512M"
# export MAVEN_OPTS

# The default arguments.  `mvn -s ...` will override this.
# Take the default settings.xml file if it exists
MVN_SETTINGS_XML_DEFAULT="$DIRNAME/tools/maven/conf/settings.xml"
if [ -f "$MVN_SETTINGS_XML_DEFAULT" ]; then
    MVN_ARGS=${MVN_ARGS:-"-s $MVN_SETTINGS_XML_DEFAULT"};
fi

# Use the maximum available, or set MAX_FD != -1 to use that.
MAX_FD="maximum"

# OS specific support (must be 'true' or 'false').
cygwin=false;
darwin=false;
case "`uname`" in
    CYGWIN*)  cygwin=true;;
    Darwin*)  darwin=true;;
esac




#
#  Integration testsuite support.
#

#
CMD_LINE_PARAMS=
TESTS_SPECIFIED="N"
. testsuite/groupDefs.sh

#
# Helper to process command line for test directives
# - user-specified parameters (allTests etc) are translated into the appropriate
# maven build profiles and removed from the command line
# - smoke tests run with build
#
process_test_directives() {

    MVN_GOALS="";

    # For each parameter, check for testsuite directives.
    for param in $@ ; do
    case $param in
      ## -s .../settings.xml - don't use our own.
      -s)                MVN_ARGS="";          CMD_LINE_PARAMS="$CMD_LINE_PARAMS -s";;
      ## -DallTests runs all tests.
      -DallTests)        TESTS_SPECIFIED="Y";  CMD_LINE_PARAMS="$CMD_LINE_PARAMS -DallTests -fae";;

      -Dinteg-tests)     TESTS_SPECIFIED="Y";  CMD_LINE_PARAMS="$CMD_LINE_PARAMS $INTEGRATION_TESTS";;
      -Dcluster-tests)   TESTS_SPECIFIED="Y";  CMD_LINE_PARAMS="$CMD_LINE_PARAMS $CLUSTER_TESTS";;
      -Dsmoke-tests)     TESTS_SPECIFIED="Y";  CMD_LINE_PARAMS="$CMD_LINE_PARAMS $SMOKE_TESTS";;
      -Dbasic-tests)     TESTS_SPECIFIED="Y";  CMD_LINE_PARAMS="$CMD_LINE_PARAMS $BASIC_TESTS";;
      -Ddomain-tests)    TESTS_SPECIFIED="Y";  CMD_LINE_PARAMS="$CMD_LINE_PARAMS $DOMAIN_TESTS";;
      -Dcompat-tests)    TESTS_SPECIFIED="Y";  CMD_LINE_PARAMS="$CMD_LINE_PARAMS $COMPAT_TESTS";;
      ## Don't run smoke tests if a single test is specified.
      -Dtest=*)          TESTS_SPECIFIED="Y";  CMD_LINE_PARAMS="$CMD_LINE_PARAMS $param";; # -DfailIfNoTests=false

      ## Collect Maven goals.
      clean)   MVN_GOALS="$MVN_GOALS$param ";;
      test)    MVN_GOALS="$MVN_GOALS$param ";;
      install) MVN_GOALS="$MVN_GOALS$param ";;
      deploy)  MVN_GOALS="$MVN_GOALS$param ";;
      site)    MVN_GOALS="$MVN_GOALS$param ";;
      ## Pass through all other params.
      *)      CMD_LINE_PARAMS="$CMD_LINE_PARAMS $param";;
    esac
    done

    #  Default goal if none specified.
    if [ -z "$MVN_GOALS" ]; then MVN_GOALS="install"; fi
    CMD_LINE_PARAMS="$MVN_GOALS $CMD_LINE_PARAMS";

    # If no tests specified, run smoke tests.
    if [[ $TESTS_SPECIFIED == "N" ]]; then
        CMD_LINE_PARAMS="$CMD_LINE_PARAMS $SMOKE_TESTS"
    fi
}

#
# Helper to complain.
#
die() {
    echo "${PROGNAME}: $*"
    exit 1
}

#
# Helper to complain.
#
warn() {
    echo "${PROGNAME}: $*"
}

#
# Helper to source a file if it exists.
#
maybe_source() {
    for file in $*; do
        if [ -f "$file" ]; then
            . $file
        fi
    done
}

#
# Main function.
#
main() {
    #  If there is a build config file. then source it.
    maybe_source "$DIRNAME/build.conf" "$HOME/.build.conf"

    #  Increase the maximum file descriptors if we can.
    if [ $cygwin = "false" ]; then
        MAX_FD_LIMIT=`ulimit -H -n`
        if [ $? -eq 0 ]; then
            if [ "$MAX_FD" = "maximum" -o "$MAX_FD" = "max" ]; then
                #  Use the system max.
                MAX_FD="$MAX_FD_LIMIT"
            fi

            ulimit -n $MAX_FD
            if [ $? -ne 0 ]; then
                warn "Could not set maximum file descriptor limit: $MAX_FD"
            fi
        else
            warn "Could not query system maximum file descriptor limit: $MAX_FD_LIMIT"
        fi
    fi

    MVN="$DIRNAME/mvnw"

    #  Change to the directory where the script lives
    #  so users are not forced to be in the same directory as build.xml.
    cd $DIRNAME/testsuite

    MVN_GOAL=$@
    if [ -z "$MVN_GOAL" ]; then
        MVN_GOAL="install"
    fi

    #  Process test directives before calling maven.
    process_test_directives $MVN_GOAL
    MVN_GOAL=$CMD_LINE_PARAMS

    # Export some stuff for maven.
    export MVN MAVEN_HOME MVN_OPTS MVN_GOAL

    # WFLY-8175 requires that we keep installing Maven under the tools directory
    # the current project, at least when mvnw is invoked from build and integration-tests
    # scripts
    MVN_ARGS="-Dmaven.user.home=$DIRNAME/tools $MVN_ARGS"

    echo "$MVN $MVN_ARGS $MVN_GOAL"

    #  Execute in debug mode, or simply execute.
    if [ "x$MVN_DEBUG" != "x" ]; then
        "${BASH_INTERPRETER}" -x $MVN $MVN_ARGS $MVN_GOAL
    else
        exec "${BASH_INTERPRETER}" $MVN $MVN_ARGS $MVN_GOAL
    fi

    cd $DIRNAME
}

##
## Bootstrap
##

main "$@"
