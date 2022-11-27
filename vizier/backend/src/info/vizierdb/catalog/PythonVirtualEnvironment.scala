package info.vizierdb.catalog

import scalikejdbc._
import info.vizierdb.serialized.PythonPackage
import java.io.File
import info.vizierdb.commands.python.SystemPython
import info.vizierdb.commands.python.Pyenv
import info.vizierdb.commands.python.PythonEnvironment
import com.typesafe.scalalogging.LazyLogging
import info.vizierdb.Vizier
import info.vizierdb.VizierException
import scala.sys.process._
import info.vizierdb.catalog.binders._
import info.vizierdb.serialized
import info.vizierdb.util.FileUtils
import info.vizierdb.commands.python.PythonProcess
import info.vizierdb.types._

case class PythonVirtualEnvironment(
  id: Identifier,
  name: String,
  pythonVersion: String,
  activeRevision: Identifier,
) extends LazyLogging
{
  def serialize(implicit session:DBSession) = 
    serialized.PythonEnvironmentDescriptor(
      name = name,
      id = id,
      pythonVersion = pythonVersion,
      revision = activeRevision,
      packages = PythonVirtualEnvironmentRevision.get(id, activeRevision).packages
    )

  def summarize =
    serialized.PythonEnvironmentSummary(
      name = name,
      id = id,
      pythonVersion = pythonVersion,
      revision = activeRevision
    )

  def dir: File = 
    new File(Vizier.config.pythonVenvDirFile, s"venv_$id")

  def bin: File =
    new File(dir, "bin")

  def exists = dir.exists()

  def revision(implicit session:DBSession) =
    PythonVirtualEnvironmentRevision.get(id, activeRevision)

  object Environment
    extends PythonEnvironment
    with LazyLogging
  {
    def python: File =
      new File(bin, "python3")

    override lazy val fullVersion = pythonVersion
  }

  def init(overwrite: Boolean = false, fallBackToSystemPython: Boolean = false): Unit =
  {
    // we need a python binary of the right version to bootstrap the venv
    val bootstrapBinary = 
      if(SystemPython.fullVersion == pythonVersion){
        SystemPython.python.toString()
      } else if(Pyenv.exists && (Pyenv.installed contains pythonVersion)) {
        // if the system python is not right, try pyenv
        Pyenv.python(pythonVersion)
      } else if(fallBackToSystemPython) {
        logger.warn(s"Python version '$pythonVersion' is not installed; Falling back to system python")
        SystemPython.python.toString()
      } else {
        throw new VizierException(s"Trying to create virtual environment for non-installed python version '$pythonVersion'")
      }

    logger.info(s"Bootstrapping venv $name with $bootstrapBinary")

    var args = 
      Seq(
        "-m", "venv",
        "--upgrade-deps",
        "--copies",
      )
    if(overwrite){ args = args :+ "--clear" }

    args = args :+ dir.toString

    {
      val err =
        Process(bootstrapBinary, args).run(
          ProcessLogger(
            logger.info(_),
            logger.warn(_)
          )
        ).exitValue()
      if(err != 0){
        throw new VizierException("Error setting up venv")
      }
    }

    logger.info(s"Set up venv $name; Installing initial packages")

    {
      val err = 
        Process(
          Environment.python.toString,
          Seq(
            "-m", "pip",
            "install"
          ) ++ PythonProcess.REQUIRED_PACKAGES.map { _._2 }
        ).run(
          ProcessLogger(
            logger.info(_),
            logger.warn(_)
          )
        ).exitValue()
      if(err != 0){
        throw new VizierException("Error installing required packages")
      }
    }

    logger.info(s"Set up venv $name; Installing user-requested packages")

    logger.info(s"Finished setting up venv $name")
  }

  def save()(implicit session: DBSession): PythonVirtualEnvironment =
  {
    val packages = 
      Environment.packages.map { serialized.PythonPackage(_) }
    val revisionId = withSQL { 
      val a = PythonVirtualEnvironmentRevision.column
      insertInto(PythonVirtualEnvironmentRevision)
        .namedValues(
          a.envId -> id,
          a.packages -> packages)
    }.updateAndReturnGeneratedKey.apply()
    withSQL { 
      val a = PythonVirtualEnvironment.column
      update(PythonVirtualEnvironment)
        .set(a.activeRevision -> revisionId)
        .where.eq(a.name, name)
    }.update.apply()
    return copy(activeRevision = revisionId)
  }

  /**
   * Delete the installed venv; Also call [[drop]]() to delete the instance reference.
   */
  def delete(): Unit =
  {
    FileUtils.recursiveDelete(dir)
  }

  /**
   * Drop the installed venv; Must call [[delete]]() first
   */
  def drop()(implicit session: DBSession): Unit =
  {
    assert(!exists)
    withSQL { 
      val a = PythonVirtualEnvironment.column
      deleteFrom(PythonVirtualEnvironment)
        .where.eq(a.name, name)
    }.update.apply()  
  }

}

/**
 * Manage and manipulate python virtual environments
 */
object PythonVirtualEnvironment
  extends SQLSyntaxSupport[PythonVirtualEnvironment]
{

  def apply(rs: WrappedResultSet): PythonVirtualEnvironment = autoConstruct(rs, (PythonVirtualEnvironment.syntax).resultName)
  override def columns = Schema.columns(table)

  def getByName(name: String)(implicit session: DBSession): PythonVirtualEnvironment =
    getByNameOption(name).get
  def getByNameOption(name: String)(implicit session: DBSession): Option[PythonVirtualEnvironment] =
    withSQL { 
      val b = PythonVirtualEnvironment.syntax 
      select
        .from(PythonVirtualEnvironment as b)
        .where.eq(b.name, name)
    }.map { apply(_) }.single.apply()
  def getById(id: Identifier)(implicit session: DBSession): PythonVirtualEnvironment =
    getByIdOption(id).get
  def getByIdOption(id: Identifier)(implicit session: DBSession): Option[PythonVirtualEnvironment] =
    withSQL { 
      val b = PythonVirtualEnvironment.syntax 
      select
        .from(PythonVirtualEnvironment as b)
        .where.eq(b.id, id)
    }.map { apply(_) }.single.apply()

  def exists(name: String)(implicit session: DBSession): Boolean =
    withSQL { 
      val b = PythonVirtualEnvironment.syntax 
      select
        .from(PythonVirtualEnvironment as b)
        .where.eq(b.name, name)
    }.map { _ => true }.single.apply().getOrElse { false }    

  def all(implicit session: DBSession): Seq[PythonVirtualEnvironment] =
    withSQL { 
      val b = PythonVirtualEnvironment.syntax 
      select
        .from(PythonVirtualEnvironment as b)
    }.map { apply(_) }.list.apply()

  def list(implicit session: DBSession): Seq[serialized.PythonEnvironmentSummary] =
    withSQL { 
      val b = PythonVirtualEnvironment.syntax 
      select(b.id, b.name, b.activeRevision, b.pythonVersion)
        .from(PythonVirtualEnvironment as b)
    }.map { r => 
        serialized.PythonEnvironmentSummary(
          id = r.get[Identifier](1),
          name = r.get[String](2),
          revision = r.get[Identifier](3),
          pythonVersion = r.get[String](4)
        ) 
    }.list.apply()
    

  /**
   * Creates an <b>uninitialized</b> [[PythonVirtualEnvironment]]
   * 
   * You <b>must</b> call [[PythonVirtualEnvironment.init]]
   */
  def make(name: String, pythonVersion: String)(implicit session: DBSession): PythonVirtualEnvironment =
  {
    val id = withSQL {
      val a = PythonVirtualEnvironment.column
      insertInto(PythonVirtualEnvironment)
        .namedValues(
          a.name -> name,
          a.pythonVersion -> pythonVersion,
          a.activeRevision -> -1
        )
    }.updateAndReturnGeneratedKey.apply()
    return PythonVirtualEnvironment(
      id, name, pythonVersion, -1
    )
  }
}