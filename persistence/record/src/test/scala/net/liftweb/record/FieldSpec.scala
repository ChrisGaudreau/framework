/*
 * Copyright 2010-2012 WorldWide Conferencing, LLC
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
package record

import field.{Countries, PasswordField, StringField}

import common.{Box, Empty, Failure, Full}
import http.{LiftSession, S}
import http.js.JE._
import http.js.JsExp
import json.JsonAST._
import util.Helpers._

import java.util.Calendar
import org.specs2.execute.Result
import org.specs2.mutable.{Around, Specification}

import fixtures._
import net.liftweb.util.{Helpers, FieldError}
import scala.xml.{NodeSeq, Elem, Node, Text}


/**
 * Systems under specification for RecordField.
 */
class FieldSpec extends Specification {
  args(sequential=true)
  val session = new LiftSession("", randomString(20), Empty)
  //def around[T <% Result](t: => T) = S.initIfUninitted(session) { t }

  def passBasicTests[A](example: A, mandatory: MandatoryTypedField[A], legacyOptional: MandatoryTypedField[A], optional: OptionalTypedField[A])(implicit m: scala.reflect.Manifest[A]): Unit = {
    val canCheckDefaultValues =
      !mandatory.defaultValue.isInstanceOf[Calendar] // don't try to use the default value of date/time typed fields, because it changes from moment to moment!

    def commonBehaviorsForMandatory(in: MandatoryTypedField[A]): Unit = {
  		if (canCheckDefaultValues) {
  			"which have the correct initial value" in {
  				in.get must_== in.defaultValue
  			}
  		}

      "which are readable and writable" in {
        in.set(example)
        in.get must_== example
        in.clear
        in.get must_!= example
        in.setBox(Box !! example)
        in.get must_== example
      }

      if (canCheckDefaultValues) {
        "which correctly clear back to the default value" in {
          in.set(example)
          in.clear
          in.get must_== in.defaultValue
        }
      }
    }

    def commonBehaviorsForAllFlavors(in: TypedField[A]): Unit = {
      if (canCheckDefaultValues) {
        "which have the correct initial boxed value" in {
          S.initIfUninitted(session) {
            in match {
              case mandatory: MandatoryTypedField[_] =>
              	mandatory.value must_== mandatory.defaultValue
              case _ => ()
            }
            in.valueBox must_== in.defaultValueBox
          }
        }
      }

      "which have readable and writable boxed values" in {
        in.setBox(Full(example))
        in.valueBox.isDefined must beTrue
        in.valueBox must_== Full(example)
        in.clear
        in.valueBox must_!= Full(example)
      }

      if (canCheckDefaultValues) {
        "which correctly clear back to the default box value" in {
          in.setBox(Full(example))
          in.valueBox.isDefined must beTrue
          in.clear
          in.valueBox must_== in.defaultValueBox
        }
      }

      "which capture error conditions set in" in {
        in.setBox(Failure("my failure"))
        in.valueBox must_== Failure("my failure")
      }

      if(canCheckDefaultValues) {
	      "which are only flagged as dirty_? when setBox is called with a different value" in {
	        in.clear
	        in match {
	          case owned: OwnedField[_] => owned.owner.runSafe {
	            in.resetDirty
	          }
	          case _ => in.resetDirty
	        }
	        in.dirty_? must_== false
	        val valueBox = in.valueBox
	        in.setBox(valueBox)
	        in.dirty_? must_== false
	        val exampleBox = Full(example)
          // TODO: Fix this
	        //valueBox must verify { v => ! (exampleBox === v) }
	        in.setBox(exampleBox)
	        in.dirty_? must_== true
	      }
      }
    }

    "support mandatory fields" in {
      commonBehaviorsForAllFlavors(mandatory)
      commonBehaviorsForMandatory(mandatory)

      "which are configured correctly" in {
        mandatory.optional_? must_== false
      }

      "which initialize to some value" in {
        mandatory.valueBox.isDefined must beTrue
      }

      "which correctly fail to be set to Empty" in {
        mandatory.valueBox.isDefined must beTrue
        mandatory.setBox(Empty)
        // TODO: fix this
        //mandatory.valueBox must beLike { case Failure(s, _, _) if s == mandatory.notOptionalErrorMessage => true }
        success
      }
    }

    "support 'legacy' optional fields (override optional_?)" in {
      commonBehaviorsForAllFlavors(legacyOptional)
      commonBehaviorsForMandatory(legacyOptional)

      "which are configured correctly" in {
        legacyOptional.optional_? must_== true
      }

      "which initialize to Empty" in {
        legacyOptional.valueBox must_== Empty
      }

      "which do not fail when set to Empty" in {
        legacyOptional.set(example)
        legacyOptional.value must_== example
        legacyOptional.valueBox must_== Full(example)
        legacyOptional.clear
        if (canCheckDefaultValues) {
          legacyOptional.value must_== legacyOptional.defaultValue
          legacyOptional.valueBox must_== legacyOptional.defaultValueBox
        }
        legacyOptional.set(example)
        legacyOptional.value must_== example
        legacyOptional.valueBox must_== Full(example)
        legacyOptional.setBox(Empty)
        if (canCheckDefaultValues) {
          legacyOptional.value must_== legacyOptional.defaultValue
          legacyOptional.valueBox must_== legacyOptional.defaultValueBox
        }
        success
      }
    }

    "support optional fields" in {
      commonBehaviorsForAllFlavors(optional)

      "which are configured correctly" in {
        optional.optional_? must_== true
      }

      "which initialize to Empty" in {
        optional.valueBox must_== Empty
      }

      "which do not fail when set to Empty" in {
        optional.set(Some(example))
        optional.value must_== Some(example)
        optional.valueBox must_== Full(example)
        optional.set(None)
        optional.value must_== None
        optional.valueBox must_== Empty
        optional.set(Some(example))
        optional.value must_== Some(example)
        optional.valueBox must_== Full(example)
        optional.setBox(Empty)
        optional.value must_== None
        optional.valueBox must_== Empty
      }
    }
  }

  def passConversionTests[A](example: A, mandatory: MandatoryTypedField[A], jsexp: JsExp, jvalue: JValue, formPattern: Box[NodeSeq]): Unit = {

    "convert to JsExp" in {
      mandatory.set(example)
      mandatory.asJs mustEqual jsexp
    }

    "convert to JValue" in {
      mandatory.set(example)
      mandatory.asJValue mustEqual jvalue
    }

    // toInternetDate doesn't retain millisecond data so, dates can't be compared accurately.
    if (!mandatory.defaultValue.isInstanceOf[Calendar]) {
      "get set from JValue" in {
        mandatory.setFromJValue(jvalue) mustEqual Full(example)
        mandatory.value mustEqual example
        () // does not compile without this: no implicit argument matching parameter type scala.reflect.Manifest[org.specs.specification.Result[mandatory.MyType]]
      }
    }

    /*formPattern foreach { fp =>
      "convert to form XML" in {
        mandatory.set(example)
        val session = new LiftSession("", randomString(20), Empty)
        S.initIfUninitted(session) {
          val formXml = mandatory.toForm
          formXml.isDefined must beTrue
          formXml foreach { fprime =>
            val f = ("* [name]" #> ".*" & "select *" #> (((ns: NodeSeq) => ns.filter {
              case e: Elem => e.attribute("selected").map(_.text) == Some("selected")
              case _ => false
            }) andThen "* [value]" #> ".*"))(fprime)
            val ret: Boolean = Helpers.compareXml(f, fp)
            ret must_== true
          }
        }
      }
    }*/
  }

    /* Since Array[Byte]s cannot be compared, commenting out this test for now
  "BinaryField" should {
    val rec = FieldTypeTestRecord.createRecord
    val a = new Array[Byte](3)
    a(0) = 1
    a(1) = 2
    a(2) = 3
    passBasicTests(a, rec.mandatoryBinaryField, rec.legacyOptionalBinaryField, rec.optionalBinaryField)
  }
    */

  "BooleanField" should {
    S.initIfUninitted(session) {
      val rec = FieldTypeTestRecord.createRecord
      val bool = true
      passBasicTests(bool, rec.mandatoryBooleanField, rec.legacyOptionalBooleanField, rec.optionalBooleanField)
      passConversionTests(
        bool,
        rec.mandatoryBooleanField,
        JsTrue,
        JBool(bool),
        Full(<input checked="checked" tabindex="1" value="true" type="checkbox" name=".*" id="mandatoryBooleanField_id"></input><input value="false" type="hidden" name=".*"></input>)
      )
      "support java.lang.Boolean" in {
        rec.mandatoryBooleanField.setFromAny(java.lang.Boolean.TRUE)
        rec.optionalBooleanField.setFromAny(java.lang.Boolean.TRUE)
        (rec.mandatoryBooleanField.is && (rec.optionalBooleanField.is getOrElse false)) must_== true
      }
      "support Full(java.lang.Boolean)" in {
        rec.mandatoryBooleanField.setFromAny(Full(java.lang.Boolean.TRUE))
        rec.optionalBooleanField.setFromAny(Full(java.lang.Boolean.TRUE))
        (rec.mandatoryBooleanField.is && (rec.optionalBooleanField.is getOrElse false)) must_== true
      }
      "support Some(java.lang.Boolean)" in {
         rec.mandatoryBooleanField.setFromAny(Some(java.lang.Boolean.TRUE))
         rec.optionalBooleanField.setFromAny(Some(java.lang.Boolean.TRUE))
         (rec.mandatoryBooleanField.is && (rec.optionalBooleanField.is getOrElse false)) must_== true
      }
    }
  }

  /*"CountryField" should {
    S.initIfUninitted(session) {
      val rec = FieldTypeTestRecord.createRecord
      val country = Countries.Canada
      passBasicTests(country, rec.mandatoryCountryField, rec.legacyOptionalCountryField, rec.optionalCountryField)
      passConversionTests(
        country,
        rec.mandatoryCountryField,
        Str(country.toString),
        JInt(country.id),
        Full(<select tabindex="1" name=".*" id="mandatoryCountryField_id"><option value=".*" selected="selected">{country.toString}</option></select>)
      )
    }
    success
  }

  "DateTimeField" should {
    S.initIfUninitted(session) {
      val rec = FieldTypeTestRecord.createRecord
      val dt = Calendar.getInstance
      val dtStr = toInternetDate(dt.getTime)
      passBasicTests(dt, rec.mandatoryDateTimeField, rec.legacyOptionalDateTimeField, rec.optionalDateTimeField)
      passConversionTests(
        dt,
        rec.mandatoryDateTimeField,
        Str(dtStr),
        JString(dtStr),
        Full(<input name=".*" type="text" tabindex="1" value={dtStr} id="mandatoryDateTimeField_id"></input>)
      )
    }
    success
  }

  "DecimalField" should {
    val rec = FieldTypeTestRecord.createRecord
    val bd = BigDecimal("12.34")
    passBasicTests(bd, rec.mandatoryDecimalField, rec.legacyOptionalDecimalField, rec.optionalDecimalField)
    passConversionTests(
      bd,
      rec.mandatoryDecimalField,
      JsRaw(bd.toString),
      JString(bd.toString),
      Full(<input name=".*" type="text" tabindex="1" value={bd.toString} id="mandatoryDecimalField_id"></input>)
    )
    success
  }

  "DoubleField" should {
    val rec = FieldTypeTestRecord.createRecord
    val d = 12.34
    passBasicTests(d, rec.mandatoryDoubleField, rec.legacyOptionalDoubleField, rec.optionalDoubleField)
    passConversionTests(
      d,
      rec.mandatoryDoubleField,
      JsRaw(d.toString),
      JDouble(d),
      Full(<input name=".*" type="text" tabindex="1" value={d.toString} id="mandatoryDoubleField_id"></input>)
    )
    success
  }

  "EmailField" should {
    val rec = FieldTypeTestRecord.createRecord
    val email = "foo@bar.baz"
    passBasicTests(email, rec.mandatoryEmailField, rec.legacyOptionalEmailField, rec.optionalEmailField)
    passConversionTests(
      email,
      rec.mandatoryEmailField,
      Str(email),
      JString(email),
      Full(<input name=".*" type="text" maxlength="100" tabindex="1" value={email} id="mandatoryEmailField_id"></input>)
    )
    success
  }

  "EnumField" should {
    val rec = FieldTypeTestRecord.createRecord
    val ev = MyTestEnum.TWO
    passBasicTests(ev, rec.mandatoryEnumField, rec.legacyOptionalEnumField, rec.optionalEnumField)
    passConversionTests(
      ev,
      rec.mandatoryEnumField,
      Str(ev.toString),
      JInt(ev.id),
      Full(<select tabindex="1" name=".*" id="mandatoryEnumField_id"><option value=".*" selected="selected">{ev.toString}</option></select>)
    )
    success
  }

  "IntField" should {
    val rec = FieldTypeTestRecord.createRecord
    val num = 123
    passBasicTests(num, rec.mandatoryIntField, rec.legacyOptionalIntField, rec.optionalIntField)
    passConversionTests(
      num,
      rec.mandatoryIntField,
      JsRaw(num.toString),
      JInt(num),
      Full(<input name=".*" type="text" tabindex="1" value={num.toString} id="mandatoryIntField_id"></input>)
    )
    success
  }

  "LocaleField" should {
    val rec = FieldTypeTestRecord.createRecord
    val example = java.util.Locale.getDefault.toString match {
      case "en_US" => "en_GB"
      case _ => "en_US"
    }
    passBasicTests(example, rec.mandatoryLocaleField, rec.legacyOptionalLocaleField, rec.optionalLocaleField)
    success
  }

  "LongField" should {
    val rec = FieldTypeTestRecord.createRecord
    val lng = 1234L
    passBasicTests(lng, rec.mandatoryLongField, rec.legacyOptionalLongField, rec.optionalLongField)
    passConversionTests(
      lng,
      rec.mandatoryLongField,
      JsRaw(lng.toString),
      JInt(lng),
      Full(<input name=".*" type="text" tabindex="1" value={lng.toString} id="mandatoryLongField_id"></input>)
    )
    success
  }

  "PasswordField" should {
    "require a nonempty password" in {
      val rec = PasswordTestRecord.createRecord.password("")

      rec.validate must_== (
        FieldError(rec.password, Text(S.?("password.must.be.set"))) ::
        Nil
      )
    }

    "correctly validate the unencrypted value" in {
      val rec = PasswordTestRecord.createRecord.password("testvalue")
      rec.validate must_== Nil

      rec.password("1234")
      rec.validate must_== (
        FieldError(rec.password, Text(S.?("password.too.short"))) ::
        Nil
      )
    }

    "match with encrypted value" in {
      val rec = PasswordTestRecord.createRecord.password("testpassword")
      rec.password.match_?("testpassword") must_== true

      rec.password.set("$2a$10$6CJWdXpKoP8bVTjGH8SbKOWevNQVL8MkYVlBLmqtywVi7dp/YgPXC")
      rec.password.match_?("dummyPassw0rd") must_== true
    }
  }

  "PostalCodeField" should {
    val rec = FieldTypeTestRecord.createRecord
    val zip = "02452"
    rec.mandatoryCountryField.set(Countries.USA)
    passBasicTests(zip, rec.mandatoryPostalCodeField, rec.legacyOptionalPostalCodeField, rec.optionalPostalCodeField)
    passConversionTests(
      zip,
      rec.mandatoryPostalCodeField,
      Str(zip),
      JString(zip),
      Full(<input name=".*" type="text" maxlength="32" tabindex="1" value={zip} id="mandatoryPostalCodeField_id"></input>)
    )
    success
  }

  "StringField" should {
    {
      val rec = FieldTypeTestRecord.createRecord
      val str = "foobar"
      passBasicTests(str, rec.mandatoryStringField, rec.legacyOptionalStringField, rec.optionalStringField)
      passConversionTests(
        str,
        rec.mandatoryStringField,
        Str(str),
        JString(str),
        Full(<input name=".*" type="text" maxlength="100" tabindex="1" value={str} id="mandatoryStringField_id"></input>)
      )
    }

    "honor validators configured in the usual way" in {
      val rec = StringTestRecord.createRecord

      rec.validate must_== (
        FieldError(rec.string, Text("String field name must be at least 3 characters.")) ::
        Nil
      )
    }

    "honor harnessed validators" in {
      val rec = ValidationTestRecord.createRecord
      val field = rec.stringFieldWithValidation

      "which always succeed" in {
        field.validationHarness = _ => Nil
        rec.validate must_== Nil
      }

      "which always fail" in {
        val fieldError = FieldError(field, Text("failed"))
        field.validationHarness = s => FieldError(rec.stringFieldWithValidation, Text("failed")) :: Nil
        rec.validate must_== (fieldError :: Nil)
      }

      "which receive the value" in {
        var received: String = null
        field.set("foobar")
        field.validationHarness = s => { received = s; Nil }
        rec.validate must_== Nil
        received must_== "foobar"
      }
    }

    "support filtering" in {
      val rec = FilterTestRecord.createRecord
      val field = rec.stringFieldWithFiltering

      "which does nothing" in {
        field.set("foobar")
        field.value must_== "foobar"
        field.valueBox must_== Full("foobar")
      }

      "which trims the input at the value level" in {
        field.setFilterHarness = _.trim
        field.set("  foobar  ")
        field.value must_== "foobar"
        field.valueBox must_== Full("foobar")
      }

      "which trims the input at the box level" in {
        field.setFilterBoxHarness = _.map(_.trim)
        field.set("   foobar   ")
        field.value must_== "foobar"
        field.valueBox must_== Full("foobar")
      }

      "which Empties the box" in {
        field.setFilterBoxHarness = s => Empty
        field.set("foobar")
        field.value must_== field.defaultValue
        field.valueBox must_== Empty
      }

      "which Fails" in {
        field.setFilterBoxHarness = s => Failure("my failure")
        field.set("foobar")
        field.value must_== field.defaultValue
        field.valueBox must_== Failure("my failure")
      }
    }
  }

  "TextareaField" should {
    val rec = FieldTypeTestRecord.createRecord
    val txt = "foobar"
    passBasicTests(txt, rec.mandatoryTextareaField, rec.legacyOptionalTextareaField, rec.optionalTextareaField)
    passConversionTests(
      txt,
      rec.mandatoryTextareaField,
      Str(txt),
      JString(txt),
      Full(<textarea name=".*" rows="8" tabindex="1" cols="20" id="mandatoryTextareaField_id">{txt}</textarea>)
    )
    success
  }

  "TimeZoneField" should {
    val rec = FieldTypeTestRecord.createRecord
    val example = java.util.TimeZone.getDefault.getID match {
      case "America/New_York" => "Europe/London"
      case _ => "America/New_York"
    }
    passBasicTests(example, rec.mandatoryTimeZoneField, rec.legacyOptionalTimeZoneField, rec.optionalTimeZoneField)
    passConversionTests(
      example,
      rec.mandatoryTimeZoneField,
      Str(example),
      JString(example),
      Full(<select tabindex="1" name=".*" id="mandatoryTimeZoneField_id"><option value=".*" selected="selected">{example}</option></select>)
    )
    success
  }*/
}

