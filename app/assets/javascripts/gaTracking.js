$(".ga-track-event").click(function(event) {
  if ($(this).is('a')) {
    event.preventDefault();
    var redirectUrl = $(this).attr('href');
    gaWithCallback('send', 'event', $(this).data('ga-event-category'), $(this).data('ga-event-action'), $(this).data('ga-event-label'), function() {
      window.location.href = redirectUrl;
    });
  } else {
    ga('send', 'event', $(this).data('ga-event-category'), $(this).data('ga-event-action'), $(this).data('ga-event-label'));
  }
});

function gaWithCallback(send, event, category, action, label, callback) {
  ga(send, event, category, action, label, {
    hitCallback: gaCallback
  });
  var gaCallbackCalled = false;
  setTimeout(gaCallback, 5000);

  function gaCallback() {
    if(!gaCallbackCalled) {
      callback();
      gaCallbackCalled = true;
    }
  }
}