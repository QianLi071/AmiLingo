/**
 * OfflineAssistant API 客户端
 * 统一封装后端调用、字段映射、降级处理
 */
const API_BASE = 'http://localhost:8000/api/v1';

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

window.AmiAPI = { callWritingGrade, callWritingGradeImage, API_BASE };
