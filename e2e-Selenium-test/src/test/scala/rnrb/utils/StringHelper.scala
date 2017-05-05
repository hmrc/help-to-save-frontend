package rnrb.utils

object StringHelper {
  def cleanGherkinInput(input: String) = input.replace("’", "")
                                              .replace("'", "")
                                              .replace(""""""", "")
                                              .replace(" ", "")
                                              .replace(")", "")
                                              .replace("(", "")
                                              .trim

  def stripCommas(input: String) = input.replace(",", "").trim
}
