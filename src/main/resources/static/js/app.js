/* SecureFS frontend: vanilla JS, talks only to the REST API. All dynamic text goes through esc() (XSS safe). */
const $ = (s, r = document) => r.querySelector(s);
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let ME = null, ROOT = null, CUR = null;               // current user, root folder id, current folder id
const TEXT_EXT = ['txt','md','sql','java','csv','json','html','css','js'];

async function api(path, opt = {}) {
  const o = { credentials: 'same-origin', ...opt };
  if (o.json !== undefined) { o.method = o.method || 'POST'; o.headers = { 'Content-Type': 'application/json' }; o.body = JSON.stringify(o.json); }
  const r = await fetch(path, o);
  if (r.status === 401) { location.href = 'index.html'; throw new Error('Not logged in'); }
  const ct = r.headers.get('content-type') || '';
  const data = ct.includes('json') ? await r.json() : await r.text();
  if (!r.ok) { const m = (data && data.message) || 'Request failed'; toast(m, true); throw new Error(m); }
  return data;
}
function toast(msg, err) {
  const t = document.createElement('div'); t.className = 'glass toast ' + (err ? 'err' : 'ok'); t.textContent = msg;
  $('#toasts').appendChild(t); setTimeout(() => t.remove(), 4500);
}
const fmtBytes = b => b < 1024 ? b + ' B' : b < 1048576 ? (b / 1024).toFixed(1) + ' KB' : (b / 1048576).toFixed(2) + ' MB';
const fmtTime = t => t ? String(t).replace('T', ' ').slice(0, 19) : '-';
const icon = f => ({pdf:'📕',txt:'📄',md:'📝',png:'🖼️',jpg:'🖼️',jpeg:'🖼️',sql:'🗄️',java:'☕',docx:'📘',pptx:'📙',xlsx:'📗',csv:'📊',json:'🧾',html:'🌐',css:'🎨',js:'⚙️'}[f] || '📄');
const hue = id => (id * 47) % 360;
const cellColor = id => `hsl(${hue(id)} 70% 45% / .85)`;

// ---------------------------------------------------------------- modal
function openModal(html, small) { const m = $('#modal'); m.className = 'glass modal' + (small ? ' small' : ''); m.innerHTML = html; $('#mbg').classList.add('open'); }
function closeModal() { $('#mbg').classList.remove('open'); $('#modal').innerHTML = ''; }
$('#mbg').addEventListener('mousedown', e => { if (e.target.id === 'mbg') closeModal(); });
document.addEventListener('keydown', e => { if (e.key === 'Escape') closeModal(); });

// ---------------------------------------------------------------- routing
const VIEWS = {
  dash: ['Dashboard', vDash], files: ['My Files', vFiles], shared: ['Shared with me', vShared],
  alloc: ['Allocation Lab', vAlloc], sched: ['Disk Scheduling Lab', vSched], audit: ['Audit Logs', vAudit], users: ['Users & Storage', vUsers]
};
function go(v) {
  const [title, fn] = VIEWS[v] || VIEWS.dash;
  document.querySelectorAll('#nav a').forEach(a => a.classList.toggle('active', a.dataset.v === v));
  $('#title').textContent = title; location.hash = v; $('#view').innerHTML = '<div class="glass panel muted">Loading…</div>';
  fn();
}
$('#nav').addEventListener('click', e => { const a = e.target.closest('a[data-v]'); if (a) go(a.dataset.v); });
$('#logout').onclick = async () => { await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin' }); location.href = 'index.html'; };

// ---------------------------------------------------------------- dashboard
async function vDash() {
  const [s, pool] = await Promise.all([api('/api/analytics/storage'), api('/api/simulate/pool')]);
  const pct = Math.round(100 * s.usedBlocks / s.totalBlocks);
  $('#view').innerHTML = `
  <div class="grid stats">
    <div class="glass stat"><div class="v">${s.usedBlocks}/${s.totalBlocks}</div><div class="l">Disk blocks used (${pct}%) · ${fmtBytes(s.blockSize)} each</div></div>
    <div class="glass stat"><div class="v">${s.freeBlocks}</div><div class="l">Free blocks · largest free run ${s.largestFreeRun}</div></div>
    <div class="glass stat"><div class="v">${s.fragmentationPct}%</div><div class="l">External fragmentation</div></div>
    <div class="glass stat"><div class="v">${s.myFiles}</div><div class="l">My files · ${fmtBytes(s.myBytes)}</div></div>
  </div>
  <div class="grid" style="grid-template-columns:repeat(auto-fit,minmax(320px,1fr));margin-top:14px">
    <div class="glass panel"><h2>Disk map</h2>${diskGrid(pool.cells, pool.totalBlocks)}${legendFor(pool.cells)}</div>
    <div class="glass panel"><h2>Files by allocation type</h2>
      ${s.byAllocation.length ? s.byAllocation.map(a => `<div class="row" style="justify-content:space-between;margin:8px 0"><span class="badge">${esc(a.allocationType)}</span><span>${a.files} file(s) · ${fmtBytes(a.bytes)}</span></div>`).join('') : '<div class="muted">No files yet.</div>'}
      <h2 style="margin-top:20px">Most accessed (mine)</h2>
      ${s.mostUsed.length ? s.mostUsed.map(m => `<div class="row" style="justify-content:space-between;margin:6px 0"><span>${esc(m.name)}</span><span class="badge">${m.accesses} accesses</span></div>`).join('') : '<div class="muted">No activity yet.</div>'}
    </div>
  </div>`;
}
function diskGrid(cells, total, plan = {}) {
  const map = {}; cells.forEach(c => map[c.blockNo] = c);
  let h = '<div class="disk">';
  for (let i = 0; i < total; i++) {
    const c = map[i], p = plan[i];
    const cls = 'cell' + (c ? ' used' : '') + (c && c.index ? ' idx' : '') + (p ? ' plan' : '');
    const style = c && !p ? `style="background:${cellColor(c.fileId)}"` : '';
    const tip = c ? `Block ${i}: ${c.fileName}${c.index ? ' (index block)' : ''}${c.nextBlock != null ? ' → ' + c.nextBlock : ''}` : p ? `Block ${i}: would be allocated here` : `Block ${i}: free`;
    h += `<div class="${cls}" ${style} title="${esc(tip)}">${i}</div>`;
  }
  return h + '</div>';
}
function legendFor(cells) {
  const seen = {}; cells.forEach(c => seen[c.fileId] = c);
  const items = Object.values(seen).slice(0, 12).map(c => `<span><i style="background:${cellColor(c.fileId)}"></i>${esc(c.fileName)}</span>`).join('');
  return `<div class="legend"><span><i style="background:rgba(255,255,255,.12)"></i>free</span>${items}<span><i style="outline:2px solid #fff;outline-offset:-2px"></i>index block</span><span><i style="background:rgba(251,191,36,.6)"></i>planned (dry-run)</span></div>`;
}

// ---------------------------------------------------------------- files
async function vFiles() { if (!ROOT) { const t = await api('/api/folders/tree'); ROOT = (t.find(f => f.parentId == null) || t[0]).id; } return openFolder(CUR || ROOT); }
async function openFolder(id) {
  CUR = id;
  const d = await api(`/api/folders/${id}/contents`);
  $('#view').innerHTML = `
  <div class="glass panel">
    <div class="row" style="justify-content:space-between;margin-bottom:14px">
      <div class="crumbs">${d.breadcrumb.map((b, i) => (i ? '<span class="muted">/</span>' : '') + `<a data-f="${b.id}">${esc(b.name)}</a>`).join('')}</div>
      <div class="row"><button id="bNew">＋ New folder</button><button id="bUp" class="primary">⬆ Upload file</button></div>
    </div>
    <div class="tiles">
      ${d.folders.map(f => `<div class="glass tile" data-f="${f.id}"><div class="ic">📁</div><div class="nm">${esc(f.name)}</div></div>`).join('')}
    </div>
    <h2 style="margin-top:22px">Files</h2>
    ${d.files.length ? `<table><tr><th>Name</th><th>Size</th><th>Allocation</th><th>Ver</th><th>Lock</th><th></th></tr>
    ${d.files.map(f => `<tr class="click" data-file="${f.id}"><td>${icon(f.fileType)} ${esc(f.name)}</td><td>${fmtBytes(f.sizeBytes)}</td><td><span class="badge">${esc(f.allocationType)}</span></td><td>v${f.version}</td>
      <td>${f.lockedBy ? `<span class="badge warn">🔒 ${esc(f.lockedBy)}</span>` : '<span class="badge ok">free</span>'}</td><td class="muted">${fmtTime(f.updatedAt || f.createdAt)}</td></tr>`).join('')}</table>`
      : '<div class="muted">This folder has no files. Upload one to see it get allocated onto the simulated disk.</div>'}
    ${d.breadcrumb.length > 1 ? '<div style="margin-top:16px"><button id="bDel" class="danger sm">Delete this folder</button></div>' : ''}
  </div>`;
  document.querySelectorAll('[data-f]').forEach(el => el.onclick = () => openFolder(+el.dataset.f));
  document.querySelectorAll('[data-file]').forEach(el => el.onclick = () => openFile(+el.dataset.file));
  $('#bNew').onclick = () => {
    openModal(`<div class="modal-head"><h3>New folder</h3><button class="sm" onclick="closeModal()">✕</button></div>
      <div class="row"><input id="nf" placeholder="Folder name" style="flex:1"><button class="primary" id="nfOk">Create</button></div>`, true);
    $('#nf').focus();
    $('#nfOk').onclick = async () => { await api('/api/folders', { json: { name: $('#nf').value, parentId: id } }); closeModal(); toast('Folder created'); openFolder(id); };
  };
  $('#bUp').onclick = () => uploadDialog(id);
  const del = $('#bDel');
  if (del) del.onclick = async () => {
    if (!confirm('Delete this folder?')) return;
    const parent = d.breadcrumb[d.breadcrumb.length - 2].id;
    try { await api(`/api/folders/${id}`, { method: 'DELETE' }); }
    catch { if (!confirm('The folder is not empty. Delete it with everything inside?')) return; await api(`/api/folders/${id}?force=true`, { method: 'DELETE' }); }
    toast('Folder deleted'); openFolder(parent);
  };
}
function uploadDialog(folderId) {
  openModal(`<div class="modal-head"><h3>Upload file</h3><button class="sm" onclick="closeModal()">✕</button></div>
    <div class="grid"><input type="file" id="uf">
    <label class="muted">File allocation algorithm (OS module)</label>
    <select id="ua"><option value="CONTIGUOUS">Contiguous — one unbroken run of blocks</option><option value="LINKED">Linked — chain of scattered blocks</option><option value="INDEXED">Indexed — index block + data blocks</option></select>
    <div class="muted">Allowed: pdf, txt, md, docx, pptx, xlsx, png, jpg, sql, java, csv, json, html, css, js · max 1 MB</div>
    <button class="primary" id="uo">Upload</button></div>`, true);
  $('#uo').onclick = async () => {
    const f = $('#uf').files[0]; if (!f) return toast('Choose a file first', true);
    const fd = new FormData(); fd.append('file', f); fd.append('folderId', folderId); fd.append('allocationType', $('#ua').value);
    $('#uo').disabled = true;
    try { const r = await api('/api/files/upload', { method: 'POST', body: fd }); closeModal(); toast(`Uploaded ${r.name} (${r.allocationType})`); openFolder(folderId); }
    catch { $('#uo').disabled = false; }
  };
}
async function vShared() {
  const list = await api('/api/files/shared');
  $('#view').innerHTML = `<div class="glass panel">${list.length ? `<table><tr><th>Name</th><th>Owner</th><th>My access</th><th>Size</th><th>Lock</th></tr>
  ${list.map(f => `<tr class="click" data-file="${f.id}"><td>${icon(f.fileType)} ${esc(f.name)}</td><td>${esc(f.ownerName)}</td>
   <td><span class="badge ${f.canWrite ? 'ok' : ''}">${f.canWrite ? 'read + write' : 'read only'}</span></td><td>${fmtBytes(f.sizeBytes)}</td><td>${f.lockedBy ? '🔒 ' + esc(f.lockedBy) : '—'}</td></tr>`).join('')}</table>`
    : '<div class="muted">Nothing has been shared with you yet.</div>'}</div>`;
  document.querySelectorAll('[data-file]').forEach(el => el.onclick = () => openFile(+el.dataset.file));
}

// ---------------------------------------------------------------- file details
async function openFile(id, tab = 'info') {
  let f; try { f = await api(`/api/files/${id}`); } catch { return; }
  const tabs = [['info', 'Details'], ['blocks', 'Disk blocks'], ['edit', 'Edit & lock'], ['versions', 'Versions'], ['activity', 'Activity']];
  if (f.owner) tabs.splice(3, 0, ['share', 'Share & permissions']);
  openModal(`<div class="modal-head"><h3>${icon(f.fileType)} ${esc(f.name)}</h3><button class="sm" onclick="closeModal()">✕</button></div>
    <div class="tabs">${tabs.map(([k, l]) => `<button class="${k === tab ? 'on' : ''}" data-t="${k}">${l}</button>`).join('')}</div><div id="tbody"></div>`);
  document.querySelectorAll('[data-t]').forEach(b => b.onclick = () => openFile(id, b.dataset.t));
  const body = $('#tbody');
  if (tab === 'info') {
    body.innerHTML = `<table>
      <tr><th>Owner</th><td>${esc(f.ownerName)} ${f.owner ? '<span class="badge ok">you</span>' : ''}</td></tr>
      <tr><th>Size</th><td>${fmtBytes(f.sizeBytes)} (${f.sizeBytes} bytes)</td></tr>
      <tr><th>Allocation</th><td><span class="badge">${esc(f.allocationType)}</span></td></tr>
      <tr><th>Version</th><td>v${f.version}</td></tr>
      <tr><th>My access</th><td>${f.owner ? 'Owner (full)' : (f.canWrite ? 'Read + Write' : 'Read only')}</td></tr>
      <tr><th>Lock</th><td>${f.lockedBy ? `🔒 ${esc(f.lockedBy)} until ${fmtTime(f.lockExpiresAt)}` : 'Not locked'}</td></tr>
      <tr><th>Created</th><td>${fmtTime(f.createdAt)}</td></tr></table>
      <div class="row" style="margin-top:16px"><a class="btn" href="/api/files/${id}/download">⬇ Download</a>
      ${f.owner ? '<button class="danger" id="fdel">Delete file</button>' : ''}</div>`;
    const d = $('#fdel'); if (d) d.onclick = async () => { if (!confirm('Delete this file permanently?')) return; await api(`/api/files/${id}`, { method: 'DELETE' }); closeModal(); toast('File deleted'); go('files'); };
  }
  if (tab === 'blocks') {
    const [bl, pool] = await Promise.all([api(`/api/files/${id}/blocks`), api('/api/simulate/pool')]);
    const plan = {}; bl.forEach(b => plan[b.blockNo] = true);
    let desc = '';
    if (f.allocationType === 'CONTIGUOUS') desc = `Blocks ${bl[0]?.blockNo}–${bl[bl.length - 1]?.blockNo} (start + length is enough to locate everything).`;
    else if (f.allocationType === 'LINKED') desc = 'Chain: ' + bl.map(b => b.blockNo).join(' → ') + ' → ∅  (each block stores a pointer to the next).';
    else { const ix = bl.find(b => b.indexBlock); desc = `Index block ${ix?.blockNo} → [${bl.filter(b => !b.indexBlock).map(b => b.blockNo).join(', ')}]  (one lookup reaches any block).`; }
    body.innerHTML = `<p>${esc(desc)}</p><div class="disk">${(() => { let h = ''; const m = {}; pool.cells.forEach(c => m[c.blockNo] = c);
      for (let i = 0; i < pool.totalBlocks; i++) { const c = m[i]; const mine = plan[i];
        h += `<div class="cell ${mine ? 'used' : ''} ${mine && c.index ? 'idx' : ''}" style="${mine ? 'background:' + cellColor(id) : c ? 'opacity:.35;background:rgba(255,255,255,.15)' : ''}" title="Block ${i}">${i}</div>`; } return h; })()}</div>
      <table style="margin-top:14px"><tr><th>Block</th><th>Seq</th><th>Next</th><th>Index?</th></tr>${bl.map(b => `<tr><td>${b.blockNo}</td><td>${b.sequenceNo ?? '-'}</td><td>${b.nextBlock ?? '-'}</td><td>${b.indexBlock ? 'yes' : ''}</td></tr>`).join('')}</table>`;
  }
  if (tab === 'edit') await editTab(f, body);
  if (tab === 'share') {
    const ps = await api(`/api/files/${id}/permissions`);
    body.innerHTML = `<table><tr><th>User</th><th>Read</th><th>Write</th><th></th></tr>
      ${ps.map(p => `<tr><td>${esc(p.username)} ${p.owner ? '<span class="badge">owner</span>' : ''}</td><td>${p.canRead ? '✓' : '✗'}</td><td>${p.canWrite ? '✓' : '✗'}</td>
        <td>${p.owner ? '' : `<button class="sm danger" data-rev="${p.userId}">Revoke</button>`}</td></tr>`).join('')}</table>
      <h2 style="margin-top:18px">Share with a user</h2>
      <div class="row"><input id="su" placeholder="Username" style="flex:1"><label><input type="checkbox" id="sw"> allow write</label><button class="primary" id="sb">Share</button></div>
      <div class="muted" style="margin-top:8px">Read is always included. Permissions are checked on the server for every request.</div>`;
    $('#sb').onclick = async () => { await api(`/api/files/${id}/share`, { json: { username: $('#su').value.trim(), canRead: true, canWrite: $('#sw').checked } }); toast('Permission saved'); openFile(id, 'share'); };
    document.querySelectorAll('[data-rev]').forEach(b => b.onclick = async () => { await api(`/api/files/${id}/share/${b.dataset.rev}`, { method: 'DELETE' }); toast('Access revoked'); openFile(id, 'share'); });
  }
  if (tab === 'versions') {
    const vs = await api(`/api/files/${id}/versions`);
    body.innerHTML = `<table><tr><th>Version</th><th>Modified</th><th>Size</th><th></th></tr>${vs.map(v => `<tr><td>v${v.versionNo} ${v.versionNo === f.version ? '<span class="badge ok">current</span>' : ''}</td><td>${fmtTime(v.modifiedAt)}</td><td>${fmtBytes(v.sizeBytes)}</td>
      <td>${f.canWrite && v.versionNo !== f.version ? `<button class="sm" data-rs="${v.versionNo}">Restore</button>` : ''}</td></tr>`).join('')}</table>
      <div class="muted" style="margin-top:10px">Restoring needs the write lock (see “Edit & lock”) and creates a <b>new</b> version, so history is never rewritten.</div>`;
    document.querySelectorAll('[data-rs]').forEach(b => b.onclick = async () => { try { await api(`/api/files/${id}/versions/${b.dataset.rs}/restore`, { method: 'POST' }); toast('Restored as a new version'); openFile(id, 'versions'); } catch {} });
  }
  if (tab === 'activity') {
    const a = await api(`/api/files/${id}/access-logs`);
    body.innerHTML = a.length ? `<table><tr><th>When</th><th>Action</th><th>User id</th></tr>${a.map(x => `<tr><td>${fmtTime(x.accessedAt)}</td><td><span class="badge">${esc(x.action)}</span></td><td>${x.userId}</td></tr>`).join('')}</table>` : '<div class="muted">No activity.</div>';
  }
}
async function editTab(f, body) {
  const id = f.id;
  if (!f.canWrite) { body.innerHTML = '<div class="muted">You only have read permission on this file.</div>'; return; }
  const ls = await api(`/api/files/${id}/lock`);
  const mine = ls.locked !== false && ls.mine;
  if (ls.locked !== false && !ls.mine) {
    body.innerHTML = `<div class="badge warn">🔒 Locked by ${esc(ls.lockedBy)} until ${fmtTime(ls.expiresAt)}</div>
      <p class="muted">Mutual exclusion: only one writer at a time. You can still read/download the file. ${f.owner ? 'As the owner you may force-release the lock.' : ''}</p>
      ${f.owner ? '<button class="danger" id="force">Force release</button>' : ''}`;
    const fr = $('#force'); if (fr) fr.onclick = async () => { await api(`/api/files/${id}/lock`, { method: 'DELETE' }); toast('Lock released'); openFile(id, 'edit'); };
    return;
  }
  if (!mine) {
    body.innerHTML = `<p>The file is not locked. Acquire the <b>write lock</b> to edit it — this runs as a database transaction (<code>SELECT … FOR UPDATE</code> on the file row), so two users can never both get it.</p>
      <button class="primary" id="acq">🔒 Acquire write lock</button>`;
    $('#acq').onclick = async () => { await api(`/api/files/${id}/lock`, { method: 'POST' }); toast('Write lock acquired'); openFile(id, 'edit'); };
    return;
  }
  const textual = TEXT_EXT.includes(f.fileType);
  body.innerHTML = `<div class="row" style="justify-content:space-between"><span class="badge ok">🔒 You hold the write lock until ${fmtTime(ls.expiresAt)}</span><button class="sm" id="rel">Release without saving</button></div>
    ${textual ? `<textarea id="ta" style="margin-top:12px" spellcheck="false">Loading…</textarea><div class="row" style="margin-top:10px"><button class="primary" id="sv">Save as v${f.version + 1} &amp; release lock</button></div>`
      : `<p class="muted" style="margin-top:12px">This file type is edited by uploading a replacement.</p><input type="file" id="rf"><div class="row" style="margin-top:10px"><button class="primary" id="sv">Upload as v${f.version + 1} &amp; release lock</button></div>`}`;
  $('#rel').onclick = async () => { await api(`/api/files/${id}/lock`, { method: 'DELETE' }); toast('Lock released'); openFile(id, 'edit'); };
  if (textual) { $('#ta').value = await api(`/api/files/${id}/content`); }
  $('#sv').onclick = async () => {
    const fd = new FormData();
    if (textual) fd.append('file', new Blob([$('#ta').value], { type: 'text/plain' }), f.name);
    else { const r = $('#rf').files[0]; if (!r) return toast('Choose a file', true); fd.append('file', r, f.name); }
    await api(`/api/files/${id}?release=true`, { method: 'PUT', body: fd });
    toast('New version saved, lock released'); openFile(id, 'versions');
  };
}

// ---------------------------------------------------------------- allocation lab
async function vAlloc() {
  const pool = await api('/api/simulate/pool');
  $('#view').innerHTML = `<div class="glass panel"><h2>Simulated disk — ${pool.totalBlocks} blocks × ${fmtBytes(pool.blockSize)}</h2>
    <div class="muted" style="margin-bottom:10px">Used ${pool.usedBlocks} · Free ${pool.freeBlocks} · Largest contiguous free run ${pool.largestFreeRun}. Hover a block for details.</div>
    <div id="dg">${diskGrid(pool.cells, pool.totalBlocks)}</div>${legendFor(pool.cells)}</div>
  <div class="glass panel" style="margin-top:14px"><h2>Dry-run: where would a file go?</h2>
    <div class="row"><select id="at"><option>CONTIGUOUS</option><option>LINKED</option><option>INDEXED</option></select>
    <input id="ab" type="number" min="1" max="100" value="5" style="width:110px"><span class="muted">data blocks</span><button class="primary" id="ar">Simulate</button></div>
    <div id="ao" style="margin-top:14px"></div></div>`;
  $('#ar').onclick = async () => {
    const r = await api('/api/simulate/allocation', { json: { type: $('#at').value, blocks: +$('#ab').value } });
    const plan = {}; if (r.success) r.plan.forEach(p => plan[p.blockNo] = true);
    $('#dg').innerHTML = diskGrid(pool.cells, pool.totalBlocks, plan);
    $('#ao').innerHTML = r.success
      ? `<span class="badge ok">success</span> <code>${esc(r.plan.map(p => p.blockNo + (p.indexBlock ? '(index)' : '')).join(r.type === 'LINKED' ? ' → ' : ', '))}</code>`
      : `<span class="badge bad">failed</span> ${esc(r.message)}<div class="muted">Tip: this is external fragmentation — total free space is enough, but no single run is big enough.</div>`;
  };
}

// ---------------------------------------------------------------- scheduling lab
async function vSched() {
  const [q, hist] = await Promise.all([api('/api/simulate/queue'), api('/api/simulate/history')]);
  $('#view').innerHTML = `<div class="glass panel"><h2>Run disk scheduling</h2>
    <div class="row"><label class="muted">Head start</label><input id="hs" type="number" min="0" max="199" value="50" style="width:90px">
    <input id="rq" value="82,170,43,140,24,16,190" style="flex:1;min-width:220px" placeholder="block requests e.g. 82,170,43">
    <select id="al"><option>FCFS</option><option>SSTF</option><option>SCAN</option><option>CSCAN</option></select></div>
    <div class="row" style="margin-top:10px"><label><input type="checkbox" id="uq"> use pending disk queue (${q.length} request${q.length === 1 ? '' : 's'} from file activity)</label>
      <label><input type="checkbox" id="cq"> mark them serviced</label>
      <button class="primary" id="run">Run selected</button><button id="cmp">Compare all 4</button></div>
    <div class="muted" style="margin-top:8px">Queue now: ${q.length ? esc(q.slice(0, 30).join(', ')) : 'empty — open or upload files to generate requests'}</div></div>
    <div class="glass panel hidden" id="out" style="margin-top:14px"></div>
    <div class="glass panel" style="margin-top:14px"><h2>Recent runs (stored in Scheduling_Runs)</h2>
      ${hist.length ? `<table><tr><th>Run</th><th>Algorithm</th><th>Head start</th><th>Total movement</th><th>When</th></tr>${hist.map(h => `<tr><td>#${h.id}</td><td>${esc(h.algorithm)}</td><td>${h.headStart}</td><td>${h.totalHeadMovement}</td><td>${fmtTime(h.runAt)}</td></tr>`).join('')}</table>` : '<div class="muted">No runs yet.</div>'}</div>`;
  const body = () => ({ headStart: +$('#hs').value, requests: $('#rq').value.split(',').map(s => s.trim()).filter(Boolean).map(Number), useQueue: $('#uq').checked, consume: $('#cq').checked });
  $('#run').onclick = async () => { const r = await api('/api/simulate/scheduling', { json: { ...body(), algorithm: $('#al').value } }); showRuns([r], r.requests, r.headStart); };
  $('#cmp').onclick = async () => {
    const b = body(); const qs = b.useQueue ? `headStart=${b.headStart}&consume=${b.consume}` : `headStart=${b.headStart}&requests=${b.requests.join(',')}`;
    const c = await api(`/api/simulate/scheduling/compare?${qs}`); showRuns(c.results, c.requests, c.headStart, true); };
}
function showRuns(results, reqs, head, compare) {
  const out = $('#out'); out.classList.remove('hidden');
  const max = Math.max(...results.map(r => r.totalMovement));
  out.innerHTML = `<h2>${compare ? 'Comparison' : esc(results[0].algorithm)} — head starts at ${head}, requests: ${esc(reqs.join(', '))}</h2>
   <table><tr><th>Algorithm</th><th>Service order</th><th>Total movement</th><th>Avg seek</th></tr>
   ${results.map(r => `<tr><td><b>${esc(r.algorithm)}</b></td><td>${esc([head, ...r.order].join(' → '))}</td><td>${r.totalMovement}</td><td>${r.avgSeek}</td></tr>`).join('')}</table>
   <div class="bars" style="margin-top:14px">${results.map(r => `<div class="b"><span>${esc(r.algorithm)}</span><div class="track"><div class="fill" style="width:${Math.round(100 * r.totalMovement / max)}%"></div></div><span>${r.totalMovement}</span></div>`).join('')}</div>
   <div class="grid" style="grid-template-columns:repeat(auto-fit,minmax(360px,1fr));margin-top:14px">${results.map(r => `<div><div class="muted">${esc(r.algorithm)} head path</div><canvas data-alg="${esc(r.algorithm)}" height="190"></canvas></div>`).join('')}</div>
   ${compare ? '<div class="muted" style="margin-top:10px">Lowest movement is not “best” in every sense: SSTF can starve far requests, SCAN/C-SCAN trade movement for fairness. C-SCAN counts the 199→0 jump as movement.</div>' : ''}`;
  results.forEach(r => drawPath(out.querySelector(`canvas[data-alg="${r.algorithm}"]`), head, r.order, 200));
}
function drawPath(cv, head, order, total) {
  const dpr = window.devicePixelRatio || 1, W = cv.clientWidth, H = 190; cv.width = W * dpr; cv.height = H * dpr;
  const g = cv.getContext('2d'); g.scale(dpr, dpr); g.clearRect(0, 0, W, H);
  const pad = 26, pts = [head, ...order], x = b => pad + (W - 2 * pad) * b / (total - 1), y = i => 20 + (H - 40) * i / Math.max(1, pts.length - 1);
  g.strokeStyle = 'rgba(255,255,255,.15)'; g.fillStyle = 'rgba(255,255,255,.55)'; g.font = '10px sans-serif';
  [0, 50, 100, 150, 199].forEach(t => { g.beginPath(); g.moveTo(x(t), 10); g.lineTo(x(t), H - 8); g.stroke(); g.fillText(t, x(t) - 8, H - 0); });
  const grad = g.createLinearGradient(0, 0, W, 0); grad.addColorStop(0, '#7c9cff'); grad.addColorStop(1, '#c084fc');
  g.strokeStyle = grad; g.lineWidth = 2; g.beginPath(); pts.forEach((p, i) => i ? g.lineTo(x(p), y(i)) : g.moveTo(x(p), y(i))); g.stroke();
  pts.forEach((p, i) => { g.fillStyle = i === 0 ? '#34d399' : '#fff'; g.beginPath(); g.arc(x(p), y(i), 3.5, 0, 7); g.fill(); if (pts.length < 14) { g.fillStyle = 'rgba(255,255,255,.8)'; g.fillText(p, x(p) + 5, y(i) - 4); } });
}

// ---------------------------------------------------------------- admin
async function vAudit(page = 0) {
  const d = await api(`/api/audit-logs?page=${page}&size=20`);
  $('#view').innerHTML = `<div class="glass panel"><table><tr><th>#</th><th>Time</th><th>User</th><th>Event</th><th>Description</th></tr>
   ${d.rows.map(r => `<tr><td>${r.id}</td><td>${fmtTime(r.time)}</td><td>${esc(r.user)}</td><td><span class="badge">${esc(r.event)}</span></td><td>${esc(r.description)}</td></tr>`).join('')}</table>
   <div class="row" style="margin-top:14px;justify-content:space-between"><span class="muted">${d.total} events · page ${d.page + 1}/${Math.max(1, d.totalPages)}</span>
   <div class="row"><button id="pp" ${d.page === 0 ? 'disabled' : ''}>← Newer</button><button id="pn" ${d.page + 1 >= d.totalPages ? 'disabled' : ''}>Older →</button></div></div></div>`;
  $('#pp').onclick = () => vAudit(page - 1); $('#pn').onclick = () => vAudit(page + 1);
}
async function vUsers() {
  const [us, st] = await Promise.all([api('/api/admin/users'), api('/api/admin/storage')]);
  const sm = {}; st.forEach(s => sm[s.userId] = s);
  $('#view').innerHTML = `<div class="glass panel"><table><tr><th>User</th><th>Email</th><th>Role</th><th>Files</th><th>Storage</th><th></th></tr>
   ${us.map(u => `<tr><td>${esc(u.username)}</td><td>${esc(u.email)}</td><td><span class="badge ${u.role === 'ADMIN' ? 'warn' : ''}">${u.role}</span></td><td>${sm[u.id]?.files ?? 0}</td><td>${fmtBytes(sm[u.id]?.bytes ?? 0)}</td>
   <td>${u.id === ME.id ? '' : `<button class="sm" data-role="${u.id}" data-to="${u.role === 'ADMIN' ? 'USER' : 'ADMIN'}">Make ${u.role === 'ADMIN' ? 'USER' : 'ADMIN'}</button>`}</td></tr>`).join('')}</table></div>`;
  document.querySelectorAll('[data-role]').forEach(b => b.onclick = async () => { await api(`/api/admin/users/${b.dataset.role}/role`, { method: 'PUT', json: { role: b.dataset.to } }); toast('Role updated'); vUsers(); });
}

// ---------------------------------------------------------------- boot
(async () => {
  ME = await api('/api/users/me');
  $('#who').innerHTML = `👤 ${esc(ME.username)} <span class="badge ${ME.role === 'ADMIN' ? 'warn' : ''}">${esc(ME.role)}</span>`;
  if (ME.role === 'ADMIN') document.querySelectorAll('.admin').forEach(e => e.classList.remove('hidden'));
  go(location.hash.slice(1) in VIEWS ? location.hash.slice(1) : 'dash');
})();
