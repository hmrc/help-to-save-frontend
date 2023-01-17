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

  // <details> polyfill for FF
  GOVUK.details.init()

  // init Do Not Track for YouTube embeds
  var DoNotTrackYouTube = new GOVUK.DoNotTrackYouTube();
  DoNotTrackYouTube.init()

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

  if (window.jsConfig && window.jsConfig.timeoutEnabled) {
    timeoutDialog = GOVUK.timeoutDialog({
      timeout: window.jsConfig.timeout,
      countdown: window.jsConfig.countdown,
      keepAliveUrl: window.jsConfig.keep_alive_url,
      signOutUrl: window.jsConfig.logout_url,
      title: window.jsConfig.timeoutTitle,
      message: window.jsConfig.timeoutMessage,
      keepAliveButtonText: window.jsConfig.timeoutKeepAliveButtonText,
      signOutButtonText: window.jsConfig.timeoutSignOutButtonText,
      properties: {
        minutes: window.jsConfig.timeoutMinutes,
        minute: window.jsConfig.timeoutMinute,
        seconds: window.jsConfig.timeoutSeconds,
        second: window.jsConfig.timeoutSecond
      }
    })
  }

})
