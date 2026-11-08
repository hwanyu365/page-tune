package com.hwanyu365.pagetune.util

import com.hwanyu365.pagetune.db.PluginAction
import org.json.JSONObject

object JsScripts {

    /**
     * 요소 정보(JSON)로 요소를 찾는 공통 단계. 숨기기·클릭이 같은 기준으로 찾도록 두 스크립트에 함께 넣는다.
     * 속성값은 셀렉터로 조립하지 않고 값끼리 비교한다 → 따옴표·`@click` 같은 속성도 예외 없이 비교된다.
     */
    private val ELEMENT_MATCH_JS = """
        function webPluginQueryAll(selector) {
            if (!selector) return [];
            try { return Array.from(document.querySelectorAll(selector)); } catch (e) { return []; }
        }

        function webPluginAttrMatches(info, firstOnly) {
            var attrs = info.attributes;
            var out = [];
            if (!info.tagName || !attrs || typeof attrs !== 'object') return out;
            var keys = Object.keys(attrs);
            if (keys.length === 0) return out;
            var els = document.getElementsByTagName(info.tagName);
            for (var i = 0; i < els.length; i++) {
                var ok = true;
                for (var k = 0; k < keys.length; k++) {
                    if (els[i].getAttribute(keys[k]) !== String(attrs[keys[k]])) { ok = false; break; }
                }
                if (ok) {
                    out.push(els[i]);
                    if (firstOnly) break;
                }
            }
            return out;
        }
    """.trimIndent()

    /**
     * Tune 편집 화면의 요소 선택 스크립트.
     * 누른 요소의 요소 정보와 그 셀렉터가 현재 문서에서 매칭하는 수를 AndroidBridge 로 보낸다.
     */
    val ELEMENT_PICKER_SCRIPT = """
        (function() {
            var UNSTABLE_ID = new RegExp(${jsString(UnstableId.PATTERN)}, 'i');

            function stableId(el) {
                return el.id && !UNSTABLE_ID.test(el.id) ? el.id : '';
            }

            function getCssSelector(el) {
                if (stableId(el)) return '#' + CSS.escape(el.id);
                if (el === document.body) return 'body';

                var path = [];
                while (el && el.nodeType === Node.ELEMENT_NODE) {
                    if (path.length > 0 && stableId(el)) {
                        path.unshift('#' + CSS.escape(el.id));
                        break;
                    }
                    var selector = el.nodeName.toLowerCase();
                    // 숫자가 든 class 는 자동 생성인 경우가 많아 뺀다.
                    var classes = Array.from(el.classList).filter(c => c.trim().length > 0 && !/\d/.test(c));
                    if (classes.length > 0) {
                        selector += '.' + classes.map(c => CSS.escape(c)).join('.');
                    } else {
                        var sib = el, nth = 1;
                        while (sib = sib.previousElementSibling) {
                            if (sib.nodeName.toLowerCase() == el.nodeName.toLowerCase()) nth++;
                        }
                        selector += ":nth-of-type(" + nth + ")";
                    }
                    path.unshift(selector);
                    el = el.parentElement;
                }
                return path.join(' > ');
            }

            function countMatches(selector) {
                try { return document.querySelectorAll(selector).length; } catch (e) { return 0; }
            }
            window.webPluginCountMatches = countMatches;

            function extractElementDetails(el) {
                var details = {
                    selector: getCssSelector(el),
                    tagName: el.tagName ? el.tagName.toLowerCase() : "",
                    textContent: el.textContent ? el.textContent.trim().substring(0, 30) : "",
                    id: stableId(el),
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
                return details;
            }

            function reportSelection(el) {
                var details = extractElementDetails(el);
                AndroidBridge.onElementSelected(JSON.stringify(details), countMatches(details.selector));
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
                        reportSelection(parent);
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
                    reportSelection(child);
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
                reportSelection(e.target);
            }

            // Bind click and touch events
            document.removeEventListener('click', clickHandler, true);
            document.addEventListener('click', clickHandler, true);

            console.log("WebPlugin: Inspector script injected.");
        })();
    """.trimIndent()

    /** 요소 정보로 찾은 첫 요소를 클릭한다. 단계마다 찾지 못하면 다음 단계로 넘어간다. */
    fun getClickEventScript(detailsJson: String): String {
        return """
            (function() {
                $ELEMENT_MATCH_JS

                function normalizeText(text) {
                    if (!text) return "";
                    // Remove numbers, parentheses, brackets, whitespace, and special characters
                    return text.replace(/[0-9\(\)\[\]\s\-\.\,\:\;\/\\\?\!\&\_\#\+\=\*\~\`\^]/g, '').trim().toLowerCase();
                }

                function findElement(info) {
                    if (info.id) {
                        var el = document.getElementById(info.id);
                        if (el) return el;
                    }

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

                    var byAttrs = webPluginAttrMatches(info, true);
                    if (byAttrs.length > 0) return byAttrs[0];

                    return webPluginQueryAll(info.selector)[0] || null;
                }

                try {
                    var info = JSON.parse(${jsString(detailsJson)});
                    var target = findElement(info);
                    if (!target) {
                        console.error("WebPlugin: Target element not found for click.");
                        return null;
                    }
                    var link = target.closest ? target.closest('a[href]') : null;
                    var base = document.querySelector('base[target]');
                    var frame = link ? (link.getAttribute('target') || (base ? base.getAttribute('target') : '')) : '';
                    if (link && frame && ['_self', '_top', '_parent'].indexOf(frame.toLowerCase()) === -1) return link.href;
                    var opened = null;
                    var open = window.open;
                    window.open = function(url) { opened = url == null ? '' : String(url); return null; };
                    try { target.click(); } finally { window.open = open; }
                    console.log("WebPlugin: Click event triggered on " + (info.id || info.textContent || info.selector));
                    if (opened === null) return null;
                    var resolver = document.createElement('a');
                    resolver.href = opened;
                    return resolver.href;
                } catch (e) {
                    console.error("WebPlugin: click rule failed", e);
                }
                return null;
            })();
        """.trimIndent()
    }

    /**
     * "color" 액션들을 style 주입 스크립트로 만든다.
     * 액션이 비면 빈 CSS 가 나가 style 태그를 비우므로, 액션 삭제 시 색상도 함께 풀린다.
     * 저장 셀렉터가 CSS 계약([isCssSafeSelector])을 통과하지 못하면 그 규칙은 대체 셀렉터로만 적용한다.
     */
    fun getColorScriptFor(actions: List<PluginAction>): String {
        val rules = actions.mapNotNull { action ->
            runCatching { JSONObject(action.targetSelector) }.getOrNull()?.let { info ->
                val selector = info.optString("selector")
                Triple(selector.takeIf(::isCssSafeSelector), colorFallbackSelector(info), action.styleCss ?: "")
            }
        }
        val primary = buildColorCss(rules.mapNotNull { (selector, _, decls) -> selector?.let { it to decls } })
        val fallbacks = rules.map { (selector, fallback, decls) -> buildColorFallbackCss(selector, fallback, decls) }
        return getColorStyleScript((listOf(primary) + fallbacks).filter { it.isNotEmpty() }.joinToString("\n"))
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
     * 대체 셀렉터 규칙. [selector] 가 있으면 그 셀렉터를 브라우저가 해석하지 못할 때만 동작하게 감싼다.
     * CSS 는 "매칭이 0개일 때" 를 조건으로 쓸 수 없어, 유효하지만 0개인 셀렉터에는 대체 규칙이 적용되지 않는다.
     * `selector()` 를 모르는 WebView 는 `not selector(…)` 를 참으로 보므로 `selector(*)` 지원을 함께 요구한다.
     */
    fun buildColorFallbackCss(selector: String?, fallback: String?, declarations: String): String {
        val rule = fallback?.let { buildColorCss(listOf(it to declarations)) }.orEmpty()
        if (rule.isEmpty()) return ""
        return if (selector == null) rule else "@supports selector(*) and (not selector($selector)) { $rule }"
    }

    /** 태그명 + 저장 속성 전부 일치를 속성 셀렉터로 표현한다. 저장 속성이 없으면 태그만으로 찾지 않으므로 null. */
    fun colorFallbackSelector(info: JSONObject): String? {
        val tag = info.optString("tagName")
        val attrs = info.optJSONObject("attributes") ?: return null
        if (tag.isEmpty() || attrs.length() == 0) return null
        return cssIdent(tag) + attrs.keys().asSequence().sorted()
            .joinToString("") { "[${cssIdent(it)}=${cssString(attrs.optString(it))}]" }
    }

    private val NTH_OF_TYPE = Regex(""":nth-of-type\(\d+\)""")

    /**
     * 저장 셀렉터를 CSS 텍스트에 그대로 넣어도 규칙 밖으로 벗어나지 않는지 판정한다.
     * 허용 목록이 아니라 경계 문자 거부 목록이다 → 선택 스크립트가 이스케이프한 `#\31 abc`, `.a\:b` 를 통과시킨다.
     */
    fun isCssSafeSelector(selector: String): Boolean {
        val s = selector.trim()
        return s.isNotEmpty() &&
            s.none { it == '{' || it == '}' } &&
            s.none { it == ';' || it == '@' } &&
            s.none { it == '"' || it == '\'' } &&
            "/*" !in s && "*/" !in s &&
            NTH_OF_TYPE.replace(s, "").none { it == '(' || it == ')' } &&
            s.none { it == '[' || it == ']' } &&
            !s.endsWith('\\') &&
            selector.none { it == '<' || it < ' ' || it == '\u007F' }
    }

    /** CSSOM "serialize an identifier". */
    fun cssIdent(value: String): String = buildString {
        value.forEachIndexed { i, c ->
            when {
                c == '\u0000' -> append('\uFFFD')
                c < ' ' || c == '\u007F' -> append(cssHexEscape(c))
                c in '0'..'9' && (i == 0 || (i == 1 && value[0] == '-')) -> append(cssHexEscape(c))
                c == '-' && i == 0 && value.length == 1 -> append("\\-")
                c.code >= 0x80 || c == '-' || c == '_' || c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z' -> append(c)
                else -> append('\\').append(c)
            }
        }
    }

    /** CSSOM "serialize a string". */
    fun cssString(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when {
                c == '\u0000' -> append('\uFFFD')
                c < ' ' || c == '\u007F' -> append(cssHexEscape(c))
                c == '"' || c == '\\' -> append('\\').append(c)
                else -> append(c)
            }
        }
        append('"')
    }

    private fun cssHexEscape(c: Char) = "\\${c.code.toString(16)} "

    /**
     * 문자열을 작은따옴표 JS 문자열 리터럴로 바꾼다. 방문한 페이지가 정한 문자열은 이 함수를 거쳐서만 스크립트에 들어간다.
     * org.json 의 따옴표 처리에 기대지 않는다 → 테스트와 기기의 org.json 구현이 달라 경계 동작을 테스트로 보장할 수 없다.
     */
    fun jsString(value: String): String = buildString {
        append('\'')
        value.forEach { c ->
            when {
                c == '\\' -> append("\\\\")
                c == '\'' -> append("\\'")
                c == '"' -> append("\\\"")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\u2028' || c == '\u2029' -> append("\\u%04X".format(c.code))
                c == '<' -> append("\\u003C")
                c < ' ' -> append("\\u%04X".format(c.code))
                else -> append(c)
            }
        }
        append('\'')
    }

    /**
     * 색상은 MutationObserver 대신 style 태그로 넣는다.
     * CSS 는 나중에 추가된 노드에도 자동 적용되므로 재적용 루프가 필요 없고, hide 쪽 observer 와도 충돌하지 않는다.
     */
    fun getColorStyleScript(css: String): String {
        return """
            (function() {
                var css = ${jsString(css)};
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
     * 저장된 스크롤 위치로 페이지를 이동시킨다.
     * 로드 직후엔 본문이 아직 짧아 scrollTo 가 문서 끝에서 잘린다. 목표에 닿을 때까지 짧게 재시도한다.
     * 사용자가 화면을 만지면 멈춘다. 재시도가 사용자의 스크롤을 계속 되돌리면 페이지를 쓸 수 없다.
     */
    fun getScrollScript(y: Int): String {
        return """
            (function() {
                var tries = 0;
                var stopped = false;
                document.addEventListener('touchstart', function() { stopped = true; }, true);
                function go() {
                    if (stopped) return;
                    window.scrollTo(0, ${y});
                    if (++tries < 20 && Math.abs(window.pageYOffset - ${y}) > 2) {
                        setTimeout(go, 100);
                    }
                }
                go();
            })();
        """.trimIndent()
    }

    /**
     * Returns JavaScript that uses MutationObserver to continuously monitor the DOM
     * and hide target elements based on advanced selectors, preventing them from being shown dynamically.
     * @param details 숨길 규칙의 요소 정보 JSON 문자열 목록. 규칙마다 따로 파싱해 하나가 잘못돼도 나머지는 적용된다.
     */
    fun getMutationObserverHideScript(details: List<String>): String {
        return """
            (function() {
                $ELEMENT_MATCH_JS

                var actionDetailsList = [${details.joinToString(", ") { jsString(it) }}];

                function findElement(info) {
                    if (info.id) {
                        var el = document.getElementById(info.id);
                        if (el) return [el];
                    }
                    var els = webPluginQueryAll(info.selector);
                    if (els.length > 0) return els;
                    return webPluginAttrMatches(info, false);
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
