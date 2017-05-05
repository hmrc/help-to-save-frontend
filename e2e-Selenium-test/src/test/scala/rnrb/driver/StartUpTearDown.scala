package rnrb.driver

trait StartUpTearDown  {
  def isJsDisabled: Boolean = false
  def driver = Driver.webDriver
}
