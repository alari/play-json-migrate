package infra.json.migrate

import org.specs2.mutable.Specification

import play.api.libs.json._
import Reads._
import Migrate._

/**
 * @author alari
 * @since 5/28/14
 */
case class TestCase(
  test: String,
  other: Seq[Long],
jsonSchemaVersion: Option[Int]
                     )

class MigrateSpec extends Specification{
  implicit val testReads = Json.reads[TestCase]
  val correctJson = Json.obj("test" -> "123", "other" -> Json.arr(2l, 4l, 6l))
  val oldJson = Json.obj("other" -> "123", "some" -> Json.arr(1, 2, 3), "any" -> "rest")

  "migrator" should {
    "rename a field" in {
      migrate(
        renameField("other", "test")
      ).reads(oldJson) must_== JsSuccess(Json.obj("test" -> "123", "some" -> Json.arr(1, 2, 3), "any" -> "rest", schemaField -> 1))

      migrate(
        renameField("other", "test"),
        renameField("some", "other")
      ).reads(oldJson) must_== JsSuccess(Json.obj("test" -> "123", "other" -> Json.arr(1, 2, 3), "any" -> "rest", schemaField -> 2))
    }

    "update a single field" in {
      migrate(
        updateField("test", (s: JsString) => JsString("nothing-more")) orElse putField("test", "nothing-more")
      ).reads(oldJson) must_== JsSuccess(Json.obj("other" -> "123", "some" -> Json.arr(1, 2, 3), "any" -> "rest", "test" -> "nothing-more", schemaField -> 1))

      migrate(
        renameField("other", "test"),
        updateField("test", (s: JsString) => JsString(s.value.reverse))
      ).reads(oldJson) must_== JsSuccess(Json.obj("test" -> "321", "some" -> Json.arr(1, 2, 3), "any" -> "rest", schemaField -> 2), __ \ "test")
    }

    "set a field" in {
      migrate(
        putField("other", 555)
      ).reads(oldJson) must_== JsSuccess(Json.obj("other" -> 555, "some" -> Json.arr(1, 2, 3), "any" -> "rest", schemaField -> 1))

      migrate(
        putField("other", 555),
        putField("new", "xkcd")

      ).reads(oldJson) must_== JsSuccess(Json.obj("other" -> 555, "some" -> Json.arr(1, 2, 3), "any" -> "rest", "new" -> "xkcd", schemaField -> 2))
    }

    "remove a field" in {
      migrate(
        removeField("other"),
        renameField("some", "funny"),
        removeField("funny")
      ).reads(oldJson) must_== JsSuccess(Json.obj("any" -> "rest", schemaField -> 3), __ \ "other" \ "funny")
    }

    "transform and read" in {
      val m = migrate(
        renameField("other", "test"),
        renameField("some", "other"),
      removeField("any"),
      updateField("other", (s: JsArray) => JsArray(s.value.map{
        case JsNumber(n) => JsNumber(n * 2)
      }))
      )

      m.reads(oldJson) must_== JsSuccess(correctJson ++ Json.obj(schemaField -> 4), __ \ "any" \ "other")

      val m2 = readMigrating[TestCase](
        renameField("other", "test"),
        renameField("some", "other"),
        removeField("any"),
        updateField("other", (s: JsArray) => JsArray(s.value.map{
          case JsNumber(n) => JsNumber(n * 2)
        }))
      )

      m2.reads(oldJson) must_== testReads.reads(correctJson).map(_.copy(jsonSchemaVersion = Some(4))).asInstanceOf[JsSuccess[TestCase]].copy(path = __ \ "any" \ "other")
    }

    "respect base version" in {
      migrateFrom(2,
      removeField("a"),
      removeField("b"),
      removeField("c")
      ).reads(Json.obj("a" -> 1, "b" -> 2, "c" -> 3)) must_== JsSuccess(Json.obj(schemaField -> 5), __ \ "a" \ "b" \ "c")

      migrateFrom(2,
      removeField("a"),
      removeField("b"),
      removeField("c")
      ).reads(Json.obj("a" -> 1, "b" -> 2, "c" -> 3, schemaField -> 4)) must_== JsSuccess(Json.obj("a" -> 1, "b" -> 2, schemaField -> 5), __ \ "c")
    }
  }
}