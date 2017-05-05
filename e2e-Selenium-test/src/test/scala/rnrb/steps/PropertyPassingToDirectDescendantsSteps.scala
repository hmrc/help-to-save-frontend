package hts.steps

import hts.pages.PropertyPassingToDirectDescendantsPage

class PropertyPassingToDirectDescendantsSteps extends Steps {

  When("""^(?:I )?answer (Yes, all of it passed|Yes, some of it passed|No) to Property Passing To Direct Descendants$""") { (answer: String) =>
    PropertyPassingToDirectDescendantsPage.fillPage(answer)
  }
}
