/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavefrontend.services


import java.time.LocalDate
import java.time.format.DateTimeFormatter

import cats.syntax.either._
import com.eclipsesource.schema.{SchemaType, SchemaValidator}
import com.google.inject.{ImplementedBy, Singleton}
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.util.JsErrorOps._

import scala.util.{Failure, Success, Try}

@ImplementedBy(classOf[JSONSchemaValidationServiceImpl])
trait JSONSchemaValidationService {

  def validate(userInfo: JsValue): Either[String,JsValue]

}

@Singleton
class JSONSchemaValidationServiceImpl extends  JSONSchemaValidationService {

  import uk.gov.hmrc.helptosavefrontend.services.JSONSchemaValidationServiceImpl._

  private val validationSchema: SchemaType =
    Json.fromJson[SchemaType](Json.parse(validationSchemaStr)).getOrElse(sys.error("Could not parse schema string"))

  private lazy val jsonValidator: SchemaValidator = new SchemaValidator()

  private val dateFormatter = DateTimeFormatter.BASIC_ISO_DATE

  private def before1800(date: LocalDate): Either[String, LocalDate] = {
    val year = date.getYear
    if (year < 1800) Left("FEATURE outgoing-json-validation: Date of birth before 1800") else Right(date)
  }

  private def futureDate(date: LocalDate): Either[String, LocalDate] = {
    val today = java.time.LocalDate.now()
    if (date.isAfter(today)) Left("FEATURE outgoing-json-validation: Date of birth in the future") else Right(date)
  }

  private def extractDateOfBirth(userInfo: JsValue): Either[String, LocalDate] = {
    (userInfo \ "dateOfBirth").toEither.fold[Either[String,LocalDate]](
      _ ⇒ Left("No date of birth found"),
      _ match {
        case  JsString(s) ⇒
          Try(LocalDate.parse(s, dateFormatter)) match {
            case Failure(e)     ⇒ Left(s"Could not parse date of birth: ${e.getMessage}")
            case Success(value) ⇒ Right(value)
          }

        case _ ⇒ Left("Date of birth was not a string")
      }
    )
  }

  private def validateAgainstSchema(userInfo: JsValue): Either[String,JsValue] =
    jsonValidator.validate(validationSchema, userInfo) match {
      case e: JsError ⇒ Left(s"User info was not valid against schema: ${e.prettyPrint()}")
      case JsSuccess(u, _) ⇒ Right(u)
    }

  def validate(userInfo: JsValue): Either[String, JsValue] =
    for {
      u ← validateAgainstSchema(userInfo)
      d ← extractDateOfBirth(userInfo)
      _ ← futureDate(d)
      _ ← before1800(d)
    } yield u

}

object JSONSchemaValidationServiceImpl {

  //For ICD v 1.7
  private val validationSchemaStr = """
    {
      "$schema": "http://json-schema.org/schema#",
      "description": "A JSON schema to validate JSON as described in PPM-30048-UEM009-ICD001-HTS-HMRC-Interfaces v1.2.docx",

      "type" : "object",
      "additionalProperties": false,
      "required": ["forename", "surname", "dateOfBirth", "contactDetails", "registrationChannel", "nino"],
      "properties" : {
          "forename" : {
          "type" : "string",
          "minLength": 1,
          "maxLength": 26
          },
          "surname": {
          "type": "string",
          "minLength": 1,
          "maxLength": 300
        },
          "dateOfBirth": {
          "type": "string",
          "minLength": 8,
          "maxLength": 8,
          "pattern": "^[0-9]{4}(01|02|03|04|05|06|07|08|09|10|11|12)[0-9]{2}$"
        },
        "contactDetails": {
          "type": "object",
          "additionalProperties": false,
          "required": ["address1", "address2", "postcode", "communicationPreference"],
          "properties": {
            "countryCode": {
              "type": "string",
              "minLength": 2,
              "maxLength": 2,
              "pattern": "[A-Z][A-Z]"
            },
            "address1": {
              "type": "string",
              "maxLength": 35
            },
            "address2": {
              "type": "string",
              "maxLength": 35
            },
            "address3": {
              "type": "string",
              "maxLength": 35
            },
            "address4": {
              "type": "string",
              "maxLength": 35
            },
           "address5": {
             "type": "string",
             "maxLength": 35
            },
            "postcode": {
              "type": "string",
              "maxLength": 10
            },
            "communicationPreference": {
              "type": "string",
              "minLength": 2,
              "maxLength": 2,
              "pattern": "00|02"
            },
           "phoneNumber": {
             "type": "string",
             "maxLength": 15
            },
            "email": {
              "type": "string",
              "maxLength": 254,
              "pattern": "^.{1,64}@.{1,252}$"
            }
          }
        },
        "registrationChannel": {
          "type": "string",
          "maxLength": 10,
          "pattern": "^online$|^callCentre$"
         },
        "nino" : {
          "type" : "string",
          "minLength": 9,
          "maxLength": 9,
          "pattern": "^(([A-CEGHJ-PR-TW-Z][A-CEGHJ-NPR-TW-Z])([0-9]{2})([0-9]{2})([0-9]{2})([A-D]{1})|((XX)(99)(99)(99)(X)))$"
        }
      }
    }""".stripMargin

}
