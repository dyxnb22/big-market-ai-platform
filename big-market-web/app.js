/**
 * 用户端主应用：登录门禁、转盘抽奖、签到兑换、AI 对话、用户中心。
 * 依赖：config.js → api-client.js；鉴权 Token 存 localStorage（CONFIG.AUTH_KEY）。
 */
var auth = readAuth();
var CHAT_KEY = "lucky-draw-chats-" + (auth.userId || "anon");
var DRAW_HISTORY_KEY = function(uid) { return "lucky-draw-history-" + (uid || "anon"); };
var CREDIT_LEDGER_KEY = function(uid) { return "lucky-draw-credit-" + (uid || "anon"); };

function showLanding() {
  document.body.classList.add("page-landing");
  document.body.classList.remove("page-verifying");
  document.getElementById("landingView").style.display = "";
  document.getElementById("appView").style.display = "none";
  var v = document.getElementById("verifyingView");
  if (v) v.style.display = "none";
}

function showVerifying() {
  document.body.classList.add("page-verifying");
  document.body.classList.remove("page-landing");
  document.getElementById("landingView").style.display = "none";
  document.getElementById("appView").style.display = "none";
  var v = document.getElementById("verifyingView");
  if (v) v.style.display = "";
}

// ===== Auth gate =====
if (!auth.token) {
  showLanding();
} else {
  showVerifying();
  apiRequest("/auth/verify", {}, {
    onAuthExpired: function() {
      clearAuth();
      toast("登录已过期，请重新登录");
      showLanding();
    }
  }).then(function() {
    initApp();
  }).catch(function(e) {
    if (e.code && e.code !== "0009") {
      clearAuth();
      toast("服务暂时不可用，请稍后再试");
      showLanding();
    } else if (!e.code) {
      toast("网络异常，请稍后刷新重试");
      showLanding();
    }
  });
}

// ===== Main App =====
function initApp() {
  document.body.classList.remove("page-landing", "page-verifying");
  document.getElementById("landingView").style.display = "none";
  document.getElementById("appView").style.display = "";
  var verifying = document.getElementById("verifyingView");
  if (verifying) verifying.style.display = "none";

  // DOM references
  var d = {
    userMenuBtn:     qs("#userMenuBtn"),
    userNameBadge:   qs("#userNameBadge"),
    creditDisplay:   qs("#creditDisplay"),
    apiStatusDot:    qs("#apiStatusDot"),
    apiStatusText:   qs("#apiStatusText"),
    wheel:           qs("#wheel"),
    drawBtn:         qs("#drawBtn"),
    drawResult:      qs("#drawResult"),
    refreshCampaign: qs("#refreshCampaignBtn"),
    signInBtn:       qs("#signInBtn"),
    signInStatus:    qs("#signInStatus"),
    surplusMetric:   qs("#surplusMetric"),
    dayMetric:       qs("#dayMetric"),
    creditMetric:    qs("#creditMetric"),
    convList:        qs("#conversationList"),
    convTitle:       qs("#activeConversationTitle"),
    msgList:         qs("#messageList"),
    msgInput:        qs("#messageInput"),
    chatForm:        qs("#chatForm"),
    sendBtn:         qs("#sendBtn"),
    newChatBtn:      qs("#newChatBtn"),
    clearChatBtn:    qs("#clearChatBtn"),
    openLotteryBtn:  qs("#openLotteryBtn"),
    mobileLotteryBtn: qs("#mOpenLotteryBtn"),
    closeDrawerBtn:  qs("#closeDrawerBtn"),
    lotteryDrawer:   qs("#lotteryDrawer"),
    userCenterBtn:   qs("#userCenterBtn"),
    userCenterDrawr: qs("#userCenterDrawer"),
    closeUcBtn:      qs("#closeUserCenterBtn"),
    drawerOverlay:   qs("#drawerOverlay"),
    contextMenu:     qs("#contextMenu"),
    renameDialog:    qs("#renameDialog"),
    renameInput:     qs("#renameInput"),
    renameConfirm:   qs("#renameConfirm"),
    renameCancel:    qs("#renameCancel"),
    userAvatar:      qs("#userAvatar"),
    userName:        qs("#userName"),
    userIdDisplay:   qs("#userIdDisplay"),
    ucCredit:        qs("#ucCredit"),
    ucSurplus:       qs("#ucSurplus"),
    ucSigned:        qs("#ucSigned"),
    logoutBtn:       qs("#logoutBtn"),
    toast:           qs("#toast"),
    exchangeInfo:    qs("#exchangeInfo"),
    exchangeBtn:     qs("#exchangeBtn"),
    ucSignInBtn:     qs("#ucSignInBtn"),
    ucExchangeBtn:   qs("#ucExchangeBtn"),
    ucExchangeHint:  qs("#ucExchangeHint"),
    activityLabel:   qs("#activityLabel"),
    activityCopy:    qs("#activityCopy"),
    drawHistoryList: qs("#drawHistoryList"),
    creditLedgerList: qs("#creditLedgerList"),
    composer:        qs("#chatForm"),
    composerDisabledMsg: null
  };

  function qs(sel) { return document.querySelector(sel); }
  var creditMobile = null; // mobile topbar removed

  var chatState = readJson(CHAT_KEY, defaultChats());
  var awards = [];
  var rotation = 0;
  var loadCampaignSeq = 0;
  var ctxTargetId = null;
  var signedToday = false;
  var chatbotEnabled = true;
  var activityDisplayReady = true;
  var metricsLoading = true;
  var pendingAssistant = false;

  // ---- Local history (draw / credit ledger) ----
  function readHistory(keyFn, fallback) {
    return readJson(keyFn(auth.userId), fallback);
  }
  function saveHistory(keyFn, data) {
    localStorage.setItem(keyFn(auth.userId), JSON.stringify(data));
  }

  function pushDrawHistory(awardTitle, awardId) {
    var list = readHistory(DRAW_HISTORY_KEY, []);
    list.unshift({ awardTitle: awardTitle || "奖品", awardId: awardId, at: Date.now() });
    if (list.length > 30) list.length = 30;
    saveHistory(DRAW_HISTORY_KEY, list);
    renderHistories();
  }

  function updateLatestDrawHistory(awardTitle) {
    var list = readHistory(DRAW_HISTORY_KEY, []);
    if (list.length) {
      list[0].awardTitle = awardTitle;
      saveHistory(DRAW_HISTORY_KEY, list);
      renderHistories();
    }
  }

  function isRandomCreditAward(title) {
    return title && String(title).indexOf("随机积分") >= 0;
  }

  function currentCreditBalance() {
    var v = parseFloat(d.creditMetric && d.creditMetric.textContent);
    if (!isNaN(v) && !isMetricPlaceholder(d.creditMetric.textContent)) return v;
    v = parseFloat(d.ucCredit && d.ucCredit.textContent);
    return isNaN(v) ? 0 : v;
  }

  function pollRandomCreditGain(beforeBalance, attempts, onDone) {
    if (attempts <= 0) { onDone(null); return; }
    apiRequest("/raffle/activity/query_user_credit_account_by_token", {
      method: "POST", body: "{}"
    }).then(function(r) {
      var after = parseFloat(r.data);
      if (!isNaN(after) && after > beforeBalance + 0.001) {
        onDone(Math.round((after - beforeBalance) * 10) / 10);
      } else {
        setTimeout(function() { pollRandomCreditGain(beforeBalance, attempts - 1, onDone); }, 600);
      }
    }).catch(function() { onDone(null); });
  }

  function postJson(path, extra) {
    return apiRequest(path, Object.assign({ method: "POST", body: "{}" }, extra || {}));
  }

  function pushCreditLedger(delta, balance, note) {
    var list = readHistory(CREDIT_LEDGER_KEY, []);
    list.unshift({ delta: delta, balance: balance, note: note || "", at: Date.now() });
    if (list.length > 40) list.length = 40;
    saveHistory(CREDIT_LEDGER_KEY, list);
    renderHistories();
  }

  function formatTime(ts) {
    try { return new Date(ts).toLocaleString(); } catch (e) { return ""; }
  }

  function renderHistories() {
    var draws = readHistory(DRAW_HISTORY_KEY, []);
    if (d.drawHistoryList) {
      d.drawHistoryList.innerHTML = draws.length
        ? draws.map(function(item) {
            return '<div class="history-item"><strong>' + esc(item.awardTitle) + '</strong><span>' + esc(formatTime(item.at)) + '</span></div>';
          }).join("")
        : '<p class="history-empty">暂无记录</p>';
    }
    var ledger = readHistory(CREDIT_LEDGER_KEY, []);
    if (d.creditLedgerList) {
      d.creditLedgerList.innerHTML = ledger.length
        ? ledger.map(function(item) {
            var sign = item.delta > 0 ? "+" : "";
            return '<div class="history-item"><strong>' + sign + item.delta + ' 积分</strong><span>' + esc(item.note || "") + ' · ' + esc(formatTime(item.at)) + (item.balance != null ? ' · 余额 ' + item.balance : '') + '</span></div>';
          }).join("")
        : '<p class="history-empty">暂无记录</p>';
    }
  }

  // ---- Chatbot gate / activity display ----
  function applyActivityGate(state) {
    var preparing = state !== "active" && state !== "online";
    if (d.drawBtn) {
      d.drawBtn.disabled = preparing || !activityDisplayReady;
    }
    if (preparing && d.drawResult && !d.drawResult.textContent.startsWith("恭喜")) {
      d.drawResult.textContent = "活动准备中，请稍后再试";
    }
    if (d.activityCopy && preparing) {
      d.activityCopy.textContent = "活动准备中，请等待管理员上架后再参与抽奖。";
      d.activityCopy.style.display = "";
    }
  }

  function applyChatbotGate() {
    var disabled = !chatbotEnabled;
    if (d.msgInput) {
      d.msgInput.disabled = disabled;
      d.msgInput.placeholder = disabled ? "AI 对话已在管理端关闭" : "给 AI 发送消息...";
    }
    if (d.sendBtn) d.sendBtn.disabled = disabled;
    if (d.chatForm) d.chatForm.classList.toggle("disabled-hint", disabled);
    var hint = d.chatForm && d.chatForm.querySelector(".composer-disabled-msg");
    if (disabled) {
      if (!hint && d.chatForm) {
        hint = document.createElement("p");
        hint.className = "composer-disabled-msg";
        d.chatForm.insertBefore(hint, d.chatForm.firstChild);
      }
      if (hint) hint.textContent = "AI 对话入口已关闭，请联系管理员或稍后再试。";
    } else if (hint) {
      hint.remove();
    }
  }

  /**
   * 解析当前活动 ID：采用 stage API 返回值，与后端上架活动保持一致。
   */
  function resolveActivityId() {
    return apiRequest("/raffle/activity/query_stage_activity_id?channel=" + encodeURIComponent(CONFIG.CHANNEL) + "&source=" + encodeURIComponent(CONFIG.SOURCE), {
      method: "GET"
    }).then(function(r) {
      var staged = (r.data && Number(r.data) > 0) ? Number(r.data) : null;
      CONFIG.ACTIVITY_ID = staged || CONFIG.DEFAULT_ACTIVITY_ID;
      return CONFIG.ACTIVITY_ID;
    }).catch(function() {
      CONFIG.ACTIVITY_ID = CONFIG.DEFAULT_ACTIVITY_ID;
      return CONFIG.ACTIVITY_ID;
    });
  }

  function isMetricPlaceholder(text) {
    if (text == null) return true;
    var t = String(text).trim();
    return t === "" || t === "-" || t === "加载中" || t === "加载中...";
  }

  function setMetricsLoading(loading) {
    metricsLoading = loading;
    if (!loading) {
      [d.surplusMetric, d.dayMetric, d.creditMetric, d.ucCredit, d.ucSurplus].forEach(function(el) {
        if (el) el.classList.remove("loading");
      });
      return;
    }
    [d.surplusMetric, d.dayMetric, d.creditMetric, d.ucCredit, d.ucSurplus].forEach(function(el) {
      if (!el) return;
      if (isMetricPlaceholder(el.textContent)) {
        el.textContent = "加载中";
        el.classList.add("loading");
      }
    });
    if (d.creditDisplay) {
      var cur = d.creditDisplay.textContent.replace(/^积分:\s*/, "");
      if (isMetricPlaceholder(cur)) d.creditDisplay.textContent = "积分: ...";
    }
  }

  function loadDisplayConfig() {
    return fetch(CONFIG.API_BASE + "/admin/config/public/display?activityId=" + CONFIG.ACTIVITY_ID)
      .then(function(r) { return r.json(); })
      .then(function(r) {
        if (r.code !== "0000" || !r.data) return;
        var data = r.data;
        if (d.activityLabel) {
          d.activityLabel.textContent = data.title || ("活动 " + CONFIG.ACTIVITY_ID);
        }
        if (d.activityCopy) {
          d.activityCopy.textContent = data.copy || "";
          d.activityCopy.style.display = data.copy ? "" : "none";
        }
        chatbotEnabled = data.chatbotEnabled !== false;
        applyChatbotGate();
        activityDisplayReady = data.state === "online" || data.state === "active";
        applyActivityGate(data.state);
      })
      .catch(function() {
        if (d.activityLabel) d.activityLabel.textContent = "活动 " + CONFIG.ACTIVITY_ID;
      });
  }

  function readJson(key, fallback) {
    try { var v = localStorage.getItem(key); return v ? JSON.parse(v) : fallback; }
    catch (e) { return fallback; }
  }

  function defaultChats() {
    var id = crypto.randomUUID();
    return { activeId: id, conversations: [{ id: id, title: "新的对话", messages: [] }] };
  }
  function saveChats() { localStorage.setItem(CHAT_KEY, JSON.stringify(chatState)); }

  // ---- Health (via gateway) — only updates when loadCampaign hasn't set a
  // business-level status.  loadCampaign calls setConnStatus which writes both
  // class and text, so if the last API response was an error the business error
  // message will not be overwritten by a green dot from this lightweight check.
  function healthCheck() {
    fetch(CONFIG.API_BASE.replace(/\/api\/v1\/?$/, "") + "/actuator/health")
      .then(function(r) { return r.json(); })
      .then(function(data) {
        var up = data.status === "UP";
        // Only update if the current text is still the default — business
        // errors set by loadCampaign (e.g. "加载积分失败") take precedence.
        var cur = d.apiStatusText.textContent;
        if (cur === "已连接" || cur === "未连接" || cur === "连接中") {
          d.apiStatusDot.className = "status-dot" + (up ? " online" : "");
          d.apiStatusText.textContent = up ? "已连接" : "未连接";
        }
      })
      .catch(function() {
        var cur = d.apiStatusText.textContent;
        if (cur === "已连接" || cur === "未连接" || cur === "连接中") {
          d.apiStatusDot.className = "status-dot";
          d.apiStatusText.textContent = "未连接";
        }
      });
  }

  // ---- Auth UI ----
  d.userNameBadge.textContent = auth.userId || "用户";
  d.userName.textContent = auth.userId || "用户";
  d.userIdDisplay.textContent = "ID: " + (auth.userId || "-");
  d.userAvatar.textContent = (auth.userId || "?")[0].toUpperCase();

  // ---- Logout ----
  function logout() {
    var token = auth.token;
    var revoke = token
      ? apiRequest("/auth/logout", { method: "POST", headers: { Authorization: "Bearer " + token } }).catch(function() {})
      : Promise.resolve();
    revoke.finally(function() {
      clearAuth();
      location.reload();
    });
  }

  // ---- Wheel / campaign / draw ----
  function renderWheel() {
    var seg = 360 / awards.length;
    var colors = ["#f97316","#14b8a6","#3b82f6","#facc15","#a855f7","#22c55e","#ef4444","#06b6d4"];
    var grad = awards.map(function(a,i) { return colors[i%colors.length]+" "+(i*seg)+"deg "+((i+1)*seg)+"deg"; }).join(", ");
    d.wheel.style.background = "conic-gradient(" + grad + ")";
    d.wheel.innerHTML = "";
    var mobile = window.innerWidth < 640;
    // Keep labels well inside the circle to avoid clipping by border-radius
    var fontSize = mobile ? (awards.length > 6 ? 9 : 11) : (awards.length > 8 ? 10 : awards.length > 6 ? 12 : 14);
    var labelWidth = mobile ? (awards.length > 6 ? 54 : 70) : (awards.length > 6 ? 68 : 90);
    var radius = mobile ? 72 : 112;
    awards.forEach(function(award, i) {
      var el = document.createElement("span");
      el.className = "wheel-label";
      el.style.fontSize = fontSize + "px";
      el.style.width = labelWidth + "px";
      el.style.marginLeft = "-" + (labelWidth/2) + "px";
      el.style.transform = "rotate(" + (i*seg+seg/2) + "deg) translateY(-" + radius + "px) rotate(90deg)";
      el.textContent = award.awardTitle || ("奖品"+(i+1));
      d.wheel.appendChild(el);
    });
  }

  /** Update connection status indicator */
  function setConnStatus(ok, msg) {
    d.apiStatusDot.className = "status-dot" + (ok ? " online" : "");
    d.apiStatusText.textContent = msg || (ok ? "已连接" : "未连接");
  }

  /** Returns a Promise that resolves when all campaign data has been refreshed. */
  function loadCampaign() {
    var seq = ++loadCampaignSeq;
    setMetricsLoading(true);
    var proms = [];

    // User activity account
    proms.push(
      apiRequest("/raffle/activity/query_user_activity_account_by_token", {
        method:"POST", body: JSON.stringify({activityId: CONFIG.ACTIVITY_ID})
      }).then(function(r) {
        if (seq !== loadCampaignSeq) return;
        setConnStatus(true);
        d.surplusMetric.textContent = r.data?.totalCountSurplus ?? 0;
        d.dayMetric.textContent = r.data?.dayCountSurplus ?? 0;
        d.ucSurplus.textContent = r.data?.totalCountSurplus ?? 0;
        if (!d.drawResult.textContent.startsWith("恭喜")) {
          d.drawResult.textContent = (Number(r.data?.totalCountSurplus||0)>0)
            ? "准备好了，点击 GO 开始抽奖" : "暂无可用抽奖次数";
        }
      }).catch(function() { setConnStatus(false, "加载数据失败"); })
    );

    // User credit account
    proms.push(
      apiRequest("/raffle/activity/query_user_credit_account_by_token", {method:"POST", body: "{}"}).then(function(r) {
        if (seq !== loadCampaignSeq) return;
        setConnStatus(true);
        d.creditMetric.textContent = r.data ?? 0;
        d.ucCredit.textContent = r.data ?? 0;
        d.creditDisplay.textContent = "积分: " + (r.data ?? 0);
        if (creditMobile) creditMobile.textContent = "积分: " + (r.data ?? 0);
      }).catch(function() { setConnStatus(false, "加载积分失败"); })
    );

    // Sign-in status
    proms.push(
      apiRequest("/raffle/activity/is_calendar_sign_rebate_by_token", {method:"POST", body: "{}"}).then(function(r) {
        if (seq !== loadCampaignSeq) return;
        setConnStatus(true);
        if (r.data === true) {
          signedToday = true;
          if (d.signInBtn) { d.signInBtn.textContent = "今日已签到"; d.signInBtn.classList.add("done"); }
          if (d.ucSignInBtn) { d.ucSignInBtn.textContent = "今日已签到"; d.ucSignInBtn.classList.add("done"); }
          if (d.ucSigned) d.ucSigned.textContent = "是";
          if (d.signInStatus) d.signInStatus.textContent = "今日已完成签到";
        } else {
          signedToday = false;
          if (d.signInBtn) { d.signInBtn.textContent = "每日签到"; d.signInBtn.classList.remove("done"); }
          if (d.ucSignInBtn) { d.ucSignInBtn.textContent = "每日签到 +10 积分"; d.ucSignInBtn.classList.remove("done"); }
          if (d.ucSigned) d.ucSigned.textContent = "否";
          if (d.signInStatus) d.signInStatus.textContent = "";
        }
      }).catch(function(e) {
        if (e.raw && e.raw.data === false) {
          signedToday = false;
          if (d.signInBtn) { d.signInBtn.textContent = "每日签到"; d.signInBtn.classList.remove("done"); }
          if (d.ucSignInBtn) { d.ucSignInBtn.textContent = "每日签到 +10 积分"; d.ucSignInBtn.classList.remove("done"); }
          if (d.ucSigned) d.ucSigned.textContent = "否";
          if (d.signInStatus) d.signInStatus.textContent = "";
          return;
        }
        setConnStatus(false, "加载签到状态失败");
      })
    );

    // Award list
    proms.push(
      apiRequest("/raffle/strategy/query_raffle_award_list_by_token", {
        method:"POST", body: JSON.stringify({activityId: CONFIG.ACTIVITY_ID})
      }).then(function(r) {
        if (seq !== loadCampaignSeq) return;
        setConnStatus(true);
        if (r.data?.length) { awards = r.data; renderWheel(); }
      }).catch(function() { setConnStatus(false, "加载奖品列表失败"); })
    );

    return Promise.all(proms).finally(function() {
      if (seq === loadCampaignSeq) setMetricsLoading(false);
      updateExchangeBtn();
    });
  }

  // ---- Draw（随机积分奖需轮询余额变化以展示实际到账） ----
  function draw() {
    if (!activityDisplayReady) {
      toast("活动准备中，请稍后再试");
      return;
    }
    busy(d.drawBtn, true);
    if (d.drawBtn) d.drawBtn.textContent = "抽奖中...";
    var creditBefore = currentCreditBalance();
    apiRequest("/raffle/activity/draw_by_token", {
      method:"POST", body: JSON.stringify({activityId: CONFIG.ACTIVITY_ID})
    }).then(function(r) {
      var title = r.data?.awardTitle || "奖品";
      var idx = Math.max(0, awards.findIndex(function(a){return a.awardId===r.data?.awardId;}));
      var seg = 360 / awards.length;
      rotation = (rotation + 1440 + (360 - idx*seg - seg/2)) % 5760;
      if (d.wheel) d.wheel.style.transform = "rotate("+rotation+"deg)";
      var pendingTitle = isRandomCreditAward(title) ? "随机积分（发放中…）" : title;
      if (d.drawResult) d.drawResult.textContent = "恭喜获得：" + pendingTitle;
      pushDrawHistory(pendingTitle, r.data?.awardId);

      function finishDraw(displayTitle) {
        if (d.drawResult) d.drawResult.textContent = "恭喜获得：" + displayTitle;
        updateLatestDrawHistory(displayTitle);
        addMsg("assistant", "抽奖完成，你获得了：" + displayTitle);
      }

      if (isRandomCreditAward(title)) {
        pollRandomCreditGain(creditBefore, 12, function(gain) {
          var displayTitle = gain != null ? ("随机积分 +" + gain) : title;
          finishDraw(displayTitle);
          loadCampaign().catch(function(){});
          if (gain != null) {
            var bal = creditBefore + gain;
            pushCreditLedger(gain, bal, "抽奖奖励");
          }
        });
      } else {
        finishDraw(title);
        setTimeout(function(){ loadCampaign().catch(function(){}); }, 1200);
      }
    }).catch(function(e) {
      toast(e.message);
    }).finally(function() { busy(d.drawBtn, false); if (d.drawBtn) d.drawBtn.textContent = "GO"; });
  }

  function requestSignIn(retry) {
    return postJson("/raffle/activity/calendar_sign_rebate_by_token").catch(function(e) {
      if (!retry && (e.code === "0001" || e.code === "0007")) {
        return new Promise(function(resolve) {
          setTimeout(function() { resolve(requestSignIn(true)); }, 800);
        });
      }
      throw e;
    });
  }

  // ---- Sign In ----
  function signIn() {
    if (signedToday) { toast("今日已签到，明天再来"); return; }
    busy(d.signInBtn, true); busy(d.ucSignInBtn, true);
    if (d.signInBtn) d.signInBtn.textContent = "签到中...";
    if (d.ucSignInBtn) d.ucSignInBtn.textContent = "签到中...";
    requestSignIn(false).then(function(r) {
      var data = r.data || {};
      signedToday = true;
      if (d.signInBtn) { d.signInBtn.textContent = "今日已签到"; d.signInBtn.classList.add("done"); }
      if (d.ucSignInBtn) { d.ucSignInBtn.textContent = "今日已签到"; d.ucSignInBtn.classList.add("done"); }
      if (d.signInStatus) d.signInStatus.textContent = data.message || "签到成功！";
      if (d.ucSigned) d.ucSigned.textContent = "是";
      toast(data.message || "签到成功，+10 积分");
      if (data.creditBalance !== undefined && data.creditBalance !== null) {
        var bal = parseFloat(data.creditBalance);
        var prev = currentCreditBalance();
        d.creditMetric.textContent = bal;
        d.ucCredit.textContent = bal;
        d.creditDisplay.textContent = "积分: " + bal;
        if (creditMobile) creditMobile.textContent = "积分: " + bal;
        var reward = data.rewardCredit != null ? parseFloat(data.rewardCredit) : (bal > prev ? bal - prev : 0);
        if (reward > 0) pushCreditLedger(reward, bal, "每日签到");
      }
      loadCampaign().catch(function(){});
    }).catch(function(e) {
      if (e.code === "0003" || e.code === "0004" || (e.message && e.message.indexOf("已签到") >= 0)) {
        treatSignedIn();
        toast("今日已签到，明天再来");
        loadCampaign().catch(function(){});
      } else {
        if (d.signInBtn) { d.signInBtn.textContent = "每日签到"; d.signInBtn.classList.remove("done"); }
        if (d.ucSignInBtn) { d.ucSignInBtn.textContent = "每日签到 +10 积分"; d.ucSignInBtn.classList.remove("done"); }
        toast(e.message || "签到失败，请稍后重试");
      }
    }).finally(function() {
      busy(d.signInBtn, false); busy(d.ucSignInBtn, false);
    });
  }

  function treatSignedIn() {
    signedToday = true;
    if (d.signInBtn) { d.signInBtn.textContent = "今日已签到"; d.signInBtn.classList.add("done"); }
    if (d.ucSignInBtn) { d.ucSignInBtn.textContent = "今日已签到"; d.ucSignInBtn.classList.add("done"); }
    if (d.signInStatus) d.signInStatus.textContent = "今日已完成签到";
    if (d.ucSigned) d.ucSigned.textContent = "是";
  }

  // ---- Chat ----
  // ---- Chat conversations ----
  function activeConv() {
    if (!chatState.conversations.length) {
      var id = crypto.randomUUID();
      chatState.conversations.push({id:id, title:"新的对话", messages:[]});
      chatState.activeId = id;
    }
    return chatState.conversations.find(function(c){return c.id===chatState.activeId;}) || chatState.conversations[0];
  }

  function createMsgElement(m) {
    var el = document.createElement("div");
    el.className = "message " + m.role;
    var content;
    if (m.role === "assistant") {
      if (typeof marked !== "undefined" && typeof DOMPurify !== "undefined") {
        content = DOMPurify.sanitize(marked.parse(m.content, {breaks: true, gfm: true}));
      } else {
        content = esc(m.content);
      }
    } else {
      content = esc(m.content);
    }
    el.innerHTML = '<div class="avatar">'+(m.role==="user"?"我":"AI")+'</div><div class="bubble">'+content+'</div>';
    return el;
  }

  function renderConvListSidebar() {
    var active = activeConv();
    d.convTitle.textContent = active.title;
    d.convList.innerHTML = "";
    chatState.conversations.forEach(function(c) {
      var el = document.createElement("div");
      el.className = "conversation-item" + (c.id===chatState.activeId?" active":"");
      var last = c.messages.length ? c.messages[c.messages.length-1].content.slice(0,30) : "";
      el.innerHTML = '<div class="conv-content"><div class="conv-title">'+esc(c.title)+'</div><div class="conv-preview">'+esc(last)+'</div></div><button class="conv-menu-btn">⋯</button>';
      el.querySelector(".conv-content").onclick = function() { chatState.activeId=c.id; saveChats(); renderChats(); };
      el.querySelector(".conv-menu-btn").onclick = function(e) {
        e.stopPropagation(); ctxTargetId = c.id;
        var r = e.currentTarget.getBoundingClientRect();
        d.contextMenu.style.display = "block";
        d.contextMenu.style.left = Math.min(r.left, window.innerWidth-150)+"px";
        d.contextMenu.style.top = (r.bottom+4)+"px";
      };
      d.convList.appendChild(el);
    });
  }

  function renderChats() {
    var active = activeConv();
    renderConvListSidebar();

    d.msgList.innerHTML = "";
    if (active.messages.length === 0 && !pendingAssistant) {
      d.msgList.innerHTML =
        '<div class="welcome-message">'+
        '<h1>你好，'+esc(auth.userId||"用户")+'</h1>'+
        '<p class="welcome-sub">我是 Lucky Draw AI 助手，可以回答你的问题。</p>'+
        '<p class="welcome-sub">抽奖、签到和积分兑换请使用左侧功能按钮。</p>'+
        '<div class="suggestion-row">'+
        '<button data-s="这个平台有哪些功能？">平台有哪些功能？</button>'+
        '<button data-s="如何参与抽奖活动？">如何参与抽奖？</button>'+
        '<button data-s="积分可以用来做什么？">积分有什么用？</button>'+
        '<button data-s="介绍一下这个抽奖平台">介绍平台</button>'+
        '</div></div>';
      d.msgList.querySelectorAll("[data-s]").forEach(function(b) {
        b.onclick = function() { d.msgInput.value = b.dataset.s; d.msgInput.focus(); };
      });
    } else {
      active.messages.forEach(function(m) {
        d.msgList.appendChild(createMsgElement(m));
      });
    }
    if (pendingAssistant) {
      var typing = document.createElement("div");
      typing.className = "message assistant typing";
      typing.innerHTML = '<div class="avatar">AI</div><div class="bubble"><span class="typing-dots">思考中...</span></div>';
      d.msgList.appendChild(typing);
    }
    d.msgList.scrollTop = d.msgList.scrollHeight;
  }

  function addMsg(role, content, payload) {
    var a = activeConv();
    a.messages.push({role:role, content:content, payload:payload, at:Date.now()});
    if (role==="user" && a.title==="新的对话") a.title = content.slice(0,18);
    saveChats(); renderChats();
  }

  /** 调用 chatbot 服务；失败退积分由后端处理，前端仅展示余额与错误提示。 */
  function ask(text) {
    text = text.trim(); if (!text) return;
    if (!chatbotEnabled) { toast("AI 对话已在管理端关闭"); return; }
    addMsg("user", text);
    d.msgInput.value = ""; d.msgInput.style.height = "auto";
    pendingAssistant = true;
    renderChats();
    busy(d.sendBtn, true);
    d.sendBtn.textContent = "...";
    var requestId = crypto.randomUUID();
    apiRequest("/chatbot/ask", {
      method:"POST",
      body: JSON.stringify({requestId: requestId, activityId:CONFIG.ACTIVITY_ID, message:text})
    }).then(function(r) {
      pendingAssistant = false;
      var data = r.data || {};
      if (data.success === false || data.toolName === "disabled") {
        chatbotEnabled = false;
        applyChatbotGate();
        addMsg("assistant", data.answer || "AI 对话当前不可用。");
        return;
      }
      var answer = data.answer || r.info || "已处理。";
      if (data.creditDeducted && data.creditDeducted > 0) {
        answer += "\n\n---\n*本次消耗 " + data.creditDeducted + " 积分*";
      }
      addMsg("assistant", answer, {creditDeducted: data.creditDeducted, creditBalance: data.creditBalance});
      // Update credit display
      if (data.creditBalance !== undefined && data.creditBalance !== null) {
        var bal = data.creditBalance;
        d.creditDisplay.textContent = "积分: " + bal;
        d.creditMetric.textContent = bal;
        d.ucCredit.textContent = bal;
        if (creditMobile) creditMobile.textContent = "积分: " + bal;
        if (data.creditDeducted && data.creditDeducted > 0) {
          pushCreditLedger(-Number(data.creditDeducted), bal, "AI 对话");
        }
      }
    }).catch(function(e) {
      if (e.code === "0003" || (e.message && e.message.indexOf("积分不足") >= 0)) {
        addMsg("assistant", "积分不足，无法发送消息。请先签到赚取积分或兑换后再试。\n\n你可以：\n1. 点击左侧「用户中心」→ 每日签到获取积分\n2. 在用户中心兑换抽奖次数");
      } else {
        addMsg("assistant", "请求失败：" + (e.message || "未知错误"));
      }
    }).finally(function() {
      pendingAssistant = false;
      busy(d.sendBtn, false);
      d.sendBtn.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>';
    });
  }

  function newChat() {
    var id = crypto.randomUUID();
    chatState.conversations.unshift({id:id, title:"新的对话", messages:[]});
    chatState.activeId = id; saveChats(); renderChats();
  }

  function deleteConv(id) {
    if (!confirm("确定删除该对话吗？")) return;
    if (chatState.conversations.length <= 1) {
      chatState.conversations[0].messages = [];
      chatState.conversations[0].title = "新的对话";
    } else {
      chatState.conversations = chatState.conversations.filter(function(c){return c.id!==id;});
      if (chatState.activeId===id) chatState.activeId = chatState.conversations[0].id;
    }
    saveChats(); renderChats();
  }

  function renameConv(id) {
    var c = chatState.conversations.find(function(c){return c.id===id;});
    if (!c) return;
    d.renameInput.value = c.title;
    d.renameDialog.style.display = "flex";
    d.renameInput.focus(); d.renameInput.select();
    ctxTargetId = id;
  }

  // ---- Drawers ----
  // ---- Drawers (lottery / user center) ----
  function openDrawer(drawer) {
    if (drawer !== d.lotteryDrawer) closeDrawer(d.lotteryDrawer);
    if (drawer !== d.userCenterDrawr) closeDrawer(d.userCenterDrawr);
    drawer.classList.add("open");
    d.drawerOverlay.classList.add("open");
  }
  function closeDrawer(drawer) { drawer.classList.remove("open"); d.drawerOverlay.classList.remove("open"); }
  function closeAll() { closeDrawer(d.lotteryDrawer); closeDrawer(d.userCenterDrawr); }
  function openLottery() { openDrawer(d.lotteryDrawer); loadCampaign().catch(function(){}); loadExchangeSku(); }
  function closeLottery() { closeDrawer(d.lotteryDrawer); }
  function openUserCenter() { openDrawer(d.userCenterDrawr); loadCampaign().catch(function(){}); loadExchangeSku(); renderHistories(); }
  function closeUserCenter() { closeDrawer(d.userCenterDrawr); }

  // ---- Credit Exchange ----
  var exchangeSku = null; // cached SKU info from server
  function loadExchangeSku() {
    apiRequest("/raffle/activity/query_sku_product_list_by_activity_id?activityId=" + CONFIG.ACTIVITY_ID, {
      method: "POST"
    }).then(function(r) {
      var list = r.data || [];
      exchangeSku = list.length > 0 ? list[0] : null;
      if (exchangeSku) {
        var cost = exchangeSku.productAmount || 0;
        var surplus = exchangeSku.stockCountSurplus || 0;
        var info = cost + " 积分 = 1 次抽奖机会";
        if (surplus > 0) info += "（剩余库存: " + surplus + "）";
        else info += "（库存不足）";
        if (d.exchangeInfo) d.exchangeInfo.textContent = info;
        updateExchangeBtn();
      } else {
        if (d.exchangeInfo) d.exchangeInfo.textContent = "暂无可兑换商品";
      }
    }).catch(function() {
      if (d.exchangeInfo) d.exchangeInfo.textContent = "加载兑换信息失败";
    });
  }

  function updateExchangeBtn() {
    if (!exchangeSku) { disableExchange("暂无可兑换商品"); return; }
    var cost = exchangeSku.productAmount || 0;
    var surplus = exchangeSku.stockCountSurplus || 0;
    var currentCredit = parseFloat(d.creditMetric.textContent) || 0;
    if (surplus <= 0) { disableExchange("库存不足"); return; }
    if (currentCredit < cost) { disableExchange("积分不足，需要 " + cost + " 积分"); return; }
    // Enable exchange
    if (d.exchangeBtn) { d.exchangeBtn.disabled = false; d.exchangeBtn.textContent = "兑换 1 次抽奖机会（消耗 " + cost + " 积分）"; }
    if (d.ucExchangeBtn) { d.ucExchangeBtn.disabled = false; d.ucExchangeBtn.textContent = "兑换 1 次抽奖机会（" + cost + " 积分）"; }
    if (d.ucExchangeHint) { d.ucExchangeHint.style.display = "none"; }
  }

  function disableExchange(msg) {
    if (d.exchangeBtn) { d.exchangeBtn.disabled = true; d.exchangeBtn.textContent = msg || "无法兑换"; }
    if (d.ucExchangeBtn) { d.ucExchangeBtn.disabled = true; d.ucExchangeBtn.textContent = msg || "无法兑换"; }
    if (d.ucExchangeHint && msg) { d.ucExchangeHint.style.display = ""; d.ucExchangeHint.textContent = msg; }
  }

  function doExchange() {
    if (!exchangeSku) { toast("暂无可兑换商品"); return; }
    var cost = exchangeSku.productAmount || 0;
    var currentCredit = parseFloat(d.creditMetric.textContent) || 0;
    if (currentCredit < cost) { toast("积分不足，先签到赚积分"); return; }
    busy(d.exchangeBtn, true);
    if (d.exchangeBtn) d.exchangeBtn.textContent = "兑换中...";
    if (d.ucExchangeBtn) { busy(d.ucExchangeBtn, true); d.ucExchangeBtn.textContent = "兑换中..."; }
    apiRequest("/raffle/activity/credit_pay_exchange_sku_by_token", {
      method: "POST",
      body: JSON.stringify({ sku: exchangeSku.sku, requestId: crypto.randomUUID() })
    }).then(function() {
      toast("兑换成功，获得 1 次抽奖机会");
      var cost = exchangeSku ? (exchangeSku.productAmount || 0) : 0;
      var bal = parseFloat(d.creditMetric.textContent) || 0;
      if (cost > 0) pushCreditLedger(-cost, bal, "兑换抽奖次数");
      loadCampaign().catch(function(){});
    }).catch(function(e) {
      toast(e.message || "兑换失败");
    }).finally(function() {
      busy(d.exchangeBtn, false); updateExchangeBtn();
      if (d.ucExchangeBtn) { busy(d.ucExchangeBtn, false); updateExchangeBtn(); }
    });
  }

  // ---- Utility ----
  function busy(el, v) { if (el) { el.disabled = v; el.style.opacity = v?"0.5":""; } }

  // Expose to inline onclick handlers
  window._uc = openUserCenter;
  window._lottery = openLottery;

// ===== EVENT BINDINGS =====
  d.userMenuBtn.onclick = openUserCenter;
  d.userCenterBtn.onclick = openUserCenter;
  d.openLotteryBtn.onclick = openLottery;
  if (d.mobileLotteryBtn) d.mobileLotteryBtn.onclick = openLottery;
  d.closeDrawerBtn.onclick = closeLottery;
  d.closeUcBtn.onclick = closeUserCenter;
  d.drawerOverlay.onclick = closeAll;
  d.drawBtn.onclick = draw;
  d.refreshCampaign.onclick = function() {
    if (d.refreshCampaign) {
      d.refreshCampaign.classList.add("refreshing");
      d.refreshCampaign.textContent = "刷新中";
    }
    loadCampaign().then(function(){ toast("已刷新"); }).catch(function(e){ toast(e.message); }).finally(function() {
      if (d.refreshCampaign) {
        d.refreshCampaign.classList.remove("refreshing");
        d.refreshCampaign.textContent = "刷新";
      }
    });
  };
  d.logoutBtn.onclick = logout;
  if (d.exchangeBtn) d.exchangeBtn.onclick = doExchange;
  if (d.signInBtn) d.signInBtn.onclick = signIn;
  if (d.ucSignInBtn) d.ucSignInBtn.onclick = signIn;
  if (d.ucExchangeBtn) d.ucExchangeBtn.onclick = doExchange;

  // Context menu
  document.addEventListener("click", function() { d.contextMenu.style.display = "none"; });
  d.contextMenu.querySelector("[data-action=delete]").onclick = function() {
    if (ctxTargetId) deleteConv(ctxTargetId); d.contextMenu.style.display = "none";
  };
  d.contextMenu.querySelector("[data-action=rename]").onclick = function() {
    if (ctxTargetId) renameConv(ctxTargetId); d.contextMenu.style.display = "none";
  };

  // Rename dialog
  d.renameConfirm.onclick = function() {
    var n = d.renameInput.value.trim();
    if (n && ctxTargetId) {
      var c = chatState.conversations.find(function(c){return c.id===ctxTargetId;});
      if (c) { c.title = n; saveChats(); renderChats(); }
    }
    d.renameDialog.style.display = "none";
  };
  d.renameCancel.onclick = function() { d.renameDialog.style.display = "none"; };
  d.renameInput.onkeydown = function(e) { if (e.key==="Enter") d.renameConfirm.click(); };
  d.renameDialog.onclick = function(e) { if (e.target===d.renameDialog) d.renameDialog.style.display="none"; };

  // Chat
  d.newChatBtn.onclick = newChat;
  d.clearChatBtn.onclick = function() {
    if (!confirm("确定清空当前对话吗？")) return;
    var a = activeConv(); a.messages = []; a.title = "新的对话"; saveChats(); renderChats();
  };
  d.chatForm.onsubmit = function(e) { e.preventDefault(); ask(d.msgInput.value); };
  d.msgInput.onkeydown = function(e) { if (e.key==="Enter"&&!e.shiftKey) { e.preventDefault(); d.chatForm.requestSubmit(); } };
  d.msgInput.oninput = function() {
    d.msgInput.style.height = "auto";
    d.msgInput.style.height = Math.min(d.msgInput.scrollHeight, 150)+"px";
  };

  // Global shortcut
  document.addEventListener("keydown", function(e) {
    if ((e.metaKey||e.ctrlKey) && e.key==="k") { e.preventDefault(); d.msgInput.focus(); }
  });

  // Init
  renderWheel();
  renderChats();
  renderHistories();
  healthCheck();
  resolveActivityId()
    .then(function() { return loadDisplayConfig(); })
    .then(function() { return loadCampaign(); })
    .catch(function(){});
  setInterval(healthCheck, 8000);
}
