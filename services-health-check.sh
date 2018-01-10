#!/usr/bin/env bash

echo "*********** Running service health check ***********"

SM_STATUS_OUTPUT=`sm -s`
echo $SM_STATUS_OUTPUT
SERVICES=(HELP_TO_SAVE_FRONTEND, HELP_TO_SAVE, HELP_TO_SAVE_STUB, ASSETS_FRONTEND, AUTH, AUTH_LOGIN_API, AUTH_LOGIN_STUB, AUTH_DES_STUB, AUTHENTICATOR, CITIZEN_DETAILS, CITIZEN_FRONTEND, DATASTREAM, GG_STUBS, GG, GG_AUTHENTICATION, IDENTITY_VERIFICATION, IDENTITY_VERIFICATION_FRONTEND, IDENTITY_VERIFICATION_STUB, KEYSTORE, USER_DETAILS, CA_FRONTEND, EMAIL_VERIFICATION, EMAIL_VERIFICATION_FRONTEND, EMAIL_VERIFICATION_STUBS, EMAIL, HMRC_EMAIL_RENDERER, MAILGUN_STUB, CONTACT_FRONTEND, HMRCDESKPRO)
for SERVICE in "${SERVICES[@]}";
do
  echo $SM_STATUS_OUTPUT | grep -q $SERVICE
  if [ $? != 0 ]; then
    echo "$SERVICE failed status check"
    echo "*********** HTS_ALL failed status check ***********"
    exit 1
  fi
done

echo "*********** HTS_ALL passed status check ***********"