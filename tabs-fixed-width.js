/* =============================================================================
 * 思源笔记 · 标签栏（固定宽度 + 分隔线 + 当前标签高亮）
 * -----------------------------------------------------------------------------
 * 只做三件事：
 *   1. 每个标签保持固定宽度（下方 WIDTH 可改），标题长短不影响；
 *   2. 标签之间一条竖线分隔；
 *   3. 当前打开的标签高亮。
 *
 * 关闭一个标签后，后面的标签由浏览器自然左移补位，鼠标停在原处可继续点。
 *
 * 安装：思源 → 设置 → 外观 → 代码片段 → JS → 新增，粘贴本文件全部内容并启用。
 * ========================================================================== */
(function () {
  "use strict";

  /* ======================= 可调参数（直接改这里） ======================= */
  var WIDTH = 180;      // 标签固定宽度（像素）
  var DIVIDER = true;   // 标签之间的竖线（不需要改成 false）
  var HIGHLIGHT = true; // 当前标签高亮（不需要改成 false）
  var DIVIDER_COLOR = "var(--b3-border-color, #c9c4d6)"; // 竖线颜色
  /* ====================================================================== */

  var STYLE_ID = "snippet-tabs-fw-style";
  var CLASS_ROOT = "snippet-tabs-fw";
  var TAB_SEL = "li.item[data-type='tab-header']";
  var MIN_WIDTH = 32;

  function connected(el) { return !!(el && el.isConnected); }
  function visible(el) {
    if (!connected(el)) return false;
    var r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  }

  // 传入“栏本身”或“包含栏的容器”（例如 #layouts）都能解析到主标签栏
  function resolveBar(el) {
    if (!el || el.nodeType !== 1) return null;
    if (el.tagName === "UL" && el.classList.contains("layout-tab-bar") &&
        !el.classList.contains("layout-tab-bar--readonly")) return el;
    var bars = el.querySelectorAll("ul.layout-tab-bar:not(.layout-tab-bar--readonly)");
    var best = null, bestScore = -1;
    for (var i = 0; i < bars.length; i++) {
      var bar = bars[i];
      if (!visible(bar)) continue;
      var parent = bar.parentElement;
      if (!parent || parent.classList.contains("fn__none")) continue;
      var items = bar.querySelectorAll(":scope > " + TAB_SEL).length;
      var score = (bar.closest(".layout__wnd--active") ? 1000 : 0) + Math.min(items, 999);
      if (score > bestScore) { bestScore = score; best = bar; }
    }
    return best;
  }

  function install(rootArg) {
    var root = resolveBar(rootArg);
    if (!root) return null;
    if (root.__tabsFw && root.__tabsFw.destroy) root.__tabsFw.destroy();

    var width = Math.max(MIN_WIDTH, Number(WIDTH) || 180);
    var alive = true;
    var styleEl = null;

    // 只作用于真实文档标签；排除固定标签 / 只读 / 拖拽占位 / 拖拽克隆体
    var SEL = TAB_SEL + ":not(.item--pin):not(.item--readonly)" +
              ":not(.item--document-drop):not([data-clone='true'])";

    function buildCss() {
      var css = "";

      // 1) 固定宽度。box-sizing:content-box ⇒ 下面那条 border-left 不会吃掉标签内容宽度。
      //    min-width:0 + overflow:hidden 防止长标题顶破宽度。
      css += SEL + "{" +
        "flex:0 0 " + width + "px !important;" +
        "width:" + width + "px !important;" +
        "min-width:0 !important;" +
        "max-width:" + width + "px !important;" +
        "box-sizing:content-box !important;" +
        "overflow:hidden !important;" +
        "transition:none !important;" +
        "}";

      // 关闭按钮贴右沿：宽度固定 ⇒ 关闭按钮位置也固定，连点关闭时鼠标不用动
      css += SEL + " > .item__close{margin-left:auto !important;flex-shrink:0 !important;}";
      css += SEL + " > .item__text{min-width:0 !important;overflow:hidden !important;}";

      // 2) 分隔线：用“背景渐变”在标签左沿画一条 1px 竖线。
      //    为什么不用 border-left / 伪元素：
      //      · border-left 会被标签的圆角弯成弧线（看起来像圆角边框的一部分）；
      //      · ::after 在 flex 容器里会被当成 flex 项参与排版 → 一个标签出现两条线，
      //        或被标签的 overflow 裁掉 → 时有时无。
      //    渐变方案不参与排版、不受圆角与裁剪影响，且竖线只在“非第一个”标签上出现，
      //    天然落在两个标签之间（上下各留 8px 不倒边）。
      if (DIVIDER) {
        css += SEL + ":not(:first-child){" +
          "background-image:linear-gradient(to right," + DIVIDER_COLOR + " 0 1px,transparent 1px) !important;" +
          "background-repeat:no-repeat !important;" +
          "background-position:left 50% !important;" +
          "background-size:1px calc(100% - 16px) !important;" +
          "}";
      }

      // 3) 当前标签高亮：主色描边 + 加粗 + 主色文字（写在分隔线之后，优先级更高）
      if (HIGHLIGHT) {
        css += SEL + ".item--focus{" +
          "background-image:none !important;" +
          "background-color:var(--b3-theme-primary-lightest,var(--b3-theme-surface-light)) !important;" +
          "box-shadow:0 0 0 2px var(--b3-theme-primary) inset !important;" +
          "border-radius:var(--b3-border-radius,6px);" +
          "}";
        css += SEL + ".item--focus > .item__text," +
               SEL + ".item--focus > .item__icon," +
               SEL + ".item--focus > .item__graphic{" +
               "color:var(--b3-theme-primary) !important;font-weight:600 !important;}";
      }
      return css;
    }

    function ensureStyle() {
      if (connected(styleEl)) {
        var want = buildCss();
        if (styleEl.textContent !== want) styleEl.textContent = want;
        return;
      }
      var existing = document.getElementById(STYLE_ID);
      if (existing) { styleEl = existing; styleEl.textContent = buildCss(); return; }
      styleEl = document.createElement("style");
      styleEl.id = STYLE_ID;
      styleEl.textContent = buildCss();
      (document.head || document.documentElement).appendChild(styleEl);
    }

    function mark() {
      ensureStyle();
      if (root.classList) root.classList.add(CLASS_ROOT);
    }

    var observer = new MutationObserver(function () { if (alive) mark(); });

    function destroy() {
      if (!alive) return;
      alive = false;
      observer.disconnect();
      if (root.classList) root.classList.remove(CLASS_ROOT);
      if (styleEl && styleEl.parentNode) styleEl.parentNode.removeChild(styleEl);
      styleEl = null;
      if (root.__tabsFw === api) delete root.__tabsFw;
    }

    var api = { destroy: destroy, apply: mark, get width() { return width; }, get css() { return buildCss(); } };
    root.__tabsFw = api;
    mark();
    observer.observe(root, { childList: true, subtree: false });
    return api;
  }

  /* ============================== 自动挂载 / 自愈 ============================== */
  var API = { install: install, version: "2.0.0" };
  if (typeof module === "object" && module.exports) module.exports = API;
  if (typeof window !== "undefined") window.SiyuanTabsFixed = API;

  if (typeof window !== "undefined") {
    if (window.SiyuanTabsFixed && window.SiyuanTabsFixed.__timer) {
      try { clearInterval(window.SiyuanTabsFixed.__timer); } catch (e) {}
    }
    if (window.SiyuanTabsFixed && window.SiyuanTabsFixed.__handle) {
      try { window.SiyuanTabsFixed.__handle.destroy(); } catch (e) {}
    }

    var mount = function () {
      try {
        var cfg = (window.siyuan && window.siyuan.config) || {};
        if (cfg.system && cfg.system.isMobile) return;
        var bar = resolveBar(document.querySelector("#layouts") || document.body);
        if (!bar) return;
        if (API.__handle && API.__handle.__root === bar && connected(bar)) return;
        if (API.__handle) { try { API.__handle.destroy(); } catch (e) {} }
        var h = install(bar);
        if (h) { h.__root = bar; API.__handle = h; }
      } catch (err) {
        if (window.console) console.warn("[tabs-fw] mount failed", err);
      }
    };

    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", mount);
    } else {
      mount();
    }

    var tick = function () {
      if (!API.__handle || !API.__handle.__root || !connected(API.__handle.__root) ||
          !visible(API.__handle.__root)) {
        mount();
      }
    };
    try {
      var mo = new MutationObserver(tick);
      var observeLayouts = function () {
        var el = document.querySelector("#layouts");
        if (el && el.__tabsFwObserved !== true) {
          el.__tabsFwObserved = true;
          mo.observe(el, { childList: true, subtree: false });
        }
      };
      observeLayouts();
      if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", observeLayouts);
      API.__timer = window.setInterval(function () { observeLayouts(); tick(); }, 1500);
    } catch (e) {}
  }
})();
