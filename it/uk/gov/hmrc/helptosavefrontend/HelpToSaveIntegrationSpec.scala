package uk.gov.hmrc.helptosavefrontend

import java.time.LocalDate

import play.api.libs.json.{JsString, Json, Reads}
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.helptosavefrontend.HelpToSaveIntegrationSpec.{EmailData, EnrolmentData, UserCapData}
import uk.gov.hmrc.helptosavefrontend.MongoSupport.JSONCollectionOps
import uk.gov.hmrc.helptosavefrontend.models.EnrolmentStatus
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService

/**
  * NB: The integration tests here requires the following to be running:
  * - mongo to be running
  * - an instance of the help-to-save backend app
  * - an instance of the auth login stub running with all its associated dependencies
  */
class HelpToSaveIntegrationSpec extends IntegrationTest with AuthorisationSupport with MongoSupport {

  lazy val enrolmentCollection: JSONCollection = collection("enrolments")

  lazy val emailCollection: JSONCollection = collection("emails")

  lazy val userCapCollection: JSONCollection = collection("usercap")

  lazy val service: HelpToSaveService = application.injector.instanceOf[HelpToSaveService]

  val nino: String = new uk.gov.hmrc.domain.Generator().nextNino.nino

  "Making a call to enrol a user" must {

    "write an entry for that user in mongo" in {
      authorisedTest(nino, { implicit hc ⇒
        // we shouldn't be enrolled to start with
        await(service.getUserEnrolmentStatus().value) shouldBe Right(EnrolmentStatus.NotEnrolled)

        // now enrol
        val result = service.enrolUser()

        // check in mongo directly
        await(result.value).isRight shouldBe true
        await(enrolmentCollection.findAll[EnrolmentData]("nino" → JsString(nino))) shouldBe List(EnrolmentData(nino, itmpHtSFlag = true))

        // check we are now enrolled
        await(service.getUserEnrolmentStatus().value) shouldBe Right(EnrolmentStatus.Enrolled(itmpHtSFlag = true))
      })

    }

  }

  "Making a call to store emails" must {

    "write the email to mongo" in {
      authorisedTest(nino, { implicit hc ⇒
        val email = "abc@def"

        // we shouldn't have an email to start with
        await(service.getConfirmedEmail().value) shouldBe Right(None)

        // now store an email
        val result = service.storeConfirmedEmail(email)
        await(result.value).isRight shouldBe true

        // we should be able to retrieve it from mongo now
        await(service.getConfirmedEmail().value) shouldBe Right(Some(email))

        // double check we have something in mongo - we can't check the actual value of the email since
        // it will be encrypted
        await(emailCollection.findAll[EmailData]("nino" → JsString(nino))).map(_.nino) shouldBe List(nino)
      })
    }

  }

  "Updating the user cap" must {

    "increment the counts correctly" in {
      authorisedTest(nino, { implicit hc ⇒
        val today = LocalDate.now()

        // get the initial data
        val userCapData: UserCapData = await(userCapCollection.findAll[UserCapData]()) match {
          case Nil      ⇒ UserCapData(today, 0, 0)
          case h :: Nil ⇒ h
          case l        ⇒ fail(s"Unexpected number of user cap data entries found: ${l.size}. Expected 1 or 0")
        }

        val result = service.updateUserCount()
        await(result.value) shouldBe Right(())

        // now check the numbers have been incremented
        await(userCapCollection.findAll[UserCapData]()) shouldBe List(
          UserCapData(today, if (today.equals(userCapData.date)) { userCapData.dailyCount + 1 } else { 1 }, userCapData.totalCount + 1))
      })
    }

  }

  override def afterAll(): Unit = {
    await(enrolmentCollection.removeAll("nino" → JsString(nino)))
    await(emailCollection.removeAll("nino" → JsString(nino)))
    super.afterAll()
  }

}

object HelpToSaveIntegrationSpec {

  case class EnrolmentData(nino: String, itmpHtSFlag: Boolean)

  object EnrolmentData {
    implicit val reads: Reads[EnrolmentData] = Json.reads[EnrolmentData]
  }

  case class EmailData(nino: String, email: String)

  object EmailData {
    implicit val reads: Reads[EmailData] = Json.reads[EmailData]
  }

  case class UserCapData(date: LocalDate, dailyCount: Int, totalCount: Int)

  object UserCapData {
    implicit val reads: Reads[UserCapData] = Json.reads[UserCapData]
  }

}
