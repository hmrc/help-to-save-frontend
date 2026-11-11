(function(window, document) {
    const form = document.getElementById("submission-form");

    if (!form) return;

    form.addEventListener("submit", function() {
      const progressIndicator = document.getElementById("submission-progress");
      const liveRegionContent = document.getElementById("live-region-content");

      // Announce the loading message immediately
      if (progressIndicator && liveRegionContent) {
        progressIndicator.appendChild(
            liveRegionContent.content.cloneNode(true)
        );
        progressIndicator.classList.add("active");
      }

      // Mark the form as busy
      form.setAttribute("aria-busy", "true");

      // Prevent multiple submissions
      for (const button of form.querySelectorAll("button")) {
        button.disabled = true;
      }
    });
  })(window, document);