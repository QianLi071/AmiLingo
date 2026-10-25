(function (root, factory) {
  "use strict";
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.CareerWrittenPracticeStore = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";
  const STORAGE_KEY = "amilingo.career.written-practice.v1";
  const VERSION = 1;
  const TRACKS = Object.freeze(["GENERAL", "SOFTWARE", "DATA", "PRODUCT", "HARDWARE"]);
  const BANK = Object.freeze({
    GENERAL: [
      ["信息判断", "你收到三份相互矛盾的项目数据，距离汇报还有两小时。请说明核实顺序与汇报方式。", ["区分事实、假设和未知项", "给出核实优先级", "说明如何披露不确定性"]],
      ["书面表达", "请用不超过 150 字向非专业同学解释：为什么一个相关关系不能直接证明因果关系？", ["定义准确", "包含一个易懂例子", "控制篇幅并给出结论"]],
      ["任务规划", "面对一个从未做过、三天后交付的小任务，请写出第一天的行动计划。", ["先澄清交付标准", "拆分可验证的小步骤", "设置风险检查点"]]
    ],
    SOFTWARE: [
      ["接口设计", "设计一个收藏岗位的接口，说明请求字段、返回结果、去重方式和失败情况。", ["字段和状态清晰", "考虑重复请求", "覆盖校验与错误响应"]],
      ["故障排查", "页面偶发显示旧数据。请列出至少三种可能原因，并说明验证顺序。", ["从浏览器到服务端分层排查", "每个假设有验证办法", "优先低成本高概率项"]],
      ["代码质量", "一个函数同时读取数据库、计算结果并发送通知。你会如何重构，为什么？", ["识别职责混杂", "说明拆分边界", "考虑测试与失败处理"]]
    ],
    DATA: [
      ["指标定义", "要评估校园招聘页面是否有帮助，你会选择哪些指标？请说明分子、分母和时间窗口。", ["区分过程与结果指标", "定义可计算口径", "说明偏差或限制"]],
      ["异常分析", "某周岗位点击量上涨 80%，但投递入口点击率下降。请给出分析步骤。", ["先核对数据质量", "提出多个竞争假设", "设计分组或对照分析"]],
      ["结果沟通", "分析没有发现明显差异，但负责人期待一个确定结论。你会怎样汇报？", ["如实表达不确定性", "说明样本和方法限制", "提出下一步验证方案"]]
    ],
    PRODUCT: [
      ["需求判断", "学生提出“自动替我投递所有岗位”。请分析真实需求、风险与可接受替代方案。", ["识别用户目标而非照单实现", "覆盖隐私与误投风险", "给出可验证的替代方案"]],
      ["流程设计", "设计从发现岗位到完成官网投递的流程，指出三个容易流失的节点。", ["流程阶段完整", "流失点有依据", "改进措施可验证"]],
      ["优先级", "团队只有一周，可做岗位筛选、简历复制板或社区讨论中的一个。你如何决定？", ["明确评价标准", "结合用户价值与成本", "说明取舍和验证方式"]]
    ],
    HARDWARE: [
      ["系统排查", "机器人运行十分钟后开始偏航。请写出从现象到定位原因的排查步骤。", ["区分机械、电气与软件因素", "一次只改变一个变量", "记录可复现条件"]],
      ["方案权衡", "为低功耗传感设备选择通信方式时，你会比较哪些因素？", ["覆盖距离、功耗与可靠性", "考虑成本和环境", "给出场景化结论"]],
      ["测试设计", "请为一个温度采集模块设计最小测试方案。", ["包含正常与边界条件", "说明基准或校准方法", "定义通过标准"]]
    ]
  });
  function text(value, limit) { return typeof value === "string" ? value.trim().slice(0, limit) : ""; }
  function id() { return typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : `local-${Date.now()}-${Math.random().toString(16).slice(2)}`; }
  function inferTrack(job) {
    const value=`${text(job&&job.positionTitle,200)} ${text(job&&job.description,1000)} ${text(job&&job.industry,100)}`.toLowerCase();
    if (/(data|数据|分析|统计|quant)/.test(value)) return "DATA";
    if (/(product|产品|运营|business)/.test(value)) return "PRODUCT";
    if (/(hardware|硬件|电子|电气|robot|机器人|embedded|嵌入)/.test(value)) return "HARDWARE";
    if (/(software|软件|开发|程序|算法|engineer|java|python)/.test(value)) return "SOFTWARE";
    return "GENERAL";
  }
  function normalizeQuestion(value) {
    const source=value&&typeof value==="object"?value:{}; const rubric=Array.isArray(source.rubric)?source.rubric.map(item=>text(item,200)).filter(Boolean).slice(0,5):[];
    const checks=Array.isArray(source.checks)?rubric.map((_,index)=>Boolean(source.checks[index])):rubric.map(()=>false);
    return { id:text(source.id,100)||id(), category:text(source.category,40), prompt:text(source.prompt,1200), rubric, answer:text(source.answer,6000), checks, reflection:text(source.reflection,1200) };
  }
  function generateQuestions(track) {
    const selected=TRACKS.includes(track)?track:"GENERAL";
    return BANK[selected].map(([category,prompt,rubric])=>normalizeQuestion({id:id(),category,prompt,rubric}));
  }
  function normalizeSession(value) {
    const source=value&&typeof value==="object"?value:{}; const track=TRACKS.includes(source.track)?source.track:"GENERAL";
    return {id:text(source.id,100)||id(),sourceJobId:text(source.sourceJobId,100),companyName:text(source.companyName,128),positionTitle:text(source.positionTitle,128),track,questions:(Array.isArray(source.questions)?source.questions:[]).map(normalizeQuestion).filter(item=>item.prompt).slice(0,12),createdAt:text(source.createdAt,40),updatedAt:text(source.updatedAt,40)};
  }
  function load(storage){try{const payload=JSON.parse(storage.getItem(STORAGE_KEY));if(!payload||payload.version!==VERSION||!Array.isArray(payload.items))return[];return payload.items.map(normalizeSession).filter(item=>item.positionTitle).slice(0,30);}catch(_error){return[];}}
  function persist(storage,items){const normalized=items.map(normalizeSession).filter(item=>item.positionTitle).slice(0,30);storage.setItem(STORAGE_KEY,JSON.stringify({version:VERSION,items:normalized}));return normalized;}
  function createSession(storage,job,requestedTrack){const items=load(storage);const sourceJobId=text(job&&job.id,100);const existing=sourceJobId?items.find(item=>item.sourceJobId===sourceJobId):null;if(existing)return existing;const track=TRACKS.includes(requestedTrack)?requestedTrack:inferTrack(job);const now=new Date().toISOString();const session=normalizeSession({id:id(),sourceJobId,companyName:job&&job.companyName,positionTitle:job&&job.positionTitle,track,questions:generateQuestions(track),createdAt:now,updatedAt:now});if(!session.positionTitle)throw new Error("岗位名称不能为空");persist(storage,[session,...items]);return session;}
  function saveSession(storage,value){const session=normalizeSession(value);if(!session.positionTitle)throw new Error("岗位名称不能为空");const items=load(storage);const index=items.findIndex(item=>item.id===session.id);const now=new Date().toISOString();const saved={...session,createdAt:session.createdAt||now,updatedAt:now};if(index>=0)items[index]=saved;else items.unshift(saved);persist(storage,items);return saved;}
  function removeSession(storage,sessionId){return persist(storage,load(storage).filter(item=>item.id!==sessionId));}
  function progress(value){const session=normalizeSession(value);const answered=session.questions.filter(item=>item.answer).length;const checked=session.questions.reduce((sum,item)=>sum+item.checks.filter(Boolean).length,0);const totalChecks=session.questions.reduce((sum,item)=>sum+item.rubric.length,0);const gaps=[];session.questions.forEach(item=>item.rubric.forEach((criterion,index)=>{if(item.answer&&!item.checks[index])gaps.push(`${item.category}：${criterion}`);}));return{answered,totalQuestions:session.questions.length,checked,totalChecks,gaps};}
  return Object.freeze({STORAGE_KEY,TRACKS,inferTrack,generateQuestions,normalizeSession,load,createSession,saveSession,removeSession,progress});
});
