package hmrc.utils

import java.io.FileInputStream
import java.util.Properties

import org.joda.time.LocalDate

object BaseUtil {
  val fis = new FileInputStream("src/test/resources/message.properties")
  val props: Properties = new Properties()
  loadProperties(fis, props)

  val feFis = new FileInputStream("src/test/resources/message.properties")
  val feProps: Properties = new Properties()
  loadProperties(feFis, feProps)

  val idFis = new FileInputStream("src/test/resources/id.properties")
  val idProps: Properties = new Properties()
  loadProperties(idFis, idProps)


  def loadProperties(aFis: FileInputStream, aProps: Properties) = {
    try {aProps.load(aFis)}
    catch {case e: Exception => println("Exception loading file")}
    finally {
      if (aFis != null) {
        try {aFis.close()}
        catch {case e: Exception => println("Exception on closing file")}
      }
    }
  }

  //This method handles the retrieval from property files in ISO 8859-1 when we need UTF-8 to handle many special chars
  def getProperty(key: String, aProps: Properties): String = {
    try {
      val utf8Property = new String(aProps.getProperty(key).getBytes("ISO-8859-1"), "UTF-8")
      utf8Property.replaceAll("''","'")
    }
    catch {case e: Exception => "Exception getting property"}
  }

  def getMessage(key: String) = getProperty(key, props).replaceAll("''", "'")
  def getFrontendMessage(key: String) = getProperty(key, feProps).replaceAll("''", "'")
  def getId(key: String) = getProperty(key, idProps).replaceAll("''", "'")

  def getFrontendMessage(key: String, substitution: String):String = {
    getFrontendMessage(key).replaceAll("\\{0}", substitution)
  }

  //TODO: Could make this a tuple of strings, for replacement, or make a single function override that takes a key and a list
  def getFrontendMessage(key: String, substitution0: String, substitution1: String):String = {
    getFrontendMessage(key).replaceAll("\\{0}", substitution0).
      replaceAll("\\{1}", substitution1)
  }
  def getFrontendMessage(key: String, substitution0: String, substitution1: String, substitution2: String):String = {
    getFrontendMessage(key).replaceAll("\\{0}", substitution0).
      replaceAll("\\{1}", substitution1).
      replaceAll("\\{2}", substitution2)
  }


  val thisYear:String = LocalDate.now().toString("yyyy")
  val lastYear=LocalDate.now().minusYears(1).toString("yyyy")


}
