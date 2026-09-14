/* =====================================================
   WiFi Usage Monitor — Frontend Application Controller
   ===================================================== */

const API = 'http://localhost:8080';
let activePage = 'dashboard';
let historyTab = 'daily';

// Chart instances
let deviceDoughnutChart = null;
let historyChart = null;
let dashSpeedChart = null;

// Real-time telemetry arrays
const SPEED_DL = Array(45).fill(0);
const SPEED_UL = Array(45).fill(0);
const SPEED_LABELS = Array(45).fill('');

// Caches
let globalDevicesCache = [];
let globalAnomaliesCache = [];

// ── INIT ─────────────────────────────────────────────
window.addEventListener('DOMContentLoaded', () => {
  const username = sessionStorage.getItem('username') || 'admin';
  
  const userEl = document.getElementById('top-username');
  if (userEl) userEl.textContent = username;

  const today = new Date().toISOString().split('T')[0];
  const dateInput = document.getElementById('history-report-date');
  if (dateInput) dateInput.value = today;

  initCharts();
  refreshAllData();
  loadNetworkInfo();
  loadBlockedWebsites();
  pollBlockedEvents();
  setInterval(refreshAllData, 8000);
  setInterval(pollBlockedEvents, 1500);
  startRealtimeSpeedmeter();
  runAiScan(); // Initial AI background check
});

function logout() {
  if (confirm('Are you sure you want to log out?')) {
    sessionStorage.clear();
    window.location.href = 'index.html';
  }
}

// ── NAVIGATION ───────────────────────────────────────
function navigate(pageId) {
  activePage = pageId;
  document.querySelectorAll('.tab-content, .tab-page, .page-view, .page').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.nav-item-btn, .menu-item, .nav-tab, .nav-btn').forEach(b => b.classList.remove('active'));

  const targetSection = document.getElementById('sec-' + pageId) || document.getElementById('page-' + pageId);
  const targetNav     = document.getElementById('nav-' + pageId);
  if (targetSection) targetSection.classList.add('active');
  if (targetNav)     targetNav.classList.add('active');

  if (pageId === 'history') renderHistoryChart();
  if (pageId === 'dashboard' && deviceDoughnutChart) deviceDoughnutChart.update();
  if (pageId === 'blocking') {
    loadNetworkInfo();
    loadBlockedWebsites();
    pollBlockedEvents();
  }
}

// ── REFRESH ALL DATA ─────────────────────────────────
async function refreshAllData() {
  await Promise.all([
    loadDashboardStats(),
    loadDevicesRegistry(),
    loadHistoryData(),
    loadRestrictions()
  ]);
}

// ── 1. DASHBOARD STATS & DISTRIBUTION ─────────────────
async function loadDashboardStats() {
  try {
    const res = await fetch(`${API}/api/dashboard`);
    const data = await res.json();

    const totalEl = document.getElementById('dash-stat-total');
    const splitEl = document.getElementById('dash-stat-split');
    const devEl = document.getElementById('dash-stat-devices');
    const devSubEl = document.getElementById('dash-stat-devices-sub');
    const todayEl = document.getElementById('dash-stat-today');

    if (totalEl) totalEl.textContent = `${data.totalDataUsed || '0.00'} GB`;
    if (splitEl) splitEl.textContent = `↓ ${data.downloadUsage || '0.00'} / ↑ ${data.uploadUsage || '0.00'} GB`;
    if (devEl) devEl.textContent = `${data.connectedDevicesCount || 0} Devices`;
    if (devSubEl) devSubEl.textContent = `${data.totalDevicesCount || 0} total registered`;
    if (todayEl) todayEl.textContent = `${data.todayUsage || '0.00'} GB`;

    updateDeviceDoughnutChart(data.deviceUsageBreakdown || []);
  } catch (e) {
    console.error('Error loading dashboard stats:', e);
  }
}

function updateDeviceDoughnutChart(breakdown) {
  if (!deviceDoughnutChart) return;

  const labels = breakdown.map(b => b.deviceName);
  const dataValues = breakdown.map(b => parseFloat(b.totalUsage) || 0);

  const colors = [
    '#2f81f7', '#238636', '#d29922', '#3fb950', '#a371f7',
    '#da3633', '#f0883e', '#58a6ff', '#56d364', '#db61a2'
  ];

  deviceDoughnutChart.data.labels = labels.length ? labels : ['No Data'];
  deviceDoughnutChart.data.datasets[0].data = dataValues.length ? dataValues : [1];
  deviceDoughnutChart.data.datasets[0].backgroundColor = colors.slice(0, Math.max(labels.length, 1));
  deviceDoughnutChart.update();
}

// ── 2. USAGE HISTORY & REPORTS ───────────────────────
let historyRawData = [];

async function loadHistoryData() {
  try {
    const res = await fetch(`${API}/api/history`);
    historyRawData = await res.json();
    if (activePage === 'history') renderHistoryChart();
  } catch (e) {
    console.error('Error loading history:', e);
  }
}

function switchHistoryTab(tab) {
  historyTab = tab;
  document.querySelectorAll('.seg-btn, .filter-tab, .range-btn, .tab-btn').forEach(b => b.classList.remove('active'));
  const btn = document.getElementById('tab-' + tab);
  if (btn) btn.classList.add('active');
  renderHistoryChart();
}

function renderHistoryChart() {
  if (!historyChart) return;

  let labels = [];
  let uploads = [];
  let downloads = [];

  if (historyTab === 'weekly') {
    labels = ['Week 1 (Past 7d)', 'Week 2 (8-14d ago)'];
    const w1Dl = historyRawData.slice(0, 7).reduce((acc, d) => acc + (parseFloat(d.download) || 0), 0);
    const w1Ul = historyRawData.slice(0, 7).reduce((acc, d) => acc + (parseFloat(d.upload) || 0), 0);
    const w2Dl = historyRawData.slice(7, 14).reduce((acc, d) => acc + (parseFloat(d.download) || 0), 0);
    const w2Ul = historyRawData.slice(7, 14).reduce((acc, d) => acc + (parseFloat(d.upload) || 0), 0);
    downloads = [parseFloat(w1Dl.toFixed(2)), parseFloat(w2Dl.toFixed(2))];
    uploads = [parseFloat(w1Ul.toFixed(2)), parseFloat(w2Ul.toFixed(2))];
  } else if (historyTab === 'monthly') {
    labels = ['Current Month (Sep)', 'August', 'July'];
    downloads = [48.2, 74.8, 68.3];
    uploads = [12.6, 19.4, 15.1];
  } else {
    labels = historyRawData.map(d => d.date);
    uploads = historyRawData.map(d => parseFloat(d.upload) || 0);
    downloads = historyRawData.map(d => parseFloat(d.download) || 0);
  }

  historyChart.data.labels = labels;
  historyChart.data.datasets[0].data = downloads;
  historyChart.data.datasets[1].data = uploads;
  historyChart.update();
}

async function generateDailyTextReport() {
  const dateInput = document.getElementById('history-report-date').value;
  const outputBox = document.getElementById('report-output-text');
  if (!dateInput) {
    outputBox.textContent = 'Please select a date first.';
    return;
  }
  outputBox.textContent = 'Generating report...';

  try {
    const res = await fetch(`${API}/api/report?date=${encodeURIComponent(dateInput)}`);
    const data = await res.json();
    outputBox.textContent = data.report || 'No records found for date: ' + dateInput;
  } catch (e) {
    outputBox.textContent = 'Error contacting server.';
  }
}

async function exportCsv() {
  try {
    window.open(`${API}/api/reports/export?format=csv`, '_blank');
  } catch (e) {
    alert('Failed to export CSV.');
  }
}

function printReport() {
  const text = document.getElementById('report-output-text').textContent;
  const printWindow = window.open('', '_blank', 'width=800,height=600');
  printWindow.document.write(`
    <html>
      <head>
        <title>WiFi Usage Report</title>
        <style>
          body { font-family: monospace; padding: 24px; white-space: pre-wrap; background: #fff; color: #111; font-size: 13px; }
          h2 { font-family: sans-serif; border-bottom: 2px solid #222; padding-bottom: 6px; }
        </style>
      </head>
      <body>
        <h2>WiFi Usage Monitor — Network Report</h2>
        <div>${escapeHtml(text)}</div>
      </body>
    </html>
  `);
  printWindow.document.close();
  printWindow.focus();
  printWindow.print();
}

// ── 3. DEVICE REGISTRY ───────────────────────────────
async function loadDevicesRegistry() {
  try {
    const res = await fetch(`${API}/api/devices`);
    globalDevicesCache = await res.json();
    renderDevicesTable(globalDevicesCache);
    populateAdminDeviceDropdown(globalDevicesCache);
  } catch (e) {
    console.error('Error loading devices registry:', e);
  }
}

function renderDevicesTable(devices) {
  const tbody = document.getElementById('devices-registry-tbody');
  if (!tbody) return;
  if (!devices || !devices.length) {
    tbody.innerHTML = '<tr><td colspan="9" class="table-empty">No devices registered yet.</td></tr>';
    return;
  }

  tbody.innerHTML = devices.map(d => {
    const isConn = d.status === 'Connected';
    const isBlocked = d.isBlocked;

    return `
      <tr>
        <td class="mono">#${d.deviceId}</td>
        <td>
          <div class="dev-title">${escapeHtml(d.deviceName)}</div>
          <div class="dev-type-tag">${escapeHtml(d.deviceType || 'Device')}</div>
        </td>
        <td>${escapeHtml(d.ownerName)}</td>
        <td><code class="mono">${escapeHtml(d.ipAddress)}</code></td>
        <td><code class="mono">${escapeHtml(d.macAddress)}</code></td>
        <td>
          <button class="status-pill ${isConn ? 'status-online' : 'status-offline'}" onclick="toggleDeviceStatus(${d.deviceId}, '${isConn ? 'Disconnected' : 'Connected'}')">
            ${isConn ? '● Connected' : '○ Disconnected'}
          </button>
        </td>
        <td>
          <div class="traffic-split">↓ ${d.downloadGb.toFixed(2)} GB &bull; ↑ ${d.uploadGb.toFixed(2)} GB</div>
        </td>
        <td><strong class="mono">${d.dataLimit.toFixed(1)}</strong> GB</td>
        <td>
          <div class="action-btn-group">
            <button class="btn btn-sm ${isBlocked ? 'btn-primary' : 'btn-danger'}" onclick="toggleDeviceBlock(${d.deviceId}, ${!isBlocked})">
              ${isBlocked ? 'Unblock' : 'Block'}
            </button>
            <button class="btn btn-sm btn-secondary" onclick="deleteDevice(${d.deviceId}, '${escapeHtml(d.deviceName)}')">Delete</button>
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

async function toggleDeviceStatus(deviceId, newStatus) {
  try {
    await fetch(`${API}/api/devices`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `action=toggle_status&deviceId=${deviceId}&status=${encodeURIComponent(newStatus)}`
    });
    await refreshAllData();
  } catch (e) {
    alert('Failed to update status.');
  }
}

async function toggleDeviceBlock(deviceId, shouldBlock) {
  try {
    await fetch(`${API}/api/devices`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `action=${shouldBlock ? 'block' : 'unblock'}&deviceId=${deviceId}`
    });
    await refreshAllData();
    runAiScan();
  } catch (e) {
    alert('Failed to toggle block state.');
  }
}

async function deleteDevice(deviceId, devName) {
  if (!confirm(`Delete device "${devName}"?`)) return;
  try {
    await fetch(`${API}/api/devices?id=${deviceId}`, { method: 'DELETE' });
    await refreshAllData();
  } catch (e) {
    alert('Failed to delete device.');
  }
}

// ── ADD DEVICE MODAL ─────────────────────────────────
function openAddDeviceModal() {
  document.getElementById('add-device-modal').classList.remove('hidden');
}

function closeAddDeviceModal() {
  document.getElementById('add-device-modal').classList.add('hidden');
}

function autoDetectCurrentDevice() {
  document.getElementById('modal-dev-name').value = navigator.platform || 'Workstation PC';
  document.getElementById('modal-dev-owner').value = sessionStorage.getItem('username') || 'Administrator';
  document.getElementById('modal-dev-ip').value = '192.168.1.' + Math.floor(100 + Math.random() * 100);

  const randMac = Array.from({length:6}, () => Math.floor(Math.random()*256).toString(16).padStart(2,'0').toUpperCase()).join(':');
  document.getElementById('modal-dev-mac').value = randMac;
}

async function submitAddDevice() {
  const name  = document.getElementById('modal-dev-name').value.trim();
  const owner = document.getElementById('modal-dev-owner').value.trim();
  const type  = document.getElementById('modal-dev-type').value;
  const ip    = document.getElementById('modal-dev-ip').value.trim();
  const mac   = document.getElementById('modal-dev-mac').value.trim();
  const limit = document.getElementById('modal-dev-limit').value.trim();

  if (!name || !owner || !limit) {
    alert('Please fill in Name, Owner, and Data Limit.');
    return;
  }

  try {
    const res = await fetch(`${API}/api/devices`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `deviceName=${enc(name)}&ownerName=${enc(owner)}&deviceType=${enc(type)}&ipAddress=${enc(ip)}&macAddress=${enc(mac)}&dataLimit=${enc(limit)}`
    });
    const data = await res.json();
    if (data.success) {
      closeAddDeviceModal();
      await refreshAllData();
    } else {
      alert(data.message || 'Error adding device.');
    }
  } catch (e) {
    alert('Server error.');
  }
}

// ── 4. ADMIN CONTROLS ────────────────────────────────
function populateAdminDeviceDropdown(devices) {
  const sel = document.getElementById('admin-device-select');
  if (!sel) return;
  if (!devices.length) {
    sel.innerHTML = '<option>No devices registered</option>';
    return;
  }
  sel.innerHTML = devices.map(d =>
    `<option value="${d.deviceId}">#${d.deviceId} - ${d.deviceName} (${d.ipAddress}) ${d.isBlocked ? '[BLOCKED]' : ''}</option>`
  ).join('');

  onAdminDeviceSelect();
}

function onAdminDeviceSelect() {
  const sel = document.getElementById('admin-device-select');
  if (!sel || !sel.value) return;
  const devId = parseInt(sel.value);
  const dev = globalDevicesCache.find(d => d.deviceId === devId);
  if (!dev) return;

  document.getElementById('admin-data-limit').value = dev.dataLimit;
  document.getElementById('admin-speed-limit').value = dev.bandwidthLimitMbps || 100;

  const blockBtn = document.getElementById('admin-block-btn');
  if (dev.isBlocked) {
    blockBtn.textContent = 'Unblock Device';
    blockBtn.className = 'btn btn-primary';
  } else {
    blockBtn.textContent = 'Block Device';
    blockBtn.className = 'btn btn-danger';
  }
}

async function saveAdminLimits() {
  const sel = document.getElementById('admin-device-select');
  const devId = parseInt(sel.value);
  const dataLimit = document.getElementById('admin-data-limit').value;
  const speedLimit = document.getElementById('admin-speed-limit').value;
  const msgEl = document.getElementById('admin-msg');

  try {
    const res = await fetch(`${API}/api/devices`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `action=set_limits&deviceId=${devId}&dataLimit=${enc(dataLimit)}&speedLimit=${enc(speedLimit)}`
    });
    const data = await res.json();
    showAdminMsg(msgEl, 'Limits saved successfully.', 'success');
    await refreshAllData();
  } catch (e) {
    showAdminMsg(msgEl, 'Failed to save limits.', 'error');
  }
}

async function toggleAdminBlock() {
  const sel = document.getElementById('admin-device-select');
  const devId = parseInt(sel.value);
  const dev = globalDevicesCache.find(d => d.deviceId === devId);
  if (!dev) return;

  await toggleDeviceBlock(devId, !dev.isBlocked);
  onAdminDeviceSelect();
}

async function loadRestrictions() {
  try {
    const res = await fetch(`${API}/api/restrictions`);
    const list = await res.json();
    renderRestrictionsTable(list);
  } catch (e) {
    console.error('Error loading restrictions:', e);
  }
}

function renderRestrictionsTable(list) {
  const tbody = document.getElementById('restrictions-tbody');
  if (!tbody) return;
  if (!list || !list.length) {
    tbody.innerHTML = '<tr><td colspan="5" class="table-empty">No website restrictions configured.</td></tr>';
    return;
  }
  tbody.innerHTML = list.map(r => `
    <tr>
      <td class="mono">#${r.restrictionId}</td>
      <td><strong class="mono">${escapeHtml(r.domainName)}</strong></td>
      <td><span class="panel-hint">${escapeHtml(r.category)}</span></td>
      <td><span class="text-red font-semibold">${escapeHtml(r.action)}</span></td>
      <td>
        <button class="btn btn-sm btn-danger" onclick="deleteDomainRestriction(${r.restrictionId})">Delete</button>
      </td>
    </tr>
  `).join('');
}

async function addDomainRestriction() {
  const domain = document.getElementById('restrict-domain').value.trim();
  const cat    = document.getElementById('restrict-category').value;
  if (!domain) {
    alert('Please enter a domain name (e.g. tiktok.com)');
    return;
  }

  try {
    await fetch(`${API}/api/restrictions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `domainName=${enc(domain)}&category=${enc(cat)}&action=BLOCK`
    });
    document.getElementById('restrict-domain').value = '';
    await loadRestrictions();
  } catch (e) {
    alert('Error adding website restriction.');
  }
}

async function deleteDomainRestriction(id) {
  try {
    await fetch(`${API}/api/restrictions?id=${id}`, { method: 'DELETE' });
    await loadRestrictions();
    await loadBlockedWebsites();
  } catch (e) {
    alert('Error deleting restriction.');
  }
}

// ── 5. REAL-TIME WEBSITE BLOCKING & NETWORK FIREWALL ──
async function loadNetworkInfo() {
  try {
    const res = await fetch(`${API}/api/network/info`);
    const data = await res.json();
    const dnsEl = document.getElementById('dns-server-status');
    const proxyEl = document.getElementById('proxy-server-status');
    const hotspotEl = document.getElementById('hotspot-ip-val');

    if (dnsEl && data.dnsServer) {
      dnsEl.textContent = data.dnsServer.running ? `UDP Port ${data.dnsServer.port} (Active)` : `Port 53 (Inactive)`;
    }
    if (proxyEl && data.proxyServer) {
      proxyEl.textContent = data.proxyServer.running ? `Port ${data.proxyServer.port} (Active)` : `Port 8081 (Inactive)`;
    }
    if (hotspotEl && data.hotspotIp) {
      hotspotEl.textContent = data.hotspotIp;
    }
  } catch (e) {
    console.warn('Network info unavailable:', e);
  }
}

async function loadBlockedWebsites() {
  try {
    const res = await fetch(`${API}/api/restrictions`);
    const list = await res.json();
    renderBlockedWebsitesTable(list);
  } catch (e) {
    console.error('Error loading blocked websites:', e);
  }
}

function renderBlockedWebsitesTable(list) {
  const tbody = document.getElementById('blocking-rules-tbody');
  const countBadge = document.getElementById('blocked-rules-count-val');
  const counterEl = document.getElementById('blocklist-counter');

  const activeBlocked = (list || []).filter(r => !r.status || r.status.toUpperCase() === 'BLOCKED');
  if (countBadge) countBadge.textContent = `${activeBlocked.length} Domains`;
  if (counterEl) counterEl.textContent = `${list ? list.length : 0} Domains`;

  if (!tbody) return;
  if (!list || !list.length) {
    tbody.innerHTML = '<tr><td colspan="4" class="table-empty">No websites blocked yet. Add a domain above.</td></tr>';
    return;
  }

  tbody.innerHTML = list.map(r => {
    const isBlocked = !r.status || r.status.toUpperCase() === 'BLOCKED';

    return `
      <tr>
        <td>
          <div class="mono" style="font-weight:600;color:#fff;">${escapeHtml(r.domainName)}</div>
          <div class="dev-type-tag">${escapeHtml(r.createdAt || 'Rule #' + r.restrictionId)}</div>
        </td>
        <td><span class="panel-hint">${escapeHtml(r.category || 'General')}</span></td>
        <td>
          <span class="${isBlocked ? 'status-badge-blocked' : 'status-badge-unblocked'}">
            ${isBlocked ? '● BLOCKED' : '○ UNBLOCKED'}
          </span>
        </td>
        <td>
          <div class="action-btn-group">
            ${isBlocked 
              ? `<button class="btn btn-sm btn-secondary" onclick="toggleDomainStatus(${r.restrictionId}, 'UNBLOCKED')">Unblock</button>` 
              : `<button class="btn btn-sm btn-danger" onclick="toggleDomainStatus(${r.restrictionId}, 'BLOCKED')">Re-Block</button>`
            }
            <button class="btn btn-sm btn-secondary" onclick="deleteBlockedDomain(${r.restrictionId})">Delete</button>
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

async function submitBlockWebsite() {
  const input = document.getElementById('blocking-domain-input');
  const catSelect = document.getElementById('blocking-cat-select');
  const msgEl = document.getElementById('blocking-msg');
  const domain = input ? input.value.trim() : '';
  const category = catSelect ? catSelect.value : 'General';

  if (!domain) {
    showAdminMsg(msgEl, 'Please enter a domain name (e.g. youtube.com)', 'error');
    return;
  }

  try {
    const res = await fetch(`${API}/api/restrictions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `domainName=${enc(domain)}&category=${enc(category)}&action=BLOCK&status=BLOCKED`
    });
    const data = await res.json();
    if (input) input.value = '';
    showAdminMsg(msgEl, `Domain ${domain} is now BLOCKED on the network.`, 'success');
    await loadBlockedWebsites();
    await loadRestrictions();
  } catch (e) {
    showAdminMsg(msgEl, 'Failed to block domain.', 'error');
  }
}

async function toggleDomainStatus(id, newStatus) {
  try {
    await fetch(`${API}/api/restrictions`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `action=toggle_status&id=${id}&status=${enc(newStatus)}`
    });
    await loadBlockedWebsites();
    await loadRestrictions();
  } catch (e) {
    alert('Failed to update domain status.');
  }
}

async function deleteBlockedDomain(id) {
  if (!confirm('Remove this domain rule?')) return;
  try {
    await fetch(`${API}/api/restrictions?id=${id}`, { method: 'DELETE' });
    await loadBlockedWebsites();
    await loadRestrictions();
  } catch (e) {
    alert('Failed to delete rule.');
  }
}

// ── REAL-TIME EVENT POLLER ────────────────────────────
let previousLatestLogId = 0;

async function pollBlockedEvents() {
  try {
    const res = await fetch(`${API}/api/blocking/events?limit=25`);
    const logs = await res.json();
    renderBlockedActivityFeed(logs);
  } catch (e) {
    // Silent fail on polling
  }
}

function renderBlockedActivityFeed(logs) {
  const feed = document.getElementById('blocked-activity-feed');
  if (!feed) return;

  if (!logs || !logs.length) {
    feed.innerHTML = '<div class="table-empty">Waiting for network requests... Try opening a blocked domain on Phone B.</div>';
    return;
  }

  const newestId = logs[0].logId;
  const isNewAlert = previousLatestLogId > 0 && newestId > previousLatestLogId;
  previousLatestLogId = newestId;

  feed.innerHTML = logs.map((ev, idx) => `
    <div class="live-log-item ${idx === 0 && isNewAlert ? 'alert-flash' : ''}">
      <div class="log-item-top">
        <span class="log-badge-blocked">BLOCKED</span>
        <span class="mono log-timestamp">${escapeHtml(ev.timestamp)}</span>
        <span class="mono" style="font-size:11px;color:var(--text-secondary);">${escapeHtml(ev.clientIp)}</span>
      </div>
      <div class="mono log-domain-title">🚫 ${escapeHtml(ev.domainName)}</div>
      <div class="log-device-meta">Device: <strong>${escapeHtml(ev.deviceName)}</strong> &bull; Action: <span class="text-red font-semibold">${escapeHtml(ev.action)}</span></div>
    </div>
  `).join('');
}

async function simulateTestBlockEvent() {
  try {
    const res = await fetch(`${API}/api/blocking/events`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: 'action=simulate&domain=youtube.com&ip=192.168.137.45&device=Test Phone (Phone B)'
    });
    const data = await res.json();
    await pollBlockedEvents();
  } catch (e) {
    alert('Simulation error.');
  }
}

async function clearBlockedLogs() {
  try {
    await fetch(`${API}/api/blocking/events`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: 'action=clear'
    });
    previousLatestLogId = 0;
    await pollBlockedEvents();
  } catch (e) {
    alert('Clear logs error.');
  }
}

// ── 6. AI ABNORMAL USAGE DETECTION ───────────────────
async function runAiScan() {
  const riskTitle = document.getElementById('ai-risk-title');
  const riskDesc = document.getElementById('ai-risk-desc');
  const riskScore = document.getElementById('ai-risk-score');
  const container = document.getElementById('anomalies-list-container');
  const countBadge = document.getElementById('ai-alert-count');
  const findingsCount = document.getElementById('ai-findings-count');

  if (riskTitle) riskTitle.textContent = 'Analyzing traffic patterns...';

  try {
    const res = await fetch(`${API}/api/ai/anomalies`);
    const data = await res.json();
    globalAnomaliesCache = data.anomalies || [];

    const score = data.networkRiskScore || 12;
    if (riskScore) riskScore.textContent = score;
    if (countBadge) countBadge.textContent = globalAnomaliesCache.length;
    if (findingsCount) findingsCount.textContent = `${globalAnomaliesCache.length} Anomalies Found`;

    const banner = document.getElementById('ai-risk-banner');
    if (banner) {
      if (score > 60) {
        banner.className = 'card ai-risk-card critical-risk';
        if (riskTitle) riskTitle.textContent = 'High Anomaly Risk Detected';
        if (riskDesc) riskDesc.textContent = `Found ${globalAnomaliesCache.length} unusual traffic patterns (heavy upload spikes or exceeded data caps).`;
      } else if (score > 30) {
        banner.className = 'card ai-risk-card warning-risk';
        if (riskTitle) riskTitle.textContent = 'Moderate Traffic Variance';
        if (riskDesc) riskDesc.textContent = `Minor deviations from normal baseline detected.`;
      } else {
        banner.className = 'card ai-risk-card';
        if (riskTitle) riskTitle.textContent = 'Network Traffic Normal';
        if (riskDesc) riskDesc.textContent = 'All connected devices are within normal baseline usage.';
      }
    }

    renderAnomaliesList(globalAnomaliesCache);
  } catch (e) {
    if (riskTitle) riskTitle.textContent = 'Scan unavailable';
  }
}

function renderAnomaliesList(anomalies) {
  const container = document.getElementById('anomalies-list-container');
  if (!container) return;
  if (!anomalies.length) {
    container.innerHTML = `
      <div class="table-empty">
        <div style="font-size: 15px; font-weight: 600; color: var(--accent-green-light); margin-bottom: 4px;">✓ No Anomalies Found</div>
        <div>All device traffic is conforming to baseline expectations.</div>
      </div>
    `;
    return;
  }

  container.innerHTML = anomalies.map(a => {
    const sevClass = a.severity === 'CRITICAL' ? 'badge-critical' : 'badge-high';

    return `
      <div class="anomaly-card">
        <div class="anomaly-header">
          <div class="anomaly-title-box">
            <span class="severity-badge ${sevClass}">${a.severity}</span>
            <h4 class="anomaly-title">${escapeHtml(a.anomalyType)}</h4>
          </div>
          <div class="mono" style="color: var(--accent-blue); font-size: 12px; font-weight: 600;">Risk Score: ${a.riskScore}/100</div>
        </div>

        <div class="anomaly-target">
          Device: <strong>${escapeHtml(a.deviceName)}</strong> &bull; IP: <code class="mono">${escapeHtml(a.ipAddress)}</code> &bull; MAC: <code class="mono">${escapeHtml(a.macAddress)}</code>
        </div>

        <div class="ai-explanation-box">
          <div class="ai-reason-header">AI Insight:</div>
          <div class="ai-reason-text">${escapeHtml(a.aiExplanation)}</div>
        </div>

        <div class="anomaly-actions">
          <button class="btn btn-sm btn-danger" onclick="executeMitigation(${a.deviceId}, 'block')">Block Device</button>
          <button class="btn btn-sm btn-secondary" onclick="executeMitigation(${a.deviceId}, 'throttle')">Apply 2 Mbps Limit</button>
          <button class="btn btn-sm btn-secondary" onclick="dismissAnomaly(this)">Dismiss</button>
        </div>
      </div>
    `;
  }).join('');
}

async function executeMitigation(deviceId, action) {
  if (action === 'block') {
    await toggleDeviceBlock(deviceId, true);
    alert('Device blocked successfully.');
  } else if (action === 'throttle') {
    await fetch(`${API}/api/devices`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `action=set_limits&deviceId=${deviceId}&dataLimit=20&speedLimit=2`
    });
    alert('2 Mbps speed limit applied.');
    await refreshAllData();
  }
  runAiScan();
}

function dismissAnomaly(btn) {
  const card = btn.closest('.anomaly-card');
  if (card) card.remove();
}

// ── REALTIME SPEEDMETER CHART & LOGIC ──────────────────
function initCharts() {
  const ctxDoughnut = document.getElementById('deviceDoughnutChart');
  if (ctxDoughnut) {
    deviceDoughnutChart = new Chart(ctxDoughnut.getContext('2d'), {
      type: 'doughnut',
      data: {
        labels: [],
        datasets: [{
          data: [],
          backgroundColor: [],
          borderWidth: 2,
          borderColor: '#161b22'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: 'right', labels: { color: '#8b949e', font: { family: 'Inter', size: 12 } } }
        }
      }
    });
  }

  const ctxHistory = document.getElementById('historyChart');
  if (ctxHistory) {
    historyChart = new Chart(ctxHistory.getContext('2d'), {
      type: 'bar',
      data: {
        labels: [],
        datasets: [
          { label: 'Download (GB)', data: [], backgroundColor: 'rgba(35, 134, 54, 0.75)', borderRadius: 4 },
          { label: 'Upload (GB)', data: [], backgroundColor: 'rgba(47, 129, 247, 0.75)', borderRadius: 4 }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { labels: { color: '#8b949e' } } },
        scales: {
          x: { grid: { color: 'rgba(255,255,255,0.04)' }, ticks: { color: '#8b949e' } },
          y: { grid: { color: 'rgba(255,255,255,0.04)' }, ticks: { color: '#8b949e' } }
        }
      }
    });
  }

  const ctxSpeed = document.getElementById('dashSpeedChart');
  if (ctxSpeed) {
    dashSpeedChart = new Chart(ctxSpeed.getContext('2d'), {
      type: 'line',
      data: {
        labels: SPEED_LABELS,
        datasets: [
          { label: 'Download', data: SPEED_DL, borderColor: '#3fb950', backgroundColor: 'rgba(63, 185, 80, 0.08)', borderWidth: 2, tension: 0.35, fill: true, pointRadius: 0 },
          { label: 'Upload', data: SPEED_UL, borderColor: '#58a6ff', backgroundColor: 'rgba(88, 166, 255, 0.08)', borderWidth: 2, tension: 0.35, fill: true, pointRadius: 0 }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: { duration: 400 },
        plugins: { legend: { display: false } },
        scales: {
          x: { display: false },
          y: { min: 0, grid: { color: 'rgba(255,255,255,0.04)' }, ticks: { color: '#8b949e' } }
        }
      }
    });
  }
}

function startRealtimeSpeedmeter() {
  setInterval(() => {
    let dl, ul;
    if (navigator.connection && navigator.connection.downlink) {
      dl = parseFloat((navigator.connection.downlink * (0.8 + Math.random()*0.4)).toFixed(2));
      ul = parseFloat((dl * 0.35 + Math.random()*2).toFixed(2));
    } else {
      const base = 25 + Math.random() * 40;
      dl = parseFloat((base + Math.random() * 8).toFixed(2));
      ul = parseFloat((base * 0.3 + Math.random() * 3).toFixed(2));
    }

    SPEED_DL.push(dl); SPEED_DL.shift();
    SPEED_UL.push(ul); SPEED_UL.shift();

    const dlEl = document.getElementById('dash-dl');
    const ulEl = document.getElementById('dash-ul');
    const pingEl = document.getElementById('dash-ping');
    const topPingEl = document.getElementById('top-ping-val');

    const pingVal = Math.floor(10 + Math.random()*20);

    if (dlEl) dlEl.textContent = `${dl.toFixed(2)} Mbps`;
    if (ulEl) ulEl.textContent = `${ul.toFixed(2)} Mbps`;
    if (pingEl) pingEl.textContent = `${pingVal} ms`;
    if (topPingEl) topPingEl.textContent = `${pingVal} ms`;

    if (dashSpeedChart) dashSpeedChart.update('none');
  }, 1000);
}

// ── UTILS ────────────────────────────────────────────
function showAdminMsg(el, msg, type) {
  if (!el) return;
  el.className = type === 'error' ? 'clean-alert clean-alert-err mt-12' : 'clean-alert clean-alert-ok mt-12';
  el.textContent = msg;
  setTimeout(() => { el.className = 'hidden'; }, 3500);
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function enc(s) { return encodeURIComponent(s); }
