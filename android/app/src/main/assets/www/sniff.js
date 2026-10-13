/**
 * UC Browser DVN (Detect Video Now) - JavaScript injection
 * 
 * Rewritten based on UC Browser's video detection approach:
 *   VideoDetectInfo: STATE_INIT -> STATE_DETECTING -> STATE_DETECTED
 *   IVideoDetectRequestResult: onDetectResult(blocked, code, url)
 *   CoreJsInterface.java patterns
 * 
 * UC Browser uses this script injected on every page to:
 *   1. Monitor <video> elements for playback
 *   2. Intercept video src/currentSrc  
 *   3. Report detected video URLs to the native bridge
 *   4. Monitor MSE (MediaSource) streams
 */
(function() {
    'use strict';

    // UC Browser VideoDetectInfo state constants
    var STATE_INIT = -1;
    var STATE_DETECTING = 0;
    var STATE_DETECTED = 1;
    var detectedUrls = new Set();

    // UC Browser DVN: report video URL to native bridge
    function reportVideo(url, width, height) {
        if (!url || url.startsWith('blob:') || url.startsWith('data:')) return;
        if (detectedUrls.has(url)) return;
        detectedUrls.add(url);
        // VideoDetectInfo state transition: INIT -> DETECTING -> DETECTED
        if (window.BlazeBridge) {
            try { window.BlazeBridge.onVideo(url); } catch(e) {}
        }
    }

    // UC Browser: report page video metadata (onPageVideo)
    function reportPageVideo(v) {
        if (!v) return;
        var w = v.videoWidth || v.clientWidth || 0;
        var h = v.videoHeight || v.clientHeight || 0;
        if (window.BlazeBridge && w > 0) {
            try { window.BlazeBridge.onPageVideo(w, h); } catch(e) {}
        }
        // Report video src URL (UC Browser DVN pattern)
        var src = v.currentSrc || v.src;
        if (src && src.length > 0 && !src.startsWith('blob:')) reportVideo(src, w, h);
    }

    // UC Browser: scan all existing video elements (DVN initial scan)
    function scanVideos() {
        var videos = document.querySelectorAll('video');
        for (var i = 0; i < videos.length; i++) {
            var v = videos[i];
            if (v.readyState > 0 || v.currentSrc || v.src) reportPageVideo(v);
        }
    }

    // UC Browser: intercept HTMLVideoElement src assignment
    // Mirrors UC Browser's WebView JavaScript bridge video detection
    var origSrcDescriptor = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
    if (origSrcDescriptor && origSrcDescriptor.set) {
        Object.defineProperty(HTMLMediaElement.prototype, 'src', {
            set: function(value) {
                if (this instanceof HTMLVideoElement && value && !value.startsWith('blob:') && !value.startsWith('data:')) {
                    reportVideo(value, this.videoWidth || 0, this.videoHeight || 0);
                }
                if (origSrcDescriptor.set) origSrcDescriptor.set.call(this, value);
            },
            get: origSrcDescriptor.get,
            configurable: true
        });
    }

    // UC Browser: monitor play events (STATE_DETECTING -> STATE_DETECTED)
    document.addEventListener('play', function(e) {
        if (e.target instanceof HTMLVideoElement) reportPageVideo(e.target);
    }, true);

    // UC Browser: monitor canplay events  
    document.addEventListener('canplay', function(e) {
        if (e.target instanceof HTMLVideoElement) reportPageVideo(e.target);
    }, true);

    // UC Browser: MutationObserver for dynamically added video elements
    // UC Browser uses this to detect videos added by JS frameworks (React, Vue, etc.)
    if (window.MutationObserver) {
        var observer = new MutationObserver(function(mutations) {
            for (var i = 0; i < mutations.length; i++) {
                var added = mutations[i].addedNodes;
                for (var j = 0; j < added.length; j++) {
                    var node = added[j];
                    if (node.tagName === 'VIDEO') {
                        reportPageVideo(node);
                    } else if (node.querySelectorAll) {
                        var vids = node.querySelectorAll('video');
                        for (var k = 0; k < vids.length; k++) reportPageVideo(vids[k]);
                    }
                }
            }
        });
        observer.observe(document.documentElement, {childList: true, subtree: true});
    }

    // UC Browser: intercept fetch and XHR for video manifests
    // Mirrors UC Browser's shouldInterceptRequest for m3u8/mpd detection
    var origFetch = window.fetch;
    if (origFetch) {
        window.fetch = function(input, init) {
            var url = typeof input === 'string' ? input : (input.url || '');
            var lower = url.toLowerCase();
            if ((lower.includes('.m3u8') || lower.includes('.mpd') || lower.includes('manifest'))
                    && !lower.includes('googlesyndication') && !lower.startsWith('blob:')) {
                reportVideo(url, 0, 0);
            }
            return origFetch.apply(this, arguments);
        };
    }

    // Initial scan
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', scanVideos);
    } else {
        scanVideos();
    }

    // Notify BlazeBridge of prefetch opportunities (UC Browser PrereadOptions pattern)
    document.addEventListener('mouseover', function(e) {
        if (e.target && e.target.tagName === 'A' && e.target.href
                && window.BlazeBridge && e.target.href.startsWith('http')) {
            try { window.BlazeBridge.prefetch(e.target.href); } catch(ex) {}
        }
    }, {passive: true});

})();
