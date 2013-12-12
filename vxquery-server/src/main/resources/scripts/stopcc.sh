#!/bin/bash

hostname

USER=$1

#Kill process
#Kill process
PID=`ps -ef|grep ${USER}|grep java|grep 'Dapp.name=pregelixcc'|awk '{print $2}'`

if [ "$PID" == "" ]; then
    PID=`ps -ef|grep ${USER}|grep java|grep 'hyracks'|awk '{print $2}'`
fi

if [ "$PID" == "" ]; then
    USERID=`id | sed 's/^uid=//;s/(.*$//'`
    PID=`ps -ef|grep ${USERID}|grep java|grep 'Dapp.name=pregelixcc'|awk '{print $2}'`
fi

echo $PID
kill -9 $PID

