/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavefrontend.util

import java.time.{LocalDateTime, ZoneId}

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig

@Singleton
class MaintenanceSchedule @Inject() (implicit frontendAppConfig: FrontendAppConfig) extends Logging {
  val schedule: Schedule = Schedule.parse(frontendAppConfig.maintenanceSchedule)
  logger.info(s"Maintenence schedule $schedule")
  logger.info(s"Now ${Schedule.londonNow()}")

  def endOfMaintenance(): Option[LocalDateTime] = schedule.endOfMaintenance(Schedule.londonNow())
}

case class Schedule(times: Seq[Maintenance]) {
  def endOfMaintenance(now: LocalDateTime): Option[LocalDateTime] = {
    val current = times.filter(m => now.isAfter(m.start) && now.isBefore(m.end))
    current.map(_.end).sortBy(-_.atZone(Schedule.londonZone).toEpochSecond).headOption
  }
}

object Schedule {
  def parse(x: String): Schedule =
    Schedule(
      x.split(",")
        .map(_.split("/") match {
          case Array(start, end) => Maintenance(LocalDateTime.parse(start), LocalDateTime.parse(end))
        })
    )

  def londonNow(): LocalDateTime = LocalDateTime.now(londonZone)

  def londonZone: ZoneId = ZoneId.of("Europe/London")

}

case class Maintenance(start: LocalDateTime, end: LocalDateTime)
