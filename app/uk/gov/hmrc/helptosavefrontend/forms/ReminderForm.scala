package uk.gov.hmrc.helptosavefrontend.forms

import cats.instances.string._
import cats.syntax.eq._
import play.api.data.Form
import play.api.data.Forms.{mapping, text}


object ReminderForm {
def giveRemindersDetailsForm()(): Form[ReminderForm] = Form(
  mapping(
    "first" → text.verifying(l ⇒ l === "Yes" || l === "No"),
    "twentyfive" → text.verifying(l ⇒ l === "Yes" || l === "No"),
    "both" → text.verifying(l ⇒ l === "Yes" || l === "No")
  )(ReminderForm.apply)(ReminderForm.unapply)
)
}


case class ReminderForm(first: String,twentyfive : String, both: String)

