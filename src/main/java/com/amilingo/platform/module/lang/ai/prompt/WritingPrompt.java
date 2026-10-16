package com.amilingo.platform.module.lang.ai.prompt;

import com.amilingo.platform.module.lang.enums.ExamType;

/**
 * 写作评分 Prompt 注册表（与原版 Python {@code backend/app/prompts/registry.py} 一一对应）。
 *
 * <p>
 * 原版流程是对本地小模型的多次独立调用：
 * <ol>
 * <li>{@link #getGradePrompt} —— 评分轮：Gemma 输出总分/档位/维度分/维度点评/总评/改进动作；</li>
 * <li>{@link #getSuggestionPrompt} —— 逐句轮：Gemma 对每一句输出 issues/revised/comment +
 * summary。</li>
 * </ol>
 * 两套模板内容原样搬运自 ielts_prompts.py / writing_prompts.py，占位符仍为
 * {@code {level}} / {@code {topic}} / {@code {essay}}，用字面 replace 填充
 * （Python 版用 str.format，JSON 花括号需 {{ }} 转义；Java 版无需转义）。
 */
public final class WritingPrompt {

  private WritingPrompt() {
  }

  // ==================================================================
  // IELTS 评分 Prompt（ielts_prompts.py IELTS_GRADE_PROMPT）
  // ==================================================================
  private static final String IELTS_GRADE_PROMPT = """
      你是雅思写作考官，按官方四项标准独立打分（每项 0-9 分，允许 0.5 分）。

      【考试类型】{level}   （Academic 学术类 / General 培训类）
      【题目】
      {topic}

      【考生作文】
      {essay}

      ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      四项评分标准（IELTS 官方）
      ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

      1. TR (Task Response) 任务回应

      2. CC (Coherence and Cohesion) 连贯衔接

      3. LR (Lexical Resource) 词汇资源

      4. GRA (Grammatical Range and Accuracy) 语法多样与准确

      【硬性要求】

      1. total_score = 四项平均分，四舍五入到 0.5 的倍数（如 6.0 / 6.5）。

      2. band 必须使用官方 0.5 分档（如 "6.5 分档"）。

      3. band_comment 引用雅思官方对应该分数的描述。

      4. next_actions 必须输出 3 条具体可执行的建议，每条 ≤20 字中文。
         next_actions 必须覆盖不同维度（如 TR/CC/LR/GRA 各至少一条），
         不要三条都是同一个方面。

      严格只输出 JSON，不要输出任何解释性文字，也不要使用 markdown 代码块：
      {
        "total_score": 6.5,
        "band": "6.5 分档",
        "band_comment": "有能力使用英语，偶尔出现不准确、不恰当和误解",
        "scores": {
          "TR": 6.5,
          "CC": 6.0,
          "LR": 6.5,
          "GRA": 6.5
        },
        "dimension_comments": {
          "TR": "任务完成度良好，观点明确",
          "CC": "段落衔接自然，但有些过渡略显生硬",
          "LR": "词汇较丰富，偶有不当搭配",
          "GRA": "句式多样，但复杂句偶有错误"
        },
        "overall_comment": "整体表现接近 6.5 分，建议加强复杂句的准确性。",
        "next_actions": [
          "每篇作文确保覆盖题目所有要求点",
          "练习 5 个高级连接词替换 however",
          "每天写 2 个复合句并自查主谓一致"
        ]
      }""";

  // ==================================================================
  // 四六级评分 Prompt（writing_prompts.py GRADE_PROMPT）
  // ==================================================================
  private static final String CET_GRADE_PROMPT = """
      你是一位资深的大学生英语四六级考试写作阅卷专家，请严格按照四六级写作评分标准对作文进行评分。

      【考试级别】{level}
      【写作题目】
      {topic}

      【考生作文】
      {essay}

      ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      一、总分评定（最重要，必须严格按官方 5 档标准）
      ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      四六级写作满分 15 分，官方分 5 个档位。请先判断整篇作文落在哪一档，再给出档位内的具体分数（允许一位小数）：

      - 13-15 分：切题，表达思想清楚，文字通顺、连贯，基本无语言错误
      - 10-12 分：切题，表达思想清楚，文字连贯，但有少量语言错误
      - 7-9 分：基本切题，有些地方表达不够清楚，文字勉强连贯
      - 4-6 分：基本切题，表达思想不清楚，连贯性差，有较多语言错误
      - 1-3 分：条理不清，思路紊乱，语言支离破碎
      - 0 分：白卷或所写内容与题目无关

      【硬性要求】
      1. total_score 是阅卷老师对整篇作文的综合判断，绝不允许用 8 个维度的算术平均计算。
      2. band 字段必须严格使用上述官方档位名称（"13-15档" / "10-12档" / "7-9档" / "4-6档" / "1-3档" / "0分"）。
      3. total_score 必须与 band 一致：如 band 为 "10-12档"，则 total_score 必须落在 [10.0, 12.0] 区间。
      4. band_comment 直接引用该档位的官方描述原文。

      ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      二、8 维度诊断（仅用于展示强弱项，不参与总分计算）
      ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      请从以下 8 个维度分别评分，每个维度满分 15 分。这些分数用于告诉学生"哪一项弱"，不用于计算 total_score：
      1. content（内容）：是否切题、观点是否明确、论证是否充实具体；
      2. structure（结构）：段落划分是否合理、开头结尾是否得当、篇章组织是否清晰；
      3. logic（逻辑）：论点与论据是否连贯、过渡衔接是否自然、有无自相矛盾；
      4. grammar（语法）：时态语态、主谓一致、冠词介词等语法错误的数量与严重程度；
      5. vocabulary（词汇）：词汇是否丰富、用词是否准确得体、拼写是否正确；
      6. sentence_variety（句式）：句型是否多样、长短句搭配是否得当、有无高级句式；
      7. naturalness（自然度）：表达是否符合英语母语习惯、中式英语的严重程度；
      8. template_trace（模板痕迹）：该维度得分越高表示套用模板的痕迹越少、原创表达越多。

      ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      三、输出格式
      ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      - 每个维度需给出不超过 30 字的中文简评（dimension_comments）。
      - overall_comment 为 50 字以内的中文总评，指出最突出的优点与最需改进的问题。

      严格只输出以下 JSON，不要输出任何解释性文字，也不要使用 markdown 代码块：
      {
        "total_score": 11.5,
        "band": "10-12档",
        "band_comment": "切题，表达思想清楚，文字连贯，但有少量语言错误",
        "scores": {
          "content": 12,
          "structure": 11,
          "logic": 12,
          "grammar": 10,
          "vocabulary": 10,
          "sentence_variety": 11,
          "naturalness": 12,
          "template_trace": 13
        },
        "dimension_comments": {
          "content": "切题，论据较充实",
          "structure": "三段式清晰，结尾稍弱",
          "logic": "论证连贯，衔接自然",
          "grammar": "存在少量时态错误",
          "vocabulary": "用词基本准确，略显单一",
          "sentence_variety": "长短句搭配得当",
          "naturalness": "表达较地道",
          "template_trace": "原创表达为主"
        },
        "overall_comment": "该作文在内容切题和逻辑连贯方面表现良好，需加强语法准确性和词汇多样性。",
        "next_actions": ["复习主谓一致规则", "积累 10 个高级连接词", "每天写 1 个复杂句并自查"]
      }""";

  // ==================================================================
  // 逐句批改 Prompt（writing_prompts.py SUGGESTION_PROMPT，各考试类型共用）
  // ==================================================================
  private static final String SUGGESTION_PROMPT = """
      你是大学英语四六级写作辅导老师，对作文逐句批改。

      【级别】{level}
      【题目】{topic}
      【作文】
      {essay}

      【批改要求】
      1. 按 .?! 拆分句子，index 从 1 开始。
      2. 每句输出：
         - issues：语法/用词/搭配/中式英语问题（数组，无问题则空数组 []）
         - revised：修改后的英文（无错误时必须逐字照抄原句）
         - comment：中文理由（≤30字）
      3. issues 优先标注对四六级评分影响最大的问题（如时态、主谓一致、中式表达）。
      4. summary 用中文总结 2-3 个最需改进方面，每条建议要具体可执行（例如"多使用非谓语动词"而不是"提升语法"）。

      【重要约束：先纠错，不炫技】
      5. 只标注真实存在的错误（语法错误、错误搭配、中式英语）。不得把本身正确、地道的表达误判为错误（例如 "She goes to school by bus every day." 完全正确，issues 必须为 []）。
      6. 当 issues 为空时，revised 必须与 original 逐字完全相同（复制粘贴，一个字母都不许改），comment 只写"无明显问题"。
         严禁以"更正式/更地道/更高级/更简洁"为由改动正确句子，例如不得把正确的 have 改成 own、But 改成 However、daily life 改成 daily lives——这类风格偏好一律不写进 issues/revised/comment。
      7. 当 issues 非空时，revised 仅做必要纠错，用词与句式难度贴近考生现有水平，不得堆砌远超其水平的难词长句；改动要最小化、可被该水平学生理解模仿。

      严格只输出 JSON，不要输出任何解释性文字，也不要使用 markdown 代码块：
      {
        "sentences": [
          {"index": 1, "original": "无错误的原句", "issues": [], "revised": "无错误的原句", "comment": "无明显问题"},
          {"index": 2, "original": "有错误的原句", "issues": ["具体错误"], "revised": "最小化修改后的句子", "comment": "理由"}
        ],
        "summary": "整体建议"
      }""";

  /** 评分轮 prompt（按考试类型分发，对应 registry.GRADE_PROMPTS）。 */
  public static String getGradePrompt(ExamType examType) {
    return switch (examType) {
      case CET4, CET6 -> CET_GRADE_PROMPT;
      case IELTS_A, IELTS_G -> IELTS_GRADE_PROMPT;
      // 未单独建 prompt 的类型默认走 IELTS 标准（与 ExamType.fromCode 容错策略一致）
      default -> IELTS_GRADE_PROMPT;
    };
  }

  /** 逐句批改轮 prompt（原版 registry 中所有考试类型均复用四六级逐句 prompt）。 */
  public static String getSuggestionPrompt(ExamType examType) {
    return SUGGESTION_PROMPT;
  }

  /** 填充 {level} / {topic} / {essay} 占位符（字面替换，无 format 转义问题）。 */
  public static String fill(String template, String level, String topic, String essay) {
    return template
        .replace("{level}", level == null ? "" : level)
        .replace("{topic}", topic == null ? "" : topic)
        .replace("{essay}", essay == null ? "" : essay);
  }
}
