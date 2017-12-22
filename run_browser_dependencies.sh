#!/bin/sh

export JAVA_HOME=/usr/lib/jvm/jdk1.7.0_51
echo "BrowserStackLocal instances:"
pidof BrowserStackLocal
cd /tmp
if pidof BrowserStackLocal; then
    echo "BrowserStackLocal running already"
else
    if [ ! -e BrowserStackLocal ]; then
        wget https://www.browserstack.com/browserstack-local/BrowserStackLocal-linux-x64.zip
        unzip BrowserStackLocal-linux-x64.zip
    fi
    ./BrowserStackLocal --key BBR9aBEmy4rjSmyqcCUj &
fi