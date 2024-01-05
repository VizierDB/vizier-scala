/* -- copyright-header:v2 --
 * Copyright (C) 2017-2021 University at Buffalo,
 *                         New York University,
 *                         Illinois Institute of Technology.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -- copyright-header:end -- */
package info.vizierdb.catalog

import scalikejdbc._
import play.api.libs.json._
import info.vizierdb.types._
import info.vizierdb.serialized
import info.vizierdb.serializers._

case class ScriptRevision(
  scriptId: Identifier,
  version: Long,
  projectId: Identifier,
  branchId: Identifier,
  workflowId: Identifier,
  modules: Array[Byte]
)
{
  def describe(name: String): serialized.VizierScript =
    serialized.VizierScript(
      id = scriptId,
      version = version,
      name = name,
      projectId = projectId,
      branchId = branchId,
      workflowId = workflowId,
      modules = decodeModules
    )

  def decodeModules = 
    Json.parse(new String(modules)).as[Seq[serialized.VizierScriptModule]]
}

object ScriptRevision
  extends SQLSyntaxSupport[ScriptRevision]
{
  def apply(rs: WrappedResultSet): ScriptRevision = autoConstruct(rs, (ScriptRevision.syntax).resultName)
  
}

case class Script(
  id: Identifier,
  name: String,
  headVersion: Identifier
)
{
  def head(implicit session:DBSession): ScriptRevision = 
    withSQL {
      val sr = ScriptRevision.syntax
      select.from(ScriptRevision as sr)
            .where.eq(sr.scriptId, id)
              .and.eq(sr.version, headVersion)
    }.map { ScriptRevision(_) }.single.apply().get
  def revisions(implicit session:DBSession): Seq[ScriptRevision] = 
    withSQL {
      val sr = ScriptRevision.syntax
      select.from(ScriptRevision as sr)
            .where.eq(sr.scriptId, id)
            .orderBy(sr.version).asc
    }.map { ScriptRevision(_) }.list.apply()
  def modify(revised: ScriptRevision, name: String = null)(implicit session:DBSession): (Script, ScriptRevision) =
  {
    val cleanForInsert = 
      revised.copy(scriptId = id, version = headVersion + 1)

    withSQL {
      val sr = ScriptRevision.column
      insertInto(ScriptRevision)
        .namedValues(
          sr.scriptId -> cleanForInsert.scriptId,
          sr.version -> cleanForInsert.version,
          sr.projectId -> cleanForInsert.projectId,
          sr.branchId -> cleanForInsert.branchId,
          sr.workflowId -> cleanForInsert.workflowId,
          sr.modules -> cleanForInsert.modules,
        )
    }.update.apply()

    withSQL {
      val s = Script.column

      if(name == null){
        update(Script)
          .set(
            s.headVersion -> cleanForInsert.version
          )
          .where.eq(s.id, id)
      } else {
        update(Script)
          .set(
            s.name -> name,
            s.headVersion -> cleanForInsert.version
          )
          .where.eq(s.id, id)
      }
    }.update.apply()

    return (copy(headVersion = cleanForInsert.version), cleanForInsert)
  }
}

object Script
  extends SQLSyntaxSupport[Script]
{
  def apply(rs: WrappedResultSet): Script = autoConstruct(rs, (Script.syntax).resultName)

  def make(name: String)(implicit session: DBSession): Script =
  {
    Script(
      id = 
        withSQL {
          val s = Script.column
          insertInto(Script)
            .namedValues(
              s.name -> name,
              s.headVersion -> -1
            )
        }.updateAndReturnGeneratedKey.apply(),
      name = name,
      headVersion = -1
    )
  }

  def get(target: Identifier)(implicit session:DBSession): Script = 
    getOption(target).get
  def getOption(target: Identifier)(implicit session:DBSession): Option[Script] = 
  {
    withSQL { 
      val s = Script.syntax 
      select
        .from(Script as s)
        .where.eq(s.id, target) 
    }.map { apply(_) }.single.apply()
  }

  def getByName(name: String)(implicit session: DBSession): Seq[Script] =
  {
    withSQL {
      val s = Script.syntax 
      select
        .from(Script as s)
        .where.eq(s.name, name) 
    }.map { apply(_) }.list.apply()
  }

  def all(implicit session: DBSession): Seq[Script] =
  {
    withSQL {
      val s = Script.syntax 
      select
        .from(Script as s)
    }.map { apply(_) }.list.apply()
  }

  def getHead(target: Identifier)(implicit session: DBSession): (Script, ScriptRevision) =
    getHeadOption(target).get

  def getHeadOption(target: Identifier)(implicit session: DBSession): Option[(Script, ScriptRevision)] =
  {
    withSQL {
      val s = Script.syntax
      val sr = ScriptRevision.syntax
      select
        .from(Script as s)
        .join(ScriptRevision as sr)
        .where.eq(s.id, target)
          .and.eq(s.id, sr.scriptId)
          .and.eq(s.headVersion, sr.version)
    }.map { r => 
      ( Script(r), ScriptRevision(r) )
    }.single.apply()
  }

  def getHeadByName(name: String)(implicit session: DBSession): (Script, ScriptRevision) =
    getHeadByNameOption(name).get

  def getHeadByNameOption(name: String)(implicit session: DBSession): Option[(Script, ScriptRevision)] =
  {
    withSQL {
      val s = Script.syntax
      val sr = ScriptRevision.syntax
      select
        .from(Script as s)
        .join(ScriptRevision as sr)
        .where.eq(s.name, name)
          .and.eq(s.id, sr.scriptId)
          .and.eq(s.headVersion, sr.version)
    }.map { r => 
      ( Script(r), ScriptRevision(r) )
    }.single.apply()
  }
}