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

package hts.utils

import scala.collection.mutable.Map

object ScenarioContext {
  var map = Map.empty[String, Any]

  def set(key: String, value: Any) {
    map.put(key, value)
  }

  def get[T: Manifest](key: String): T = {
    map.get(key) match {
      case Some(obj) ⇒ obj.asInstanceOf[T]
      case None      ⇒ throw new Exception("Map not found")
    }
  }
  //  def get(key: String): Any = {
  //    map.get(key) match {
  //      case Some(obj) ⇒ obj
  //      case None      ⇒ throw new Exception("Map not found")
  //    }
  //  }

  def reset() {
    map = Map.empty[String, Any]
  }
}
