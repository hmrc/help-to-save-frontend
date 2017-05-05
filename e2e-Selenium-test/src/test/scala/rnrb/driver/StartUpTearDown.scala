package hts.driver

trait StartUpTearDown  {
  def isJsDisabled: Boolean = false
  def driver = Driver.webDriver
}
