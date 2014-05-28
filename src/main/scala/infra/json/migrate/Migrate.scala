package infra.json.migrate

import play.api.libs.json._
import play.api.libs.functional.syntax._
import Reads._

/**
 * @author alari
 * @since 5/28/14
 */
object Migrate {
  val schemaField = "jsonSchemaVersion"

  type VersionedDocument = {def jsonSchemaVersion: Option[Int]}
  
  def schemaOf(json: JsValue) = (json \ schemaField).asOpt[Int].getOrElse(0)

  def migrate(mutate: Reads[_ <: JsValue]*): Reads[JsValue] = migrateFrom(0, mutate: _*)

  def migrateFrom(baseVersion: Int, mutate: Reads[_ <: JsValue]*): Reads[JsValue] = Reads[JsValue]{
    json =>
      mutate.drop(schemaOf(json) - baseVersion).foldLeft[JsResult[JsValue]](JsSuccess(json)){
        case (a, b) => a.flatMap(_.transform(b))
      }.flatMap(_.transform(putField(schemaField, baseVersion + mutate.length)))
  }

  def readMigrating[T <:VersionedDocument](mutate: Reads[_ <: JsValue]*)(implicit r: Reads[T]): Reads[T] = {
    readMigratingFrom(0, mutate: _*)
  }

  def readMigratingFrom[T <: VersionedDocument](baseVersion: Int, mutate: Reads[_ <: JsValue]*)(implicit r: Reads[T]): Reads[T] = {
    migrateFrom(baseVersion, mutate: _*) andThen r
  }

  def renameField(from: String, to: String): Reads[JsObject] = (
    (__ \ to).json.copyFrom((__ \ from).json.pick) ~
      (__ \ from).json.prune
    ).reduce

  def updateField[T : Reads](name: String, change: T => _ <: JsValue): Reads[JsObject] = {
    (__ \ name).json.update( __.read[T].map(change) )
  }

  def putField[T : Writes](name: String, value: T): Reads[JsObject] = {
    (
      __.json.pickBranch ~
      (__ \ name).json.put(implicitly[Writes[T]].writes(value))
      ).reduce
  }

  def removeField(name: String): Reads[JsObject] = {
    (__ \ name).json.prune
  }

  def moveBranch(from: JsPath, to: JsPath): Reads[JsObject] = ???

  def insideBranch(branch: JsPath, mutate: Reads[_ <: JsValue]*): Reads[JsObject] = ???

}
