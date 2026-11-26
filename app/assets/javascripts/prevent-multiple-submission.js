(function (window, document) {
    const form = document.getElementById("submission-form");
    if (!form) return;

    const progressIndicator = document.getElementById("submission-progress");
    const liveRegionContent = document.getElementById("live-region-content");
    let clearTimer;

    function clearProgress() {
        if (!progressIndicator) return;
        clearTimeout(clearTimer);
        progressIndicator.replaceChildren();
        progressIndicator.classList.remove("active");
        form.removeAttribute("aria-busy");
    }

    form.addEventListener("submit", function () {
        if (progressIndicator && liveRegionContent) {
            progressIndicator.replaceChildren(
                liveRegionContent.content.cloneNode(true)
            );
            progressIndicator.classList.add("active");

            clearTimer = setTimeout(clearProgress, 3000);
        }

        form.setAttribute("aria-busy", "true");

        for (const button of form.querySelectorAll("button")) {
            button.disabled = true;
        }
    });

    window.addEventListener("pagehide", clearProgress);

    window.addEventListener("pageshow", function (e) {
        if (e.persisted) {
            clearProgress();
            for (const button of form.querySelectorAll("button")) {
                button.disabled = false;
            }
        }
    });
})(window, document);