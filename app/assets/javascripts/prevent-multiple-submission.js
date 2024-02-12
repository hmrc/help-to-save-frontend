(function(window, document) {
    for (const form of document.getElementsByTagName("form")) {
      if (form.dataset.preventMultipleSubmit === "true") {
        form.addEventListener("submit", () => {
          for (const submit of form.querySelectorAll("button")) {
            submit.disabled = true;
          }
          const progressIndicator = document.getElementById('submission-progress')
          const liveRegionContent = document.getElementById('live-region-content')
          if(progressIndicator && liveRegionContent) {
            progressIndicator.setAttribute("tabindex", "-1")
            progressIndicator.focus()
            window.setTimeout(function(){
              progressIndicator.removeAttribute("tabindex")
              progressIndicator.classList.add("active")
            }, 10)
            window.setTimeout(function(){
              progressIndicator.appendChild(liveRegionContent.content)
            }, 20)
          }
        })
      }
    }
})(window, document);
