package infra.json.migrate

import play.api.libs.json._
import play.api.libs.functional.syntax._
import Reads._

/**
 * @author alari
 * @since 5/28/14
 *
 *        It's a helper to help you keep your jsons consistent
 */
object Migrate {
  /**
   * A field where json schema number is contained
   */
  val schemaVersionField = "jsonSchemaVersion"

  /**
   * You should store json schema version in json
   */
  type VersionedDocument = {def jsonSchemaVersion: Option[Int]}

  /**
   * Applies a number of mutations to json
   * @param json source document
   * @param mutate migration reads
   * @return
   */
  def mutatesTransform(json: JsValue, mutate: Seq[Reads[_ <: JsValue]]): JsResult[JsValue] =
    mutate.foldLeft[JsResult[JsValue]](JsSuccess(json)) {
      case (a, b) => a.flatMap(_.transform(b))
    }

  /**
   * Converts a number of mutations to a Reads[JsValue]
   * @param mutate migration reads
   * @return
   */
  def mutatesReads(mutate: Seq[Reads[_ <: JsValue]]): Reads[JsValue] = Reads[JsValue] {
    json => mutatesTransform(json, mutate)
  }

  /**
   * Schema version number
   * @param json source document
   * @return
   */
  def schemaVersion(json: JsValue) = (json \ schemaVersionField).asOpt[Int].getOrElse(0)

  /**
   * Creates a single Reads for migrations
   * @param mutate migration reads
   * @return
   */
  def migrate(mutate: Reads[_ <: JsValue]*): Reads[JsValue] = migrate(0)(mutate: _*)

  /**
   * Creates a single Reads for migrations, given that you've dropped baseVersion number of migrations
   * @param baseVersion base version to count migrations from
   * @param onInit function to be called once with number of current schema. It's launched in current thread
   * @param mutate migration reads
   * @return
   */
  def migrate(baseVersion: Int = 0, onInit: (Int)=>Unit = {_:Int => ()})(mutate: Reads[_ <: JsValue]*): Reads[JsValue] = {
    onInit(baseVersion + mutate.size)
    Reads[JsValue] {
      json =>
        mutatesTransform(
          json,
          mutate.drop(schemaVersion(json) - baseVersion)
        ).flatMap(_.transform(putField(schemaVersionField, baseVersion + mutate.length)))
    }
  }

  /**
   * A composed Read for your domain class, given that you've dropped baseVersion number of migrations
   * @param reads Reads for the last version of your data
   * @param baseVersion base version to count migrations from
   * @param onInit function to be called once with number of current schema. It's launched in current thread
   * @param mutate migration reads
   * @tparam T type of your domain class. Must contain schemaVersionField
   * @return
   */
  def readMigrating[T <: VersionedDocument](reads: Reads[T], baseVersion: Int = 0, onInit: (Int)=>Unit = {_:Int => ()})(mutate: Reads[_ <: JsValue]*): Reads[T] = {
    migrate(baseVersion, onInit)(mutate: _*) andThen reads
  }

  /**
   * A composed Format for your domain class, given that you've dropped baseVersion number of migrations
   * @param format Format for the last version of your data
   * @param baseVersion base version to count migrations from
   * @param onInit function to be called once with number of current schema. It's launched in current thread
   * @param mutate migration reads
   * @tparam T type of your domain class. Must contain schemaVersionField
   * @return
   */
  def formatMigrating[T <: VersionedDocument](format: Format[T], baseVersion: Int = 0, onInit: (Int)=>Unit = {_:Int => ()})(mutate: Reads[_ <: JsValue]*): Format[T] = {
    Format[T](readMigrating(format, baseVersion, onInit)(mutate: _*), writeMigrating(baseVersion + mutate.size, format))
  }

  /**
   * Write latest version by default -- for new domains
   * @param version current version
   * @param writes writes without custom schema version logic
   * @tparam T versioned domain
   * @return
   */
  def writeMigrating[T <: VersionedDocument](version: Int, writes: Writes[T]): Writes[T] = Writes[T]{o =>
    val json = writes.writes(o)
    (json \ schemaVersionField).asOpt[Int] match {
      case Some(_) => json
      case None =>
        json.asInstanceOf[JsObject] ++ Json.obj(schemaVersionField -> version)
    }
  }

  /**
   * Simple field renaming mutator
   * @param from from
   * @param to to
   * @return
   */
  def renameField(from: String, to: String): Reads[JsObject] =
    moveBranch(__ \ from, __ \ to)

  /**
   * Field updater. MERGES old field's data with the new one -- if it's an JsObject
   * @param name field name
   * @param change mapper function
   * @tparam T current field type
   * @return
   */
  def updateField[T: Reads](name: String)(change: T => _ <: JsValue): Reads[JsObject] = {
    updateField(__ \ name)(change)
  }

  /**
   * Field updater. MERGES old field's data with the new one -- if it's an JsObject
   * @param path leaf path
   * @param change mapper function
   * @tparam T current field type
   * @return
   */
  def updateField[T: Reads](path: JsPath)(change: T => _ <: JsValue): Reads[JsObject] = {
    path.json.update(__.read[T].map(change))
  }

  /**
   * Replaces field value with the mapper output.
   * @param name field name
   * @param change mapper function
   * @tparam T current type of the field
   * @return
   */
  def mapField[T: Reads](name: String)(change: T => _ <: JsValue): Reads[JsObject] = {
    mapField(__ \ name)(change)
  }

  /**
   * Replaces field value with the mapper output.
   * @param path field path
   * @param change mapper function
   * @tparam T current type of the field
   * @return
   */
  def mapField[T: Reads](path: JsPath)(change: T => _ <: JsValue): Reads[JsObject] = {
    path.json.pickBranch(__.read[T].map(change))
  }

  /**
   * Puts some data into a field
   * @param name field name
   * @param value new value
   * @tparam T value type to write
   * @return
   */
  def putField[T: Writes](name: String, value: T): Reads[JsObject] = {
    (
      __.json.pickBranch ~
        (__ \ name).json.put(implicitly[Writes[T]].writes(value))
      ).reduce
  }

  /**
   * Puts some data into a field
   * @param path field path
   * @param value new value
   * @tparam T value type to write
   * @return
   */
  def putField[T: Writes](path: JsPath, value: T): Reads[JsObject] = {
    (
      __.json.pickBranch ~
        path.json.put(implicitly[Writes[T]].writes(value))
      ).reduce
  }

  /**
   * Removes a field by name
   * @param name field name
   * @return
   */
  def removeField(name: String): Reads[JsObject] =
    removeField(__ \ name)

  /**
   * Removes a field by path
   * @param path field path
   * @return
   */
  def removeField(path: JsPath): Reads[JsObject] =
    path.json.prune

  /**
   * Moves a branch from one path to another. If some of the old ancestors are empty, they will be kept
   * @param from from path
   * @param to to path
   * @return
   */
  def moveBranch(from: JsPath, to: JsPath): Reads[JsObject] = (
    to.json.copyFrom(from.json.pick) ~
      from.json.prune
    ).reduce

  /**
   * Allows to execute some mutations in a branch scope.
   * Use with care! There could be bugs.
   * Don't try to nest insideBranch inside insideBranch without deep testing.
   * @param branch branch path
   * @param mutate migration reads
   * @return
   */
  def insideBranch(branch: JsPath)(mutate: Reads[_ <: JsValue]*): Reads[JsObject] =
    branch.json.pickBranch(
      mutatesReads(mutate)
    )

}
