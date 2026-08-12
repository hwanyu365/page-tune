package com.hwanyu365.pagetune.util

import com.hwanyu365.pagetune.db.PluginAction
import org.json.JSONObject

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
     * hex(`#000`, `#000000`) 또는 CSS 색상 이름(`red`, `transparent`)만 허용한다.
     * 알파벳만 통과시키므로 값에 세미콜론이나 중괄호를 끼워 다른 CSS 선언을 덧붙이는 것이 막힌다.
     * 이름의 오타는 잡지 않는다. 브라우저가 해당 선언만 무시하고, 인스펙터 미리보기에서 바로 드러난다.
     */
    fun isColorValue(value: String): Boolean = COLOR_VALUE.matches(value)

    private val COLOR_VALUE = Regex("^(#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})|[a-zA-Z]+)$")

    /**
     * "color" 액션들을 style 주입 스크립트로 만든다.
     * 액션이 비면 빈 CSS 가 나가 style 태그를 비우므로, 액션 삭제 시 색상도 함께 풀린다.
     */
    fun getColorScriptFor(actions: List<PluginAction>): String {
        val rules = actions.mapNotNull { action ->
            runCatching {
                val info = JSONObject(action.targetSelector)
                val id = info.optString("id")
                // id 가 있으면 가장 안정적인 셀렉터다.
                val selector = if (id.isNotEmpty()) "#$id" else info.optString("selector")
                selector to (action.styleCss ?: "")
            }.getOrNull()
        }
        return getColorStyleScript(buildColorCss(rules))
    }

    /**
     * "color" 액션들을 하나의 CSS 텍스트로 조립한다.
     * 셀렉터에 하위 노드(`sel *`)까지 넣는 이유는, 자식이 자기 색을 갖고 있으면 부모에만 건 규칙이 덮이기 때문이다.
     * @param rules (CSS 셀렉터, "background-color:#000;color:#fff" 형태의 선언부) 쌍.
     */
    fun buildColorCss(rules: List<Pair<String, String>>): String {
        return rules.mapNotNull { (selector, declarations) ->
            val sel = selector.trim()
            val decls = declarations.split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("; ") { "$it !important" }
            if (sel.isEmpty() || decls.isEmpty()) null else "$sel, $sel * { $decls; }"
        }.joinToString("\n")
    }

    /**
     * 색상은 MutationObserver 대신 style 태그로 넣는다.
     * CSS 는 나중에 추가된 노드에도 자동 적용되므로 재적용 루프가 필요 없고, hide 쪽 observer 와도 충돌하지 않는다.
     */
    fun getColorStyleScript(css: String): String {
        val escaped = css.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        return """
            (function() {
                var css = '$escaped';
                var tag = document.getElementById('webPluginColorStyle');
                if (!tag) {
                    tag = document.createElement('style');
                    tag.id = 'webPluginColorStyle';
                    (document.head || document.documentElement).appendChild(tag);
                }
                tag.textContent = css;
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
