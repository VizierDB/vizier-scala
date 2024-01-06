#!/bin/bash

cd `dirname $0`/..

# Fix Copyrights
python3 scripts/fix_copyrights.py

export SONATYPE_USERNAME="$(
  pass sonatype.org |
    grep '^login:' |
    sed 's/login: //'
)"

export SONATYPE_PASSWORD="$(
  pass sonatype.org |
    head -n 1
)"


mill mill.scalalib.PublishModule/publishAll \
      vizier.publishArtifacts \
      --release true\
      --sonatypeUri https://s01.oss.sonatype.org/service/local \
      --sonatypeSnapshotUri https://s01.oss.sonatype.org/content/repositories/snapshots


