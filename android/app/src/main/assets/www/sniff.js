(function () {
  if (window.__blazeSniff) return;
  window.__blazeSniff = true;

  function send(url) {
    if (!url || url.indexOf("blob:") === 0 || url.indexOf("data:") === 0) return;
    try { BlazeBridge.onVideo(url); } catch (e) {}
  }

  function report(v) {
    if (!v || v.tagName !== "VIDEO") return;
    var box = v.getBoundingClientRect();
    var tiny = box.width < 80 || box.height < 48;
    if (tiny && v.paused) return;
    var w = v.videoWidth || Math.round(box.width) || 0;
    var h = v.videoHeight || Math.round(box.height) || 0;
    try { BlazeBridge.onPageVideo(w, h); } catch (e) {}
    send(v.currentSrc || v.src);
  }

  function scan() {
    var nodes = document.querySelectorAll("video");
    for (var i = 0; i < nodes.length; i++) report(nodes[i]);
  }

  try {
    new MutationObserver(scan).observe(document.documentElement, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ["src"]
    });
  } catch (e) {}

  document.addEventListener("play", function (e) { report(e.target); }, true);
  document.addEventListener("loadedmetadata", function (e) { report(e.target); }, true);
  setInterval(scan, 2000);

  document.addEventListener("touchstart", function (e) {
    var n = e.target;
    while (n && n.tagName !== "A") n = n.parentElement;
    if (n && n.href && n.href.indexOf("http") === 0) {
      try { BlazeBridge.prefetch(n.href); } catch (err) {}
    }
  }, true);

  scan();
})();
