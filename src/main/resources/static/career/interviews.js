(function () {
  "use strict";
  const API_BASE = window.location.protocol === "file:" ? "http://localhost:8080" : "";
  const store = window.CareerInterviewStore;
  const labels = { clarity:"表达清晰", evidence:"事实证据", relevance:"岗位关联", reflection:"反思改进" };
  const list = document.querySelector("#session-list");
  const questionList = document.querySelector("#question-list");
  const empty = document.querySelector("#interview-empty");
  const content = document.querySelector("#interview-content");
  const status = document.querySelector("#interview-status");
  let active = null;

  function setStatus(message, error) { status.className = error ? "form-status error" : "form-status success"; status.textContent = message; }
  function renderSessions() {
    const items = store.load(window.localStorage);
    document.querySelector("#session-count").textContent = `${items.length} 场`;
    list.replaceChildren();
    if (!items.length) { const p=document.createElement("p"); p.className="copy-empty"; p.textContent="还没有练习记录。"; list.appendChild(p); return; }
    items.forEach(item => {
      const button=document.createElement("button"); button.type="button"; button.className="session-item";
      button.classList.toggle("active", active && active.id===item.id);
      const title=document.createElement("strong"); title.textContent=item.positionTitle;
      const meta=document.createElement("span"); meta.textContent=item.companyName || "未填写公司";
      button.append(title,meta); button.addEventListener("click",()=>selectSession(item)); list.appendChild(button);
    });
  }
  function scoreOptions(question, dimension) {
    const wrapper=document.createElement("label"); wrapper.textContent=labels[dimension];
    const select=document.createElement("select"); select.innerHTML='<option value="0">未评分</option><option value="1">1</option><option value="2">2</option><option value="3">3</option><option value="4">4</option><option value="5">5</option>';
    select.value=String(question.ratings[dimension]);
    select.addEventListener("change",()=>{ question.ratings[dimension]=Number(select.value); saveActive(); });
    wrapper.appendChild(select); return wrapper;
  }
  function renderQuestions() {
    questionList.replaceChildren();
    active.questions.forEach((question,index)=>{
      const article=document.createElement("article"); article.className="practice-question";
      const badge=document.createElement("span"); badge.className="question-category"; badge.textContent=`${index+1} · ${question.category}`;
      const prompt=document.createElement("h3"); prompt.textContent=question.prompt;
      const answerLabel=document.createElement("label"); answerLabel.textContent="回答要点";
      const answer=document.createElement("textarea"); answer.rows=5; answer.maxLength=5000; answer.value=question.answer; answer.placeholder="记录事实、你的行动和可以验证的结果";
      answer.addEventListener("change",()=>{ question.answer=answer.value; saveActive(); }); answerLabel.appendChild(answer);
      const ratings=document.createElement("div"); ratings.className="rating-grid"; store.DIMENSIONS.forEach(name=>ratings.appendChild(scoreOptions(question,name)));
      const improveLabel=document.createElement("label"); improveLabel.textContent="下一次具体改进";
      const improvement=document.createElement("input"); improvement.maxLength=1200; improvement.value=question.improvement; improvement.placeholder="例如：先给结论，再补充两项数据";
      improvement.addEventListener("change",()=>{ question.improvement=improvement.value; saveActive(); }); improveLabel.appendChild(improvement);
      article.append(badge,prompt,answerLabel,ratings,improveLabel); questionList.appendChild(article);
    });
  }
  function renderReview() {
    const review=store.buildReview(active); const panel=document.querySelector("#review-summary"); panel.replaceChildren();
    const progress=document.createElement("p"); progress.textContent=`已完成 ${review.answeredCount}/${review.totalCount} 题`;
    const score=document.createElement("p"); const values=store.DIMENSIONS.filter(name=>review.averages[name]).map(name=>`${labels[name]} ${review.averages[name]}/5`); score.textContent=values.length?values.join(" · "):"完成回答并自评后，这里会汇总练习重点。";
    const next=document.createElement("p"); next.textContent=review.priority?`优先改进：${labels[review.priority]}`:"";
    panel.append(progress,score); if(next.textContent) panel.appendChild(next);
  }
  function saveActive() { active=store.saveSession(window.localStorage,active); renderReview(); renderSessions(); setStatus("练习记录已保存在当前浏览器"); }
  function selectSession(session) {
    active=store.normalizeSession(session); empty.hidden=true; content.hidden=false;
    document.querySelector("#interview-company").textContent=active.companyName || "Interview practice";
    document.querySelector("#interview-position").textContent=active.positionTitle;
    renderQuestions(); renderReview(); renderSessions();
  }
  function create(job) { const session=store.createSession(window.localStorage,job); selectSession(session); setStatus("已生成 6 道原创通用练习问题"); }
  document.querySelector("#new-session-form").addEventListener("submit",event=>{ event.preventDefault(); try { create({ companyName:document.querySelector("#session-company").value, positionTitle:document.querySelector("#session-position").value }); event.target.reset(); } catch(error){setStatus(error.message,true);} });
  document.querySelector("#delete-session").addEventListener("click",()=>{ if(active&&window.confirm("删除这场面试练习？")){store.removeSession(window.localStorage,active.id);active=null;content.hidden=true;empty.hidden=false;renderSessions();} });
  document.querySelector("#copy-review").addEventListener("click",async()=>{ if(!active)return; try{await navigator.clipboard.writeText(store.reviewText(active,labels));setStatus("复盘已复制");}catch(_error){setStatus("复制失败，请手动选择复盘文字",true);} });
  async function loadSelectedJob() {
    const jobId=new URLSearchParams(window.location.search).get("job"); if(!jobId||!/^[0-9]+$/.test(jobId))return;
    try { const response=await fetch(`${API_BASE}/api/v1/career/jobs/${encodeURIComponent(jobId)}`); const payload=await response.json(); if(!response.ok||!payload.success)throw new Error(); create(payload.data); }
    catch(_error){setStatus("无法读取所选岗位，你仍可以手动新建练习。",true);}
  }
  renderSessions(); loadSelectedJob();
})();
