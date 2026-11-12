(() => {
'use strict';
const app = document.getElementById('app');

// ---- helpers (DOM is built with textContent only, never innerHTML) ----------------------------
const h = (tag, attrs, ...kids) => {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (k === 'class') e.className = v;
    else if (k === 'style') e.style.cssText = v;
    else if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
    else if (v === true) e.setAttribute(k, '');
    else if (v !== false && v != null) e.setAttribute(k, v);
  }
  for (const c of kids.flat(2)) if (c != null && c !== false) e.append(c.nodeType ? c : String(c));
  return e;
};
const svg = (tag, attrs) => {
  const e = document.createElementNS('http://www.w3.org/2000/svg', tag);
  for (const [k, v] of Object.entries(attrs || {})) { if (k === 'style') e.style.cssText = v; else e.setAttribute(k, v); }
  return e;
};
const clear = (e) => { while (e.firstChild) e.removeChild(e.firstChild); };

const fmtReq = (t) => t == null ? 'Not set' : fmtTicks(t);
const fmtTicks = (t) => fmtSecs(Math.floor((t || 0) / 20));
function fmtSecs(s) {
  const d = Math.floor(s / 86400), hr = Math.floor(s % 86400 / 3600), m = Math.floor(s % 3600 / 60);
  if (d) return `${d}d ${hr}h`;
  if (hr) return `${hr}h ${m}m`;
  if (m) return `${m}m`;
  return `${s}s`;
}
const fmtDate = (ms) => ms == null ? '—' : new Date(ms).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
// Goal/reward timestamps are stored as text ("2026-11-01 10:00:00", UTC on SQLite) or as epoch millis
const parseTs = (s) => {
  if (!s) return NaN;
  return new Date(/^\d+$/.test(s) ? Number(s) : s.replace(' ', 'T') + (/[zZ+]/.test(s) ? '' : 'Z')).getTime();
};
const fmtText = (s) => {
  if (!s) return '—';
  const t = parseTs(s);
  return isNaN(t) ? s : new Date(t).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
};
const AV = ['#43bff0', '#ff904e', '#fff361', '#cfe5ee'];
const avatar = (name, big) => {
  let x = 0; for (const c of name) x = (x * 31 + c.charCodeAt(0)) >>> 0;
  return h('div', { class: 'avatar' + (big ? ' lg' : ''), style: `--av:${AV[x % AV.length]}` }, (name[0] || '?').toUpperCase());
};

async function api(path, opts) {
  const r = await fetch(path, Object.assign({ credentials: 'same-origin' }, opts));
  if (r.status === 401) { showLogin(); throw new Error('unauthorized'); }
  if (!r.ok) throw new Error('HTTP ' + r.status);
  return r.json();
}

const loading = (label) => h('div', { class: 'loading', role: 'status' }, h('span', { class: 'spin' }), label || 'Loading…');

const brandMark = () => h('div', { class: 'brand' }, h('img', { src: '/icon.webp', alt: '', width: 34, height: 34 }),
  h('span', { class: 'wordmark' }, h('span', { class: 'pt' }, 'Playtime'), ' ', h('span', { class: 'tm' }, 'Manager')));

// ---- pagination -------------------------------------------------------------------------------
/** Numbered pager: 1 … 4 5 [6] 7 8 … 20 */
function pager(page, pages, total, onChange) {
  const nums = new Set([0, pages - 1, page - 1, page, page + 1]);
  const list = [...nums].filter(n => n >= 0 && n < pages).sort((a, b) => a - b);
  const btns = [];
  list.forEach((n, i) => {
    if (i && n - list[i - 1] > 1) btns.push(h('span', { class: 'muted' }, '…'));
    btns.push(h('button', { class: 'btn pg' + (n === page ? ' on' : ''), onclick: () => onChange(n) }, n + 1));
  });
  return h('div', { class: 'pager' }, h('span', {}, `${total.toLocaleString()} total`),
    pages > 1 && h('div', { class: 'pages' },
      h('button', { class: 'btn pg', disabled: page <= 0, onclick: () => onChange(page - 1), 'aria-label': 'Previous' }, '‹'),
      btns,
      h('button', { class: 'btn pg', disabled: page >= pages - 1, onclick: () => onChange(page + 1), 'aria-label': 'Next' }, '›')));
}

/** Client-side paginated list. render(item) returns an element; wrap(rows) the container. */
function paged(items, size, render, empty, wrap) {
  const box = h('div');
  let page = 0;
  const draw = () => {
    clear(box);
    if (!items.length) { box.append(h('div', { class: 'empty' }, empty)); return; }
    const pages = Math.ceil(items.length / size);
    const rows = items.slice(page * size, page * size + size).map(render);
    box.append(wrap ? wrap(rows) : h('div', { class: 'list' }, rows),
      pager(page, pages, items.length, (n) => { page = n; draw(); }));
  };
  draw();
  box.reveal = (pred) => {                       // jump to the page that holds the first matching item
    const i = items.findIndex(pred);
    if (i >= 0 && Math.floor(i / size) !== page) { page = Math.floor(i / size); draw(); }
  };
  return box;
}

// ---- login (username and password) -----------------------------------------------------------
function showLogin() {
  clear(app);
  const err = h('p', { class: 'muted err' });
  const user = h('input', { type: 'text', name: 'username', autocomplete: 'username', placeholder: 'Username', required: 'required', maxlength: 32 });
  const pass = h('input', { type: 'password', name: 'password', autocomplete: 'current-password', placeholder: 'Password', required: 'required', maxlength: 200 });
  const btn = h('button', { class: 'btn solid', type: 'submit' }, 'Log in');
  const form = h('form', { class: 'loginform' }, user, pass, err, btn);
  form.onsubmit = async (e) => {
    e.preventDefault();
    btn.disabled = true; err.textContent = '';
    try {
      const r = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: user.value, password: pass.value }) });
      if (r.ok) { boot(); return; }
      err.textContent = r.status === 401 ? 'Wrong username or password.'
        : r.status === 429 ? 'Too many attempts. Wait a few minutes and retry.' : 'Could not log in.';
      pass.value = ''; pass.focus();
    } catch (x) { err.textContent = 'Server unreachable.'; }
    btn.disabled = false;
  };
  app.append(h('div', { class: 'login' }, h('div', { class: 'card' },
    h('div', { class: 'mark' }, h('img', { src: '/icon.webp', alt: '', width: 84, height: 84 }),
      h('div', { class: 'wordmark' }, h('span', { class: 'pt' }, 'Playtime'), h('span', { class: 'tm' }, 'Manager'))),
    form)));
  user.focus();
}

// ---- shell ------------------------------------------------------------------------------------
const views = { overview: renderOverview, players: renderPlayers, goals: renderGoals, rewards: renderRewards };
let main;

async function boot() {
  try { await api('/api/me'); } catch (e) { return; }
  clear(app);
  const nav = h('nav');
  for (const k of Object.keys(views)) nav.append(h('a', { class: 'tab', href: '#' + k, 'data-v': k }, k[0].toUpperCase() + k.slice(1)));
  main = h('main');
  app.append(
    h('div', { class: 'top' }, h('div', { class: 'wrap' },
      brandMark(), nav,
      h('button', { class: 'btn', onclick: async () => { await fetch('/api/auth/logout', { method: 'POST' }); showLogin(); } }, 'Log out'))),
    h('div', { class: 'wrap' }, main));
  window.onhashchange = route;
  route();
}
function route() {
  const v = location.hash.slice(1);
  const cur = views[v] ? v : 'overview';
  document.querySelectorAll('nav .tab').forEach(a => a.classList.toggle('on', a.dataset.v === cur));
  clear(main);
  const wait = loading();
  main.append(wait);   // hidden by CSS as soon as the view appends its content
  views[cur]().catch(e => { if (e.message !== 'unauthorized') main.append(h('div', { class: 'card empty' }, 'Failed to load data.')); })
    .finally(() => wait.remove());
}

const tile = (k, v, color, span = 'c3') => h('div', { class: 'card stat ' + span, style: `--c:${color}` }, h('div', { class: 'k' }, k), h('div', { class: 'v' }, v));

// ---- overview ---------------------------------------------------------------------------------
async function renderOverview() {
  const d = await api('/api/overview');
  const jr = d.journal;
  const afkShare = d.totalPlaytime ? Math.round(d.totalAfk / d.totalPlaytime * 100) : 0;
  const avg = d.players ? d.totalPlaytime / d.players : 0;
  const todayPlayers = d.activity.length ? d.activity[d.activity.length - 1].players : 0;
  const bestDay = Math.max(0, ...d.activity.map(x => x.players));
  const ms = (v) => fmtSecs(Math.round(v / 1000));
  const sumRow = (k, v, sub) => h('div', { class: 'srow' }, h('span', { class: 'k' }, k),
    h('span', { class: 'v' }, v, sub && h('small', {}, sub)));

  main.append(
    h('section', { class: 'hero' },
      h('div', { class: 'eyebrow' }, 'Total playtime across the server'),
      h('div', { class: 'big' }, fmtTicks(d.totalPlaytime)),
      h('div', { class: 'chips' },
        h('span', { class: 'chip' }, d.players.toLocaleString() + ' players'),
        h('span', { class: 'chip' }, afkShare + '% AFK'),
        h('span', { class: 'chip' }, 'avg ' + fmtTicks(avg) + ' each'))),
    h('div', { class: 'cols' },
      tile('Players today', todayPlayers.toLocaleString(), 'var(--blue)'),
      tile('Best day · 30d', bestDay.toLocaleString(), 'var(--orange)'),
      tile('AFK time', fmtTicks(d.totalAfk), 'var(--yellow)'),
      tile('Avg session · 30d', jr.sessions ? ms(jr.avgMs) : '—', 'var(--ice)')),
    h('div', { class: 'cols' },
      h('div', { class: 'card c8' }, h('h2', {}, 'Unique players per day', h('span', { class: 'count' }, 'last 30 days')),
        barChart(d.activity.map(x => ({ day: x.day, v: x.players })), { h: 340, fmt: (v) => `${v} players`, tick: (v) => String(Math.round(v)) })),
      h('div', { class: 'card c4' }, h('h2', {}, 'Top playtime'), leaderboard(d.top.slice(0, 7)))),
    h('div', { class: 'cols' },
      h('div', { class: 'card c8' }, h('h2', {}, 'When players join', h('span', { class: 'count' }, 'sessions started · 30 days')), heatmap(jr.heat)),
      h('div', { class: 'card c4' }, h('h2', {}, 'Journal summary', h('span', { class: 'count' }, '30 days')),
        jr.sessions ? h('div', { class: 'summary' },
          sumRow('Sessions', jr.sessions.toLocaleString()),
          sumRow('Time online', ms(jr.totalMs)),
          sumRow('AFK in sessions', jr.totalMs ? Math.round(jr.afkMs / jr.totalMs * 100) + '%' : '0%'),
          sumRow('Active players', jr.activePlayers.toLocaleString()),
          sumRow('New players', jr.newPlayers.toLocaleString()),
          sumRow('Longest session', ms(jr.longestMs), jr.longestBy ? jr.longestBy + ' · ' + fmtDate(jr.longestAt) : null),
          sumRow('Peak online', jr.peakPlayers + ' at once', fmtDate(jr.peakAt)))
          : h('div', { class: 'empty' }, 'No finished sessions in the last 30 days.'))),
    h('div', { class: 'cols' },
      h('div', { class: 'card c6' }, h('h2', {}, 'Session length', h('span', { class: 'count' }, 'how long visits last')), lengthChart(jr.lengths)),
      h('div', { class: 'card c6' }, h('h2', {}, 'Hours online per day', h('span', { class: 'count' }, 'last 30 days')),
        barChart(d.activity.map((x, i) => ({ day: x.day, v: (jr.onlinePerDay[i] || 0) / 3.6e6 })),
          { color: 'var(--orange)', fmt: (v) => v.toFixed(1) + ' h', tick: (v) => (v >= 10 ? Math.round(v) : v.toFixed(1)) + 'h' }))));
}

function leaderboard(top) {
  const max = Math.max(1, ...top.map(p => p.playtime));
  return h('div', { class: 'lb' }, top.map((p, i) => h('div', { class: 'r', onclick: () => openPlayer(p.uuid) },
    h('div', { class: 'rank' }, i + 1), avatar(p.nickname),
    h('div', { style: 'min-width:0' }, h('div', { class: 'nm' }, p.nickname),
      h('div', { class: 'meter' }, h('i', { style: `width:${p.playtime / max * 100}%` }))),
    h('div', { class: 'val' }, fmtTicks(p.playtime)))));
}

/** Daily bars for 30 days; the last bar (today) is highlighted. items: [{day, v}] */
function barChart(items, opt) {
  const W = 720, H = opt.h || 300, L = 36, B = 26, T = 10;
  const max = Math.max(1e-9, ...items.map(x => x.v));
  const el = svg('svg', { viewBox: `0 0 ${W} ${H}`, width: '100%', role: 'img' });
  const grad = 'g' + Math.random().toString(36).slice(2, 7);
  const base = opt.color || 'var(--blue)';
  const defs = svg('defs'), g = svg('linearGradient', { id: grad, x1: 0, y1: 0, x2: 0, y2: 1 });
  g.append(svg('stop', { offset: '0', style: `stop-color:${base}` }), svg('stop', { offset: '1', style: `stop-color:${base};stop-opacity:.35` }));
  defs.append(g); el.append(defs);
  for (let i = 0; i <= 4; i++) {
    const y = T + (H - B - T) * i / 4;
    el.append(svg('line', { x1: L, x2: W, y1: y, y2: y, stroke: 'var(--line)' }));
    const t = svg('text', { x: L - 6, y: y + 4, 'text-anchor': 'end' }); t.textContent = opt.tick(max * (1 - i / 4)); el.append(t);
  }
  const bw = (W - L) / items.length;
  items.forEach((x, i) => {
    const bh = (H - B - T) * x.v / max, last = i === items.length - 1;
    const r = svg('rect', { x: L + i * bw + 3, y: H - B - bh, width: Math.max(2, bw - 6), height: Math.max(bh, x.v ? 3 : 0), rx: 5,
      fill: last ? 'var(--orange)' : `url(#${grad})` });
    if (last && opt.color) r.setAttribute('fill', 'var(--yellow)');
    const t = svg('title'); t.textContent = `${new Date(x.day).toLocaleDateString()}: ${opt.fmt(x.v)}`; r.append(t);
    el.append(r);
    if ((i % 5 === 0 && i <= items.length - 4) || last) {
      const lb = svg('text', { x: last ? W : L + i * bw + bw / 2, y: H - 8, 'text-anchor': last ? 'end' : 'middle' });
      lb.textContent = new Date(x.day).toLocaleDateString([], { month: 'short', day: 'numeric' }); el.append(lb);
    }
  });
  return el;
}

/** Weekday x hour grid of session starts. The server counts in UTC 15-minute slots; shift to the viewer's timezone. */
function heatmap(slots) {
  const shift = Math.round(-new Date().getTimezoneOffset() / 15);          // 15-minute slots east of UTC
  const n = slots.length;                                                    // 672, slot 0 = Thursday 00:00 UTC
  const grid = Array.from({ length: 7 }, () => new Array(24).fill(0));       // [weekday Mon..Sun][hour]
  const names = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  slots.forEach((c, i) => {
    const s = (((i + shift) % n) + n) % n;
    const dayFromThu = Math.floor(s / 96), hour = Math.floor((s % 96) / 4);
    grid[(dayFromThu + 3) % 7][hour] += c;                                   // Thu -> index 3 in Mon-first order
  });
  const max = Math.max(1, ...grid.flat());
  const W = 720, L = 40, T = 4, cw = (W - L) / 24, ch = 42, H = T + 7 * ch + 24;
  const el = svg('svg', { viewBox: `0 0 ${W} ${H}`, width: '100%', role: 'img', 'aria-label': 'Sessions started by weekday and hour' });
  const text = (x, y, anchor, str) => { const t = svg('text', { x, y, 'text-anchor': anchor }); t.textContent = str; el.append(t); };
  grid.forEach((row, d) => {
    text(L - 8, T + d * ch + ch / 2 + 4, 'end', names[d]);
    row.forEach((c, hr) => {
      const r = svg('rect', { x: L + hr * cw + 1.5, y: T + d * ch + 1.5, width: cw - 3, height: ch - 3, rx: 6, tabindex: c ? 0 : -1,
        style: `fill:var(--blue);fill-opacity:${c ? (0.14 + 0.86 * c / max).toFixed(3) : 0.07}` });
      const on = () => showTip(r, [h('div', { class: 'tt' }, `${names[d]} ${String(hr).padStart(2, '0')}:00`),
        h('div', { class: 'tl' }, h('span', {}, 'Sessions started'), h('b', {}, c))]);
      r.addEventListener('mouseenter', on); r.addEventListener('focus', on);
      r.addEventListener('mouseleave', hideTip); r.addEventListener('blur', hideTip);
      el.append(r);
    });
  });
  for (let hr = 0; hr < 24; hr += 3) text(L + hr * cw + cw / 2, H - 6, 'middle', String(hr).padStart(2, '0'));
  return el;
}

function lengthChart(lengths) {
  const labels = ['< 5 min', '5–15 min', '15–30 min', '30–60 min', '1–2 h', '2–4 h', '4 h +'];
  const total = lengths.reduce((a, b) => a + b, 0), max = Math.max(1, ...lengths);
  if (!total) return h('div', { class: 'empty' }, 'No finished sessions yet.');
  return h('div', { class: 'hbars' }, lengths.map((c, i) => h('div', { class: 'hb' },
    h('span', { class: 'hl' }, labels[i]),
    h('div', { class: 'meter big' }, h('i', { style: `width:${c / max * 100}%` })),
    h('span', { class: 'hv' }, c.toLocaleString(), h('small', {}, ' ' + Math.round(c / total * 100) + '%')))));
}

// ---- players ----------------------------------------------------------------------------------
const pState = { search: '', sort: 'playtime', dir: 'desc', page: 0, size: 15 };
async function renderPlayers() {
  const holder = h('div', { class: 'card' });
  const search = h('input', { type: 'search', placeholder: 'Search players…', value: pState.search, 'aria-label': 'Search players' });
  let t;
  search.addEventListener('input', () => { clearTimeout(t); t = setTimeout(() => { pState.search = search.value; pState.page = 0; load(); }, 250); });
  const size = h('select', { 'aria-label': 'Rows per page', onchange: (e) => { pState.size = Number(e.target.value); pState.page = 0; load(); } },
    [10, 15, 25, 50].map(n => h('option', { value: n, selected: n === pState.size }, n + ' per page')));
  const onlineCard = h('div', { class: 'card' });
  main.append(onlineCard, h('div', { class: 'toolbar' }, search, h('div', { class: 'grow' }), size), holder);
  async function loadOnline() {
    const d = await api('/api/players/online');
    clear(onlineCard);
    onlineCard.append(h('h2', {}, 'Online now', h('span', { class: 'count' }, String(d.rows.length))),
      d.rows.length
        ? h('div', { class: 'scroll' }, h('table', {}, h('thead', {}, h('tr', {}, h('th', {}, 'Player'),
            h('th', { class: 'n' }, 'Playtime'), h('th', { class: 'n' }, 'Streak'), h('th', {}, 'Online since'))),
            h('tbody', {}, d.rows.map(p => h('tr', { class: 'row', onclick: () => openPlayer(p.uuid) },
              h('td', {}, h('div', { class: 'who' }, avatar(p.nickname), p.nickname)),
              h('td', { class: 'n' }, fmtTicks(p.playtime)),
              h('td', { class: 'n' }, h('span', { class: 'pill' }, p.relativeStreak)),
              h('td', { class: 'muted' }, fmtDate(p.since)))))))
        : h('div', { class: 'empty' }, 'Nobody is online right now.'));
  }
  loadOnline();

  const cols = [['nickname', 'Player'], ['playtime', 'Playtime', 1], ['afk', 'AFK', 1], ['artificial', 'Artificial', 1],
    ['streak', 'Streak', 1], ['lastSeen', 'Last seen'], ['firstJoin', 'First join']];
  async function load() {
    const q = new URLSearchParams({ search: pState.search, sort: pState.sort, dir: pState.dir, page: pState.page, size: pState.size });
    const d = await api('/api/players?' + q);
    clear(holder);
    const head = h('tr', {}, cols.map(([k, label, num]) => h('th', { class: 's' + (num ? ' n' : '') + (pState.sort === k ? ' act' : ''),
      onclick: () => { pState.dir = pState.sort === k && pState.dir === 'desc' ? 'asc' : 'desc'; pState.sort = k; pState.page = 0; load(); } },
      label + (pState.sort === k ? (pState.dir === 'desc' ? ' ↓' : ' ↑') : ''))));
    const body = d.rows.map(p => h('tr', { class: 'row', onclick: () => openPlayer(p.uuid) },
      h('td', {}, h('div', { class: 'who' }, avatar(p.nickname), p.nickname)),
      h('td', { class: 'n' }, fmtTicks(p.playtime)),
      h('td', { class: 'n' }, fmtTicks(p.afk)), h('td', { class: 'n' }, fmtTicks(p.artificial)),
      h('td', { class: 'n' }, h('span', { class: 'pill' }, p.relativeStreak)),
      h('td', { class: 'muted' }, fmtDate(p.lastSeen)), h('td', { class: 'muted' }, fmtDate(p.firstJoin))));
    holder.append(d.rows.length
      ? h('div', { class: 'scroll' }, h('table', {}, h('thead', {}, head), h('tbody', {}, body)))
      : h('div', { class: 'empty' }, 'No players match your search.'),
      pager(pState.page, Math.max(1, Math.ceil(d.total / d.size)), d.total, (n) => { pState.page = n; load(); }));
  }
  await load();
}

// ---- modal (shared by players and goals) ------------------------------------------------------
/** side: elements for the left column; tabs: { name: () => element }. */
function openModal({ label, side, tabs }) {
  const close = () => { scrim.remove(); modal.remove(); hideTip(); document.removeEventListener('keydown', esc); };
  const esc = (e) => { if (e.key === 'Escape') close(); };
  document.addEventListener('keydown', esc);
  const scrim = h('div', { class: 'scrim', onclick: close });
  const content = h('div');
  const tabBar = h('div', { class: 'tabs' });
  const show = (name) => {
    hideTip();
    tabBar.querySelectorAll('.tab').forEach(b => b.classList.toggle('on', b.dataset.t === name));
    clear(content); content.append(tabs[name]());
  };
  Object.keys(tabs).forEach(name => tabBar.append(h('button', { class: 'tab', 'data-t': name, onclick: () => show(name) }, name)));
  tabBar.append(h('button', { class: 'btn x', onclick: close, 'aria-label': 'Close' }, '✕'));
  const modal = h('div', { class: 'modal', role: 'dialog', 'aria-modal': 'true', 'aria-label': label },
    h('div', { class: 'side' }, side), h('div', { class: 'body' }, tabBar, content));
  document.body.append(scrim, modal);
  show(Object.keys(tabs)[0]);
  return close;
}
const kv = (k, v, c) => h('div', { class: 'kv', style: `--c:${c}` }, h('span', { class: 'k' }, k), h('span', { class: 'v' }, v));

// ---- tooltip ----------------------------------------------------------------------------------
let tip = null;
function hideTip() { if (tip) { tip.remove(); tip = null; } }
function showTip(anchor, lines) {
  hideTip();
  tip = h('div', { class: 'tip', role: 'tooltip' }, lines);
  document.body.append(tip);
  const r = anchor.getBoundingClientRect(), t = tip.getBoundingClientRect();
  let x = r.left + r.width / 2 - t.width / 2;
  x = Math.max(8, Math.min(x, window.innerWidth - t.width - 8));
  let y = r.top - t.height - 10;
  if (y < 8) y = r.bottom + 10;
  tip.style.left = x + 'px'; tip.style.top = y + 'px';
}

// ---- player modal -----------------------------------------------------------------------------
async function openPlayer(uuid) {
  const wait = h('div', { class: 'scrim' }, loading());
  document.body.append(wait);
  let d;
  try { d = await api('/api/player/' + encodeURIComponent(uuid)); }
  finally { wait.remove(); }
  const pendingRewards = d.pendingRewards.filter(r => !r.expired);
  openModal({
    label: d.nickname,
    side: [avatar(d.nickname, true), h('div', {}, h('h1', {}, d.nickname), h('div', { class: 'uuid' }, d.uuid)),
      h('div', { class: 'kvs' },
        kv('Playtime', fmtTicks(d.playtime), 'var(--blue)'), kv('AFK', fmtTicks(d.afk), 'var(--orange)'),
        kv('Artificial', fmtTicks(d.artificial), 'var(--yellow)'), kv('Current streak', String(d.relativeStreak), 'var(--ice)'),
        kv('First join', fmtDate(d.firstJoin), 'var(--ice)'), kv('Last seen', fmtDate(d.lastSeen), 'var(--ice)'))],
    tabs: {
      Journal: () => journal(d),
      Trend: () => trend(d.uuid),
      [`Goals (${d.goals.length})`]: () => paged(d.goals, 8, g => h('div', { class: 'li' },
        h('span', {}, g.name + ' ', h('span', { class: 'pill ' + (g.received ? 'ok' : 'warn') }, g.received ? 'received' : 'unclaimed')),
        h('span', { class: 'muted' }, fmtText(g.completedAt))), 'No goals completed yet.'),
      [`Rewards (${d.receivedRewards.length + pendingRewards.length})`]: () => paged(
        [...pendingRewards.map(r => ({ ...r, st: 'warn' })), ...d.receivedRewards.map(r => ({ ...r, st: 'ok' }))], 8,
        r => h('div', { class: 'li' },
          h('span', {}, `#${r.rewardId} · ${r.joins} joins `, h('span', { class: 'pill ' + r.st }, r.st === 'ok' ? 'received' : 'to claim')),
          h('span', { class: 'muted' }, fmtText(r.at))), 'No join streak rewards yet.'),
    },
  });
}

function journal(d) {
  if (!d.sessions.length) return h('div', { class: 'empty' }, 'No sessions recorded.');
  const group = (arr) => { const m = new Map(); for (const x of arr) (m.get(x.session) || m.set(x.session, []).get(x.session)).push(x); return m; };
  const afk = group(d.afkIntervals), van = group(d.vanishIntervals);
  const now = Date.now();
  const clip = (arr, from, to) => (arr || []).reduce((a, x) => a + Math.max(0, Math.min(x.end, to) - Math.max(x.start, from)), 0);

  // everything registered for each session, once
  const sessions = d.sessions.map(s => {
    const open = s.logout <= s.login, end = open ? now : s.logout, len = Math.max(1, end - s.login);
    const inside = (t) => { const v = parseTs(t); return !isNaN(v) && v >= s.login && v <= end; };
    return { ...s, open, end, len,
      afkMs: clip(afk.get(s.login), s.login, end), afkN: (afk.get(s.login) || []).length,
      vanMs: clip(van.get(s.login), s.login, end), vanN: (van.get(s.login) || []).length,
      goals: d.goals.filter(g => inside(g.completedAt)).map(g => g.name),
      rewards: d.receivedRewards.filter(r => inside(r.at)).map(r => `#${r.rewardId} · ${r.joins} joins`) };
  }).sort((x, y) => x.login - y.login);

  const total = sessions.reduce((a, s) => a + (s.open ? 0 : s.len), 0), afkTotal = sessions.reduce((a, s) => a + s.afkMs, 0);
  const sum = (k, v) => h('div', { class: 'sum' }, h('div', { class: 'k' }, k), h('div', { class: 'v' }, v));

  // a session is selected from its row or from its dot; both stay in sync
  let selected = null, chart, list;
  const syncRows = () => list.querySelectorAll('.sess').forEach(r => r.classList.toggle('sel', Number(r.dataset.login) === selected));
  const select = (login, fromChart) => {
    selected = selected === login ? null : login;
    chart.select(selected);
    if (selected != null && fromChart) list.reveal(x => x.login === selected);   // bring its row into view
    syncRows();
  };

  const render = (s) => {
    const bar = h('div', { class: 'bar' });
    for (const [cls, m] of [['afk', afk], ['vanish', van]]) {
      for (const x of m.get(s.login) || []) {
        const a = Math.max(0, (x.start - s.login) / s.len * 100), w = Math.max(0.8, (Math.min(x.end, s.end) - x.start) / s.len * 100);
        bar.append(h('i', { class: cls, style: `left:${a}%;width:${Math.min(w, 100 - a)}%` }));
      }
    }
    return h('div', { class: 'sess' + (s.login === selected ? ' sel' : ''), 'data-login': s.login, tabindex: 0,
      onclick: () => select(s.login, false),
      onkeydown: (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); select(s.login, false); } } },
      h('span', { class: 'muted' }, fmtDate(s.login)), bar,
      h('span', { style: 'text-align:right;font-weight:650' }, s.open ? 'online' : fmtSecs(Math.round(s.len / 1000))));
  };

  chart = sessionChart(sessions, (login) => select(login, true));
  list = paged([...sessions].reverse(), 6, render, 'No sessions recorded.', rows => h('div', {}, rows));
  return h('div', {},
    h('div', { class: 'sums' }, sum('Sessions shown', sessions.length), sum('Time online', fmtSecs(Math.round(total / 1000))),
      sum('AFK in them', total ? Math.round(afkTotal / total * 100) + '%' : '0%')),
    h('div', { class: 'chartbox' }, chart),
    h('div', { class: 'legend' },
      h('span', {}, h('b', { style: 'background:var(--blue)' }), 'Mostly active'),
      h('span', {}, h('b', { style: 'background:var(--orange)' }), '25%+ AFK'),
      h('span', {}, h('b', { class: 'ring' }), 'Vanish used'),
      h('span', { class: 'muted' }, 'One dot per session · hover for details · click to select')),
    list);
}

/** Daily playtime / AFK lines. Default 30 days, extendable up to the first recorded session. */
function trend(uuid) {
  const box = h('div'), ranges = [['30 days', 30], ['90 days', 90], ['1 year', 365], ['All', 0]];
  let days = 30;
  const tz = Math.round(-new Date().getTimezoneOffset());
  async function load() {
    box.querySelectorAll('.chartbox').forEach(c => c.classList.add('stale'));   // keep the old chart dimmed while loading
    const wait = loading();
    box.append(wait);
    let d;
    try { d = await api(`/api/player/${encodeURIComponent(uuid)}/daily?days=${days}&tz=${tz}`); }
    finally { wait.remove(); }
    clear(box);
    if (d.firstSession == null) { box.append(h('div', { class: 'empty' }, 'No sessions recorded.')); return; }
    const bar = h('div', { class: 'tabs', style: 'margin-bottom:8px' }, ranges
      .filter(([, n]) => n === 0 || n === 30 || n < d.totalDays)
      .map(([label, n]) => h('button', { class: 'tab' + (n === days ? ' on' : ''), onclick: () => { days = n; load(); } }, label)));
    const tot = d.days.reduce((a, x) => a + x.playtime, 0), afk = d.days.reduce((a, x) => a + x.afk, 0);
    const sum = (k, v) => h('div', { class: 'sum' }, h('div', { class: 'k' }, k), h('div', { class: 'v' }, v));
    box.append(bar,
      h('div', { class: 'sums' }, sum('Playtime', fmtSecs(Math.round(tot / 1000))), sum('AFK', fmtSecs(Math.round(afk / 1000))),
        sum('Daily average', fmtSecs(Math.round(tot / d.days.length / 1000)))),
      h('div', { class: 'chartbox' }, lineChart(d.days)),
      h('div', { class: 'legend' },
        h('span', {}, h('b', { style: 'background:var(--blue)' }), 'Playtime'),
        h('span', {}, h('b', { style: 'background:var(--orange)' }), 'AFK'),
        h('span', { class: 'muted' }, `Recording since ${fmtDate(d.firstSession)} · hover for details`)));
  }
  load();
  return box;
}

function lineChart(items) {
  const W = 720, H = 260, L = 48, R = 14, T = 14, B = 28;
  const max = Math.max(1, ...items.map(x => x.playtime));
  const el = svg('svg', { viewBox: `0 0 ${W} ${H}`, width: '100%', role: 'img', 'aria-label': 'Playtime and AFK per day' });
  const text = (x, y, anchor, str) => { const t = svg('text', { x, y, 'text-anchor': anchor }); t.textContent = str; el.append(t); };
  const n = items.length, step = n > 1 ? (W - L - R) / (n - 1) : 0;
  const X = (i) => n > 1 ? L + step * i : (L + W - R) / 2;
  const Y = (v) => T + (H - T - B) * (1 - v / max);
  for (let i = 0; i <= 4; i++) {
    const y = T + (H - T - B) * i / 4;
    el.append(svg('line', { x1: L, x2: W - R, y1: y, y2: y, stroke: 'var(--line)' }));
    text(L - 8, y + 4, 'end', fmtSecs(Math.round(max * (1 - i / 4) / 1000)));
  }
  const ticks = Math.min(n, 5);
  for (let k = 0; k < ticks; k++) {
    const i = ticks === 1 ? 0 : Math.round((n - 1) * k / (ticks - 1));
    text(X(i), H - 8, k === 0 && n > 1 ? 'start' : k === ticks - 1 && n > 1 ? 'end' : 'middle',
      new Date(items[i].day).toLocaleDateString([], { month: 'short', day: 'numeric' }));
  }
  const path = (key) => items.map((x, i) => `${i ? 'L' : 'M'}${X(i).toFixed(1)} ${Y(x[key]).toFixed(1)}`).join('');
  for (const [key, color] of [['playtime', 'var(--blue)'], ['afk', 'var(--orange)']]) {
    if (n > 1) el.append(svg('path', { d: path(key), fill: 'none', style: `stroke:${color};stroke-width:2.5;stroke-linejoin:round` }));
    if (n <= 60) items.forEach((x, i) => el.append(svg('circle', { cx: X(i), cy: Y(x[key]), r: 3.5, style: `fill:${color};pointer-events:none` })));
  }
  const line = (k, v, c) => h('div', { class: 'tl' }, h('span', {}, k), h('b', { style: `color:${c}` }, v));
  items.forEach((x, i) => {
    const w = Math.max(step, 6);
    const r = svg('rect', { x: X(i) - w / 2, y: T, width: w, height: H - T - B, fill: 'transparent', tabindex: 0,
      'aria-label': `${new Date(x.day).toLocaleDateString()}: ${fmtSecs(Math.round(x.playtime / 1000))}` });
    const on = () => showTip(r, [h('div', { class: 'tt' }, new Date(x.day).toLocaleDateString([], { dateStyle: 'medium' })),
      line('Playtime', fmtSecs(Math.round(x.playtime / 1000)), 'var(--blue)'), line('AFK', fmtSecs(Math.round(x.afk / 1000)), 'var(--orange)')]);
    r.addEventListener('mouseenter', on); r.addEventListener('focus', on);
    r.addEventListener('mouseleave', hideTip); r.addEventListener('blur', hideTip);
    el.append(r);
  });
  return el;
}

/** Scatter: x = when the session started, y = how long it lasted, colour = share of AFK time. */
function sessionChart(sessions, onPick) {
  const W = 720, H = 250, L = 48, R = 14, T = 14, B = 28;
  const t0 = sessions[0].login, t1 = Math.max(...sessions.map(s => s.end));
  const span = Math.max(1, t1 - t0), maxLen = Math.max(...sessions.map(s => s.len));
  const el = svg('svg', { viewBox: `0 0 ${W} ${H}`, width: '100%', role: 'img', 'aria-label': 'Sessions over time' });
  const text = (x, y, anchor, str) => { const t = svg('text', { x, y, 'text-anchor': anchor }); t.textContent = str; el.append(t); };

  for (let i = 0; i <= 4; i++) {
    const y = T + (H - T - B) * i / 4;
    el.append(svg('line', { x1: L, x2: W - R, y1: y, y2: y, stroke: 'var(--line)' }));
    text(L - 8, y + 4, 'end', fmtSecs(Math.round(maxLen * (1 - i / 4) / 1000)));
  }
  for (let i = 0; i <= 3; i++) {
    text(L + (W - L - R) * i / 3, H - 8, i === 0 ? 'start' : i === 3 ? 'end' : 'middle',
      new Date(t0 + span * i / 3).toLocaleDateString([], { month: 'short', day: 'numeric' }));
  }
  const r = sessions.length > 120 ? 4 : sessions.length > 40 ? 5 : 6.5;
  const pad = Math.min(8, (W - L - R) / 4);
  for (const s of sessions) {
    const share = Math.min(1, s.afkMs / s.len);
    const c = svg('circle', {
      'data-login': s.login, cx: L + pad + (W - L - R - 2 * pad) * (s.login - t0) / span,
      cy: T + (H - T - B) * (1 - s.len / maxLen), r, tabindex: 0, class: 'dot',
      'aria-label': `${fmtDate(s.login)}, ${s.open ? 'online' : fmtSecs(Math.round(s.len / 1000))}`,
      style: `fill:${share >= 0.25 ? 'var(--orange)' : 'var(--blue)'};` +
        (s.vanN ? 'stroke:var(--yellow);stroke-width:3' : 'stroke:var(--surface-solid);stroke-width:1.5') });
    const line = (k, v) => h('div', { class: 'tl' }, h('span', {}, k), h('b', {}, v));
    const lines = () => [
      h('div', { class: 'tt' }, fmtDate(s.login)),
      line('Left', s.open ? 'still online' : fmtDate(s.logout)),
      line('Duration', s.open ? 'ongoing' : fmtSecs(Math.round(s.len / 1000))),
      line('AFK', s.afkN ? `${fmtSecs(Math.round(s.afkMs / 1000))} · ${s.afkN}×` : 'none'),
      line('Vanish', s.vanN ? `${fmtSecs(Math.round(s.vanMs / 1000))} · ${s.vanN}×` : 'none'),
      s.goals.length > 0 && line('Goals', s.goals.join(', ')),
      s.rewards.length > 0 && line('Rewards', s.rewards.join(', '))];
    const on = () => showTip(c, lines());
    c.addEventListener('mouseenter', on); c.addEventListener('focus', on);
    c.addEventListener('mouseleave', hideTip); c.addEventListener('blur', hideTip);
    c.addEventListener('click', () => onPick && onPick(s.login));
    c.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onPick && onPick(s.login); } });
    el.append(c);
  }
  // highlight the selected session's dot and draw it on top of the others
  el.select = (login) => el.querySelectorAll('.dot').forEach(c => {
    const on = Number(c.dataset.login) === login;
    c.classList.toggle('sel', on);
    if (on) el.append(c);
  });
  return el;
}

// ---- goals ------------------------------------------------------------------------------------
const table = (head, rowFn, items, empty) => paged(items, 10, rowFn, empty, rows => h('div', { class: 'scroll' },
  h('table', {}, h('thead', {}, h('tr', {}, head.map(x => h('th', x[1] ? { class: 'n' } : {}, x[0])))), h('tbody', {}, rows))));

async function renderGoals() {
  const d = await api('/api/goals');
  const done = d.goals.reduce((a, g) => a + g.completed, 0);
  const top = d.goals.reduce((b, g) => (!b || g.completed > b.completed) ? g : b, null);
  main.append(
    h('div', { class: 'cols' },
      tile('Goals', d.goals.length, 'var(--blue)', 'c4'),
      tile('Total completions', done.toLocaleString(), 'var(--orange)', 'c4'),
      tile('Most completed', top && top.completed ? top.name : '—', 'var(--yellow)', 'c4')),
    h('div', { class: 'card' }, h('h2', {}, 'Goals', h('span', { class: 'count' }, 'click one for details')),
      table([['Goal'], ['Status'], ['Requires'], ['Players', 1], ['Completed', 1]], g => h('tr', { class: 'row', onclick: () => openGoal(g.name) },
        h('td', {}, g.name),
        h('td', {}, g.configured ? h('span', { class: 'pill ' + (g.active ? 'ok' : 'warn') }, g.active ? 'active' : 'inactive')
          : h('span', { class: 'pill warn' }, 'removed')),
        h('td', { class: 'muted' }, g.configured ? fmtReq(g.time) : '—'),
        h('td', { class: 'n' }, g.players), h('td', { class: 'n' }, g.completed)), d.goals, 'No goals configured.')));
}

async function openGoal(name) {
  const g = await api('/api/goal/' + encodeURIComponent(name));
  const yn = (b) => b ? 'Yes' : 'No';
  const chips = (arr, empty) => arr && arr.length
    ? h('div', { class: 'chipsrow' }, arr.map(x => h('code', {}, x))) : h('span', { class: 'muted' }, empty);
  const field = (k, v) => h('div', { class: 'field' }, h('div', { class: 'fk' }, k), h('div', { class: 'fv' }, v));

  const details = () => !g.configured
    ? h('div', { class: 'empty' }, 'This goal no longer exists in the configuration. Only its history is kept.')
    : h('div', { class: 'fields' },
      field('Requirements', h('div', {}, field('Playtime needed', fmtReq(g.time)),
        field('Permissions', chips(g.requiredPermissions, 'none')),
        field('Placeholder conditions', chips(g.placeholderConditions, 'none')))),
      field('Rewards', h('div', {}, field('Commands run', chips(g.rewardCommands, 'none')),
        field('Permissions granted', chips(g.rewardPermissions, 'none')),
        field('Given while offline', yn(g.offlineRewards)))),
      field('Announcement', h('div', {}, field('Message', g.message || h('span', { class: 'muted' }, 'none')),
        field('Sound', g.sound || h('span', { class: 'muted' }, 'none')))),
      field('Checking', h('div', {}, field('Every', g.checkText || g.checkEvery || '—'),
        field('Mode', g.perPlayerCheck ? 'Own timer for each player' : 'Everyone at the same time'),
        field('Next check', g.nextCheck ? fmtDate(g.nextCheck) : '—'))));

  openModal({
    label: g.name,
    side: [h('div', { class: 'avatar lg', style: '--av:var(--yellow)' }, '★'),
      h('div', {}, h('h1', {}, g.name), g.configured
        ? h('span', { class: 'pill ' + (g.active ? 'ok' : 'warn') }, g.active ? 'active' : 'inactive') : h('span', { class: 'pill warn' }, 'removed')),
      h('div', { class: 'kvs' },
        kv('Completions', g.completed.toLocaleString(), 'var(--blue)'), kv('Players', g.players.toLocaleString(), 'var(--orange)'),
        g.configured && kv('Playtime needed', fmtReq(g.time), 'var(--yellow)'),
        g.configured && kv('Repeatable', yn(g.repeatable), 'var(--ice)'))],
    tabs: {
      Details: details,
      [`Completed by (${g.completions.length})`]: () => paged(g.completions, 8, c => h('div', { class: 'li link', tabindex: 0,
        onclick: () => { document.querySelectorAll('.scrim,.modal').forEach(n => n.remove()); hideTip(); openPlayer(c.uuid); } },
        h('span', { class: 'who' }, avatar(c.nickname), c.nickname), h('span', { class: 'muted' }, fmtText(c.at))), 'Nobody has completed this goal yet.'),
    },
  });
}

// ---- rewards ----------------------------------------------------------------------------------
async function renderRewards() {
  const d = await api('/api/rewards');
  const received = d.rewards.reduce((a, r) => a + r.received, 0);
  main.append(
    h('div', { class: 'cols' },
      tile('Join streak rewards', d.rewards.length, 'var(--blue)', 'c4'),
      tile('Rewards received', received.toLocaleString(), 'var(--orange)', 'c4'),
      tile('Waiting to be claimed', d.pending.length.toLocaleString(), 'var(--yellow)', 'c4')),
    h('div', { class: 'cols' },
      h('div', { class: 'card c6' }, h('h2', {}, 'Join streak rewards', h('span', { class: 'count' }, 'click one for details')),
        table([['Reward'], ['Joins'], ['Received', 1], ['Waiting', 1]], r => h('tr', { class: 'row', onclick: () => openReward(r.id) },
          h('td', {}, '#' + r.id, !r.configured && h('span', { class: 'pill warn', style: 'margin-left:8px' }, 'removed')),
          h('td', { class: 'muted' }, r.joins), h('td', { class: 'n' }, r.received), h('td', { class: 'n' }, r.pending)),
          d.rewards, 'No rewards configured.')),
      h('div', { class: 'card c6' }, h('h2', {}, 'Waiting to be claimed'),
        table([['Player'], ['Reward'], ['Since']], r => h('tr', { class: 'row', onclick: () => openReward(r.rewardId) },
          h('td', {}, h('div', { class: 'who' }, avatar(r.nickname), r.nickname)),
          h('td', {}, `#${r.rewardId} · ${r.joins} joins`), h('td', { class: 'muted' }, fmtText(r.at))), d.pending, 'Nothing pending.'))));
}

async function openReward(id) {
  const r = await api('/api/reward/' + id);
  const yn = (b) => b ? 'Yes' : 'No';
  const chips = (arr, empty) => arr && arr.length
    ? h('div', { class: 'chipsrow' }, arr.map(x => h('code', {}, x))) : h('span', { class: 'muted' }, empty);
  const field = (k, v) => h('div', { class: 'field' }, h('div', { class: 'fk' }, k), h('div', { class: 'fv' }, v));
  const text = (v) => v || h('span', { class: 'muted' }, 'none');
  const close = () => { document.querySelectorAll('.scrim,.modal').forEach(n => n.remove()); hideTip(); };
  const people = (arr, empty, label) => paged(arr, 8, c => h('div', { class: 'li link', tabindex: 0,
    onclick: () => { close(); openPlayer(c.uuid); } },
    h('span', { class: 'who' }, avatar(c.nickname), c.nickname, h('span', { class: 'muted' }, ` · ${c.joins} joins`)),
    h('span', { class: 'muted' }, fmtText(c.at))), empty);

  const details = () => !r.configured
    ? h('div', { class: 'empty' }, 'This reward no longer exists in the configuration. Only its history is kept.')
    : h('div', { class: 'fields' },
      field('Requirements', h('div', {}, field('Joins needed', r.joins), field('Repeatable', yn(r.repeatable)))),
      field('Rewards', h('div', {}, field('Commands run', chips(r.commands, 'none')),
        field('Permissions granted', chips(r.permissions, 'none')))),
      field('Announcement', h('div', {}, field('Message', text(r.message)), field('Sound', text(r.sound)))),
      field('Display', h('div', {}, field('Description', text(r.description)),
        field('Reward description', text(r.rewardDescription)), field('Icon', text(r.icon)))));

  openModal({
    label: 'Reward #' + r.id,
    side: [h('div', { class: 'avatar lg', style: '--av:var(--orange)' }, '#' + r.id),
      h('div', {}, h('h1', {}, 'Reward #' + r.id),
        r.configured ? h('span', { class: 'pill ok' }, 'configured') : h('span', { class: 'pill warn' }, 'removed')),
      h('div', { class: 'kvs' },
        kv('Received', r.received.toLocaleString(), 'var(--blue)'), kv('Players', r.players.toLocaleString(), 'var(--orange)'),
        kv('Waiting', r.pending.toLocaleString(), 'var(--yellow)'),
        r.configured && kv('Joins needed', r.joins, 'var(--ice)'))],
    tabs: {
      Details: details,
      [`Received by (${r.receivers.length})`]: () => people(r.receivers, 'Nobody has received this reward yet.'),
      [`Waiting (${r.waiting.length})`]: () => people(r.waiting, 'Nobody is waiting to claim this reward.'),
    },
  });
}

boot();
})();
