#! /bin/bash
USAGE="run_selenium_system_test.sh [--environment -e=env] [--browser -b=browser] [--driver -d=driver] [--with-java-flags -j=java_opts: optional] [--with-tags -t=tags: optional]

env      - The environment to run the tests on [ dev | qa | local ]
browser  - The browser to run the tests on [ chrome | zap-chrome | headless | browserstack ]
driver   - The path to the folder containing Selenium driver files
javaopts - comma separated list of any extra java arguments that are needed such as BrowserStack,
           no spaces should be given between these arguments
           e.g. -j="-Dbrowserstack.os=android,-Dbrowserstack.os_version="7.0",-Dbrowserstack.device=Samsung_Galaxy_S8,-Dbrowserstack.real_mobile=true,-Dbrowserstack.username=username,-Dbrowserstack.key=some-key"
tags     - comma separated list of tags with no spaces between tags. Only run tests with the given
           tags. Not specifying any tags will run all tests. Tags may be prefixed with an @ symbol
           "

# Join a space delimited string with a given delimiter, e.g. 'join_by , "A B C"' returns the string "A,B,C"
join_by() {
  local IFS="$1"
  shift
  echo "$*"
}

# Returns a single string which contains the requested tags. Each tag is prepended with a single '@' character
# and separated by a comma. Input is string array of tags
modify_tags() {
    # loop through the array and create a string which contains the tags each prepended with an '@' symbol
    local modifiedTags=""

    for i in $@
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

    echo "$(join_by ',' ${modifiedTags})"
}

usage(){
  echo -e "Expected usage:\n${USAGE}"
}


java_opts=()
extra_java_opts=""
tags=""

while [ "$1" != "" ]; do
 PARAM=$(echo $1 | awk -F= '{print $1}')
 VALUE=$(echo $1 | awk -F= '{for (i=2; i<NF; i++) printf $i "="; print $NF}')
 case $PARAM in
   --environment | -e)
   if [ "$VALUE" != 'dev' ] && [ "$VALUE" != 'qa' ] && [ "$VALUE" != 'local' ]&& [ "$VALUE" != 'esit' ]
   then
     usage
     exit 1
   fi
       java_opts+=("-Denvironment=$VALUE")
       ;;
   --browser | -b)
   if [ "$VALUE" != 'chrome' ] && [ "$VALUE" != 'firefox' ] && [ "$VALUE" != 'zap-chrome' ] && [ "$VALUE" != 'headless' ] && [ "$VALUE" != 'browserstack' ] && [ "$VALUE" != 'browserstack1' ] && [ "$VALUE" != 'browserstack2' ] && [ "$VALUE" != 'browserstack3' ] && [ "$VALUE" != 'browserstack4' ]
   then
        usage
        exit 1
   fi
        java_opts+=("-Dbrowser=$VALUE")
        ;;
   --driver | -d)
        java_opts+=("-Dwebdriver.driver=$VALUE")
        ;;
   --with-java-flags | -j)
        extra_java_opts="$VALUE"
        ;;
   --with-tags | -t)
     # convert the arguments into a string array
        IFS=',' read -r -a array <<< "$VALUE"
        tags=$(modify_tags ${array[@]})
       ;;
   *)
     echo "ERROR: unknown parameter \"$PARAM\""
     usage
     exit 1
     ;;

    esac
    shift
done

if [ $? != 0 ]
then
  exit 1
fi


process_java_options() {
  local options=("$@")

  # add extra java args to array
  local java_opts_string=$(join_by ',' "${options[@]}")

  if [ "$extra_java_opts" != "" ]
  then
    java_opts_string+=","$extra_java_opts
  fi

  IFS=',' read -r -a unquoted_args_array <<< "${java_opts_string[@]}"

  quoted_args_array=()
  for arg in "${unquoted_args_array[@]}"
  do
    quoted_args_array+=("\"${arg}\"")
  done

  echo $(join_by ',' "${quoted_args_array[@]}")
}

JAVA_OPTS=$(process_java_options "${java_opts[@]}")

if [ "${tags}" != "" ]
then
  JAVA_OPTS+=",\"-Dcucumber.options=--tags ${tags}\""
fi

echo "JAVA options are: $JAVA_OPTS"

# Doing `sbt -Doption1=value1 -Doption2="value2 with spaces" selenium:test` works on some
# environments but doesn't work with others - here we run sbt and add java system properties
# within the sbt session and then run the tests
sbt "; set javaOptions in SeleniumTest ++= Seq($JAVA_OPTS); selenium:test"