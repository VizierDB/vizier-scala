MAVEN_BASE=info.vizierdb:vizier_2.12
VERSION=$(
  $(dirname $0)/mill show vizier.versionString 2> /dev/null | 
    jq -r |
    sed 's/ .*//'
)
ARTIFACT=$MAVEN_BASE:$VERSION

echo $ARTIFACT

coursier bootstrap $ARTIFACT\
  -r https://central.sonatype.com/ \
  -r https://repo.osgeo.org/repository/release/ \
  -r https://maven.mimirdb.org/ \
  -M info.vizierdb.Vizier \
  -o bin/vizier -f
