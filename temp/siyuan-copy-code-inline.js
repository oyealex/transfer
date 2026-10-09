// ============================================================
// 思源笔记 · 行内代码悬浮复制按钮
// 安装：设置 → 外观 → 代码片段 → JS → 新增，粘贴本段并启用
// 功能：鼠标经过行内代码时，在“最后一个字符”的右上角显示复制按钮；
//       点击复制原文；移出后按钮消失。
// ============================================================
(function () {
  "use strict";

  /* ==================== 可调参数 ==================== */
  const GAP = 4;              // 按钮与最后一个字符之间的水平间距(px)
  const OFFSET_Y = 0;         // 按钮垂直偏移(px)，负数上移、正数下移
  const SIZE = 20;            // 按钮边长(px)
  const HIDE_DELAY = 150;     // 鼠标移出后的隐藏延迟(ms)，留时间移到按钮上
  const DONE_DURATION = 1200; // “已复制”图标保持时长(ms)

  // 思源编辑器中的行内代码，可能是 data-type="code"，也可能是 "strong code" 等组合
  const CODE_SELECTOR = '[data-type~="code"]';
  // 编辑器容器（编辑与只读视图都用 protyle-wysiwyg）
  const EDITOR_SELECTOR = ".protyle-wysiwyg";

  // 思源插入的不可见字符：零宽空格 / 零宽非连接符 / 零宽连接符 / 单词连接符 / BOM
  const INVISIBLE = /[\u200b\u200c\u200d\u2060\ufeff]/;
  const INVISIBLE_G = /[\u200b\u200c\u200d\u2060\ufeff]/g;

  /* ==================== 图标 ==================== */
  const ICON_COPY =
    '<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
    '<rect x="9" y="9" width="12" height="12" rx="2"></rect>' +
    '<path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>' +
    "</svg>";
  const ICON_DONE =
    '<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round">' +
    '<polyline points="20 6 9 17 4 12"></polyline>' +
    "</svg>";

  /* ==================== 状态 ==================== */
  let btn = null;        // 全局唯一的浮动按钮
  let activeEl = null;   // 当前悬停的行内代码元素
  let hideTimer = 0;
  let doneTimer = 0;

  /* ==================== 创建按钮 ==================== */
  function ensureButton() {
    if (btn) return btn;

    btn = document.createElement("button");
    btn.type = "button";
    btn.className = "siyuan-inline-code-copy-btn";
    btn.title = "复制";
    btn.setAttribute("aria-label", "复制行内代码");
    btn.innerHTML = ICON_COPY;
    btn.style.cssText = [
      "position:fixed",
      "display:none",
      "align-items:center",
      "justify-content:center",
      "width:" + SIZE + "px",
      "height:" + SIZE + "px",
      "padding:0",
      "margin:0",
      "border:1px solid var(--b3-border-color, #d0d0d0)",
      "border-radius:4px",
      "background:var(--b3-theme-background, #ffffff)",
      "color:var(--b3-theme-on-background, #333333)",
      "box-shadow:0 1px 4px rgba(0,0,0,.16)",
      "cursor:pointer",
      "box-sizing:border-box",
      "outline:none",
      "z-index:999999",
      "user-select:none",
      "-webkit-user-select:none",
    ].join(";");

    // 鼠标进入按钮时取消隐藏，并给出悬停反馈
    btn.addEventListener("mouseenter", function () {
      cancelHide();
      btn.style.background = "var(--b3-list-hover, #eeeeee)";
    });
    btn.addEventListener("mouseleave", function () {
      btn.style.background = "var(--b3-theme-background, #ffffff)";
      scheduleHide();
    });

    // 阻止事件传回编辑器，避免点击按钮时编辑器失焦 / 收起选区
    btn.addEventListener("mousedown", function (e) {
      e.preventDefault();
      e.stopPropagation();
    });
    btn.addEventListener("mouseup", function (e) {
      e.stopPropagation();
    });

    // 点击复制
    btn.addEventListener("click", function (e) {
      e.preventDefault();
      e.stopPropagation();
      if (!activeEl) return;
      copyText(getCodeText(activeEl)).then(function (ok) {
        if (ok) showDone();
      });
    });

    document.body.appendChild(btn);
    return btn;
  }

  /* ==================== 复制逻辑 ==================== */
  // 取行内代码文本：去掉编辑器插入的不可见字符，其余原样保留
  function getCodeText(el) {
    return (el.textContent || "").replace(INVISIBLE_G, "");
  }

  function copyText(text) {
    if (navigator.clipboard && window.isSecureContext !== false) {
      return navigator.clipboard.writeText(text).then(
        function () { return true; },
        function () { return fallbackCopy(text); }
      );
    }
    return Promise.resolve(fallbackCopy(text));
  }

  function fallbackCopy(text) {
    const prevFocus = document.activeElement;
    const ta = document.createElement("textarea");
    ta.value = text;
    ta.setAttribute("readonly", "");
    ta.style.cssText = "position:fixed;top:0;left:-9999px;opacity:0;";
    document.body.appendChild(ta);
    ta.select();
    let ok = false;
    try { ok = document.execCommand("copy"); } catch (e) { ok = false; }
    ta.remove();
    if (prevFocus && typeof prevFocus.focus === "function") prevFocus.focus();
    return ok;
  }

  /* ==================== 图标状态 ==================== */
  function resetIcon() {
    clearTimeout(doneTimer);
    if (!btn) return;
    btn.innerHTML = ICON_COPY;
    btn.title = "复制";
    btn.style.color = "var(--b3-theme-on-background, #333333)";
  }

  function showDone() {
    if (!btn) return;
    btn.innerHTML = ICON_DONE;
    btn.title = "已复制";
    btn.style.color = "var(--b3-theme-success, #4caf50)";
    clearTimeout(doneTimer);
    doneTimer = window.setTimeout(resetIcon, DONE_DURATION);
  }

  /* ==================== 定位 ==================== */
  // 取“最后一个可见字符”的位置；行内代码换行时自然落在最后一行的末字符上
  function getLastCharRect(el) {
    const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, null);
    let lastText = null;
    let node;
    while ((node = walker.nextNode())) {
      if (node.nodeValue && node.nodeValue.replace(INVISIBLE_G, "").length) {
        lastText = node;
      }
    }
    if (lastText) {
      const s = lastText.nodeValue;
      let end = s.length;
      while (end > 0 && INVISIBLE.test(s.charAt(end - 1))) end--;
      if (end > 0) {
        try {
          const range = document.createRange();
          range.setStart(lastText, end - 1);
          range.setEnd(lastText, end);
          const rects = range.getClientRects();
          const rect = rects.length ? rects[rects.length - 1] : range.getBoundingClientRect();
          if (rect && (rect.width || rect.height)) return rect;
        } catch (e) { /* 忽略，退回到整体矩形 */ }
      }
    }
    return el.getBoundingClientRect();
  }

  function positionButton(codeEl) {
    if (!codeEl.isConnected) { hideButton(); return; }
    const r = getLastCharRect(codeEl);
    // 滚出可视区域则隐藏
    if (r.bottom < 0 || r.top > window.innerHeight || r.right < 0 || r.left > window.innerWidth) {
      hideButton();
      return;
    }
    let left = r.right + GAP;
    let top = r.top + r.height / 2 - SIZE / 2 + OFFSET_Y; // 按钮中心与字符中心水平对齐
    // 避免超出窗口
    left = Math.max(4, Math.min(left, window.innerWidth - SIZE - 4));
    top = Math.max(4, Math.min(top, window.innerHeight - SIZE - 4));
    btn.style.left = left + "px";
    btn.style.top = top + "px";
  }

  /* ==================== 显示 / 隐藏 ==================== */
  function showButton(codeEl) {
    cancelHide();
    const changed = activeEl !== codeEl;
    activeEl = codeEl;
    ensureButton();
    if (changed) resetIcon();
    btn.style.display = "flex";
    positionButton(codeEl);
  }

  function cancelHide() {
    if (hideTimer) {
      clearTimeout(hideTimer);
      hideTimer = 0;
    }
  }

  function scheduleHide() {
    cancelHide();
    hideTimer = window.setTimeout(hideButton, HIDE_DELAY);
  }

  function hideButton() {
    cancelHide();
    activeEl = null;
    if (btn) {
      btn.style.display = "none";
      btn.style.background = "var(--b3-theme-background, #ffffff)";
    }
  }

  /* ==================== 事件 ==================== */
  function onMouseOver(e) {
    const target = e.target;
    if (!(target instanceof Element)) return;
    const codeEl = target.closest(CODE_SELECTOR);
    if (!codeEl) return;
    if (!codeEl.closest(EDITOR_SELECTOR)) return; // 只处理编辑器中的行内代码
    if (activeEl === codeEl) { cancelHide(); return; }
    showButton(codeEl);
  }

  function onMouseOut(e) {
    if (!activeEl) return;
    const to = e.relatedTarget;
    if (to instanceof Node) {
      if (activeEl.contains(to)) return;                    // 仍在代码元素内
      if (btn && (to === btn || btn.contains(to))) return;  // 移到了按钮上
    }
    scheduleHide();
  }

  function onViewportChange() {
    if (!activeEl) return;
    if (activeEl.isConnected && btn && btn.style.display !== "none") {
      positionButton(activeEl);
    } else {
      hideButton();
    }
  }

  function init() {
    ensureButton();
    document.addEventListener("mouseover", onMouseOver, true);
    document.addEventListener("mouseout", onMouseOut, true);
    window.addEventListener("scroll", onViewportChange, true);
    window.addEventListener("resize", onViewportChange);
    // 编辑器内容变化（DOM 被重绘）后按钮可能失效，直接隐藏
    document.addEventListener("input", function () { if (activeEl) hideButton(); }, true);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
