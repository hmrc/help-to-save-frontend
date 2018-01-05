#!/bin/bash

USAGE="run_selenium_system_test.sh [env] [browser] [driver] [tags: optional]

env     - The environment to run the tests on [ dev | qa | local ]
browser - The browser to run the tests on [ chrome | zap-chrome | headless ]
driver  - The path to the folder containing Selenium driver files
tags    - Space separated list of tags. Only run tests with the given
          tags. Not specifying any tags will run all tests.
          Tags with an '@' symbol."

if [ "$1" != 'dev' ] && [ "$1" != 'qa' ] && [ "$1" != 'local' ] || ([ "$2" != 'chrome' ] && [ "$2" != 'firefox' ] && [ "$2" != 'zap-chrome' ] && [ "$2" != 'headless' ] && [ "$2" != 'browserstack' ])
then
  echo -e "Expected usage:\n${USAGE}"
  exit 1
fi

# Returns a single string which contains the requested tags. Each tag is prepended with a single '@' character
# and separated by a comma
get_tags() {
 # convert the arguments into a string array
 local rawTags=("$@")
 # loop through the array and create a string which contains the tags each prepended with an '@' symbol
 local modifiedTags=""

 for i in ${rawTags[@]}
   do
     # if the tag already begins with an '@' symbol return the tag as
     # is, otherwise prepend an '@' symbol
     if [[ ${i} == @* ]]
     then
       modifiedTags+="${i} "
     else
       modifiedTags+="@${i} "
     fi
 done

 echo "$(join_by , ${modifiedTags})"
}

# Return the necessary java options as a single string. Each java option is surrounded by double quotes and
# separated by a comma

get_java_opts() {
  local auth_host
  local tags
  local driverLocation

  # get the driver location
  if [ ! -z $3 ]
  then
    driverLocation=$3
  fi

  # get the tags
  if [ ! -z $4 ]
  then
   tags=$(get_tags ${@:4})
  fi

  # create an array with the java options
  local opts=(-Denvironment=$1 -Dbrowser=$2)

  if [ ! -z "${driverLocation}" ]
  then
    opts+=("-Dwebdriver.$2.driver=${driverLocation}")
  fi

  if [ ! -z "${tags}" ]
  then
    opts+=("-Dcucumber.options=--tags ${tags}")
  fi

  # wrap each element of the above array with double quotes
  local quoted_opts=()
  for i in "${opts[@]}"
    do
      quoted_opts+=("\"${i}\"")
  done

  # now convert the string array to a single string with each element separated by a comma
  echo "$(IFS=$','; echo "${quoted_opts[*]}" )"
}

# Join a space delimited string with a given delimiter, e.g. 'join_by , "A B C"' returns the string "A,B,C"
join_by() {
  local IFS="$1"
  shift
  echo "$*"
}

# Doing `sbt -Doption1=value1 -Doption2="value2 with spaces" selenium:test` works on some
# environments but doesn't work with others - here we run sbt and add java system properties
# within the sbt session and then run the tests
JAVA_OPTS=$(get_java_opts $1 $2 $3 ${@:4})
sbt "; set javaOptions in SeleniumTest ++= Seq($JAVA_OPTS); selenium:test"
