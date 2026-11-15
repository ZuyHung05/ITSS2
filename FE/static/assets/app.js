'use strict';
const $ = (q, root = document) => root.querySelector(q);
const $$ = (q, root = document) => [...root.querySelectorAll(q)];
function errorText(error) {
  if(error instanceof TypeError && /fetch|network|load failed/i.test(error.message)) return 'Không thể kết nối máy chủ. Hãy kiểm tra mạng và thử lại.';
  return translateServerText(error?.message) || 'Đã xảy ra lỗi. Vui lòng thử lại.';
}
const E = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const TZ = 'Asia/Ho_Chi_Minh';
const fmt = value => value ? new Intl.DateTimeFormat('vi-VN',{timeZone:TZ,day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(value)) : '—';
const dateFmt = value => value ? new Intl.DateTimeFormat('vi-VN',{timeZone:TZ,day:'2-digit',month:'2-digit',year:'numeric'}).format(new Date(`${value}T12:00:00+07:00`)) : '—';
const localValue = value => value ? new Date(new Date(value).getTime()+7*3600000).toISOString().slice(0,16) : '';
// Mã trạng thái dùng cho API/DB; chỉ nhãn hiển thị được Việt hóa.
const STATUS_LABELS = {
  TODO:'Chưa bắt đầu', IN_PROGRESS:'Đang thực hiện', DONE:'Đã hoàn thành',
  DRAFT:'Bản nháp', CONFIRMED:'Đã xác nhận', ACHIEVED:'Đạt mục tiêu', NOT_ACHIEVED:'Chưa đạt mục tiêu',
  PENDING:'Chờ chốt tuần', GREEN:'Đạt mục tiêu tuần', YELLOW:'Cần theo dõi', RED:'Thiếu báo cáo', GRAY:'Không có việc đến hạn',
  OPEN:'Đang mở', CLOSED:'Đã chốt', NOT_YET_DUE:'Chưa đến kỳ', RESOLVED:'Đã xử lý', LEAD:'Trưởng nhóm', MEMBER:'Thành viên'
};
const statusLabel = value => STATUS_LABELS[value] || value || '';
const badge = value => `<span class="badge ${E(value)}">${E(statusLabel(value))}</span>`;
const taskUrl = t => `/projects/${t.projectId}/tasks/${t.id}`;
const query = new URLSearchParams(location.search);
const segments = location.pathname.split('/').filter(Boolean);
const projectId = segments[1];
let session, projects = [], project, dashboardCache, renderCurrent;
let toastTimer;
function toast(message,error=false) { const el=$('#toast');el.textContent=message;el.className=error?'error':'';el.hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.hidden=true,6000); }
async function api(url, options={}) {
  const headers={'Accept':'application/json',...options.headers};
  if(options.body) headers['Content-Type']='application/json';
  if(session) headers[session.csrfHeader]=session.csrfToken;
  const res=await fetch(url,{credentials:'same-origin',...options,headers});
  if(res.status===401) { location.href='/login';throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'); }
  const body=await res.json().catch(()=>null);
  if(!res.ok || !body?.success) throw new Error(translateServerText(body?.error?.message) || `Yêu cầu thất bại (${res.status}). Vui lòng thử lại.`);
  return body.data;
}
async function act(button, operation) {
  if(button?.disabled) return;
  if(button) {button.disabled=true;button.setAttribute('aria-busy','true');}
  try {await operation();} catch(error) {toast(errorText(error),true);} finally {if(button){button.disabled=false;button.removeAttribute('aria-busy');}}
}
function modal(title, content, bind) {$('#modal-body').innerHTML=`<h2>${E(title)}</h2>${content}`;$('#modal').showModal();bind?.($('#modal-body'));}
function closeModal() {$('#modal').close();}
function heading(kicker,title,description='',actions='') { return `<div class="page-heading"><div><p class="eyebrow">${E(kicker)}</p><h1>${E(title)}</h1><p class="muted">${E(description)}</p></div><div class="heading-actions">${actions}</div></div>`; }
function empty(title,text='') {return `<div class="empty"><h3>${E(title)}</h3><p>${E(text)}</p></div>`;}
function taskRows(tasks) {return tasks.length?tasks.map(t=>`<div class="task-row"><div><h3><a href="${taskUrl(t)}">${E(t.title)}</a></h3><p class="small muted">${E(t.assigneeName)} · ${fmt(t.deadline)}</p></div><div class="actions">${badge(t.status)} ${badge(t.planningStatus)}</div></div>`).join(''):empty('Chưa có nội dung','Các công việc đã giao sẽ xuất hiện tại đây.');}
function historicNotice(d) {return d.historicalApproximation?'<div class="notice warning">Dữ liệu tuần trước được tính theo trạng thái và lịch công việc hiện tại, nên không phản ánh chính xác thời điểm hoàn thành hoặc việc hoàn thành đúng hạn trong quá khứ.</div>':'';}
function planFields(t={}) {return `<label>Mô tả<textarea name="description" maxlength="10000">${E(t.description)}</textarea></label><label>Kết quả cần bàn giao<textarea name="expectedOutput" required maxlength="10000">${E(t.expectedOutput)}</textarea></label><div class="form-row"><label>Ngày bắt đầu<input type="date" name="startDate" required value="${E(t.startDate)}"></label><label>Hạn hoàn thành · giờ Việt Nam<input type="datetime-local" name="deadline" required value="${localValue(t.deadline)}"></label></div>`;}
function planBody(form) {const b=Object.fromEntries(new FormData(form));b.deadline=`${b.deadline}:00+07:00`;return b;}
async function createTask() {
  const members=await api(`/api/projects/${projectId}/members`);
  const requestId=crypto.randomUUID();
  if(!members.length) return toast('Vui lòng thêm thành viên vào dự án trước khi giao công việc.',true);
  modal('Tạo công việc',`<div class="notice">Lịch ban đầu là tạm thời. Thành viên được giao việc sẽ đề xuất lịch phù hợp và trưởng nhóm xác nhận.</div><form id="create-task"><label>Tiêu đề<input name="title" required maxlength="200"></label><label>Giao cho<select name="assigneeId">${members.map(m=>`<option value="${m.id}">${E(m.name)} (${E(statusLabel(m.role))})</option>`).join('')}</select></label>${planFields()}<button class="primary" type="submit">Tạo công việc →</button></form>`,root=>{
    $('#create-task',root).onsubmit=e=>{e.preventDefault();act($('button[type=submit]',e.target),async()=>{const t=await api(`/api/projects/${projectId}/tasks`,{method:'POST',body:JSON.stringify({...planBody(e.target),requestId})});location.href=taskUrl(t);});};
  });
}
async function renderProjects() {
  $('#app').innerHTML=heading('KHÔNG GIAN LÀM VIỆC','Cùng rõ việc, an tâm mỗi tuần.','Chọn dự án để xem các công việc và kế hoạch của nhóm.')+`<div class="project-grid">${projects.length?projects.map(p=>`<a class="project-card" href="/projects/${p.id}">${badge(p.role)}<h2>${E(p.name)}</h2><p class="muted">${p.deadline?'Hạn hoàn thành dự án '+fmt(p.deadline):'Không gian làm việc chung cho nhóm.'}</p><span>Mở không gian làm việc →</span></a>`).join(''):empty('Bạn chưa tham gia dự án nào','Hãy liên hệ trưởng nhóm hoặc quản trị viên để được thêm vào dự án.')}</div>`;
}
async function getDashboard(date) {return api(`/api/projects/${projectId}/dashboard${date?`?checkpointDate=${encodeURIComponent(date)}`:''}`);}
function shiftDate(date,days) {const next=new Date(`${date}T12:00:00Z`);next.setUTCDate(next.getUTCDate()+days);return next.toISOString().slice(0,10);}
function vietnamDate(value) {
  const parts=new Intl.DateTimeFormat('en-GB',{timeZone:TZ,day:'2-digit',month:'2-digit',year:'numeric'}).formatToParts(new Date(value));
  const part=type=>parts.find(p=>p.type===type).value;
  return `${part('year')}-${part('month')}-${part('day')}`;
}
function overdueDays(task) {
  if(task.status==='DONE'||new Date(task.deadline)>=new Date(session.serverNow)) return 0;
  const today=vietnamDate(session.serverNow),deadline=vietnamDate(task.deadline);
  return Math.max(0,Math.round((Date.parse(today+'T00:00:00Z')-Date.parse(deadline+'T00:00:00Z'))/86400000));
}
function taskPriority(task) {
  const due=new Date(task.deadline),now=new Date(session.serverNow);
  if(due<now) return 0;
  if(due.getTime()-now.getTime()<=7*86400000) return 1;
  if(task.status==='IN_PROGRESS') return 2;
  return 3;
}
function focusTask(task) {
  const late = new Date(task.deadline) < new Date(session.serverNow), days = overdueDays(task);
  const diffBadge = task.difficulty ? `<span class="badge difficulty-${task.difficulty.toLowerCase()}">${task.difficulty === 'EASY' ? 'Dễ' : task.difficulty === 'HARD' ? 'Khó' : 'Trung bình'}</span>` : '';
  return `<article class="focus-task ${late ? 'is-overdue' : ''}">
    <div class="focus-task-top">
      <div class="focus-title-wrap">
        <h3><a href="${taskUrl(task)}">${E(task.title)}</a></h3>
      </div>
      ${late ? `<span class="overdue-label">Quá hạn ${days ? `${days} ngày` : 'hôm nay'}</span>` : ''}
    </div>
    <p class="focus-deadline">🕒 Hạn hoàn thành: ${fmt(task.deadline)}</p>
    <div class="focus-status">
      ${badge(task.status)}
      ${diffBadge}
      ${task.planningStatus === 'DRAFT' ? '<span class="planning-note">Chờ duyệt kế hoạch</span>' : '<span class="planning-confirmed">Kế hoạch đã duyệt</span>'}
    </div>
    <div class="focus-actions">
      <a class="button btn-task-open" href="${taskUrl(task)}">Mở công việc →</a>
      <button class="button btn-task-help help-action" type="button" data-help-task="${task.id}" data-help-title="${E(task.title)}">🤝 Yêu cầu hỗ trợ</button>
    </div>
  </article>`;
}
function bindHelpActions() {$$('[data-help-task]').forEach(button=>button.addEventListener('click',()=>openHelp(button.dataset.helpTask,button.dataset.helpTitle)));}
function openHelp(taskId,title) {
  modal('Yêu cầu hỗ trợ',`<form id="help-form"><label>Công việc<input type="text" value="${E(title)}" readonly></label><label>Lý do khó khăn<textarea name="reason" required maxlength="5000" placeholder="Bạn đang gặp khó khăn gì?"></textarea></label><label>Điều bạn cần trưởng nhóm hỗ trợ<textarea name="requestedHelp" maxlength="5000" placeholder="Nêu nội dung bạn cần hỗ trợ hoặc đề xuất giải pháp"></textarea></label><p class="small muted">Yêu cầu này được gửi riêng đến trưởng nhóm. Trưởng nhóm sẽ trao đổi và quyết định nếu cần đăng phần việc hỗ trợ lên nhóm.</p><button class="primary" type="submit">Gửi yêu cầu riêng cho TL</button></form>`,root=>{$('#help-form',root).onsubmit=e=>{e.preventDefault();act($('button[type=submit]',e.target),async()=>{await api(`/api/tasks/${taskId}/support-requests`,{method:'POST',body:JSON.stringify(Object.fromEntries(new FormData(e.target)))});closeModal();toast('Đã gửi yêu cầu hỗ trợ đến trưởng nhóm.');});};});
}
async function renderDashboard() {
  const current = await getDashboard(); dashboardCache = current;
  const active = current.closed ? await getDashboard(shiftDate(current.checkpointDate, 7)) : current;
  const previous = current.closed ? current : await getDashboard(shiftDate(current.checkpointDate, -7));
  const mine = active.members.find(m => m.member.id === session.id);
  const earlier = previous.members.find(m => m.member.id === session.id);
  const openTasks = active.myTasks.filter(t => t.status !== 'DONE').sort((a,b) => taskPriority(a) - taskPriority(b) || new Date(a.deadline) - new Date(b.deadline));
  const taskSection = (title, tasks) => `<div class="task-group"><h3>${title}</h3><div class="focus-list">${tasks.map(focusTask).join('')}</div></div>`;
  let taskContent;
  if (!openTasks.length) taskContent = empty('Bạn đã xử lý hết công việc đang mở.', 'Công việc mới sẽ xuất hiện tại đây khi được phân công.');
  else if (openTasks.length <= 6) taskContent = `<div class="focus-list">${openTasks.map(focusTask).join('')}</div>`;
  else {
    const attention = openTasks.filter(t => taskPriority(t) < 3), upcoming = openTasks.filter(t => taskPriority(t) === 3);
    taskContent = (attention.length ? taskSection('Cần chú ý', attention) : '') + (upcoming.length ? taskSection('Sắp tới', upcoming) : '');
  }

  const overdue = openTasks.filter(t => new Date(t.deadline) < new Date(session.serverNow)).length;
  const missing = mine?.missing || 0;
  const required = mine?.tasks.filter(w => w.reportRequired).length || 0;
  const previousMissing = previous.closed ? (earlier?.missing || 0) : 0;
  const canReport = vietnamDate(session.serverNow) === active.checkpointDate && !active.closed;
  const reportLink = `/projects/${projectId}/checkpoint?date=${active.checkpointDate}`;

  // 1. BANNER BÁO CÁO TUẦN: 3 TRẠNG THÁI MÀU (ĐỎ / XANH DƯƠNG / XANH LÁ)
  let bannerClass, bannerKicker, bannerMessage, bannerDetail, bannerActions;
  if (overdue > 0 || previousMissing > 0) {
    // Trạng thái ĐỎ (overdue / nợ deadline)
    bannerClass = 'checkpoint-overdue';
    bannerKicker = '⚠️ Báo cáo tuần này · Cảnh báo quá hạn';
    bannerMessage = `Bạn có ${overdue || 1} công việc đã quá hạn · Còn chậm deadline ${overdue || 1} việc.`;
    bannerDetail = `Task chưa hoàn thành từ kỳ trước. ${missing ? `Bạn còn ${missing} công việc cần cập nhật trong tuần này.` : 'Hãy xử lý công việc quá hạn để đảm bảo tiến độ.'}`;
    bannerActions = `
      ${canReport && missing ? `<a class="button btn-pill-danger" href="${reportLink}">Xử lý báo cáo →</a>` : ''}
      <a class="button btn-pill-secondary" href="/projects/${projectId}/tasks?filter=overdue">Xem việc quá hạn</a>
      ${previousMissing ? `<a class="link-subtle" href="/projects/${projectId}/team-progress?date=${previous.checkpointDate}">Kỳ trước</a>` : ''}
    `;
  } else if (missing > 0) {
    // Trạng thái XANH DƯƠNG (còn việc cần cập nhật trước hạn - tránh trùng màu xanh lá)
    bannerClass = 'checkpoint-pending';
    bannerKicker = '🕒 Báo cáo tuần này · Chờ cập nhật';
    bannerMessage = `Bạn còn ${missing} công việc cần cập nhật trước hạn.`;
    bannerDetail = `Các công việc đến hạn trong tuần cần được cập nhật trước 22:00 thứ Sáu (nhắc nhở lúc 15:00).`;
    bannerActions = `
      <a class="button btn-pill-primary" href="${reportLink}">Báo cáo tuần →</a>
      ${!canReport ? '<span class="checkpoint-window">Mở nộp vào thứ Sáu · nhắc lúc 15:00</span>' : ''}
    `;
  } else if (required === 0) {
    bannerClass = 'checkpoint-empty';
    bannerKicker = '📅 Báo cáo tuần này';
    bannerMessage = `Không có công việc cần báo cáo trong kỳ này.`;
    bannerDetail = `Bạn vẫn có thể cập nhật tiến độ công việc và kế hoạch khi cần.`;
    bannerActions = `<a class="button btn-pill-secondary" href="/projects/${projectId}/tasks">Xem công việc →</a>`;
  } else {
    // Trạng thái XANH LÁ (đã cập nhật đầy đủ)
    bannerClass = 'checkpoint-complete';
    bannerKicker = '✅ Báo cáo tuần này · Đã hoàn thành';
    bannerMessage = `Bạn đã cập nhật đầy đủ`;
    bannerDetail = `Không còn báo cáo cần nộp trong kỳ hiện tại. Kết quả đang chờ TL kiểm tra.`;
    bannerActions = `
      <a class="button btn-pill-success" href="/projects/${projectId}/checkpoint?date=${active.checkpointDate}">Xem lại báo cáo →</a>
      ${previous.closed ? `<a class="link-subtle" href="/projects/${projectId}/team-progress?date=${previous.checkpointDate}">Kỳ trước</a>` : ''}
    `;
  }

  // 2. BẢNG TIẾN ĐỘ NHÓM & KHUNG TÊN THÀNH VIÊN THEO 4 MÀU (XANH / VÀNG / ĐỎ / XÁM)
  const displayCohort = active.members.some(m => m.due > 0) ? active : previous;
  const cohortMembers = displayCohort.members || [];
  const memberCardsHtml = cohortMembers.map(m => {
    const tasks = m.tasks || [];
    const achieved = tasks.filter(w => w.task.status === 'DONE' || w.report?.result === 'ACHIEVED').length;
    const late = tasks.filter(w => w.overdue || (w.task.status !== 'DONE' && new Date(w.task.deadline) < new Date(session.serverNow))).length;
    const missingReports = m.missing || 0;
    const total = m.due || tasks.length;
    
    let colorState = 'GRAY', statusText = 'Chưa có task đến hạn', statusPill = 'Chưa có việc';
    if (total === 0) {
      colorState = 'GRAY';
      statusText = 'Không có task đến hạn trong kỳ này';
      statusPill = 'Chưa có việc';
    } else if (late >= 3 || missingReports >= 3 || (displayCohort.closed && m.state === 'RED')) {
      colorState = 'RED';
      statusText = `Chậm ${late || missingReports} task · Nguy cơ bỏ / Cần TL xử lý`;
      statusPill = `Trễ ${late || missingReports} task`;
    } else if (late > 0 || missingReports > 0 || (displayCohort.closed && m.state === 'YELLOW')) {
      colorState = 'YELLOW';
      statusText = `Chậm ${late || missingReports} task`;
      statusPill = `Chậm ${late || missingReports} task`;
    } else if (achieved === total || (displayCohort.closed && m.state === 'GREEN') || (m.state === 'GREEN')) {
      colorState = 'GREEN';
      statusText = `Hoàn thành đầy đủ (${achieved}/${total} việc)`;
      statusPill = 'Đạt mục tiêu';
    } else {
      colorState = 'GREEN';
      statusText = `Đang đúng tiến độ (${achieved}/${total} việc)`;
      statusPill = 'Đúng hạn';
    }

    return `
      <details class="team-pulse-card state-${colorState.toLowerCase()}">
        <summary class="team-pulse-summary">
          <div class="team-pulse-user">
            <span class="avatar avatar-${colorState.toLowerCase()}">${E(m.member.name.slice(0, 2).toUpperCase())}</span>
            <div class="team-pulse-name-wrap">
              <strong class="team-pulse-name">${E(m.member.name)}</strong>
              <span class="team-pulse-sub">${statusText}</span>
            </div>
          </div>
          <span class="team-pulse-pill pill-${colorState.toLowerCase()}">${statusPill}</span>
        </summary>
        <div class="team-pulse-task-list">
          ${tasks.length ? tasks.map(w => `
            <div class="pulse-task-row ${w.overdue ? 'is-late' : ''}">
              <div class="pulse-task-main">
                <a href="${taskUrl(w.task)}">${E(w.task.title)}</a>
                <span class="pulse-task-date">Hạn: ${fmt(w.task.deadline)}${w.overdue ? ' · <strong class="text-danger">Quá hạn</strong>' : ''}</span>
              </div>
              <div class="pulse-task-tags">
                ${badge(w.task.status)}
                <span class="pulse-report-tag">${w.task.status === 'DONE' ? 'Đã hoàn thành' : w.report ? E(statusLabel(w.report.result)) : 'Chưa có báo cáo'}</span>
              </div>
            </div>
          `).join('') : '<p class="muted small">Không có công việc nào trong kỳ này.</p>'}
        </div>
      </details>
    `;
  }).join('');

  const summaryCounts = cohortMembers.reduce((sum, m) => {
    sum.due += m.due;
    if (m.state === 'GREEN') sum.green++;
    else if (m.state === 'YELLOW') sum.yellow++;
    else if (m.state === 'RED') sum.red++;
    else sum.gray++;
    return sum;
  }, { due: 0, green: 0, yellow: 0, red: 0, gray: 0 });

  const pulseSummary = summaryCounts.due ? 
    `<span class="badge-dot dot-green"></span> ${summaryCounts.green} hoàn thành đầy đủ · <span class="badge-dot dot-yellow"></span> ${summaryCounts.yellow} chậm 1-2 task · <span class="badge-dot dot-red"></span> ${summaryCounts.red} chậm nhiều / nguy cơ` :
    'Không có công việc đến hạn trong kỳ này.';

  // 3. THANH HÀNH ĐỘNG DÀNH RIÊNG CHO TRƯỞNG NHÓM (TL)
  const leadBar = project.role === 'LEAD' ? `
    <div class="lead-action-bar">
      <div class="lead-bar-info">
        <span class="lead-badge">⚡ Trưởng nhóm</span>
        <span>Quản lý kế hoạch tuần, duyệt báo cáo sau checkpoint và xử lý yêu cầu hỗ trợ.</span>
      </div>
      <div class="lead-bar-links">
        <a class="button btn-lead-action" href="/projects/${projectId}/review?tab=drafts">Duyệt kế hoạch nháp →</a>
        <a class="button btn-lead-action" href="/projects/${projectId}/review?tab=help">Yêu cầu hỗ trợ →</a>
        <a class="button btn-lead-action" href="/projects/${projectId}/review?tab=weekly">Kiểm tra báo cáo tuần →</a>
      </div>
    </div>
  ` : '';

  $('#app').innerHTML =
    heading('', 'Không gian làm việc', `Chào mừng trở lại! Kỳ báo cáo hiện tại chốt lúc 22:00 thứ Sáu ${dateFmt(active.checkpointDate)}.`, project.role === 'LEAD' ? '<button class="primary" id="new-task">＋ Tạo công việc</button>' : '') +
    leadBar +
    `<section class="checkpoint-card ${bannerClass}">
      <div class="checkpoint-top-bar">
        <span class="section-kicker">${bannerKicker}</span>
        <span class="checkpoint-time-badge">${dateFmt(active.checkpointDate)} · 22:00</span>
      </div>
      <div class="checkpoint-main-content">
        <div class="checkpoint-copy">
          <h2 class="checkpoint-message">${bannerMessage}</h2>
          <p class="checkpoint-detail">${bannerDetail}</p>
        </div>
        <div class="checkpoint-actions">
          ${bannerActions}
        </div>
      </div>
    </section>` +
    `<section class="section-block team-pulse-section">
      <div class="section-heading">
        <div>
          <h2>Tiến độ nhóm</h2>
          <p class="section-subtitle">${pulseSummary}</p>
        </div>
        <a class="view-all-link" href="/projects/${projectId}/team-progress?date=${displayCohort.checkpointDate}">Xem bảng chi tiết →</a>
      </div>
      <div class="team-pulse-grid">
        ${memberCardsHtml || empty('Chưa có thành viên trong dự án')}
      </div>
    </section>` +
    `<section class="section-block my-work">
      <div class="section-heading">
        <div>
          <h2>Công việc của tôi</h2>
          <p class="section-subtitle">Những việc bạn cần chú ý và hoàn thành trong tuần này.</p>
        </div>
        <a class="view-all-link" href="/projects/${projectId}/tasks">Xem tất cả công việc →</a>
      </div>
      ${taskContent}
    </section>`;

  bindHelpActions();
  $('#new-task')?.addEventListener('click', e => act(e.currentTarget, createTask));
}
async function renderTasks() {
  const scope=query.get('scope')==='team'&&project.role==='LEAD'?'team':'mine';
  const [tasks,d]=await Promise.all([api(`/api/projects/${projectId}/tasks?scope=${scope}`),getDashboard()]);
  const dueIds=new Set(d.members.flatMap(m=>m.tasks.map(w=>w.task.id)));
  $('#app').innerHTML=heading('KẾ HOẠCH CÔNG VIỆC',scope==='team'?'Công việc của nhóm':'Công việc của tôi','Đề xuất lịch thực hiện phù hợp và cùng trưởng nhóm xác nhận.',project.role==='LEAD'?'<button class="primary" id="new-task">＋ Tạo công việc</button>':'')+
    (project.role==='LEAD'?`<div class="filters"><a class="button" href="?scope=mine">Công việc của tôi</a><a class="button" href="?scope=team">Công việc của nhóm</a><a class="button" href="/projects/${projectId}/review?tab=drafts">Kế hoạch nháp cần duyệt</a></div>`:'')+
    `<div class="filters" id="task-filters">${['Tất cả','Bản nháp','Đã xác nhận','Đến hạn tuần này','Quá hạn','Đã hoàn thành'].map((f,i)=>`<button data-filter="${E(f)}" class="${!i?'selected':''}">${E(f)}</button>`).join('')}</div><div class="task-grid" id="task-list"></div>`;
  function list(filter='Tất cả') {
    const shown=tasks.filter(t=>filter==='Tất cả'||filter==='Bản nháp'&&t.planningStatus==='DRAFT'||filter==='Đã xác nhận'&&t.planningStatus==='CONFIRMED'||filter==='Đến hạn tuần này'&&dueIds.has(t.id)||filter==='Quá hạn'&&t.status!=='DONE'&&new Date(t.deadline)<new Date(session.serverNow)||filter==='Đã hoàn thành'&&t.status==='DONE');
    $('#task-list').innerHTML=shown.length?shown.map(t=>`<article class="task-card"><div class="actions">${badge(t.planningStatus)} ${badge(t.status)}</div><h3><a href="${taskUrl(t)}">${E(t.title)}</a></h3><p class="small muted">${E(t.assigneeName)} · ${E(t.description?.slice(0,140)||'Chưa có mô tả.')}</p><p class="output">${E(t.expectedOutput||'Chưa có kết quả cần bàn giao.')}</p><p class="small"><span class="muted">Bắt đầu</span> ${dateFmt(t.startDate)}<br><span class="muted">Đến hạn</span> ${fmt(t.deadline)}</p><div class="actions"><a class="button" href="${taskUrl(t)}">Mở công việc →</a>${t.planningStatus==='DRAFT'&&t.assigneeId===session.id?`<a href="${taskUrl(t)}?edit=1">Sửa đề xuất</a>`:''}</div></article>`).join(''):empty('Không có công việc trong danh sách này','Hãy chọn bộ lọc khác hoặc trao đổi với trưởng nhóm về công việc tiếp theo.');
  }
  const initialFilter=query.get('filter')==='overdue'?'Quá hạn':'Tất cả';
  list(initialFilter);$$('[data-filter]').forEach(b=>{b.classList.toggle('selected',b.dataset.filter===initialFilter);b.onclick=()=>{$$('[data-filter]').forEach(x=>x.classList.toggle('selected',x===b));list(b.dataset.filter);};});
  $('#new-task')?.addEventListener('click',e=>act(e.currentTarget,createTask));
}
function teamStateLabel(state,closed) {
  if(!closed) return 'Đang cập nhật';
  return ({GREEN:'Hoàn thành tốt',YELLOW:'Cần chú ý',RED:'Thiếu báo cáo',GRAY:'Không có việc đến hạn'})[state]||'Đã chốt';
}
function teamProgressRows(members,closed) {
  return members.map(m=>{
    const achieved=m.tasks.filter(w=>w.task.status==='DONE'||w.report?.result==='ACHIEVED').length;
    const state=closed?m.state:'OPEN';
    return `<details class="team-member" data-state="${E(state)}"><summary><span class="avatar">${E(m.member.name.slice(0,2).toUpperCase())}</span><span class="team-member-name"><strong>${E(m.member.name)}</strong><small>${achieved}/${m.due} công việc hoàn tất hoặc đạt mục tiêu</small></span><span class="team-member-status">${E(teamStateLabel(m.state,closed))}</span><span class="expand-label">Xem công việc</span></summary><div class="team-member-tasks">${m.tasks.length?m.tasks.map(w=>`<article class="team-task"><div><a href="${taskUrl(w.task)}">${E(w.task.title)}</a><p>Hạn hoàn thành: ${fmt(w.task.deadline)}${w.overdue?' · Quá hạn':''}</p></div><div class="team-task-states"><span>${E(statusLabel(w.task.status))}</span><span>${w.task.status==='DONE'?'Đã hoàn thành':w.report?E(statusLabel(w.report.result)):'Chưa nộp báo cáo'}</span></div></article>`).join(''):'<p class="muted">Không có công việc cần báo cáo trong kỳ này.</p>'}</div></details>`;
  }).join('');
}
async function renderTeamProgress() {
  const date=query.get('date');const d=await getDashboard(date);dashboardCache=d;
  const stateText=d.closed?'Đã chốt':'Đang mở';
  const counts=d.members.reduce((sum,m)=>({due:sum.due+m.due,completed:sum.completed+m.tasks.filter(w=>w.task.status==='DONE'||w.report?.result==='ACHIEVED').length,missing:sum.missing+m.missing}),{due:0,completed:0,missing:0});
  $('#app').innerHTML=heading('Không gian làm việc','Tiến độ nhóm','Tình hình công việc của nhóm trong kỳ báo cáo đã chọn.')+
    `<div class="team-week-bar"><div><strong>Thứ Sáu, ${dateFmt(d.checkpointDate)}</strong><span class="period-state ${d.closed?'is-final':''}">${stateText}</span></div><span>Chốt báo cáo lúc 22:00 · Giờ Việt Nam</span></div>${datePicker(d.checkpointDate)}${historicNotice(d)}`+
    `<div class="team-summary"><span><strong>${counts.due}</strong> công việc đến hạn</span><span><strong>${counts.completed}</strong> đã hoàn thành</span><span><strong>${counts.missing}</strong> báo cáo còn thiếu</span></div>`+
    `<section class="team-members" aria-label="Tiến độ từng thành viên">${d.members.length?teamProgressRows(d.members,d.closed):empty('Chưa có thành viên trong dự án')}</section>`;
  bindDatePicker();
}
async function renderTask() {
  const detail=await api(`/api/tasks/${segments[3]}`),t=detail.task,lead=detail.role==='LEAD';
  const checkpoint=await api(`/api/projects/${projectId}/checkpoint`);
  const eligible=checkpoint.tasks.some(w=>w.task.id===t.id);
  $('#app').innerHTML=`<p><a href="/projects/${projectId}/tasks">← Quay lại công việc của tôi</a></p>`+heading('CHI TIẾT CÔNG VIỆC',t.title,`Người thực hiện: ${t.assigneeName}`)+
    `<div class="notice ${t.planningStatus==='DRAFT'?'warning':''}">${t.planningStatus==='DRAFT'?'Chưa được xác nhận. Lịch hiện tại là tạm thời và có thể thay đổi. Trưởng nhóm có thể xem đề xuất trong danh sách kế hoạch nháp cần duyệt.':`Được xác nhận bởi ${E(t.confirmedByName)} vào ${fmt(t.confirmedAt)}. Thành viên có thể cập nhật trạng thái thực hiện; lịch công việc đã được khóa.`}</div>`+
    `<div class="two-col"><section class="panel"><div class="panel-head"><h2>Kế hoạch đã thống nhất</h2><div class="actions">${badge(t.status)} ${badge(t.planningStatus)}</div></div><div class="detail-grid"><div><span class="detail-label">Ngày bắt đầu</span><p>${dateFmt(t.startDate)}</p></div><div><span class="detail-label">Hạn hoàn thành · giờ Việt Nam</span><p>${fmt(t.deadline)}</p></div></div><span class="detail-label">Kết quả cần bàn giao</span><p class="long-text">${E(t.expectedOutput||'Chưa cung cấp')}</p><span class="detail-label">Mô tả</span><p class="long-text">${E(t.description||'Chưa có mô tả bổ sung.')}</p><div class="actions">${t.planningStatus==='DRAFT'&&(detail.owner||lead)?'<button id="edit-plan">Sửa kế hoạch nháp</button>':''}${lead&&t.planningStatus==='DRAFT'?'<button class="primary" id="confirm-plan">Xác nhận kế hoạch</button>':''}${lead&&t.planningStatus==='CONFIRMED'?'<button id="reopen-plan">Mở lại bản nháp</button>':''}${detail.owner?'<button id="need-help">Yêu cầu hỗ trợ</button>':''}</div></section><section class="panel"><h2>Trạng thái thực hiện</h2>${(detail.owner||lead)&&t.planningStatus==='CONFIRMED'?`<form id="status-form"><label>Trạng thái<select name="status">${['TODO','IN_PROGRESS','DONE'].map(s=>`<option value="${s}" ${s===t.status?'selected':''}>${E(statusLabel(s))}</option>`).join('')}</select></label>${!detail.owner?'<label>Lý do trưởng nhóm điều chỉnh<textarea name="rationale" required maxlength="2000"></textarea></label><p class="small muted">Lý do điều chỉnh được gửi cho người thực hiện qua thông báo trong ứng dụng. Hệ thống chưa lưu lịch sử kiểm toán đầy đủ.</p>':''}<button type="submit">Cập nhật trạng thái</button></form>`:'<p class="muted">Người thực hiện hoặc trưởng nhóm có thể cập nhật trạng thái sau khi kế hoạch được xác nhận.</p>'}${eligible?`<a class="button primary" href="/projects/${projectId}/checkpoint">Mở báo cáo tuần →</a>`:''}<p class="small muted">Đánh dấu hoàn thành không tự tạo báo cáo tuần và không thay đổi hạn hoàn thành.</p></section></div>`+
    `<section class="panel"><h2>Lịch sử báo cáo tuần</h2>${detail.owner||lead?(detail.reports.length?detail.reports.map(r=>`<div class="task-row"><div><h3>${dateFmt(r.checkpointDate)} · ${badge(r.result)}</h3><p class="long-text">${E(r.note||'Chưa có ghi chú')}</p>${evidenceLink(r.evidenceUrl)}<p class="small muted">Đã báo cáo ${fmt(r.reportedAt)} · Kết quả do thành viên tự báo cáo</p></div></div>`).join(''):empty('Chưa có báo cáo','Công việc đã hoàn thành được hiển thị riêng với báo cáo tuần.')):'<p class="muted">Nội dung báo cáo chi tiết chỉ dành cho người thực hiện và trưởng nhóm. Các thành viên khác xem tóm tắt trên trang tổng quan.</p>'}</section>`+
    `<section class="panel"><h2>Yêu cầu hỗ trợ</h2><p class="small muted">Chỉ người gửi yêu cầu và trưởng nhóm dự án được xem nội dung.</p>${detail.supportRequests.length?detail.supportRequests.map(s=>`<div class="support"><h3>${badge(s.status)} ${fmt(s.createdAt)}</h3><p class="long-text">${E(s.reason)}</p><p class="muted long-text">${E(s.requestedHelp)}</p>${s.leaderNote?`<p class="notice long-text">Ghi chú của trưởng nhóm: ${E(s.leaderNote)}</p>`:''}</div>`).join(''):empty('Chưa có yêu cầu hỗ trợ cho công việc này')}</section>`;
  function edit() {modal('Sửa kế hoạch nháp',`<form id="plan-form">${planFields(t)}<p class="small muted">Sau khi lưu, công việc vẫn ở trạng thái bản nháp và xuất hiện trong danh sách trưởng nhóm cần duyệt.</p><button class="primary" type="submit">Lưu bản nháp · Gửi trưởng nhóm xác nhận</button></form>`,root=>{$('#plan-form',root).onsubmit=e=>{e.preventDefault();act($('button[type=submit]',e.target),async()=>{await api(`/api/tasks/${t.id}/plan`,{method:'PATCH',body:JSON.stringify(planBody(e.target))});closeModal();toast('Đã lưu bản nháp. Trưởng nhóm có thể xem trong danh sách kế hoạch cần duyệt.');await renderTask();});};});}
  $('#edit-plan')?.addEventListener('click',edit);
  if(query.get('edit')==='1'&&t.planningStatus==='DRAFT'&&(detail.owner||lead)) {query.delete('edit');history.replaceState(null,'',location.pathname);edit();}
  $('#confirm-plan')?.addEventListener('click',e=>act(e.currentTarget,async()=>{if(!confirm('Xác nhận kết quả cần bàn giao và lịch thực hiện này? Thành viên sẽ không thể tự sửa kế hoạch sau khi xác nhận.'))return;await api(`/api/tasks/${t.id}/confirm`,{method:'POST'});await renderTask();toast('Đã xác nhận kế hoạch.');}));
  $('#reopen-plan')?.addEventListener('click',e=>act(e.currentTarget,async()=>{if(!confirm('Mở lại kế hoạch đã xác nhận thành bản nháp để thành viên xem và điều chỉnh?'))return;await api(`/api/tasks/${t.id}/reopen`,{method:'POST'});await renderTask();toast('Đã mở lại bản nháp.');}));
  $('#status-form')?.addEventListener('submit',e=>{e.preventDefault();act($('button',e.target),async()=>{const b=Object.fromEntries(new FormData(e.target));if(b.status==='DONE'&&!confirm('Đánh dấu công việc đã hoàn thành? Thao tác này không tự nộp báo cáo tuần.'))return;await api(`/api/tasks/${t.id}/status`,{method:'PATCH',body:JSON.stringify(b)});await renderTask();toast('Đã cập nhật trạng thái công việc.');});});
  $('#need-help')?.addEventListener('click',()=>openHelp(t.id,t.title));
}
function evidenceLink(url) {try{const u=new URL(url);return ['http:','https:'].includes(u.protocol)?`<a href="${E(u.href)}" target="_blank" rel="noopener noreferrer">Xem minh chứng ↗</a>`:'';}catch{return '';}}
function datePicker(date) {return `<form class="date-picker" id="week-picker"><label>Kỳ báo cáo thứ Sáu<input type="date" name="date" value="${E(date)}" required></label><button type="submit">Xem tuần</button></form>`;}
function bindDatePicker() {$('#week-picker').onsubmit=e=>{e.preventDefault();const d=new FormData(e.target).get('date');if(new Date(`${d}T12:00:00+07:00`).getUTCDay()!==5)return toast('Vui lòng chọn ngày thứ Sáu.',true);query.set('date',d);location.search=query.toString();};}
async function renderCheckpoint() {
  const d=await api(`/api/projects/${projectId}/checkpoint${query.has('date')?`?date=${encodeURIComponent(query.get('date'))}`:''}`),open=d.window==='OPEN';
  $('#app').innerHTML=heading('BÁO CÁO TUẦN',`Thứ Sáu, ${dateFmt(d.checkpointDate)}`,'Chia sẻ tình hình thực tế bằng một cập nhật ngắn gọn, trung thực.',badge(d.window))+
    `<div class="notice">Giờ hiện tại: ${fmt(session.serverNow)}. Hạn nộp: ${fmt(d.cutoff)} (giờ Việt Nam). Bạn có thể nộp và sửa báo cáo vào thứ Sáu trước 22:00.</div>${datePicker(d.checkpointDate)}`+
    (d.window==='CLOSED'?'<div class="notice warning">Kỳ báo cáo đã chốt. Báo cáo chưa nộp vẫn được ghi nhận là chưa nộp. Bạn có thể yêu cầu hỗ trợ hoặc liên hệ trưởng nhóm; hệ thống không nhận báo cáo muộn.</div>':d.window==='NOT_YET_DUE'?'<div class="notice">Báo cáo sẽ mở vào thứ Sáu đã chọn. Bạn vẫn có thể cập nhật trạng thái công việc và yêu cầu hỗ trợ bất cứ lúc nào.</div>':'')+
    (d.tasks.length?d.tasks.map(w=>`<section class="panel"><div class="panel-head"><h2><a href="${taskUrl(w.task)}">${E(w.task.title)}</a></h2>${badge(w.task.status)}</div><p class="small muted">Hạn hoàn thành công việc: ${fmt(w.task.deadline)} ${w.overdue?'· Đã quá hạn hoàn thành':''}</p><p class="small muted">Kết quả báo cáo và việc trễ hạn là hai thông tin khác nhau. Chưa đạt mục tiêu vào thứ Sáu không đồng nghĩa với trễ hạn công việc đến hạn Chủ nhật.</p>${d.previousReports[w.task.id]?`<p class="small muted">Kỳ báo cáo trước: ${dateFmt(d.previousReports[w.task.id].checkpointDate)} · ${E(statusLabel(d.previousReports[w.task.id].result))}</p>`:''}<form class="report-form" data-task="${w.task.id}"><label>Kết quả báo cáo<select name="result" ${!open?'disabled':''} required><option value="">Chọn kết quả</option>${['ACHIEVED','NOT_ACHIEVED'].map(r=>`<option value="${r}" ${r===w.report?.result?'selected':''}>${E(statusLabel(r))}</option>`).join('')}</select></label><label>Ghi chú kết quả / lý do chưa đạt mục tiêu<textarea name="note" maxlength="5000" ${!open?'readonly':''}>${E(w.report?.note)}</textarea></label><label>Đường dẫn minh chứng (không bắt buộc)<input type="url" name="evidenceUrl" maxlength="2000" value="${E(w.report?.evidenceUrl)}" ${!open?'readonly':''}></label>${open?'<label class="checkbox"><input type="checkbox" name="markDone">Tôi xác nhận công việc đã xong và muốn đánh dấu đã hoàn thành.</label><button class="primary" type="submit">Lưu báo cáo</button>':''}<span class="report-state small muted">${w.report?'Đã nộp · '+fmt(w.report.reportedAt):'Chưa nộp'}</span></form></section>`).join(''):empty('Không có công việc cần báo cáo trong tuần này','Công việc đến hạn từ tuần sau chưa bắt buộc báo cáo trong thứ Sáu này.'))+
    `<section class="panel"><h2>Đã hoàn thành công việc (${d.completed.length})</h2><p class="small muted">Các công việc này không cần nộp thêm báo cáo. Hệ thống chưa lưu thời điểm hoàn thành, nên không thể kết luận công việc đã hoàn thành đúng hạn.</p>${d.completed.length?d.completed.map(w=>`<div class="task-row"><a href="${taskUrl(w.task)}">${E(w.task.title)}</a><span>${w.report?badge(w.report.result):'Đã hoàn thành công việc / chưa có báo cáo tuần'}</span></div>`).join(''):empty('Chưa có công việc hoàn thành trong kỳ')}</section>`;
  bindDatePicker();
  $$('.report-form').forEach(form=>{
    const result=$('[name=result]',form),note=$('[name=note]',form);
    const required=()=>{note.required=result.value==='NOT_ACHIEVED';};result.onchange=required;required();
    form.onsubmit=e=>{e.preventDefault();act($('button',form),async()=>{const b=Object.fromEntries(new FormData(form));if(b.markDone&&b.result!=='ACHIEVED')throw new Error('Vui lòng chọn đạt mục tiêu trước khi xác nhận công việc đã hoàn thành.');await api(`/api/tasks/${form.dataset.task}/reports/${d.checkpointDate}`,{method:'PUT',body:JSON.stringify({result:b.result,note:b.note,evidenceUrl:b.evidenceUrl})});$('.report-state',form).textContent='Đã nộp · '+fmt(new Date());toast('Đã lưu báo cáo tuần.');if(b.markDone){await api(`/api/tasks/${form.dataset.task}/status`,{method:'PATCH',body:JSON.stringify({status:'DONE'})});await renderCheckpoint();}});};
  });
}
async function renderReview() {
  const r=await api(`/api/projects/${projectId}/review${query.has('date')?`?date=${encodeURIComponent(query.get('date'))}`:''}`),d=r.dashboard;
  const tab=['drafts','weekly','help'].includes(query.get('tab'))?query.get('tab'):'drafts';
  $('#app').innerHTML=heading('TRƯỞNG NHÓM','Cùng phối hợp để hoàn thành.','Duyệt kế hoạch, xem các cập nhật còn thiếu và hỗ trợ thành viên.')+
    `<div class="tabs">${[['drafts',`Kế hoạch nháp (${r.drafts.length})`],['weekly',`Báo cáo tuần (${d.issues})`],['help',`Yêu cầu hỗ trợ (${d.openSupportRequests})`]].map(([id,label])=>`<button data-tab="${id}" class="${tab===id?'selected':''}">${E(label)}</button>`).join('')}</div><div id="review-tab"></div>`;
  function show(id) {
    query.set('tab',id);history.replaceState(null,'',`${location.pathname}?${query}`);$$('[data-tab]').forEach(b=>b.classList.toggle('selected',b.dataset.tab===id));
    if(id==='drafts') $('#review-tab').innerHTML=`<section class="panel"><h2>Kế hoạch nháp cần duyệt</h2><p class="muted small">Bản nháp bao gồm lịch tạm thời ban đầu và đề xuất của thành viên. Hãy trao đổi về kết quả cần bàn giao và lịch thực hiện trước khi xác nhận.</p>${taskRows(r.drafts)}</section>`;
    if(id==='weekly') {
      $('#review-tab').innerHTML=`${datePicker(d.checkpointDate)}${historicNotice(d)}<div class="notice">${d.closed?'Kỳ báo cáo đã chốt':'Đang cập nhật / chờ chốt báo cáo'} · Thứ Sáu ${dateFmt(d.checkpointDate)} lúc 22:00. Báo cáo chưa nộp và kết quả chưa đạt mục tiêu được hiển thị riêng.</div><label class="checkbox"><input type="checkbox" id="only-issues">Chỉ xem các vấn đề báo cáo</label><section class="team-members" id="review-table">${teamProgressRows(d.members,d.closed)}</section>${session.demo?'<section class="panel"><h3>Mô phỏng kỳ báo cáo</h3><p class="small muted">Chạy mô phỏng cho thứ Sáu đã chọn. Chức năng chỉ có trong chế độ dùng thử, tạo thông báo thật trong ứng dụng và không tạo trùng khi chạy lại.</p><div class="actions"><button data-job="remind">Gửi nhắc báo cáo lúc 15:30</button><button data-job="finalize">Gửi tổng kết lúc 22:00</button></div></section>':''}`;
      bindDatePicker();$('#only-issues').onchange=e=>$('#review-table').innerHTML=teamProgressRows(e.target.checked?d.members.filter(m=>m.tasks.some(w=>w.reportRequired&&(!w.report||w.report.result==='NOT_ACHIEVED'))):d.members,d.closed);
      $$('[data-job]').forEach(b=>b.onclick=()=>act(b,async()=>{if(!confirm('Chạy mô phỏng và tạo thông báo trong ứng dụng?'))return;const result=await api(`/api/projects/${projectId}/demo-jobs?date=${d.checkpointDate}&job=${b.dataset.job}`,{method:'POST'});toast(`${result.created} thông báo đã được tạo.`);await refreshNotifications();}));
    }
    if(id==='help') {
      $('#review-tab').innerHTML=`<section class="panel"><h2>Yêu cầu hỗ trợ riêng tư</h2><p class="muted small">Trao đổi với người gửi và ghi lại phản hồi. Hệ thống không tự chuyển công việc cho người khác.</p>${r.supportRequests.length?r.supportRequests.map(s=>`<article class="support"><details ${s.status==='OPEN'?'open':''}><summary>${E(s.requesterName)} · ${E(s.taskTitle)} ${badge(s.status)}</summary><p class="small muted">${fmt(s.createdAt)}</p><p class="long-text">${E(s.reason)}</p><span class="detail-label">Nội dung cần hỗ trợ</span><p class="long-text">${E(s.requestedHelp||'Chưa cung cấp')}</p><a href="/projects/${projectId}/tasks/${s.taskId}">Xem công việc →</a>${s.status==='OPEN'?`<form class="resolve-form" data-id="${s.id}"><label>Phản hồi / ghi chú của trưởng nhóm (không bắt buộc)<textarea name="leaderNote" maxlength="5000">${E(s.leaderNote)}</textarea></label><button type="submit" class="primary">Đánh dấu đã xử lý</button></form>`:`<div class="notice long-text">${E(s.leaderNote||'Trưởng nhóm đã đánh dấu yêu cầu được xử lý.')}<br><span class="small">${fmt(s.resolvedAt)}</span></div>`}<button disabled title="Chức năng của nhóm B chưa được triển khai">Tạo lời mời hỗ trợ tự nguyện</button><span class="help-handoff">Giải pháp 2 chưa được triển khai. Thông tin tích hợp: taskId=${E(s.taskId)}, projectId=${E(projectId)}, supportRequestId=${E(s.id)}.</span></details></article>`).join(''):empty('Chưa có yêu cầu hỗ trợ','Yêu cầu sẽ xuất hiện tại đây khi người thực hiện cần hỗ trợ.')}</section>`;
      $$('.resolve-form').forEach(form=>form.onsubmit=e=>{e.preventDefault();act($('button',form),async()=>{if(!confirm('Đánh dấu yêu cầu đã được xử lý và thông báo cho người gửi?'))return;await api(`/api/support-requests/${form.dataset.id}/resolve`,{method:'PATCH',body:JSON.stringify(Object.fromEntries(new FormData(form)))});await renderReview();toast('Đã xử lý yêu cầu hỗ trợ.');});});
    }
  }
  $$('[data-tab]').forEach(b=>b.onclick=()=>show(b.dataset.tab));show(tab);
}
function notificationUrl(n) {
  const root=`/projects/${n.projectId}`;
  if(n.type==='CHECKPOINT_REMINDER')return `${root}/checkpoint?date=${n.checkpointDate}`;
  if(n.type==='CHECKPOINT_ALERT')return `${root}/review?tab=weekly&date=${n.checkpointDate}`;
  if(n.type==='NEED_HELP')return `${root}/review?tab=help`;
  return `${root}/tasks`;
}
function notificationTitle(n) {
  if(n.type==='CHECKPOINT_REMINDER') return 'Nhắc nộp báo cáo tuần';
  if(n.type==='CHECKPOINT_ALERT') return 'Báo cáo tuần cần trưởng nhóm xem xét';
  return (n.title || '').replace(/^Need Help: /,'Yêu cầu hỗ trợ: ')
    .replace(/^Need Help resolved$/,'Yêu cầu hỗ trợ đã được xử lý')
    .replace(/^Task status updated by TL$/,'Trưởng nhóm đã cập nhật trạng thái công việc');
}
function notificationMessage(n) {
  let message=n.message || '';
  const reminder=message.match(/^(\d+) unfinished due task\(s\) still need your report before 22:00 Vietnam time\.$/);
  if(reminder) return `Bạn còn ${reminder[1]} công việc đến hạn chưa hoàn thành cần báo cáo trước 22:00 giờ Việt Nam.`;
  const alert=message.match(/^(\d+) missing report\(s\), (\d+) NOT_ACHIEVED report\(s\)\./);
  if(alert) return `Còn ${alert[1]} báo cáo chưa nộp và ${alert[2]} báo cáo chưa đạt mục tiêu. Hãy kiểm tra riêng hạn hoàn thành thực tế của từng công việc.`;
  if(n.type==='HELP_UPDATE' && n.title==='Task status updated by TL') return message.replace(/ → (TODO|IN_PROGRESS|DONE)\. Rationale: /, (_,status)=>` → ${statusLabel(status)}. Lý do: `);
  return message.replace(' has asked for TL support. Open Team Review to see the private request.',' đã gửi yêu cầu hỗ trợ. Mở mục duyệt và hỗ trợ để xem nội dung riêng tư.')
    .replace('The TL marked your request resolved.','Trưởng nhóm đã đánh dấu yêu cầu của bạn được xử lý.');
}
async function refreshNotifications() {
  const data=await api('/api/notifications');$('#unread').textContent=data.unread;$('#unread').hidden=!data.unread;
  $('#notification-drawer').innerHTML=`<div class="panel-head"><h2>Thông báo</h2><button class="icon-btn" id="close-drawer" aria-label="Đóng thông báo">×</button></div><p class="small muted">20 thông báo gần nhất. Danh sách tự cập nhật mỗi phút.</p>${data.items.length?data.items.map(n=>`<article class="notification ${n.readAt?'':'unseen'}"><h3>${E(notificationTitle(n))}</h3><p>${E(notificationMessage(n))}</p><p class="small muted">${fmt(n.createdAt)}</p><div class="actions"><a class="notification-open" data-id="${n.id}" href="${notificationUrl(n)}">Mở →</a>${!n.readAt?`<button class="text-btn mark-read" data-id="${n.id}">Đánh dấu đã đọc</button>`:''}</div></article>`).join(''):empty('Chưa có thông báo','Thông báo nhắc báo cáo và cập nhật của nhóm sẽ xuất hiện tại đây.')}`;
  $('#close-drawer').onclick=()=>$('#notification-drawer').hidden=true;
  $$('.mark-read').forEach(b=>b.onclick=()=>act(b,async()=>{await api(`/api/notifications/${b.dataset.id}/read`,{method:'PATCH'});await refreshNotifications();}));
  $$('.notification-open').forEach(a=>a.onclick=async e=>{e.preventDefault();try{await api(`/api/notifications/${a.dataset.id}/read`,{method:'PATCH'});location.href=a.href;}catch(error){toast(errorText(error),true);}});
}
async function load() {
  try {await renderCurrent();} catch(error) {$('#app').innerHTML=heading('KHÔNG GIAN LÀM VIỆC','Không thể tải trang này.',errorText(error))+`<button id="retry">Thử lại</button>`;$('#retry').onclick=load;}
}
async function init() {
  try {
    session=await api('/api/session');projects=await api('/api/projects');project=projects.find(p=>p.id===projectId);
    const logoutForm=$('.header-actions form');
    if(logoutForm&&!logoutForm.querySelector('[name="_csrf"]')) {
      const token=document.createElement('input');token.type='hidden';token.name='_csrf';token.value=session.csrfToken;logoutForm.append(token);
    }
    $('#user-name').textContent=session.name;$('#local-date').textContent=new Intl.DateTimeFormat('vi-VN',{timeZone:TZ,day:'2-digit',month:'2-digit',year:'numeric'}).format(new Date(session.serverNow));
    $('#menu-toggle').onclick=()=>$('#sidebar').classList.toggle('open');$('#close-modal').onclick=closeModal;
    $('#notification-toggle').onclick=()=>act($('#notification-toggle'),async()=>{const drawer=$('#notification-drawer');drawer.hidden=!drawer.hidden;if(!drawer.hidden)await refreshNotifications();});
    if(!projectId) {renderCurrent=renderProjects;$('#nav').innerHTML='<a class="active" href="/projects">◈ Dự án</a>';}
    else {
      if(!project)throw new Error('Bạn không có quyền truy cập dự án này.');
      $('#project-label').textContent=project.name;
      const active=segments[2]||'dashboard';
      const navigation=[['dashboard','◈','Không gian làm việc',''],['tasks','▤','Công việc của tôi','/tasks'],['checkpoint','◷','Báo cáo tuần','/checkpoint'],['team-progress','◉','Tiến độ nhóm','/team-progress'],...(project.role==='LEAD'?[['review','☷','Quản lý nhóm','/review']]:[])];
      $('#breadcrumb').textContent=navigation.find(([key])=>key===active)?.[2]||'Không gian làm việc';
      $('#nav').innerHTML=navigation.map(([key,icon,label,path])=>`<a class="${active===key?'active':''}" href="/projects/${projectId}${path}"><span>${icon}</span>${label}</a>`).join('');
      renderCurrent=active==='team-progress'?renderTeamProgress:segments[3]?renderTask:active==='tasks'?renderTasks:active==='checkpoint'?renderCheckpoint:active==='review'?renderReview:renderDashboard;
    }
    await load();await refreshNotifications();setInterval(()=>refreshNotifications().catch(()=>{}),60000);
  } catch(error) {$('#app').innerHTML=heading('KHÔNG GIAN LÀM VIỆC','Không thể mở không gian làm việc',errorText(error))+'<a class="button" href="/projects">Quay lại danh sách dự án</a>';}
}
init();
