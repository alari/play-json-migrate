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

class MigrateSpec extends Specification {
  implicit val testReads = Json.reads[TestCase]
  val correctJson = Json.obj("test" -> "123", "other" -> Json.arr(2l, 4l, 6l))
  val oldJson = Json.obj("other" -> "123", "some" -> Json.arr(1, 2, 3), "any" -> "rest")

  "migrator" should {
    "rename a field" in {
      migrate(
        renameField("other", "test")
      ).reads(oldJson) must_== JsSuccess(Json.obj("test" -> "123", "some" -> Json.arr(1, 2, 3), "any" -> "rest", schemaVersionField -> 1))

      migrate(
        renameField("other", "test"),
        renameField("some", "other")
      ).reads(oldJson) must_== JsSuccess(Json.obj("test" -> "123", "other" -> Json.arr(1, 2, 3), "any" -> "rest", schemaVersionField -> 2))
    }

    "update a single field" in {
      migrate(
        updateField("test")((s: JsString) => JsString("nothing-more")) orElse putField("test", "nothing-more")
      ).reads(oldJson) must_== JsSuccess(Json.obj("other" -> "123", "some" -> Json.arr(1, 2, 3), "any" -> "rest", "test" -> "nothing-more", schemaVersionField -> 1))

      migrate(
        renameField("other", "test"),
        updateField("test")((s: JsString) => JsString(s.value.reverse))
      ).reads(oldJson) must_== JsSuccess(Json.obj("test" -> "321", "some" -> Json.arr(1, 2, 3), "any" -> "rest", schemaVersionField -> 2), __ \ "test")
    }

    "set a field" in {
      migrate(
        putField("other", 555)
      ).reads(oldJson) must_== JsSuccess(Json.obj("other" -> 555, "some" -> Json.arr(1, 2, 3), "any" -> "rest", schemaVersionField -> 1))

      migrate(
        putField("other", 555),
        putField("new", "xkcd")

      ).reads(oldJson) must_== JsSuccess(Json.obj("other" -> 555, "some" -> Json.arr(1, 2, 3), "any" -> "rest", "new" -> "xkcd", schemaVersionField -> 2))
    }

    "remove a field" in {
      migrate(
        removeField("other"),
        renameField("some", "funny"),
        removeField("funny")
      ).reads(oldJson) must_== JsSuccess(Json.obj("any" -> "rest", schemaVersionField -> 3), __ \ "other" \ "funny")
    }

    "remove a field in a branch" in {
      migrate(
        removeField(__ \ "a" \ "b")
      ).reads(Json.obj("a" -> Json.obj("b" -> "z", "c" -> "0"))) must_== JsSuccess(Json.obj("a" -> Json.obj("c" -> "0"), schemaVersionField -> 1), __ \ "a" \ "b" \ "b")
    }

    "transform and read" in {
      val m = migrate(
        renameField("other", "test"),
        renameField("some", "other"),
        removeField("any"),
        updateField("other")((s: JsArray) => JsArray(s.value.map {
          case JsNumber(n) => JsNumber(n * 2)
        }))
      )

      m.reads(oldJson) must_== JsSuccess(correctJson ++ Json.obj(schemaVersionField -> 4), __ \ "any" \ "other")

      val m2 = readMigrating[TestCase](
        renameField("other", "test"),
        renameField("some", "other"),
        removeField("any"),
        updateField("other")((s: JsArray) => JsArray(s.value.map {
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
      ).reads(Json.obj("a" -> 1, "b" -> 2, "c" -> 3)) must_== JsSuccess(Json.obj(schemaVersionField -> 5), __ \ "a" \ "b" \ "c")

      migrateFrom(2,
        removeField("a"),
        removeField("b"),
        removeField("c")
      ).reads(Json.obj("a" -> 1, "b" -> 2, "c" -> 3, schemaVersionField -> 4)) must_== JsSuccess(Json.obj("a" -> 1, "b" -> 2, schemaVersionField -> 5), __ \ "c")
    }

    "move a branch" in {
      migrate(
        moveBranch(__ \ "a" \ "b", __ \ "c")
      ).reads(Json.obj("a" -> Json.obj("b" -> "0"))) must_== JsSuccess(Json.obj("a" -> Json.obj(), "c" -> "0", schemaVersionField -> 1))
    }

    "change inside branch" in {
      val src = Json.obj(
        "root" -> Json.obj(
          "branch1" -> Json.obj(
            "leaf1" -> 1
          ),
          "branch2" -> Json.obj(
            "leaf2" -> 2,
            "leaf3" -> 3
          )
        )
      )
      val dest = Json.obj(
        "root" -> Json.obj(
          "leaf1" -> 1,
          "branch1" -> Json.obj(
            "newLeaf" -> 5
          )
        ),
        schemaVersionField -> 1
      )
      val m = migrate(
        insideBranch(__ \ "root")(
          moveBranch(__ \ "branch1" \ "leaf1", __ \ "leaf1"),
          putField(__ \ "branch1" \ "newLeaf", 5),
          removeField("branch2")
        )
      )
      m.reads(src) must_== JsSuccess(dest, __ \ "root" \ "branch2")

      migrate(
        mapField(__ \ "root" \ "branch1"){a: JsObject => JsArray(Seq(JsString("1")))},
        removeField(__ \ "root" \ "branch2")

      ).reads(src) must_== JsSuccess(Json.obj("root" -> Json.obj("branch1" -> Json.arr("1")), schemaVersionField -> 2), __ \ "root" \ "branch1" \ "root" \ "branch2" \ "branch2")
    }
  }
}