(function () {
  if (window.__blazeSniff) return;
  window.__blazeSniff = true;

  function send(url) {
    if (!url || url.indexOf("blob:") === 0 || url.indexOf("data:") === 0) return;
    try { BlazeBridge.onVideo(url); } catch (e) {}
  }

  function scan() {
    var nodes = document.querySelectorAll("video,source");
    for (var i = 0; i < nodes.length; i++) {
      send(nodes[i].currentSrc || nodes[i].src);
    }
  }

  try {
    new MutationObserver(scan).observe(document.documentElement, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ["src"]
    });
  } catch (e) {}

  document.addEventListener("play", function (e) {
    if (e.target && e.target.currentSrc) send(e.target.currentSrc);
  }, true);

  document.addEventListener("touchstart", function (e) {
    var n = e.target;
    while (n && n.tagName !== "A") n = n.parentElement;
    if (n && n.href && n.href.indexOf("http") === 0) {
      try { BlazeBridge.prefetch(n.href); } catch (err) {}
    }
  }, true);

  scan();
})();
