var auth = readAuth();
var CHAT_KEY = "lucky-draw-chats-" + (auth.userId || "anon");

// ===== Auth gate =====
if (!auth.token) {
  showLanding();
} else {
  showLanding();
  // Verify token — only enter app on success
  apiRequest("/auth/verify", {}, {
    onAuthExpired: function() {
      clearAuth();
      toast("登录已过期，请重新登录");
    }
  }).then(function() {
    initApp();
  }).catch(function(e) {
    clearAuth();
    // Network error — show toast on landing
    toast("后端不可用: " + e.message);
  });
}

function showLanding() {
  document.getElementById("landingView").style.display = "";
  document.getElementById("appView").style.display = "none";
}

// ===== Main App =====
function initApp() {
  document.getElementById("landingView").style.display = "none";
  document.getElementById("appView").style.display = "";

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
    toast:           qs("#toast")
  };

  function qs(sel) { return document.querySelector(sel); }
  var creditMobile = document.getElementById("creditDisplayMobile");

  var chatState = readJson(CHAT_KEY, defaultChats());
  var awards = [
    {awardTitle: "积分礼包", awardId: 101},
    {awardTitle: "抽奖次数", awardId: 102},
    {awardTitle: "体验权益", awardId: 103},
    {awardTitle: "谢谢参与", awardId: 104},
    {awardTitle: "专属折扣", awardId: 105},
    {awardTitle: "加赠奖励", awardId: 106}
  ];
  var rotation = 0;
  var ctxTargetId = null;
  var signedToday = false;

  function readJson(key, fallback) {
    try { var v = localStorage.getItem(key); return v ? JSON.parse(v) : fallback; }
    catch (e) { return fallback; }
  }

  function defaultChats() {
    var id = crypto.randomUUID();
    return { activeId: id, conversations: [{ id: id, title: "新的对话", messages: [] }] };
  }
  function saveChats() { localStorage.setItem(CHAT_KEY, JSON.stringify(chatState)); }

  // ---- Health (via gateway) ----
  function healthCheck() {
    fetch(CONFIG.API_BASE.replace("/api/v1", "") + "/actuator/health")
      .then(function(r) { return r.json(); })
      .then(function(data) {
        var up = data.status === "UP";
        d.apiStatusDot.className = "status-dot" + (up ? " online" : "");
        d.apiStatusText.textContent = up ? "已连接" : "未连接";
      })
      .catch(function() {
        d.apiStatusDot.className = "status-dot";
        d.apiStatusText.textContent = "未连接";
      });
  }

  // ---- Auth UI ----
  d.userNameBadge.textContent = auth.userId || "用户";
  d.userName.textContent = auth.userId || "用户";
  d.userIdDisplay.textContent = "ID: " + (auth.userId || "-");
  d.userAvatar.textContent = (auth.userId || "?")[0].toUpperCase();

  // ---- Logout ----
  function logout() {
    clearAuth();
    location.reload();
  }

  // ---- Wheel ----
  function renderWheel() {
    var seg = 360 / awards.length;
    var colors = ["#f97316","#14b8a6","#3b82f6","#facc15","#a855f7","#22c55e","#ef4444","#06b6d4"];
    var grad = awards.map(function(a,i) { return colors[i%colors.length]+" "+(i*seg)+"deg "+((i+1)*seg)+"deg"; }).join(", ");
    d.wheel.style.background = "conic-gradient(" + grad + ")";
    d.wheel.innerHTML = "";
    awards.forEach(function(award, i) {
      var el = document.createElement("span");
      el.className = "wheel-label";
      el.style.transform = "rotate(" + (i*seg+seg/2) + "deg) translateY(-118px) rotate(90deg)";
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
    var proms = [];

    // Armory (fire-and-forget, may fail silently)
    apiRequest("/raffle/activity/armory?activityId=" + CONFIG.ACTIVITY_ID, {method:"GET"}).catch(function(){});

    // User activity account
    proms.push(
      apiRequest("/raffle/activity/query_user_activity_account_by_token", {
        method:"POST", body: JSON.stringify({activityId: CONFIG.ACTIVITY_ID})
      }).then(function(r) {
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
      apiRequest("/raffle/activity/query_user_credit_account_by_token", {method:"POST"}).then(function(r) {
        setConnStatus(true);
        d.creditMetric.textContent = r.data ?? 0;
        d.ucCredit.textContent = r.data ?? 0;
        d.creditDisplay.textContent = "积分: " + (r.data ?? 0);
        if (creditMobile) creditMobile.textContent = "积分: " + (r.data ?? 0);
      }).catch(function() { setConnStatus(false, "加载积分失败"); })
    );

    // Sign-in status
    proms.push(
      apiRequest("/raffle/activity/is_calendar_sign_rebate_by_token", {method:"POST"}).then(function(r) {
        setConnStatus(true);
        if (r.data === true) {
          signedToday = true;
          if (d.signInBtn) { d.signInBtn.textContent = "今日已签到"; d.signInBtn.classList.add("done"); }
          if (d.ucSigned) d.ucSigned.textContent = "是";
          if (d.signInStatus) d.signInStatus.textContent = "今日已完成签到";
        } else {
          signedToday = false;
          if (d.signInBtn) { d.signInBtn.textContent = "每日签到"; d.signInBtn.classList.remove("done"); }
          if (d.ucSigned) d.ucSigned.textContent = "否";
          if (d.signInStatus) d.signInStatus.textContent = "";
        }
      }).catch(function() { setConnStatus(false, "加载签到状态失败"); })
    );

    // Award list
    proms.push(
      apiRequest("/raffle/strategy/query_raffle_award_list_by_token", {
        method:"POST", body: JSON.stringify({activityId: CONFIG.ACTIVITY_ID})
      }).then(function(r) {
        setConnStatus(true);
        if (r.data?.length) { awards = r.data; renderWheel(); }
      }).catch(function() { setConnStatus(false, "加载奖品列表失败"); })
    );

    return Promise.all(proms);
  }

  // ---- Draw ----
  function draw() {
    busy(d.drawBtn, true);
    apiRequest("/raffle/activity/draw_by_token", {
      method:"POST", body: JSON.stringify({activityId: CONFIG.ACTIVITY_ID})
    }).then(function(r) {
      var idx = Math.max(0, awards.findIndex(function(a){return a.awardId===r.data?.awardId;}));
      var seg = 360 / awards.length;
      rotation += 1440 + (360 - idx*seg - seg/2);
      if (d.wheel) d.wheel.style.transform = "rotate("+rotation+"deg)";
      if (d.drawResult) d.drawResult.textContent = "恭喜获得：" + (r.data?.awardTitle||"奖品");
      addMsg("assistant", "抽奖完成，你获得了：" + (r.data?.awardTitle||"奖品"));
      setTimeout(function(){ loadCampaign().catch(function(){}); }, 1200);
    }).catch(function(e) {
      toast(e.message);
    }).finally(function() { busy(d.drawBtn, false); });
  }

  // ---- Sign In ----
  function signIn() {
    if (signedToday) { toast("今日已签到"); return; }
    busy(d.signInBtn, true);
    apiRequest("/raffle/activity/calendar_sign_rebate_by_token", {method:"POST"}).then(function(r) {
      signedToday = true;
      if (d.signInBtn) { d.signInBtn.textContent = "今日已签到"; d.signInBtn.classList.add("done"); }
      if (d.signInStatus) d.signInStatus.textContent = "签到成功！";
      if (d.ucSigned) d.ucSigned.textContent = "是";
      toast("签到成功！");
      loadCampaign().catch(function(){});
    }).catch(function(e) {
      if (e.code === "0003") {
        treatSignedIn(); toast("今日已签到");
      } else {
        // Sign-in might have failed because already signed in today
        // (loadCampaign may not have refreshed signedToday). Check status.
        apiRequest("/raffle/activity/is_calendar_sign_rebate_by_token", {method:"POST"}).then(function(r) {
          if (r.data === true) {
            treatSignedIn(); toast("今日已签到");
          } else {
            toast(e.message || "签到失败");
          }
        }).catch(function() {
          toast(e.message || "签到失败");
        });
      }
    }).finally(function() {
      busy(d.signInBtn, false);
    });
  }

  function treatSignedIn() {
    signedToday = true;
    if (d.signInBtn) { d.signInBtn.textContent = "今日已签到"; d.signInBtn.classList.add("done"); }
    if (d.signInStatus) d.signInStatus.textContent = "今日已完成签到";
    if (d.ucSigned) d.ucSigned.textContent = "是";
  }

  // ---- Chat ----
  function activeConv() {
    return chatState.conversations.find(function(c){return c.id===chatState.activeId;}) || chatState.conversations[0];
  }

  function renderChats() {
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
        var r = e.target.getBoundingClientRect();
        d.contextMenu.style.display = "block";
        d.contextMenu.style.left = Math.min(r.left, window.innerWidth-150)+"px";
        d.contextMenu.style.top = (r.bottom+4)+"px";
      };
      d.convList.appendChild(el);
    });

    d.msgList.innerHTML = "";
    if (active.messages.length === 0) {
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
        var el = document.createElement("div");
        el.className = "message "+m.role;
        el.innerHTML = '<div class="avatar">'+(m.role==="user"?"我":"AI")+'</div><div class="bubble">'+esc(m.content)+'</div>';
        if (m.payload) {
          var p = document.createElement("div"); p.className="payload";
          p.textContent = JSON.stringify(m.payload,null,2);
          el.querySelector(".bubble").appendChild(p);
        }
        d.msgList.appendChild(el);
      });
    }
    d.msgList.scrollTop = d.msgList.scrollHeight;
  }

  function addMsg(role, content, payload) {
    var a = activeConv();
    a.messages.push({role:role, content:content, payload:payload, at:Date.now()});
    if (role==="user" && a.title==="新的对话") a.title = content.slice(0,18);
    saveChats(); renderChats();
  }

  function ask(text) {
    text = text.trim(); if (!text) return;
    addMsg("user", text);
    d.msgInput.value = ""; d.msgInput.style.height = "auto";
    busy(d.sendBtn, true);
    apiRequest("/chatbot/ask", {
      method:"POST",
      body: JSON.stringify({token:auth.token||"", activityId:CONFIG.ACTIVITY_ID, message:text})
    }).then(function(r) {
      addMsg("assistant", (r.data&&r.data.answer)||r.info||"已处理。");
    }).catch(function(e) {
      addMsg("assistant", "请求失败："+e.message);
    }).finally(function() { busy(d.sendBtn, false); });
  }

  function newChat() {
    var id = crypto.randomUUID();
    chatState.conversations.unshift({id:id, title:"新的对话", messages:[]});
    chatState.activeId = id; saveChats(); renderChats();
  }

  function deleteConv(id) {
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
  function openDrawer(drawer) { drawer.classList.add("open"); d.drawerOverlay.classList.add("open"); }
  function closeDrawer(drawer) { drawer.classList.remove("open"); d.drawerOverlay.classList.remove("open"); }
  function closeAll() { closeDrawer(d.lotteryDrawer); closeDrawer(d.userCenterDrawr); }
  function openLottery() { openDrawer(d.lotteryDrawer); loadCampaign().catch(function(){}); }
  function closeLottery() { closeDrawer(d.lotteryDrawer); }
  function openUserCenter() { openDrawer(d.userCenterDrawr); loadCampaign().catch(function(){}); }
  function closeUserCenter() { closeDrawer(d.userCenterDrawr); }

  // ---- Utility ----
  function busy(el, v) { if (el) { el.disabled = v; el.style.opacity = v?"0.5":""; } }

  // Expose to inline onclick handlers
  window._uc = openUserCenter;
  window._lottery = openLottery;

  // Mobile nav button bindings
  var mNewChat = document.getElementById("mNewChatBtn");
  var mUc = document.getElementById("mUserCenterBtn");
  var mLottery = document.getElementById("mOpenLotteryBtn");
  if (mNewChat) mNewChat.onclick = newChat;
  if (mUc) mUc.onclick = openUserCenter;
  if (mLottery) mLottery.onclick = openLottery;

  // ===== EVENT BINDINGS =====
  d.userMenuBtn.onclick = openUserCenter;
  d.userCenterBtn.onclick = openUserCenter;
  d.openLotteryBtn.onclick = openLottery;
  d.closeDrawerBtn.onclick = closeLottery;
  d.closeUcBtn.onclick = closeUserCenter;
  d.drawerOverlay.onclick = closeAll;
  d.drawBtn.onclick = draw;
  d.signInBtn.onclick = signIn;
  d.refreshCampaign.onclick = function() { loadCampaign().then(function(){toast("已刷新");}).catch(function(e){toast(e.message);}); };
  d.logoutBtn.onclick = logout;

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
  healthCheck();
  loadCampaign().catch(function(){});
  setInterval(healthCheck, 8000);
}
