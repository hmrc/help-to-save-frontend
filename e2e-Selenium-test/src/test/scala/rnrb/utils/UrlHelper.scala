package hts.utils

object UrlHelper {

  def convertToUrlSlug(text: String, preserveCase: Boolean = false) = {
    val url = text.replace("’", "")
      .replace("'", "")
      .replace(""""""", "")
      .replace(" ", "-")
      .trim

    preserveCase match {
      case true => url
      case _ => url.toLowerCase
    }
  }

  def getFullUrl(slug: String, preserveCase: Boolean = false) = {
    val convertedSlug = convertToUrlSlug(slug, preserveCase)
    s"${Configuration.settings.htsUrl}/$convertedSlug"
  }
}
