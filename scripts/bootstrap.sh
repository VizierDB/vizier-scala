MAVEN_BASE=info.vizierdb:vizier_2.12
VERSION=$(
  mill show vizier.versionString 2> /dev/null | 
    jq -r |
    sed 's/ .*//'
)
ARTIFACT=$MAVEN_BASE:$VERSION

echo $ARTIFACT

coursier bootstrap $ARTIFACT\
  -r https://s01.oss.sonatype.org/content/groups/public/ \
  -r https://repo.osgeo.org/repository/release/ \
  -r https://maven.mimirdb.org/ \
  -M info.vizierdb.Vizier \
  -o bin/vizier -f