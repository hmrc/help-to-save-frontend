(function(window, document) {
    const form = document.getElementById("submission-form");

    if (!form) return;


form.addEventListener("submit", function(event) {

  const progressIndicator = document.getElementById("submission-progress");
  const liveRegionContent = document.getElementById("live-region-content");

  if (progressIndicator && liveRegionContent) {
    progressIndicator.replaceChildren(
        liveRegionContent.content.cloneNode(true)
    );

    progressIndicator.classList.add("active");
  }

  form.setAttribute("aria-busy", "true");

  for (const button of form.querySelectorAll("button")) {
    button.disabled = true;
  }
});
})(window, document);