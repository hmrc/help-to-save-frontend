/*
    Do Not Track (IE9+: https://caniuse.com/#search=do%20not%20track)
*/

;(function (global) {
  'use strict'

  var $ = global.jQuery
  var GOVUK = global.GOVUK || {}


  function DoNotTrackYouTube () {
    var self = this

    var config = {
      $dntEmbedIframe: document.querySelectorAll('[data-dnt-embed]'),
      doNotTrackIsEnabled: (
        navigator.doNotTrack === '1' ||
        navigator.doNotTrack === 'yes' || // Firefox 31 and below
        navigator.msDoNotTrack === '1' || // IE 9 - 10
        window.doNotTrack === '1' // IE 11 / Edge 16 and below
      ),
      showThumbnailOverlay: false
    }

    function wrapNode (toWrap, wrapper) {
      toWrap.parentNode.insertBefore(wrapper, toWrap)
      return wrapper.appendChild(toWrap)
    };

    // Used to generate a unique string, allows multiple instances of the component without
    // Them conflicting with each other.
    function uuidv4 () {
      return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8)
        return v.toString(16)
      })
    }

    // Announcer pattern requires timeouts to read messages in sequence
    function announce (message, $announcer) {
      $announcer.textContent = ','
      setTimeout(function () {
        $announcer.textContent = message
      }, 100)
    }

    function loadIframe($iframe) {
      var iframeSrc = $iframe.dataset.src

      $iframe.src = iframeSrc
      $iframe.removeAttribute('hidden')
    }

    function createLoader() {
      var loader = document.createElement('div')
      loader.className = 'dnt-embed__loader'
      loader.setAttribute('hidden', true)
      var loaderPip1 = document.createElement('div')
      var loaderPip2 = document.createElement('div')
      var loaderPip3 = document.createElement('div')
      loaderPip1.className = 'dnt-embed__loader__pip'
      loaderPip2.className = 'dnt-embed__loader__pip'
      loaderPip3.className = 'dnt-embed__loader__pip'
      loader.appendChild(loaderPip1)
      loader.appendChild(loaderPip2)
      loader.appendChild(loaderPip3)

      // Accessible name when focused
      loader.setAttribute('aria-label', 'Loading embedded content')
      // Allow text to be focused programmatically
      loader.tabIndex = '-1'

      return loader;
    }

    function createAnnouncer() {
      var announcer = document.createElement('div')
      announcer.className = 'dnt-embed__announcer'
      announcer.setAttribute('aria-live', 'assertive')

      return announcer
    }

    function createButton($iframe, intoId) {
      var button = document.createElement('button')
      button.className = 'dnt-embed__button'
      button.textContent = $iframe.dataset.dntDefaultParagraph

      // aria-describedby allows us to associate this button with the text, for more context.
      button.setAttribute('aria-describedby', intoId)

      return button
    }

    function createText($iframe, intoId) {
      var text = document.createElement('p')
      text.className = 'dnt-embed__text'
      text.textContent = $iframe.dataset.dntParagraph

      text.id = intoId

      return text
    }

    function setWrapperClassNames($dntEmbed, $iframe) {
      var originalClassName = $iframe.className
      if (originalClassName) {
        $dntEmbed.className = originalClassName + ' ' + $dntEmbed.className
      }
      $iframe.className = 'dnt-embed__iframe'
    }

    function createWrapperElement ($iframe) {
      var $dntEmbed = document.createElement('div')
      $dntEmbed.className = 'dnt-embed dnt-embed--active'

      // Move className from iFrame to wrapper
      setWrapperClassNames($dntEmbed, $iframe)

      return $dntEmbed
    }

    // If we want the video thumbnail as an overlay:
    function createThumbnail(youtubeId) {
      var $thumbnail = document.createElement('div')
      $thumbnail.className = 'dnt-embed__thumbnail'
      $thumbnail.style.backgroundImage = 'url(https://i3.ytimg.com/vi/' + youtubeId + '/mqdefault.jpg)' // IF BACKGROUND IMAGE IS REQUIRED

      return $thumbnail
    }

    function setupDNT($iframe) {
      var iframeSrc = $iframe.dataset.src
      var iframeTitle = $iframe.title
      var isYouTube = iframeSrc.indexOf('youtube.com') !== -1

      // Set a unique identifier for the text, so we can associate the button with it.
      // This should give the button more context when used with assistive technologies.
      var intoId = 'dnt-text-' + uuidv4()

      // Create the wrapper element to contain everything.
      var $dntEmbed = createWrapperElement($iframe)

      // Create the paragraph with the text explaining the component.
      var $text = createText($iframe, intoId)

      // Setup button to show embedded content
      var $button = createButton($iframe, intoId)

      // Add loader, for slower connections.
      var $loader = createLoader()

      // Announce updates to loading state, using the announcer pattern.
      var $announcer = createAnnouncer()

      // Add elements to the wrapper element.
      $dntEmbed.appendChild($text)
      $dntEmbed.appendChild($button)
      $dntEmbed.appendChild($loader)
      $dntEmbed.appendChild($announcer)

      // Wrap the iframe with everything else.
      wrapNode($iframe, $dntEmbed)

      // Hide the iFrame
      $iframe.setAttribute('hidden', true)

      // If the iFrame has a title, use it to give the button more context
      if (iframeTitle) {
        $button.textContent = $iframe.dataset.dntDefaultButtontextLeft + iframeTitle + $iframe.dataset.dntDefaultButtontextRight
      }

      // If the iFrame is a YouTube embed, we can get the thumbnail
      if (isYouTube) {
        var match = iframeSrc.match(/youtube.com\/embed\/(.*)/)
        var youtubeId = match[1]

        if (config.showThumbnailOverlay) $dntEmbed.appendChild(createThumbnail(youtubeId))

        // YouTube allows a privacy mode, if the user has DoNotTrack enabled,
        // It's likely they want this on.
        // https://support.google.com/youtube/answer/171780?hl=en

        iframeSrc = iframeSrc.replace('youtube', 'youtube-nocookie')
      }

      // TODO: Handle error cases if iframe fails to load
      $iframe.addEventListener('load', function (event) {
        // If the iFrame has a src set, and has loaded
        if ($iframe.src) {
          announce('Content loaded', $announcer)

          // Remove the embed UI
          $dntEmbed.classList.remove('dnt-embed--active')
          $text.setAttribute('hidden', true)
          $button.setAttribute('hidden', true)

          if (config.showThumbnailOverlay) $thumbnail.setAttribute('hidden', true)

          $loader.setAttribute('hidden', true)

          // Show the iFrame
          $iframe.removeAttribute('hidden')
          $iframe.focus()
        }
      })

      $button.addEventListener('click', function () {
        $iframe.src = iframeSrc
        $loader.removeAttribute('hidden')
        announce('Content loading', $announcer)
      })
    }

    // Set up event handlers
    function init () {
      if (!config.$dntEmbedIframe) {
        return
      }

      // Configure do not track for each embed
      var dntEmbedIfr = Array.from(config.$dntEmbedIframe); // To ensure support in IE (forEach unsupported for Node Lists)
      dntEmbedIfr.forEach(function ($iframe) {
        if (!config.doNotTrackIsEnabled) {
          loadIframe($iframe)
          return
        }
        setupDNT($iframe)
      })
    }

    self.DoNotTrackYouTube = function () {
      init()
    }
  }

  DoNotTrackYouTube.prototype.init = function () {
    this.DoNotTrackYouTube()
  }

  GOVUK.DoNotTrackYouTube = DoNotTrackYouTube
  global.GOVUK = GOVUK
})(window)