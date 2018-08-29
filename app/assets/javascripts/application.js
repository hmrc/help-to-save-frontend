/* global $ */
/* global jQuery */
/* global GOVUK */

$(document).ready(function () {
  // Turn off jQuery animation
  jQuery.fx.off = true

  // Where .multiple-choice uses the data-target attribute
  // to toggle hidden content
  var showHideContent = new GOVUK.ShowHideContent()
  showHideContent.init()

  function setUpErrorSummary($el) {
    $el.find('a').each(function (i, link) {
      $(link).on('click', function () {
        var focuses = $(this).attr('data-focuses');
        $('#' + focuses).trigger('focus')
      })
    });
    // focus on the error summary
    $el.trigger('focus');
  }

  setUpErrorSummary($('.error-summary'));

})
