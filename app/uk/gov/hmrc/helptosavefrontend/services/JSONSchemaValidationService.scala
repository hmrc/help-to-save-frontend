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
import com.google.inject.{ImplementedBy, Inject, Singleton}
import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigRenderOptions}
import play.api.Configuration
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.util.JsErrorOps._

import scala.util.{Failure, Success, Try}

@ImplementedBy(classOf[JSONSchemaValidationServiceImpl])
trait JSONSchemaValidationService {

  def validate(userInfo: JsValue): Either[String,JsValue]

}

@Singleton
class JSONSchemaValidationServiceImpl @Inject()(conf: Configuration) extends  JSONSchemaValidationService {

  private val validationSchema: SchemaType = {
    val schemaStr = conf.underlying.getString("schema")
    Json.fromJson[SchemaType](Json.parse(schemaStr)).getOrElse(sys.error("Could not parse schema string"))
  }

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