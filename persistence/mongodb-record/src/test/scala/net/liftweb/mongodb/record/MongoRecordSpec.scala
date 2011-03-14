/*
 * Copyright 2010-2011 WorldWide Conferencing, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.liftweb
package mongodb
package record

import java.util.{Date, UUID}
import java.util.regex.Pattern

import org.bson.types.ObjectId

import common._
import json._

import net.liftweb.record.field.Countries


/**
 * Systems under specification for MongoRecord.
 */
object MongoRecordSpec extends MongoTestKit {
  "MongoRecord Specification".title
  import fixtures._

  "MongoRecord field introspection" should {
    checkMongoIsRunning

    val rec = MongoFieldTypeTestRecord.createRecord
    val allExpectedFieldNames: List[String] = "_id" :: (for {
      typeName <- "Date DBRef JsonObject ObjectId Pattern UUID".split(" ")
      flavor <- "mandatory legacyOptional".split(" ")
    } yield flavor + typeName + "Field").toList

    "introspect only the expected fields" in {
      rec.fields().map(_.name).sortWith(_ < _) must_== allExpectedFieldNames.sortWith(_ < _)
    }

    "correctly look up fields by name" in {
      for (name <- allExpectedFieldNames) {
        rec.fieldByName(name).isDefined must beTrue
      }
	  success
    }

    "not look up fields by bogus names" in {
      for (name <- allExpectedFieldNames) {
        rec.fieldByName("x" + name + "y").isDefined must beFalse
      }
	  success
    }
  }

  "MongoRecord lifecycle callbacks" should {
    checkMongoIsRunning

    def testOneHarness(scope: String, f: LifecycleTestRecord => HarnessedLifecycleCallbacks) = {
      "be called before validation when specified at " + scope ! {
        val rec = LifecycleTestRecord.createRecord
        var triggered = false
        f(rec).beforeValidationHarness = () => triggered = true
        rec.foreachCallback(_.beforeValidation)
        triggered must_== true
      }

      "be called after validation when specified at " + scope ! {
        val rec = LifecycleTestRecord.createRecord
        var triggered = false
        f(rec).afterValidationHarness = () => triggered = true
        rec.foreachCallback(_.afterValidation)
        triggered must_== true
      }

      "be called around validate when specified at " + scope ! {
        val rec = LifecycleTestRecord.createRecord
        var triggeredBefore = false
        var triggeredAfter = false
        f(rec).beforeValidationHarness = () => triggeredBefore = true
        f(rec).afterValidationHarness = () => triggeredAfter = true
        rec.validate must_== Nil
        triggeredBefore must_== true
        triggeredAfter must_== true
      }

      "be called before save when specified at " + scope ! {
        val rec = LifecycleTestRecord.createRecord
        var triggered = false
        f(rec).beforeSaveHarness = () => triggered = true
        rec.foreachCallback(_.beforeSave)
        triggered must_== true
      }

      "be called before create when specified at " + scope ! {
        val rec = LifecycleTestRecord.createRecord
        var triggered = false
        f(rec).beforeCreateHarness = () => triggered = true
        rec.foreachCallback(_.beforeCreate)
        triggered must_== true
      }

      "be called before update when specified at " + scope ! {
        val rec = LifecycleTestRecord.createRecord
        var triggered = false
        f(rec).beforeUpdateHarness = () => triggered = true
        rec.foreachCallback(_.beforeUpdate)
        triggered must_== true
      }

      "be called after save when specified at " + scope ! {
        val rec = LifecycleTestRecord.createRecord
        var triggered = false
        f(rec).afterSaveHarness = () => triggered = true
        rec.foreachCallback(_.afterSave)
        triggered must_== true
      }

      "be called after create when specified at " + scope ! {
        val rec = LifecycleTestRecord.createRecord
        var triggered = false
        f(rec).afterCreateHarness = () => triggered = true
        rec.foreachCallback(_.afterCreate)
        triggered must_== true
      }

      "be called after update when specified at " + scope ! {
        val rec = LifecycleTestRecord.createRecord
        var triggered = false
        f(rec).afterUpdateHarness = () => triggered = true
        rec.foreachCallback(_.afterUpdate)
        triggered must_== true
      }

      "be called before delete when specified at " + scope ! {
        val rec = LifecycleTestRecord.createRecord
        var triggered = false
        f(rec).beforeDeleteHarness = () => triggered = true
        rec.foreachCallback(_.beforeDelete)
        triggered must_== true
      }

      "be called after delete when specified at " + scope ! {
        val rec = LifecycleTestRecord.createRecord
        var triggered = false
        f(rec).afterDeleteHarness = () => triggered = true
        rec.foreachCallback(_.afterDelete)
        triggered must_== true
      }
    }

    testOneHarness("the record level", rec => rec)
    testOneHarness("the inner object level", rec => rec.innerObjectWithCallbacks: HarnessedLifecycleCallbacks)
    testOneHarness("the field level", rec => rec.stringFieldWithCallbacks: HarnessedLifecycleCallbacks)
  }

  "MongoRecord" should {
    checkMongoIsRunning
    
    val fttr = FieldTypeTestRecord.createRecord
      //.mandatoryBinaryField()
      .mandatoryBooleanField(false)
      .mandatoryCountryField(Countries.USA)
      .mandatoryDecimalField(BigDecimal("3.14"))
      .mandatoryDoubleField(1999)
      .mandatoryEmailField("test@liftweb.net")
      .mandatoryEnumField(MyTestEnum.ONE)
      .mandatoryIntField(99)
      .mandatoryLocaleField("en_US")
      .mandatoryLongField(100L)
      .mandatoryPostalCodeField("55401")
      .mandatoryStringField("string")
      .mandatoryTextareaField("string")
      .mandatoryTimeZoneField("America/Chicago")

    val mfttr = MongoFieldTypeTestRecord.createRecord
      .mandatoryDateField(new Date)
      .mandatoryJsonObjectField(TypeTestJsonObject(1, "jsonobj1"))
      .mandatoryObjectIdField(ObjectId.get)
      .mandatoryPatternField(Pattern.compile("^Mo", Pattern.CASE_INSENSITIVE))
      .mandatoryUUIDField(UUID.randomUUID)
    
    /* This causes problems if MongoDB is not running */
    if (isMongoRunning) {
      mfttr.mandatoryDBRefField(DBRefTestRecord.createRecord.getRef)
    }

    val ltr = ListTestRecord.createRecord
      .mandatoryStringListField(List("abc", "def", "ghi"))
      .mandatoryIntListField(List(4, 5, 6))
      .mandatoryMongoJsonObjectListField(List(TypeTestJsonObject(1, "jsonobj1"), TypeTestJsonObject(2, "jsonobj2")))
      .mongoCaseClassListField(List(MongoCaseClassTestObject(1,"str")))
      
    val mtr = MapTestRecord.createRecord
      .mandatoryStringMapField(Map("a" -> "abc", "b" -> "def", "c" -> "ghi"))
      .mandatoryIntMapField(Map("a" -> 4, "b" -> 5, "c" -> 6))

    val json = "{\"mandatoryDateField\":{\"$dt\":\""+mfttr.meta.formats.dateFormat.format(mfttr.mandatoryDateField.value)+"\"},\"mandatoryJsonObjectField\":{\"intField\":1,\"stringField\":\"jsonobj1\"},\"mandatoryObjectIdField\":{\"$oid\":\""+mfttr.mandatoryObjectIdField.value.toString+"\"},\"mandatoryPatternField\":{\"$regex\":\"^Mo\",\"$flags\":2},\"mandatoryUUIDField\":{\"$uuid\":\""+mfttr.mandatoryUUIDField.value.toString+"\"},\"_id\":{\"$oid\":\""+mfttr.id.toString+"\"}}"
    val ljson = "{\"mandatoryStringListField\":[\"abc\",\"def\",\"ghi\"],\"legacyOptionalStringListField\":[],\"mandatoryIntListField\":[4,5,6],\"legacyOptionalIntListField\":[],\"mandatoryMongoJsonObjectListField\":[{\"intField\":1,\"stringField\":\"jsonobj1\"},{\"intField\":2,\"stringField\":\"jsonobj2\"}],\"legacyOptionalMongoJsonObjectListField\":[],\"_id\":{\"$oid\":\""+ltr.id.toString+"\"}}"
    val mjson = "{\"mandatoryStringMapField\":{\"a\":\"abc\",\"b\":\"def\",\"c\":\"ghi\"},\"legacyOptionalStringMapField\":{},\"mandatoryIntMapField\":{\"a\":4,\"b\":5,\"c\":6},\"legacyOptionalIntMapField\":{},\"_id\":{\"$oid\":\""+mtr.id.toString+"\"}}"

    "save and retrieve 'standard' type fields" in {
      checkMongoIsRunning

      fttr.save

      val fttrFromDb = FieldTypeTestRecord.find(fttr.id)
      fttrFromDb must not be empty
      fttrFromDb foreach { tr =>
        tr must_== fttr
      }
	  success
    }

    "save and retrieve Mongo type fields" in {
      checkMongoIsRunning

      mfttr.save

      val mfttrFromDb = MongoFieldTypeTestRecord.find(mfttr.id)
      mfttrFromDb must not be empty
      mfttrFromDb foreach { tr =>
        tr.mandatoryDBRefField.value.getId must_== mfttr.mandatoryDBRefField.value.getId
        tr.mandatoryDBRefField.value.getRef must_== mfttr.mandatoryDBRefField.value.getRef
        tr must_== mfttr
      }

      ltr.save

      val ltrFromDb = ListTestRecord.find(ltr.id)
      ltrFromDb must not be empty
      ltrFromDb foreach { tr =>
        tr must_== ltr
      }

      mtr.save

      val mtrFromDb = MapTestRecord.find(mtr.id)
      mtrFromDb must not be empty
      mtrFromDb foreach { tr =>
        tr must_== mtr
      }
	  success
    }

    "convert Mongo type fields to JValue" in {
      checkMongoIsRunning

      mfttr.asJValue must_== JObject(List(
        JField("_id", JObject(List(JField("$oid", JString(mfttr.id.toString))))),
        JField("mandatoryDateField", JObject(List(JField("$dt", JString(mfttr.meta.formats.dateFormat.format(mfttr.mandatoryDateField.value)))))),
        JField("legacyOptionalDateField", JNothing),
        JField("mandatoryDBRefField", JNothing),
        JField("legacyOptionalDBRefField", JNothing),
        JField("mandatoryJsonObjectField", JObject(List(JField("intField", JInt(1)), JField("stringField", JString("jsonobj1"))))),
        JField("legacyOptionalJsonObjectField", JObject(List(JField("intField", JInt(0)), JField("stringField", JString(""))))),
        JField("mandatoryObjectIdField", JObject(List(JField("$oid", JString(mfttr.mandatoryObjectIdField.value.toString))))),
        JField("legacyOptionalObjectIdField", JNothing),
        JField("mandatoryPatternField", JObject(List(JField("$regex", JString(mfttr.mandatoryPatternField.value.pattern)), JField("$flags", JInt(mfttr.mandatoryPatternField.value.flags))))),
        JField("legacyOptionalPatternField", JNothing),
        JField("mandatoryUUIDField", JObject(List(JField("$uuid", JString(mfttr.mandatoryUUIDField.value.toString))))),
        JField("legacyOptionalUUIDField", JNothing)
      ))

      ltr.asJValue must_== JObject(List(
        JField("_id", JObject(List(JField("$oid", JString(ltr.id.toString))))),
        JField("mandatoryStringListField", JArray(List(JString("abc"), JString("def"), JString("ghi")))),
        JField("legacyOptionalStringListField", JArray(List())),
        JField("mandatoryIntListField", JArray(List(JInt(4), JInt(5), JInt(6)))),
        JField("legacyOptionalIntListField", JArray(List())),
        JField("mandatoryMongoJsonObjectListField", JArray(List(
          JObject(List(JField("intField", JInt(1)), JField("stringField", JString("jsonobj1")))),
          JObject(List(JField("intField", JInt(2)), JField("stringField", JString("jsonobj2"))))
        ))),
        JField("legacyOptionalMongoJsonObjectListField", JArray(List())),
        JField("mongoCaseClassListField",JArray(List(
          JObject(List(JField("intField", JInt(1)), JField("stringField", JString("str"))))
        )))
      ))

      mtr.asJValue must_== JObject(List(
        JField("_id", JObject(List(JField("$oid", JString(mtr.id.toString))))),
        JField("_id", JObject(List(JField("$oid", JString(mtr.id.toString))))),
        JField("mandatoryStringMapField", JObject(List(
          JField("a", JString("abc")),
          JField("b", JString("def")),
          JField("c", JString("ghi"))
        ))),
        JField("legacyOptionalStringMapField", JObject(List())),
        JField("mandatoryIntMapField", JObject(List(
          JField("a", JInt(4)),
          JField("b", JInt(5)),
          JField("c", JInt(6))
        ))),
        JField("legacyOptionalIntMapField", JObject(List()))
      ))
    }
    
    "convert Mongo type fields to JsExp" in {
      checkMongoIsRunning

      /*
      mfttr.asJsExp must_== JsObj(
        ("_id", JsObj(("$oid", Str(mfttr.id.toString)))),
        ("mandatoryDateField", JsObj(("$dt", Str(mfttr.meta.formats.dateFormat.format(mfttr.mandatoryDateField.value))))),
        ("legacyOptionalDateField", Str("null")),
        ("mandatoryDBRefField", Str("null")),
        ("legacyOptionalDBRefField", Str("null")),
        ("mandatoryJsonObjectField", JsObj(("intField", Num(1)), ("stringField", Str("jsonobj1")))),
        ("legacyOptionalJsonObjectField", JsObj(("intField", Num(0)), ("stringField", Str("")))),
        ("mandatoryObjectIdField", JsObj(("$oid", Str(mfttr.mandatoryObjectIdField.value.toString)))),
        ("legacyOptionalObjectIdField", Str("null")),
        ("mandatoryPatternField", JsObj(("$regex", Str(mfttr.mandatoryPatternField.value.pattern)), ("$flags", Num(mfttr.mandatoryPatternField.value.flags)))),
        ("legacyOptionalPatternField", Str("null")),
        ("mandatoryUUIDField", JsObj(("$uuid", Str(mfttr.mandatoryUUIDField.value.toString)))),
        ("legacyOptionalUUIDField", Str("null"))
      )*/

      /*
      ltr.asJsExp must_== JsObj(
        ("_id", JsObj(("$oid", Str(ltr.id.toString)))),
        ("mandatoryStringListField", JsArray(Str("abc"), Str("def"), Str("ghi"))),
        ("legacyOptionalStringListField", JsArray()),
        ("mandatoryIntListField", JsArray(Num(4), Num(5), Num(6))),
        ("legacyOptionalIntListField", JsArray()),
        ("mandatoryMongoJsonObjectListField", JsArray(
          JsObj(("intField", Num(1)), ("stringField", Str("jsonobj1"))),
          JsObj(("intField", Num(2)), ("stringField", Str("jsonobj2")))
        )),
        ("legacyOptionalMongoJsonObjectListField", JsArray())
      )

      mtr.asJsExp must_== JsObj(
        ("_id", JsObj(("$oid", Str(mtr.id.toString)))),
        ("_id", JsObj(("$oid", Str(mtr.id.toString)))),
        ("mandatoryStringMapField", JsObj(
          ("a", Str("abc")),
          ("b", Str("def")),
          ("c", Str("ghi"))
        )),
        ("legacyOptionalStringMapField", JsObj()),
        ("mandatoryIntMapField", JsObj(
          ("a", Num(4)),
          ("b", Num(5)),
          ("c", Num(6))
        )),
        ("legacyOptionalIntMapField", JsObj())
      )*/
      success
    }

    "get set from json string using lift-json parser" in {
      checkMongoIsRunning

      val mfftrFromJson = MongoFieldTypeTestRecord.fromJsonString(json)
      mfftrFromJson must not be empty
      mfftrFromJson foreach { tr =>
        tr must_== mfttr
      }

      val ltrFromJson = ListTestRecord.fromJsonString(ljson)
      ltrFromJson must not be empty
      ltrFromJson foreach { tr =>
        tr must_== ltr
      }

      val mtrFromJson = MapTestRecord.fromJsonString(mjson)
      mtrFromJson must not be empty
      mtrFromJson foreach { tr =>
        tr must_== mtr
      }
	  success
    }

    "handle null" in {
      checkMongoIsRunning

      val ntr = NullTestRecord.createRecord
      ntr.nullstring.set(null)
      ntr.jsonobjlist.set(List(JsonObj("1", null), JsonObj("2", "jsonobj2")))

      ntr.save must_== ntr

      val ntrFromDb = NullTestRecord.find(ntr.id)

      ntrFromDb must not be empty

      ntrFromDb foreach { n =>
        // goes in as
        ntr.nullstring.valueBox.map(_ must beNull)
        ntr.nullstring.value must beNull
        // comes out as
        n.nullstring.valueBox.map(_ must_== "")
        n.nullstring.value must_== ""
        // JsonObjects
        n.jsonobjlist.value.size must_== 2
        ntr.jsonobjlist.value.size must_== 2
        n.jsonobjlist.value(0).id must_== ntr.jsonobjlist.value(0).id
        n.jsonobjlist.value(0).name must beNull
        ntr.jsonobjlist.value(0).name must beNull
        n.jsonobjlist.value(1).id must_== ntr.jsonobjlist.value(1).id
        n.jsonobjlist.value(1).name must_== ntr.jsonobjlist.value(1).name
      }
	  success
    }

    "handle Box using JsonBoxSerializer" in {
      checkMongoIsRunning
      
      val btr = BoxTestRecord.createRecord
      btr.jsonobjlist.set(
        BoxTestJsonObj("1", Empty, Full("Full String1"), Failure("Failure1")) ::
        BoxTestJsonObj("2", Empty, Full("Full String2"), Failure("Failure2")) ::
        Nil
      )

      btr.save

      val btrFromDb = BoxTestRecord.find(btr.id)

      btrFromDb must not be empty

      btrFromDb foreach { b =>
        b.jsonobjlist.value.size must_== 2
        btr.jsonobjlist.value.size must_== 2
        val sortedList = b.jsonobjlist.value.sortWith(_.id < _.id)
        sortedList(0).boxEmpty must_== Empty
        sortedList(0).boxFull must_== Full("Full String1")
        sortedList(0).boxFail must_== Failure("Failure1")
      }
	  success
    }

  }
}

