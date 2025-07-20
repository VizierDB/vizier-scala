#!/bin/bash

# This script expects credentials stored in `pass` 
# (https://www.passwordstore.org/).  Create a `pass` archive for
# sonatype.org and make sure it includes two lines: 
# ```
# token_user: ...
# token_pass: ...
# ```
# The values from these lines can be populated from the user token
# which can be obtained from your user profile (instructions here):
# https://central.sonatype.org/publish/generate-token/


cd `dirname $0`/..

# Fix Copyrights
python3 scripts/fix_copyrights.py

export SONATYPE_USERNAME="$(
  pass sonatype.org |
    grep '^token_user:' |
    sed 's/token_user: //' |
    head -n 1
)"

export SONATYPE_PASSWORD="$(
  pass sonatype.org |
    grep '^token_pass:' |
    sed 's/token_pass: //' |
    head -n 1
)"

mill mill.scalalib.PublishModule/publishAll \
      vizier.publishArtifacts \
      --release true\
      --sonatypeUri https://s01.oss.sonatype.org/service/local \
      --sonatypeSnapshotUri https://s01.oss.sonatype.org/content/repositories/snapshots


