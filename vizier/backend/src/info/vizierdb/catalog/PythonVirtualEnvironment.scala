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

case class PythonVirtualEnvironment(
  name: String,
  val version: String,
  val packages: Seq[PythonPackage]
) extends LazyLogging
{
  def serialize = 
    serialized.PythonEnvironment(
      version,
      packages
    )

  def dir: File = 
    new File(Vizier.config.pythonVenvDirFile, name)

  def bin: File =
    new File(dir, "bin")

  def exists = dir.exists()

  object Environment
    extends PythonEnvironment
    with LazyLogging
  {
    def python: File =
      new File(bin, "python3")

  }

  def init(overwrite: Boolean = false, fallBackToSystemPython: Boolean = false): Unit =
  {
    // we need a python binary of the right version to bootstrap the venv
    val bootstrapBinary = 
      if(SystemPython.fullVersion == version){
        SystemPython.python.toString()
      } else if(Pyenv.exists && (Pyenv.installed contains version)) {
        // if the system python is not right, try pyenv
        Pyenv.python(version)
      } else if(fallBackToSystemPython) {
        logger.warn(s"Python version '$version' is not installed; Falling back to system python")
        SystemPython.python.toString()
      } else {
        throw new VizierException(s"Trying to create virtual environment for non-installed python version '$version'")
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

    packages.foreach { pkg => 
      logger.info(s"Installing into venv $name: $pkg")
      Environment.install(pkg.name, pkg.version) 
    }
    logger.info(s"Finished setting up venv $name")
  }

  def save()(implicit session: DBSession): PythonVirtualEnvironment =
  {
    val updatedEnv = copy(packages = 
      Environment.packages.map { serialized.PythonPackage(_) }
    )
    withSQL { 
      val a = PythonVirtualEnvironment.column
      update(PythonVirtualEnvironment)
        .set(a.packages -> updatedEnv.packages)
        .where.eq(a.name, name)
    }.update.apply()
    return updatedEnv
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

  def get(name: String)(implicit session: DBSession): PythonVirtualEnvironment =
    getOption(name).get
  def getOption(name: String)(implicit session: DBSession): Option[PythonVirtualEnvironment] =
    withSQL { 
      val b = PythonVirtualEnvironment.syntax 
      select
        .from(PythonVirtualEnvironment as b)
        .where.eq(b.name, name)
    }.map { apply(_) }.single.apply()

  def all(implicit session: DBSession): Seq[PythonVirtualEnvironment] =
    withSQL { 
      val b = PythonVirtualEnvironment.syntax 
      select
        .from(PythonVirtualEnvironment as b)
    }.map { apply(_) }.list.apply()

  def list(implicit session: DBSession): Seq[String] =
    withSQL { 
      val b = PythonVirtualEnvironment.syntax 
      select(b.name)
        .from(PythonVirtualEnvironment as b)
    }.map { _.get[String](1) }.list.apply()
    

  /**
   * Creates an <b>uninitialized</b> [[PythonVirtualEnvironment]]
   * 
   * You <b>must</b> call [[PythonVirtualEnvironment.init]]
   */
  def make(name: String, version: String)(implicit session: DBSession): PythonVirtualEnvironment =
  {
    withSQL {
      val a = PythonVirtualEnvironment.column
      insertInto(PythonVirtualEnvironment)
        .namedValues(
          a.name -> name,
          a.version -> version,
          a.packages -> Seq[PythonPackage](),
        )
    }.update.apply()
    return PythonVirtualEnvironment(
      name, version, Seq.empty
    )
  }
}