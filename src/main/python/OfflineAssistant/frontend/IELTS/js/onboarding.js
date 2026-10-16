/* ============================================================
 * onboarding.js — 个性化测评引导流程控制
 * 步骤：欢迎 → 问卷 → 词汇 → 阅读 → 听力 → 写作 → 口语 → AI分析 → 结果
 * ============================================================ */

const Onboarding = (() => {
  const TOTAL_STEPS = 8; // 欢迎(0) 问卷(1) 词汇(2) 阅读(3) 听力(4) 写作(5) 口语(6) AI(7) 结果(8)
  let step = 0;
  const state = {
    questionnaire: {},
    vocab: { answers: [], correct: 0, total: Assessment.VOCAB_QUESTIONS.length },
    reading: { answers: [], correct: 0, total: Assessment.READING_TEST.questions.length },
    listening: { answers: [], correct: 0, total: Assessment.LISTENING_TEST.questions.length },
    writing: { text: '', score: null },
    speaking: { text: '', score: null },
  };
  let result = null;

  // ---------- 入口 ----------
  function start() {
    // 检查是否已有 profile
    const saved = localStorage.getItem('alimigo_profile');
    if (saved) {
      try {
        const profile = JSON.parse(saved);
        applyProfile(profile);
        $('#onboarding').classList.add('hidden');
        return;
      } catch (e) { /* 解析失败，重新测评 */ }
    }
    step = 0;
    render();
  }

  // ---------- 渲染分发 ----------
  function render() {
    const content = $('#ob-content');
    const progress = (step / (TOTAL_STEPS - 1)) * 100;
    $('#ob-progress-bar').style.width = progress + '%';

    // 按钮状态
    $('#ob-prev').style.display = step > 0 && step < TOTAL_STEPS ? 'inline-block' : 'none';
    const nextBtn = $('#ob-next');

    switch (step) {
      case 0: renderWelcome(content); nextBtn.textContent = '开始测评'; break;
      case 1: renderQuestionnaire(content); nextBtn.textContent = '下一步'; break;
      case 2: renderVocab(content); nextBtn.textContent = '下一步'; break;
      case 3: renderReading(content); nextBtn.textContent = '下一步'; break;
      case 4: renderListening(content); nextBtn.textContent = '下一步'; break;
      case 5: renderWriting(content); nextBtn.textContent = '下一步'; break;
      case 6: renderSpeaking(content); nextBtn.textContent = '提交测评'; break;
      case 7: renderAnalyzing(content); break;
      case 8: renderResult(content); nextBtn.textContent = '进入主页'; break;
    }

    updateHint();
  }

  function updateHint() {
    const hints = ['', '', '20 题 · 约 3 分钟', '1 篇 · 5 题', '1 段音频 · 5 题', '1 篇作文', '1 道口语题', '', ''];
    $('#ob-hint').textContent = hints[step] || '';
  }

  // ---------- Step 0: 欢迎 ----------
  function renderWelcome(c) {
    c.innerHTML = `
      <div class="ob-welcome">
        <div class="emoji">🎯</div>
        <h2>欢迎来到 AlimigoAI 语言区</h2>
        <p>用 10 分钟完成个性化测评，AI 将根据你的真实水平生成专属雅思备考计划。</p>
        <div class="ob-features">
          <div class="ob-feature"><b>📝 水平诊断</b>词汇量 + 听说读写综合测评</div>
          <div class="ob-feature"><b>🎯 目标反推</b>自动设定合理目标分数</div>
          <div class="ob-feature"><b>📅 AI 排程</b>按空闲时段生成学习计划</div>
          <div class="ob-feature"><b>💡 薄弱分析</b>识别最弱分科并给出建议</div>
        </div>
      </div>
    `;
  }

  // ---------- Step 1: 问卷 ----------
  function renderQuestionnaire(c) {
    const q = state.questionnaire;
    c.innerHTML = `
      <h3 style="margin-bottom:18px;">📋 基础信息</h3>
      <div class="ob-question">
        <label>你的目标雅思分数？</label>
        <div class="ob-radio-group" id="q-target">
          ${[5.5, 6.0, 6.5, 7.0, 7.5, 8.0].map(s => `
            <div class="ob-radio ${q.targetScore === s ? 'active' : ''}" data-val="${s}">${s}</div>
          `).join('')}
        </div>
      </div>
      <div class="ob-question">
        <label>计划考试日期</label>
        <input type="date" id="q-date" value="${q.examDate || ''}" />
      </div>
      <div class="ob-question">
        <label>目标留学地区</label>
        <div class="ob-radio-group" id="q-region">
          ${['香港', '英国', '澳洲', '加拿大', '美国', '其他'].map(r => `
            <div class="ob-radio ${q.region === r ? 'active' : ''}" data-val="${r}">${r}</div>
          `).join('')}
        </div>
      </div>
      <div class="ob-question">
        <label>每周可投入学习小时数</label>
        <input type="number" id="q-hours" value="${q.weeklyHours || 12}" min="1" max="50" />
      </div>
      <div class="ob-question">
        <label>你目前的英语水平自评？</label>
        <div class="ob-radio-group" id="q-rating">
          ${['入门 (<4.0)', '基础 (4.0-5.0)', '中等 (5.5-6.0)', '良好 (6.5-7.0)', '优秀 (>7.0)'].map(r => `
            <div class="ob-radio ${q.selfRating === r ? 'active' : ''}" data-val="${r}">${r}</div>
          `).join('')}
        </div>
      </div>
    `;
    // 绑定单选
    c.querySelectorAll('.ob-radio-group').forEach(group => {
      group.querySelectorAll('.ob-radio').forEach(opt => {
        opt.addEventListener('click', () => {
          group.querySelectorAll('.ob-radio').forEach(o => o.classList.remove('active'));
          opt.classList.add('active');
        });
      });
    });
  }

  function collectQuestionnaire() {
    const q = state.questionnaire;
    const targetEl = document.querySelector('#q-target .ob-radio.active');
    const regionEl = document.querySelector('#q-region .ob-radio.active');
    const ratingEl = document.querySelector('#q-rating .ob-radio.active');
    q.targetScore = targetEl ? parseFloat(targetEl.dataset.val) : 6.5;
    q.examDate = $('#q-date').value;
    q.region = regionEl ? regionEl.dataset.val : '香港';
    q.weeklyHours = parseInt($('#q-hours').value) || 12;
    q.selfRating = ratingEl ? ratingEl.dataset.val : '中等 (5.5-6.0)';
    return q.targetScore && q.examDate;
  }

  // ---------- Step 2: 词汇 ----------
  function renderVocab(c) {
    const answers = state.vocab.answers;
    c.innerHTML = `
      <div class="ob-quiz-header">
        <span class="ob-q-title">📚 词汇量测试</span>
        <span class="ob-q-count">${answers.filter(a => a !== undefined).length}/${Assessment.VOCAB_QUESTIONS.length}</span>
      </div>
      <div id="vocab-list">
        ${Assessment.VOCAB_QUESTIONS.map((q, i) => `
          <div class="q-item" style="margin-bottom:16px;">
            <div class="ob-vocab-card">
              <div class="ob-vocab-word">${q.word}</div>
              <div class="ob-vocab-level">难度：${q.level}</div>
            </div>
            <div class="ob-options">
              ${q.options.map((opt, j) => `
                <div class="ob-opt ${answers[i] === j ? 'selected' : ''}" data-q="${i}" data-opt="${j}">
                  <span class="opt-letter">${String.fromCharCode(65 + j)}</span>
                  <span>${opt}</span>
                </div>
              `).join('')}
            </div>
          </div>
        `).join('')}
      </div>
    `;
    c.querySelectorAll('.ob-opt').forEach(opt => {
      opt.addEventListener('click', () => {
        const qi = +opt.dataset.q;
        const oi = +opt.dataset.opt;
        state.vocab.answers[qi] = oi;
        opt.closest('.q-item').querySelectorAll('.ob-opt').forEach(o => o.classList.remove('selected'));
        opt.classList.add('selected');
        // 更新计数
        const cnt = state.vocab.answers.filter(a => a !== undefined).length;
        $('.ob-q-count').textContent = `${cnt}/${Assessment.VOCAB_QUESTIONS.length}`;
      });
    });
  }

  // ---------- Step 3: 阅读 ----------
  function renderReading(c) {
    const answers = state.reading.answers;
    const test = Assessment.READING_TEST;
    c.innerHTML = `
      <div class="ob-quiz-header">
        <span class="ob-q-title">📖 阅读理解</span>
        <span class="ob-q-count">${test.questions.length} 题</span>
      </div>
      <div class="ob-passage">${test.passage}</div>
      ${test.questions.map((q, i) => `
        <div class="q-item" style="margin-bottom:14px;">
          <div class="q-text">${i + 1}. ${q.text}</div>
          <div class="ob-options">
            ${q.options.map((opt, j) => `
              <div class="ob-opt ${answers[i] === j ? 'selected' : ''}" data-q="${i}" data-opt="${j}">
                <span class="opt-letter">${String.fromCharCode(65 + j)}</span>
                <span>${opt}</span>
              </div>
            `).join('')}
          </div>
        </div>
      `).join('')}
    `;
    c.querySelectorAll('.ob-opt').forEach(opt => {
      opt.addEventListener('click', () => {
        const qi = +opt.dataset.q;
        state.reading.answers[qi] = +opt.dataset.opt;
        opt.closest('.q-item').querySelectorAll('.ob-opt').forEach(o => o.classList.remove('selected'));
        opt.classList.add('selected');
      });
    });
  }

  // ---------- Step 4: 听力 ----------
  let speechUtterance = null;
  function renderListening(c) {
    const answers = state.listening.answers;
    const test = Assessment.LISTENING_TEST;
    c.innerHTML = `
      <div class="ob-quiz-header">
        <span class="ob-q-title">🎧 听力理解</span>
        <span class="ob-q-count">${test.questions.length} 题</span>
      </div>
      <div class="ob-listen-box">
        <button class="ob-listen-btn" id="btn-play-audio">
          <span id="play-icon">▶</span> <span id="play-text">播放音频</span>
        </button>
        <div class="ob-listen-status" id="listen-status">点击播放，听一段图书馆介绍</div>
      </div>
      ${test.questions.map((q, i) => `
        <div class="q-item" style="margin-bottom:14px;">
          <div class="q-text">${i + 1}. ${q.text}</div>
          <div class="ob-options">
            ${q.options.map((opt, j) => `
              <div class="ob-opt ${answers[i] === j ? 'selected' : ''}" data-q="${i}" data-opt="${j}">
                <span class="opt-letter">${String.fromCharCode(65 + j)}</span>
                <span>${opt}</span>
              </div>
            `).join('')}
          </div>
        </div>
      `).join('')}
    `;
    c.querySelectorAll('.ob-opt').forEach(opt => {
      opt.addEventListener('click', () => {
        const qi = +opt.dataset.q;
        state.listening.answers[qi] = +opt.dataset.opt;
        opt.closest('.q-item').querySelectorAll('.ob-opt').forEach(o => o.classList.remove('selected'));
        opt.classList.add('selected');
      });
    });

    $('#btn-play-audio').addEventListener('click', playAudio);
  }

  function playAudio() {
    const btn = $('#btn-play-audio');
    const icon = $('#play-icon');
    const text = $('#play-text');
    const status = $('#listen-status');

    if (!('speechSynthesis' in window)) {
      status.textContent = '⚠️ 浏览器不支持语音合成，请直接阅读题目作答';
      // 显示文本
      let box = document.querySelector('.ob-listen-transcript');
      if (!box) {
        box = document.createElement('div');
        box.className = 'ob-passage';
        box.style.marginTop = '12px';
        box.textContent = Assessment.LISTENING_TEST.audio;
        $('.ob-listen-box').appendChild(box);
      }
      return;
    }

    if (speechSynthesis.speaking) {
      speechSynthesis.cancel();
      icon.textContent = '▶';
      text.textContent = '播放音频';
      status.textContent = '已停止';
      return;
    }

    speechUtterance = new SpeechSynthesisUtterance(Assessment.LISTENING_TEST.audio);
    speechUtterance.lang = 'en-US';
    speechUtterance.rate = 0.95;
    speechUtterance.onstart = () => {
      icon.textContent = '⏸';
      text.textContent = '停止播放';
      status.textContent = '正在播放...';
    };
    speechUtterance.onend = () => {
      icon.textContent = '▶';
      text.textContent = '重新播放';
      status.textContent = '播放完成';
    };
    speechSynthesis.speak(speechUtterance);
  }

  // ---------- Step 5: 写作 ----------
  function renderWriting(c) {
    c.innerHTML = `
      <div class="ob-quiz-header">
        <span class="ob-q-title">✍️ 写作测试</span>
        <span class="ob-q-count">Task 2</span>
      </div>
      <div class="ob-passage">${Assessment.WRITING_TEST_PROMPT}</div>
      <textarea class="ob-textarea" id="writing-text" placeholder="在此输入你的作文（建议 200-300 字）...">${state.writing.text}</textarea>
      <div style="margin-top:8px;font-size:12px;color:var(--muted);" id="writing-count">0 字</div>
    `;
    const ta = $('#writing-text');
    ta.addEventListener('input', () => {
      state.writing.text = ta.value;
      $('#writing-count').textContent = `${ta.value.trim().length} 字`;
    });
    $('#writing-count').textContent = `${ta.value.trim().length} 字`;
  }

  // ---------- Step 6: 口语 ----------
  let recognition = null;
  function renderSpeaking(c) {
    c.innerHTML = `
      <div class="ob-quiz-header">
        <span class="ob-q-title">🗣️ 口语测试</span>
        <span class="ob-q-count">Part 2</span>
      </div>
      <div class="ob-passage">${Assessment.SPEAKING_TEST_PROMPT.part2.replace(/\n/g, '<br>')}</div>
      <div class="ob-speech-box">
        <button class="ob-record-btn" id="btn-record">🎤</button>
        <div class="ob-speech-text" id="speech-status">点击麦克风开始录音（或在下方输入回答）</div>
        <div class="ob-speech-transcript" id="speech-transcript" style="display:none;"></div>
      </div>
      <textarea class="ob-textarea" id="speaking-text" placeholder="如果无法录音，可在此输入你的口语回答（英文）...">${state.speaking.text}</textarea>
    `;

    const ta = $('#speaking-text');
    ta.addEventListener('input', () => { state.speaking.text = ta.value; });

    $('#btn-record').addEventListener('click', toggleRecord);
  }

  function toggleRecord() {
    const btn = $('#btn-record');
    const status = $('#speech-status');
    const transcript = $('#speech-transcript');

    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) {
      status.textContent = '⚠️ 浏览器不支持语音识别，请在文本框输入回答';
      return;
    }

    if (recognition) {
      recognition.stop();
      return;
    }

    recognition = new SR();
    recognition.lang = 'en-US';
    recognition.continuous = true;
    recognition.interimResults = true;

    btn.classList.add('recording');
    status.textContent = '🔴 正在录音... 再次点击停止';
    transcript.style.display = 'block';

    recognition.onresult = (e) => {
      let text = '';
      for (let i = 0; i < e.results.length; i++) {
        text += e.results[i][0].transcript;
      }
      transcript.textContent = text;
      state.speaking.text = text;
      $('#speaking-text').value = text;
    };

    recognition.onend = () => {
      recognition = null;
      btn.classList.remove('recording');
      status.textContent = '✅ 录音完成，可继续修改文本';
    };

    recognition.onerror = (e) => {
      recognition = null;
      btn.classList.remove('recording');
      status.textContent = '录音出错：' + e.error + '，请改用文本输入';
    };

    recognition.start();
  }

  // ---------- Step 7: AI 分析 ----------
  async function renderAnalyzing(c) {
    c.innerHTML = `
      <div class="ob-loading">
        <div class="ob-spinner"></div>
        <h3>AI 正在分析你的测评结果...</h3>
        <p class="muted" style="margin-top:8px;">综合词汇量、听说读写表现，生成个性化备考方案</p>
        <div style="color:#888;font-size:12px;margin-top:8px;">
          AI 正在评分（调用本地大模型），约需 1-3 分钟，请耐心等待…
        </div>
      </div>
    `;
    $('#ob-next').style.display = 'none';
    $('#ob-prev').style.display = 'none';

    try {
      // 计算各科正确数
      state.vocab.correct = Assessment.VOCAB_QUESTIONS.reduce(
        (acc, q, i) => acc + (state.vocab.answers[i] === q.answer ? 1 : 0), 0);
      state.reading.correct = Assessment.READING_TEST.questions.reduce(
        (acc, q, i) => acc + (state.reading.answers[i] === q.answer ? 1 : 0), 0);
      state.listening.correct = Assessment.LISTENING_TEST.questions.reduce(
        (acc, q, i) => acc + (state.listening.answers[i] === q.answer ? 1 : 0), 0);

      // 写作、口语评分
      state.writing.score = await Assessment.scoreWritingRemote(
        state.writing.text,
        Assessment.WRITING_TEST_PROMPT
      );
      state.speaking.score = Assessment.scoreSpeaking(state.speaking.text);

      // AI 综合评估
      result = await Assessment.AI.assess(state);
    } catch (e) {
      console.error('AI 评估出错，使用默认值：', e);
      // 出错兜底：用问卷自评分作为当前分
      const selfMap = { '入门 (<4.0)': 4.0, '基础 (4.0-5.0)': 4.5, '中等 (5.5-6.0)': 5.5, '良好 (6.5-7.0)': 6.5, '优秀 (>7.0)': 7.0 };
      const s = selfMap[state.questionnaire.selfRating] || 5.5;
      result = {
        current: { total: s, L: s, R: s, W: s, S: s },
        target: { total: Math.min(9, s + 1), parts: { L: s + 1, R: s + 1, W: s + 1, S: s + 1 } },
        vocabulary: 4000,
        weakest: 'W',
        feedback: ['基于你的自评生成初始计划，完成练习后可获得更精准的评估'],
      };
    }

    // 保存 profile
    const profile = {
      current: result.current,
      target: result.target,
      questionnaire: state.questionnaire,
      vocabulary: result.vocabulary,
      weakest: result.weakest,
      feedback: result.feedback,
      completedAt: new Date().toISOString(),
    };
    localStorage.setItem('alimigo_profile', JSON.stringify(profile));

    // 应用到主页数据并直接进入主页
    applyProfile(profile);
    finish();
  }

  // ---------- Step 8: 结果 ----------
  function renderResult(c) {
    const r = result;
    const partName = { L: '听力', R: '阅读', W: '写作', S: '口语' };
    c.innerHTML = `
      <div class="ob-result">
        <div class="ob-r-label">你的估算雅思总分</div>
        <div class="ob-r-score">${r.current.total}</div>
        <div class="ob-r-label">词汇量约 ${r.vocabulary} 词</div>

        <div class="ob-r-parts">
          ${['L', 'R', 'W', 'S'].map(k => `
            <div class="ob-r-part">
              <div class="ob-rp-name">${partName[k]}</div>
              <div class="ob-rp-val">${r.current[k]}</div>
            </div>
          `).join('')}
        </div>

        <div class="ob-r-target">
          <h4>🎯 AI 推荐目标</h4>
          <div style="font-size:20px;font-weight:800;color:var(--primary);">${r.target.total} 分</div>
          <div class="muted" style="font-size:13px;">
            单科目标：听 ${r.target.parts.L} · 读 ${r.target.parts.R} · 写 ${r.target.parts.W} · 说 ${r.target.parts.S}
          </div>
          <div class="muted" style="font-size:12px;margin-top:6px;">
            距目标 ${(r.target.total - r.current.total).toFixed(1)} 分，预计需 ${Math.ceil((r.target.total - r.current.total) * 60)} 小时备考
          </div>
        </div>

        <div class="ob-r-feedback">
          <h4>💡 AI 学习建议</h4>
          <ul>
            ${r.feedback.map(f => `<li>${f}</li>`).join('')}
          </ul>
        </div>
      </div>
    `;
    $('#ob-next').style.display = 'inline-block';
    $('#ob-next').textContent = '进入主页';
  }

  // ---------- 将测评结果应用到主页数据 ----------
  function applyProfile(profile) {
    CURRENT_SCORE.total = profile.current.total;
    CURRENT_SCORE.L = profile.current.L;
    CURRENT_SCORE.R = profile.current.R;
    CURRENT_SCORE.W = profile.current.W;
    CURRENT_SCORE.S = profile.current.S;
    EXAM_GOAL.target_total = profile.target.total;
    EXAM_GOAL.target_parts = { ...profile.target.parts };
    if (profile.questionnaire.examDate) {
      EXAM_GOAL.exam_date = profile.questionnaire.examDate;
    }
    // 更新侧栏目标分显示
    const roleEl = $('#user-role');
    if (roleEl) roleEl.textContent = `目标 ${profile.target.total} 分`;
  }

  // ---------- 导航按钮 ----------
  function next() {
    if (step === 1) {
      if (!collectQuestionnaire()) {
        toast('请填写考试日期');
        return;
      }
    }
    if (step === 2) {
      const answered = state.vocab.answers.filter(a => a !== undefined).length;
      if (answered < Assessment.VOCAB_QUESTIONS.length) {
        if (!confirm(`还有 ${Assessment.VOCAB_QUESTIONS.length - answered} 题未答，确定继续吗？`)) return;
      }
    }
    if (step === 6) {
      // 提交测评：弱校验，不阻塞流程
      if (state.writing.text.trim().length < 30) {
        toast('作文较短，评分仅供参考');
      }
    }
    step++;
    render();
  }

  function prev() {
    if (step > 0) {
      step--;
      render();
    }
  }

  function finish() {
    $('#onboarding').classList.add('hidden');
    // 重新渲染主页
    renderCountdown();
    renderDashboard();
    // 自动生成一次计划
    $('#cfg-exam-date').value = EXAM_GOAL.exam_date;
    $('#btn-generate').click();
  }

  // 重新测评（供主页调用）
  function restart() {
    localStorage.removeItem('alimigo_profile');
    step = 0;
    state.vocab = { answers: [], correct: 0, total: Assessment.VOCAB_QUESTIONS.length };
    state.reading = { answers: [], correct: 0, total: Assessment.READING_TEST.questions.length };
    state.listening = { answers: [], correct: 0, total: Assessment.LISTENING_TEST.questions.length };
    state.writing = { text: '', score: null };
    state.speaking = { text: '', score: null };
    $('#onboarding').classList.remove('hidden');
    render();
  }

  // 绑定底部按钮
  document.addEventListener('DOMContentLoaded', () => {
    $('#ob-next').addEventListener('click', () => {
      if (step === 7) return; // 分析中不可点
      if (step === 8) { finish(); return; }
      next();
    });
    $('#ob-prev').addEventListener('click', prev);
  });

  return { start, restart };
})();
