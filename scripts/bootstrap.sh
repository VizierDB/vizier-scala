cd `dirname $0`/..
VERSION=$(mill show vizier.version | jq -r)
ARTIFACT=info.vizierdb:vizier_2.12:${VERSION}

coursier bootstrap $ARTIFACT\
  -r https://s01.oss.sonatype.org/content/groups/public/ \
  -r https://repo.osgeo.org/repository/release/ \
  -r https://maven.mimirdb.org/ \
  -M info.vizierdb.Vizier \
  -o bin/vizier -f