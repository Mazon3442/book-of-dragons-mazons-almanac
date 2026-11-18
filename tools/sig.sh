#!/bin/bash
# usage: tools/sig.sh fully.qualified.Class ...   (prints declared members of classes inside libs/)
cd "$(dirname "$0")/../libs"
for c in "$@"; do echo "=== $c"; javap -p -cp "bookofdragons-1.4.1-1.21.1.jar;faunaengine-1.2-1.21.1-neoforge.jar" "$c" 2>&1 \
 | grep -vE 'lambda|VALUES|static \{|^Compiled|\$values' \
 | sed 's/net\.magister\.liberanimalium\.//g;s/net\.magister\.bookofdragons\.//g;s/net\.minecraft\.//g;s/java\.lang\.//g;s/java\.util\.//g'; done
