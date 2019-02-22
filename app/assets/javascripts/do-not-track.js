// IIFE to avoid polluting global scope
;(function (window, document) {
    // TODO: Consider doing this as a custom element

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

    // Do not track is IE9+ feature https://caniuse.com/#search=do%20not%20track
    var DO_NOT_TRACK_ENABLED = (
        navigator.doNotTrack === '1' ||
        navigator.doNotTrack === 'yes' || // Firefox 31 and below
        navigator.msDoNotTrack === '1' || // IE 9 - 10
        window.doNotTrack === '1' // IE 11 / Edge 16 and below
    )

    // Override for testing
    // DO_NOT_TRACK_ENABLED = false

    var $dntEmbedIframe = document.querySelectorAll('[data-dnt-embed]')

    if (!$dntEmbedIframe) {
        return
    }

    $dntEmbedIframe.forEach(function ($iframe) {
        var iframeSrc = $iframe.dataset.src
        var iframeTitle = $iframe.title

        var isYouTube = iframeSrc.indexOf('youtube.com') !== -1

        if (!DO_NOT_TRACK_ENABLED) {
            // Load the iframe regardless
            $iframe.src = iframeSrc
            $iframe.removeAttribute('hidden')
            return
        }

        // Create the wrapper element to contain everything.
        var $dntEmbed = document.createElement('div')
        $dntEmbed.className = 'dnt-embed dnt-embed--active'

        // Move styles from iFrame to the wrapper
        var originalStyles = $iframe.getAttribute('style')
        if (originalStyles) {
            $dntEmbed.setAttribute('style', originalStyles)
            $iframe.style = ''
        }

        // Move className from iFrame to wrapper
        var originalClassName = $iframe.className
        if (originalClassName) {
            $dntEmbed.className = originalClassName + ' ' + $dntEmbed.className
        }
        $iframe.className = 'dnt-embed__iframe'

        // Create the paragraph with the text explaining the component.
        var $text = document.createElement('p')
        $text.className = 'dnt-embed__text'
        $text.textContent = 'This embedded content is from a site that may not respect the Do Not Track setting enabled in your browser.'

        // Set a unique identifier for the text, so we can associate the button with it.
        // This should give the button more context when used with assistive technologies.
        var intoId = 'dnt-text-' + uuidv4()
        $text.id = intoId

        // Setup button to show embedded content
        var $button = document.createElement('button')
        $button.className = 'dnt-embed__button'
        $button.textContent = 'Show embedded content'

        // aria-describedby allows us to associate this button with the text, for more context.
        $button.setAttribute('aria-describedby', intoId)

        // Add loader, for slower connections.
        var $loader = document.createElement('div')
        $loader.className = 'dnt-embed__loader'
        $loader.setAttribute('hidden', true)
        var $loaderPip1 = document.createElement('div')
        var $loaderPip2 = document.createElement('div')
        var $loaderPip3 = document.createElement('div')
        $loaderPip1.className = 'dnt-embed__loader__pip'
        $loaderPip2.className = 'dnt-embed__loader__pip'
        $loaderPip3.className = 'dnt-embed__loader__pip'
        $loader.appendChild($loaderPip1)
        $loader.appendChild($loaderPip2)
        $loader.appendChild($loaderPip3)

        // Accessible name when focused
        $loader.setAttribute('aria-label', 'Loading embedded content')
        // Allow text to be focused programtically
        $loader.tabIndex = '-1'

        // Announce updates to loading state, using the announcer pattern.
        var $announcer = document.createElement('div')
        $announcer.className = 'dnt-embed__announcer'
        $announcer.setAttribute('aria-live', 'assertive')

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
            $button.textContent = 'Show ‘' + iframeTitle + '’'
        }

        // If the iFrame is a YouTube embed, we can get the thumbnail
        if (isYouTube) {
            var match = iframeSrc.match(/youtube.com\/embed\/(.*)/)
            var youtubeId = match[1]
            var $thumbnail = document.createElement('div')
            $thumbnail.className = 'dnt-embed__thumbnail'
            $thumbnail.style.backgroundImage = 'url(https://i3.ytimg.com/vi/' + youtubeId + '/mqdefault.jpg)'
            $dntEmbed.appendChild($thumbnail)

            // YouTube allows a privacy mode, if the user has DoNotTrack enabled,
            // It's likely they want this on.
            // This doesnt stop adtracking, maybe a service worker could?
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
                $thumbnail.setAttribute('hidden', true)
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
    })
})(window, document)




function isDoNotTrackEnabled () {
    const doNotTrackOption = (
        window.doNotTrack || // IE 11 / Edge 16 and below
        window.navigator.doNotTrack ||
        window.navigator.msDoNotTrack // IE 9 - 10
    )

    if (!doNotTrackOption) {
        return false
    }

    if (
        doNotTrackOption.charAt(0)  === '1' ||
        doNotTrackOption === 'yes' // Firefox 31 and below
    ) {
        return true
    }

    return false
}

// TODO: Might be able to do this nicer, with a server-side.
var isForced = (window.location.search === "?force=dnt")
var isEnabled = isDoNotTrackEnabled()
var isDisabled = !isEnabled

if(isEnabled) {
    document.querySelector('.js-dnt-enabled').removeAttribute('hidden')
} else if (isForced) {
    document.querySelector('.js-dnt-forced').removeAttribute('hidden')
    document.querySelector('[href="/?force=dnt"]').setAttribute('hidden', true)
    document.querySelector('[href="/"]').removeAttribute('hidden')

    window.doNotTrack = "1"
} else {
    document.querySelector('.js-dnt-disabled').removeAttribute('hidden')
    document.querySelector('[href="/"]').setAttribute('hidden', true)
    document.querySelector('[href="/?force=dnt"]').removeAttribute('hidden')
}