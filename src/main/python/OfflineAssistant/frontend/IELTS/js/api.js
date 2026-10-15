/**
 * OfflineAssistant API 客户端
 * 统一封装后端调用、字段映射、降级处理
 */
// 同源：前端由 Spring Boot 静态托管，后端接口与页面同端口（8080）
const API_BASE = '/api/v1';

/**
 * 演示账号静默登录（仅本地 Demo）：页面加载时登录，浏览器会自动保存
 * access_token cookie，之后所有同源 fetch 自动携带，无需登录页。
 * @returns {Promise<boolean>} 是否登录成功
 */
async function ensureDemoLogin() {
  try {
    const resp = await fetch(`${API_BASE}/auth/portal/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify({
        loginType: 'EMAIL_PWD',
        credential: { email: 'demo@amilingo.com', password: '123456' },
      }),
    });
    const json = await resp.json().catch(() => ({}));
    if (json.success) {
      console.info('[Demo] 演示账号已登录 demo@amilingo.com');
    }
    return !!json.success;
  } catch (e) {
    console.warn('[Demo] 演示登录失败:', e.message);
    return false;
  }
}

/**
 * 调用后端写作评分接口
 * @param {Object} p
 * @param {string} p.content  - 作文正文
 * @param {string} p.topic    - 题目
 * @param {string} p.examType - CET4/CET6/IELTS_A/IELTS_G
 * @param {string} p.level    - 四级/六级/Academic/General
 * @returns {Promise<Object>} 统一格式的评分结果
 */
async function callWritingGrade({ content, topic, examType = 'IELTS_A', level = 'Academic' }) {
  const resp = await fetch(`${API_BASE}/writing/grade`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      content,
      topic,
      level,
      exam_type: examType,
    }),
  });
  if (!resp.ok) {
    throw new Error(`后端返回 ${resp.status}`);
  }
  const json = await resp.json();
  if (!json.success) {
    throw new Error(json.message || 'AI 服务异常');
  }
  const d = json.data;
  return {
    overall: d.total_score,                              // 数字
    overallText: Number(d.total_score).toFixed(1),       // 字符串
    breakdown: {
      TR: d.scores.TR,
      CC: d.scores.CC,
      LR: d.scores.LR,
      GRA: d.scores.GRA,
    },
    band: d.band || '',
    bandComment: d.band_comment || '',
    nextActions: d.next_actions || [],
    dimensionComments: d.dimension_comments || {},
    sentences: d.sentences || [],
    source: 'ai',
  };
}

/**
 * 调用后端图片作文评分接口（多模态：识别 + 评分）
 * @param {Object} p
 * @param {string} p.imageBase64 - 图片 base64（不含 data: 前缀）
 * @param {string} p.topic       - 题目
 * @param {string} p.examType    - CET4/CET6/IELTS_A/IELTS_G
 * @param {string} p.level       - 四级/六级/Academic/General
 * @param {string} p.imageType   - 图片格式 jpeg/png/webp，默认 jpeg
 * @returns {Promise<Object>} 统一格式的评分结果（含 recognizedText 识别原文）
 */
async function callWritingGradeImage({ imageBase64, topic, examType = 'IELTS_A', level = 'Academic', imageType = 'jpeg' }) {
  // 多模态推理慢，10 分钟超时上限
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 600000);
  let resp;
  try {
    resp = await fetch(`${API_BASE}/writing/grade-image`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        image_base64: imageBase64,
        topic,
        level,
        exam_type: examType,
        image_type: imageType,
      }),
      signal: ctrl.signal,
    });
  } catch (e) {
    throw new Error(e.name === 'AbortError' ? '请求超时（10 分钟）' : `网络请求失败: ${e.message}`);
  } finally {
    clearTimeout(timer);
  }
  if (!resp.ok) {
    throw new Error(`后端返回 ${resp.status}`);
  }
  const json = await resp.json();
  if (!json.success) {
    throw new Error(json.message || 'AI 服务异常');
  }
  const d = json.data;
  return {
    overall: d.total_score,                              // 数字
    overallText: Number(d.total_score).toFixed(1),       // 字符串
    breakdown: {
      TR: d.scores.TR,
      CC: d.scores.CC,
      LR: d.scores.LR,
      GRA: d.scores.GRA,
    },
    band: d.band || '',
    nextActions: d.next_actions || [],
    dimensionComments: d.dimension_comments || {},
    sentences: d.sentences || [],
    recognizedText: d.content || '',                     // 识别出的作文原文
    source: 'ai',
  };
}

window.AmiAPI = { callWritingGrade, callWritingGradeImage, ensureDemoLogin, API_BASE };
