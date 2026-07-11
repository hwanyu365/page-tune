package com.hwanyu365.webplugin.util

object JsScripts {

    /**
     * JavaScript injected into the WebView during Plugin creation mode.
     * When the user touches any HTML element, it prevents default navigation
     * and sends the CSS selector, id, attributes, and textContent back to Kotlin.
     */
    val ELEMENT_PICKER_SCRIPT = """
        (function() {
            // Helper to generate CSS selector for an element
            function getCssSelector(el) {
                if (el.id) return '#' + el.id;
                if (el === document.body) return 'body';
                
                var path = [];
                while (el && el.nodeType === Node.ELEMENT_NODE) {
                    var selector = el.nodeName.toLowerCase();
                    if (el.className) {
                        // Filter out empty classes and join with dots. Exclude classes with numbers (often dynamic/auto-generated)
                        var classes = Array.from(el.classList).filter(c => c.trim().length > 0 && !/\d/.test(c));
                        if (classes.length > 0) {
                            selector += '.' + classes.join('.');
                        }
                    } else {
                        var sib = el, nth = 1;
                        while (sib = sib.previousElementSibling) {
                            if (sib.nodeName.toLowerCase() == el.nodeName.toLowerCase()) nth++;
                        }
                        selector += ":nth-of-type(" + nth + ")";
                    }
                    path.unshift(selector);
                    el = el.parentNode;
                }
                return path.join(' > ');
            }

            // Extract detailed information of touched element for resilient matching
            function extractElementDetails(el) {
                var details = {
                    selector: getCssSelector(el),
                    tagName: el.tagName ? el.tagName.toLowerCase() : "",
                    textContent: el.textContent ? el.textContent.trim().substring(0, 30) : "",
                    id: el.id || "",
                    attributes: {}
                };
                
                // Collect meaningful attributes, excluding dynamic/page-specific ones
                var excludeAttrs = ['id', 'class', 'style', 'href', 'src', 'action'];
                for (var i = 0; i < el.attributes.length; i++) {
                    var attr = el.attributes[i];
                    if (excludeAttrs.indexOf(attr.name) === -1 && attr.name.indexOf('data-') !== 0) {
                        details.attributes[attr.name] = attr.value;
                    }
                }
                return JSON.stringify(details);
            }

            // Highlight chosen element visually
            window.prevHighlighted = null;
            function highlightElement(el) {
                if (window.prevHighlighted && window.prevHighlighted !== el) {
                    window.prevHighlighted.style.removeProperty('outline');
                }
                window.prevHighlighted = el;
                el.style.setProperty('outline', '3px solid #ff9800', 'important'); // Orange outline
            }

            window.currentlySelectedElement = null;
            window.webPluginInspectEnabled = true;

            // Transverse up (to parent node)
            window.webPluginSelectParent = function() {
                var current = window.currentlySelectedElement;
                if (current && current.parentElement) {
                    var parent = current.parentElement;
                    if (parent !== document.documentElement && parent !== document.body) {
                        window.currentlySelectedElement = parent;
                        highlightElement(parent);
                        var detailsJson = extractElementDetails(parent);
                        AndroidBridge.onElementSelected(detailsJson);
                    }
                }
            };

            // Transverse down (to first child node)
            window.webPluginSelectChild = function() {
                var current = window.currentlySelectedElement;
                if (current && current.firstElementChild) {
                    var child = current.firstElementChild;
                    window.currentlySelectedElement = child;
                    highlightElement(child);
                    var detailsJson = extractElementDetails(child);
                    AndroidBridge.onElementSelected(detailsJson);
                }
            };

            // Clear highlights when inspector turns off
            window.webPluginClearHighlight = function() {
                if (window.prevHighlighted) {
                    window.prevHighlighted.style.removeProperty('outline');
                    window.prevHighlighted = null;
                }
                window.currentlySelectedElement = null;
            };

            // Capture clicks on the entire document
            function clickHandler(e) {
                if (!window.webPluginInspectEnabled) return;
                
                e.preventDefault();
                e.stopPropagation();
                
                window.currentlySelectedElement = e.target;
                highlightElement(e.target);
                
                var detailsJson = extractElementDetails(e.target);
                AndroidBridge.onElementSelected(detailsJson);
            }

            // Bind click and touch events
            document.removeEventListener('click', clickHandler, true);
            document.addEventListener('click', clickHandler, true);
            
            console.log("WebPlugin: Inspector script injected.");
        })();
    """.trimIndent()

    /**
     * Returns JavaScript to click a dynamic HTML element based on advanced selector criteria.
     * This searches by ID, attributes, textContent (using XPath), and CSS Selector fallback.
     */
    fun getClickEventScript(detailsJson: String): String {
        return """
            (function() {
                var info = $detailsJson;
                
                function normalizeText(text) {
                    if (!text) return "";
                    // Remove numbers, parentheses, brackets, whitespace, and special characters
                    return text.replace(/[0-9\(\)\[\]\s\-\.\,\:\;\/\\\?\!\&\_\#\+\=\*\~\`\^]/g, '').trim().toLowerCase();
                }

                function findElement() {
                    // 1. Match by ID (most reliable)
                    if (info.id) {
                        var el = document.getElementById(info.id);
                        if (el) return el;
                    }
                    
                    // 2. Match by Normalized Text Content + tag name (highly resilient across pages)
                    if (info.textContent) {
                        var targetNorm = normalizeText(info.textContent);
                        if (targetNorm) {
                            var tag = info.tagName || '*';
                            var els = document.getElementsByTagName(tag);
                            for (var i = 0; i < els.length; i++) {
                                var elNorm = normalizeText(els[i].textContent);
                                if (elNorm && (elNorm.indexOf(targetNorm) !== -1 || targetNorm.indexOf(elNorm) !== -1)) {
                                    return els[i];
                                }
                            }
                        }
                    }
                    
                    // 3. Match by stable attributes (non-dynamic)
                    for (var key in info.attributes) {
                        if (info.attributes.hasOwnProperty(key)) {
                            var val = info.attributes[key];
                            var el = document.querySelector("[" + key + "='" + val + "']");
                            if (el) return el;
                        }
                    }
                    
                    // 4. Fallback to CSS Selector path
                    if (info.selector) {
                        return document.querySelector(info.selector);
                    }
                    return null;
                }

                var target = findElement();
                if (target) {
                    target.click();
                    console.log("WebPlugin: Click event triggered on " + (info.id || info.textContent || info.selector));
                } else {
                    console.error("WebPlugin: Target element not found for click.");
                }
            })();
        """.trimIndent()
    }

    /**
     * Returns JavaScript that uses MutationObserver to continuously monitor the DOM
     * and hide target elements based on advanced selectors, preventing them from being shown dynamically.
     * @param selectorJsonArray A JSON array of element details strings (actions to hide).
     */
    fun getMutationObserverHideScript(selectorJsonArray: String): String {
        return """
            (function() {
                var actionDetailsList = $selectorJsonArray;

                function findElement(info) {
                    // 1. Match by ID (most reliable)
                    if (info.id) {
                        var el = document.getElementById(info.id);
                        if (el) return [el];
                    }
                    
                    // 2. Match by CSS Selector path (class/tag structure)
                    if (info.selector) {
                        var els = document.querySelectorAll(info.selector);
                        if (els.length > 0) {
                            var results = [];
                            els.forEach(el => results.push(el));
                            return results;
                        }
                    }
                    
                    // 3. Match by other unique attributes
                    var results = [];
                    for (var key in info.attributes) {
                        if (info.attributes.hasOwnProperty(key)) {
                            var val = info.attributes[key];
                            var els = document.querySelectorAll("[" + key + "='" + val + "']");
                            els.forEach(el => results.push(el));
                        }
                    }
                    return results;
                }

                function applyHide() {
                    actionDetailsList.forEach(function(detailsStr) {
                        try {
                            var info = JSON.parse(detailsStr);
                            var targets = findElement(info);
                            targets.forEach(function(el) {
                                if (el && el.style.display !== 'none') {
                                    console.log("WebPlugin: hiding element", el);
                                    el.style.setProperty('display', 'none', 'important');
                                    // Collapse empty parent containers
                                    collapseEmptyParents(el.parentElement);
                                }
                            });
                        } catch(e) {
                            console.error("WebPlugin: error parsing or hiding target element", e);
                        }
                    });
                }

                function collapseEmptyParents(parent) {
                    try {
                        while (parent && parent !== document.body) {
                            var hasVisibleChild = false;
                            for (var i = 0; i < parent.children.length; i++) {
                                var child = parent.children[i];
                                var style = window.getComputedStyle(child);
                                if (style && style.display !== 'none') {
                                    hasVisibleChild = true;
                                    break;
                                }
                            }
                            if (!hasVisibleChild) {
                                parent.style.setProperty('display', 'none', 'important');
                                parent = parent.parentElement;
                            } else {
                                break;
                            }
                        }
                    } catch(e) {
                        console.error("WebPlugin: error collapsing parent", e);
                    }
                }

                // Initial run
                applyHide();

                // Start observing DOM changes
                if (window.webPluginObserver) {
                    window.webPluginObserver.disconnect();
                }
                
                var observer = new MutationObserver(function(mutations) {
                    applyHide();
                });
                
                observer.observe(document.body, {
                    childList: true,
                    subtree: true
                });
                
                window.webPluginObserver = observer;
                console.log("WebPlugin: MutationObserver registered to hide " + actionDetailsList.length + " targets.");
            })();
        """.trimIndent()
    }
}
