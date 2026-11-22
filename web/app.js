'use strict';
const $ = s => document.querySelector(s);
const native = window.AndroidBridge;
let token = new URLSearchParams(location.hash.slice(1)).get('token') || '';
if (native) { try { token = JSON.parse(native.getConfig()).token; } catch (_) {} }
let page = 1, total = 0, loading = false, current = null, busyJobs = [], savedHistory = [], toastTimer;
const pictures = new Map();
const observer = new IntersectionObserver(entries => entries.forEach(e => {
  if (e.isIntersecting) { observer.unobserve(e.target); loadImage(e.target, e.target.dataset.id); }
}), {rootMargin:'200px'});

async function api(path, body) {
  const response = await fetch(path, {headers:{Authorization:'Bearer '+token,...(body?{'Content-Type':'application/json'}:{})},method:body?'POST':'GET',body:body?JSON.stringify(body):undefined});
  const data = await response.json();
  if (!response.ok) throw Error(data.error || '连接失败，请检查电脑服务。');
  return data;
}
function el(tag, className, text) { const x=document.createElement(tag); if(className)x.className=className; if(text!==undefined)x.textContent=text; return x; }
function toast(text) { clearTimeout(toastTimer); $('#toast').textContent=text; $('#toast').classList.remove('hidden'); toastTimer=setTimeout(()=>$('#toast').classList.add('hidden'),4500); }
function bytes(n) { if(!n)return '大小未知'; const u=['B','KB','MB','GB']; const i=Math.min(3,Math.floor(Math.log(n)/Math.log(1024))); return (n/1024**i).toFixed(i>1?1:0)+' '+u[i]; }
function date(n) { return n?new Date(n*1000).toLocaleDateString('zh-CN'):''; }
function type(item) { return item.tags.find(t=>['Video','Scene','Web','Application'].includes(t)) || '作品'; }
const typeNames={Video:'视频',Scene:'场景',Web:'网页',Application:'应用'};
async function loadImage(img,id) {
  try {
    if(!pictures.has(id)) {
      const r=await fetch('/api/preview/'+id,{headers:{Authorization:'Bearer '+token}});
      if(!r.ok) return;
      pictures.set(id,URL.createObjectURL(await r.blob()));
    }
    img.src=pictures.get(id);
  } catch(_) {}
}
function showPage(name) {
  document.querySelectorAll('.page').forEach(p=>p.classList.toggle('hidden',p.id!==name));
  document.querySelectorAll('.nav-item').forEach(b=>b.classList.toggle('active',b.dataset.page===name));
  if(name==='transfers')refreshJobs(); if(name==='saved')renderSaved();
  window.scrollTo(0,0);
}
function queryString() {
  const q=new URLSearchParams({q:$('#search').value.trim(),sort:$('#sort').value,days:$('#days').value,page:String(page)});
  ['#type','#category','#resolution'].forEach(id=>{if($(id).value)q.append('tag',$(id).value);});
  $('#extra-tags').value.split(/[,，]/).map(t=>t.trim()).filter(Boolean).forEach(t=>q.append('tag',t));
  if($('#date-from').value)q.set('from',$('#date-from').value);
  if($('#date-to').value)q.set('to',$('#date-to').value);
  return q;
}
async function search(reset=true) {
  if(loading)return;
  loading=true; $('#load-more').disabled=true;
  if(reset) {
    page=1; observer.disconnect(); $('#cards').replaceChildren();
    pictures.forEach(url=>URL.revokeObjectURL(url));pictures.clear();
    for(let i=0;i<6;i++)$('#cards').append(el('div','skeleton'));
  }
  $('#message').classList.add('hidden');
  try {
    const data=await api('/api/catalog?'+queryString()); total=data.total;
    if(reset)$('#cards').replaceChildren();
    data.items.forEach(item=>$('#cards').append(card(item)));
    $('#result-count').textContent=Number(total).toLocaleString()+' 个结果';
    $('#load-more').classList.toggle('hidden',!data.items.length || page*50>=total);
    if(!total) {$('#message').textContent='没有找到符合条件的作品，试试减少筛选条件。';$('#message').className='message';}
    document.querySelectorAll('[data-sort]').forEach(b=>b.classList.toggle('active',b.dataset.sort===$('#sort').value));
  } catch(e) {if(reset)$('#cards').replaceChildren();$('#message').textContent=e.message;$('#message').className='message error';$('#load-more').classList.add('hidden');}
  finally {loading=false;$('#load-more').disabled=false;}
}
function card(item) {
  const c=el('article','card');c.tabIndex=0;c.setAttribute('role','button');
  const preview=el('div','preview'), img=el('img');img.alt=item.title;img.dataset.id=item.id;observer.observe(img);
  preview.append(img,el('span','type-label',typeNames[type(item)]||type(item)));
  const body=el('div','card-body'),meta=el('div','card-meta');
  body.append(el('h3','',item.title));meta.append(el('span','',bytes(item.size)),el('span','',date(item.created)));body.append(meta);c.append(preview,body);
  c.onclick=()=>detail(item);c.onkeydown=e=>{if(e.key==='Enter')detail(item);};return c;
}
function detail(item) {
  current=item;$('#detail-title').textContent=item.title;$('#detail-description').textContent=item.description||'暂无介绍';
  $('#detail-tags').textContent=item.tags.join(' · ');$('#detail-info').textContent=bytes(item.size)+' · 发布 '+date(item.created)+' · 更新 '+date(item.updated);
  $('#detail-image').removeAttribute('src');loadImage($('#detail-image'),item.id);
  const video=item.tags.some(t=>t.toLowerCase()==='video');$('#detail-download').disabled=!video;$('#detail-download').textContent=video?'保存原始视频':'这个类型没有可直接保存的视频';
  $('#detail').showModal();
}
async function createJob(item) {
  try {
    const job=await api('/api/jobs',{item_id:item.id});$('#detail').close();showPage('transfers');
    if(native)native.saveVideo(job.id);else toast('已交给电脑准备，请在 Android App 中保存。');
    refreshJobs();
  } catch(e){toast(e.message);}
}
function button(label,action,style='secondary') {const b=el('button','button '+style,label);b.onclick=action;return b;}
async function refreshJobs() {
  if(!token)return;
  try {busyJobs=(await api('/api/jobs')).jobs;$('#connection').textContent='● 电脑已连接';$('#connection').classList.add('online');renderJobs();}
  catch(_) {$('#connection').textContent='○ 电脑未连接';$('#connection').classList.remove('online');}
}
const states={queued:'等待电脑处理',downloading:'Steam 正在下载',preparing:'正在准备并校验视频',ready:'准备完成，等待保存',error:'需要处理',cancelled:'已取消'};
function renderJobs() {
  const list=$('#job-list');list.replaceChildren();$('#job-badge').textContent=busyJobs.length||'';
  let local=[];try {local=native?JSON.parse(native.getHistory()):[];}catch(_){}
  if(!busyJobs.length)list.append(el('div','empty','还没有传输任务。发现喜欢的视频后，点击“保存原始视频”。'));
  busyJobs.slice().reverse().forEach(j=>{
    const box=el('article','job');box.append(el('h3','',j.title));
    const record=local.find(r=>r.id===j.id),label=record?.state==='saving'?'正在保存到手机':record?.saved?'已保存到手机，等待电脑清理':states[j.state]||j.state;
    box.append(el('p','',label));
    if(j.total&&j.state==='downloading'){const p=el('progress','progress');p.max=j.total;p.value=j.received;box.append(p,el('p','',bytes(j.received)+' / '+bytes(j.total)));}
    if(j.error)box.append(el('p','',j.error));if(j.note)box.append(el('p','',j.note));
    const actions=el('div','job-actions');
    if(record?.saved&&!record.cleaned&&native)actions.append(button('重试电脑清理',()=>native.retryCleanup(j.id),'primary'));
    else if(j.state==='ready') {const b=button(native?'保存到相册':'请在手机 App 中保存',()=>native&&native.saveVideo(j.id),'primary');b.disabled=!native||record?.state==='saving';actions.append(b);}
    if(!record?.saved)actions.append(button('取消并清理',async()=>{try{const r=await api('/api/jobs/'+j.id+'/cancel',{});toast(r.note);refreshJobs();}catch(e){toast(e.message);}}));
    box.append(actions);list.append(box);
  });
}
function renderSaved() {
  const list=$('#saved-list');list.replaceChildren();
  try {savedHistory=native?JSON.parse(native.getHistory()):[];}catch(_){savedHistory=[];}
  const entries=savedHistory.filter(x=>x.saved);
  if(!entries.length)list.append(el('div','empty',native?'还没有已保存的视频。':'已保存记录只在 Android App 中显示。'));
  entries.reverse().forEach(record=>{
    const box=el('article','job');box.append(el('h3','',record.name),el('p','',bytes(record.size)+' · '+(record.cleaned?'电脑任务已清理':'电脑清理待确认')));
    if(record.note)box.append(el('p','',record.note));
    const actions=el('div','job-actions');actions.append(button('打开视频',()=>native.openVideo(record.id),'primary'));
    if(!record.cleaned)actions.append(button('重试电脑清理',()=>native.retryCleanup(record.id)));
    box.append(actions);list.append(box);
  });
}
window.onNativeUpdate=()=>{refreshJobs();renderSaved();};
async function pairing() {
  try {
    const p=await fetch('/api/pairing').then(r=>{if(!r.ok)throw Error();return r.json();});
    token=p.token;$('#pairing').classList.remove('hidden');
    p.addresses.forEach(ip=>{
      const url='http://'+ip+':'+p.port+'/#token='+p.token;
      const row=el('div','pair-link');row.append(el('code','',url),button('复制',async()=>{try{await navigator.clipboard.writeText(url);toast('连接地址已复制');}catch(_){toast('请手动复制连接地址');}}));$('#pair-links').append(row);
    });
  }catch(_){$('#message').textContent='请在 Android App 中输入电脑提供的连接地址。';$('#message').className='message';}
}
$('#search-form').onsubmit=e=>{e.preventDefault();search();};
$('#filter-toggle').onclick=()=>{const hidden=$('#filters').classList.toggle('hidden');$('#filter-toggle').setAttribute('aria-expanded',String(!hidden));};
$('#reset').onclick=()=>{$('#search-form').reset();search();};
$('#load-more').onclick=()=>{page++;search(false);};
document.querySelectorAll('[data-sort]').forEach(b=>b.onclick=()=>{$('#sort').value=b.dataset.sort;search();});
document.querySelectorAll('[data-page]').forEach(b=>b.onclick=()=>showPage(b.dataset.page));
$('#close-detail').onclick=()=>$('#detail').close();$('#detail-download').onclick=()=>current&&createJob(current);
$('#connection').onclick=()=>{if(native)native.disconnect();else pairing();};
(async()=>{if(!token)await pairing();if(token){refreshJobs();search();setInterval(refreshJobs,3000);}})();
