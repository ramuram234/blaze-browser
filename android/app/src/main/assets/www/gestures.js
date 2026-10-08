(function () {
  if (window.__blazeGestures) return;
  window.__blazeGestures = true;

  var mode = "none";
  var startX = 0;
  var startY = 0;
  var startTime = 0;
  var originVol = 80;
  var originBright = 100;
  var target = null;
  var hud = null;
  var nextPreloaded = false;

  function clamp(n, a, b) { return Math.max(a, Math.min(b, n)); }
  function fmt(s) {
    s = Math.max(0, Math.floor(s || 0));
    var m = Math.floor(s / 60);
    var r = s % 60;
    return m + ":" + (r < 10 ? "0" : "") + r;
  }
  function videos() {
    return Array.prototype.slice.call(document.querySelectorAll("video"));
  }
  function activeVideo(x, y) {
    var list = videos();
    var i, v, r;
    for (i = 0; i < list.length; i++) {
      v = list[i];
      r = v.getBoundingClientRect();
      if (r.width < 80 || r.height < 48) continue;
      if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) return v;
    }
    for (i = 0; i < list.length; i++) {
      if (!list[i].paused && list[i].readyState >= 2) return list[i];
    }
    return null;
  }
  function ensureHud() {
    if (hud) return hud;
    hud = document.createElement("div");
    hud.style.cssText = "position:fixed;z-index:2147483647;left:50%;top:42%;transform:translate(-50%,-50%);background:rgba(12,13,16,.82);color:#fff;padding:12px 16px;border-radius:16px;font:600 14px sans-serif;pointer-events:none;display:none;";
    document.documentElement.appendChild(hud);
    return hud;
  }
  function show(text) {
    var el = ensureHud();
    el.textContent = text;
    el.style.display = "block";
  }
  function hide() {
    if (hud) hud.style.display = "none";
  }
  function preloadAhead(current) {
    if (nextPreloaded) return;
    var list = videos();
    var i;
    for (i = 0; i < list.length; i++) {
      if (list[i] === current) continue;
      try { list[i].preload = "auto"; list[i].load(); } catch (e) {}
    }
    var sources = document.querySelectorAll("video source, a[href$='.mp4'], a[href$='.webm']");
    var n = 0;
    sources.forEach(function (node) {
      if (n >= 2) return;
      var src = node.src || node.href || node.getAttribute("src");
      if (!src || src === current.currentSrc) return;
      var ghost = document.createElement("video");
      ghost.preload = "auto";
      ghost.muted = true;
      ghost.src = src;
      ghost.setAttribute("playsinline", "");
      ghost.style.display = "none";
      document.documentElement.appendChild(ghost);
      try { ghost.load(); } catch (e) {}
      n += 1;
    });
    nextPreloaded = true;
  }

  document.addEventListener("touchstart", function (e) {
    if (!e.touches || !e.touches[0]) return;
    var t = e.touches[0];
    target = activeVideo(t.clientX, t.clientY);
    if (!target) { mode = "none"; return; }
    mode = "pending";
    startX = t.clientX;
    startY = t.clientY;
    startTime = target.currentTime || 0;
    try { target.preload = "auto"; } catch (err) {}
    preloadAhead(target);
    try { originVol = window.BlazeBridge && BlazeBridge.getVolume ? BlazeBridge.getVolume() : 80; } catch (err) { originVol = 80; }
    originBright = window.__blazeBright || 100;
  }, { passive: false });

  document.addEventListener("touchmove", function (e) {
    if (!target || mode === "none" || !e.touches || !e.touches[0]) return;
    var t = e.touches[0];
    var dx = t.clientX - startX;
    var dy = t.clientY - startY;
    var w = window.innerWidth || 1;
    var h = window.innerHeight || 1;
    if (mode === "pending") {
      if (Math.hypot(dx, dy) < 14) return;
      if (Math.abs(dx) > Math.abs(dy) * 1.15) mode = "seek";
      else mode = startX < w / 2 ? "bright" : "volume";
    }
    if (mode === "seek") {
      var dur = target.duration && isFinite(target.duration) ? target.duration : 0;
      var span = Math.max(dur * 0.45, 40);
      var next = clamp(startTime + (dx / w) * span, 0, dur || span);
      show((dx >= 0 ? "+" : "-") + fmt(Math.abs(next - startTime)) + "   " + fmt(next));
      try { target.currentTime = next; } catch (err) {}
      e.preventDefault();
    } else if (mode === "volume") {
      var vol = clamp(Math.round(originVol - (dy / h) * 120), 0, 100);
      show("Volume " + vol + "%");
      try { if (window.BlazeBridge) BlazeBridge.setVolume(vol); } catch (err) {}
      e.preventDefault();
    } else if (mode === "bright") {
      var bright = clamp(Math.round(originBright - (dy / h) * 120), 8, 100);
      window.__blazeBright = bright;
      show("Brightness " + bright + "%");
      try { if (window.BlazeBridge) BlazeBridge.setBrightness(bright); } catch (err) {}
      e.preventDefault();
    }
  }, { passive: false });

  function end() {
    mode = "none";
    target = null;
    setTimeout(hide, 280);
  }
  document.addEventListener("touchend", end);
  document.addEventListener("touchcancel", end);
})();
