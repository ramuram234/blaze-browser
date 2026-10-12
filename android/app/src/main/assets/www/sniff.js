(function () {
  if (window.__blazeSniff) return;
  window.__blazeSniff = true;

  var detectedUrls = {};

  function send(url) {
    if (!url || url.indexOf('data:') === 0) return;
    if (detectedUrls[url]) return;
    detectedUrls[url] = true;
    // Skip short ad clips (url contains dur=N.N where N < 20)
    var durMatch = url.match(/[?&]dur=([0-9.]+)/);
    if (durMatch && parseFloat(durMatch[1]) < 20) return;
    try { BlazeBridge.onVideo(url); } catch (e) {}
  }

  function reportElement(v) {
    if (!v || v.tagName !== 'VIDEO') return;
    var box = v.getBoundingClientRect();
    var tiny = box.width < 80 || box.height < 48;
    if (tiny && v.paused) return;
    var w = v.videoWidth || Math.round(box.width) || 0;
    var h = v.videoHeight || Math.round(box.height) || 0;
    try { BlazeBridge.onPageVideo(w, h); } catch (e) {}
    var src = v.currentSrc || v.src;
    if (src && src.indexOf('blob:') !== 0) send(src);
    // Also check <source> children
    var sources = v.querySelectorAll('source');
    for (var i = 0; i < sources.length; i++) {
      if (sources[i].src) send(sources[i].src);
    }
  }

  function scanAll() {
    var nodes = document.querySelectorAll('video');
    for (var i = 0; i < nodes.length; i++) reportElement(nodes[i]);
    // Also scan audio elements with video MIME
    var audios = document.querySelectorAll('audio');
    for (var i = 0; i < audios.length; i++) {
      var s = audios[i].currentSrc || audios[i].src;
      if (s && (s.indexOf('.mp4') > -1 || s.indexOf('.webm') > -1)) send(s);
    }
  }

  // --- XHR Interception: catch HLS/DASH manifests fetched by players ---
  var origXHROpen = XMLHttpRequest.prototype.open;
  var origXHRSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function(method, url) {
    this.__blazeUrl = url;
    return origXHROpen.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function() {
    var url = this.__blazeUrl;
    if (url && typeof url === 'string') {
      var u = url.toLowerCase();
      if (u.indexOf('.m3u8') > -1 || u.indexOf('.mpd') > -1 ||
          u.indexOf('format=m3u8') > -1 || u.indexOf('mime=video') > -1 ||
          (u.indexOf('googlevideo.com') > -1 && u.indexOf('videoplayback') > -1) ||
          u.indexOf('.mp4') > -1 || u.indexOf('.webm') > -1) {
        send(url);
      }
    }
    return origXHRSend.apply(this, arguments);
  };

  // --- Fetch Interception: catch manifest requests via fetch() ---
  if (window.fetch) {
    var origFetch = window.fetch;
    window.fetch = function(input) {
      var url = (input && typeof input === 'string') ? input :
                (input && input.url ? input.url : '');
      if (url) {
        var u = url.toLowerCase();
        if (u.indexOf('.m3u8') > -1 || u.indexOf('.mpd') > -1 ||
            u.indexOf('format=m3u8') > -1 || u.indexOf('mime=video') > -1 ||
            (u.indexOf('googlevideo.com') > -1 && u.indexOf('videoplayback') > -1)) {
          send(url);
        }
      }
      return origFetch.apply(this, arguments);
    };
  }

  // --- MediaSource tracking: detect blob: video by watching addSourceBuffer ---
  if (window.MediaSource) {
    var origAddSB = MediaSource.prototype.addSourceBuffer;
    MediaSource.prototype.addSourceBuffer = function(mimeType) {
      if (mimeType && mimeType.indexOf('video') > -1) {
        // Find which video element is using this MediaSource blob URL
        setTimeout(function() {
          var vids = document.querySelectorAll('video');
          for (var i = 0; i < vids.length; i++) {
            var src = vids[i].currentSrc || vids[i].src;
            if (src && src.indexOf('blob:') === 0) {
              var w = vids[i].videoWidth || Math.round(vids[i].clientWidth) || 0;
              var h = vids[i].videoHeight || Math.round(vids[i].clientHeight) || 0;
              try { BlazeBridge.onPageVideo(w, h); } catch(e) {}
            }
          }
        }, 300);
      }
      return origAddSB.apply(this, arguments);
    };
  }

  // --- MutationObserver: detect dynamically added <video> elements ---
  try {
    new MutationObserver(function(mutations) {
      mutations.forEach(function(m) {
        m.addedNodes.forEach(function(n) {
          if (n.tagName === 'VIDEO') reportElement(n);
          else if (n.querySelectorAll) {
            var vids = n.querySelectorAll('video');
            for (var i = 0; i < vids.length; i++) reportElement(vids[i]);
          }
        });
        // Watch for src attribute changes on video elements
        if (m.type === 'attributes' && m.target.tagName === 'VIDEO') {
          reportElement(m.target);
        }
      });
    }).observe(document.documentElement, {
      subtree: true, childList: true,
      attributes: true, attributeFilter: ['src', 'currentSrc']
    });
  } catch (e) {}

  // --- Event listeners ---
  document.addEventListener('play', function(e) { reportElement(e.target); }, true);
  document.addEventListener('loadedmetadata', function(e) { reportElement(e.target); }, true);
  document.addEventListener('canplay', function(e) { reportElement(e.target); }, true);

  // --- Prefetch on touchstart hover ---
  document.addEventListener('touchstart', function(e) {
    var n = e.target;
    while (n && n.tagName !== 'A') n = n.parentElement;
    if (n && n.href && n.href.indexOf('http') === 0) {
      try { BlazeBridge.prefetch(n.href); } catch (err) {}
    }
  }, true);

  // --- Periodic scan (catches lazy-loaded videos) ---
  setInterval(scanAll, 2000);
  scanAll();
})();