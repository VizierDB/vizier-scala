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

export MILL_SONATYPE_USERNAME="$(
  pass sonatype.com |
    grep '^token_user:' |
    sed 's/token_user: //' |
    head -n 1
)"

export MILL_SONATYPE_PASSWORD="$(
  pass sonatype.com |
    grep '^token_pass:' |
    sed 's/token_pass: //' |
    head -n 1
)"

export MILL_PGP_PASSPHRASE="$(
  pass sonatype.com |
    grep '^vizier_gpg:' |
    sed 's/vizier_gpg: //' |
    head -n 1
)"

export VIZIER_GPG_KEY_ID=99859F92672FA05BE0CFE673E22B405F39030C36

export MILL_PGP_SECRET_BASE64="$(
  gpg --batch --yes \
      --passphrase-file <(echo $MILL_PGP_PASSPHRASE) \
      --pinentry-mode loopback \
      --export-secret-key -a $VIZIER_GPG_KEY_ID | base64
)"

$(dirname $0)/mill vizier.publishSonatypeCentral --release true

#mill mill.javalib.SonatypeCentralPublishModule/ \
      #--publishArtifacts vizier.publishArtifacts \
      #--release true
      #--sonatypeUri https://central.sonatype.com/service/local \
      #--sonatypeSnapshotUri https://central.sonatype.com/content/repositories/snapshots


