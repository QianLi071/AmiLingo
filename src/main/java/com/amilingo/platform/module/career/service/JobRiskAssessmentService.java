package com.amilingo.platform.module.career.service;

import com.amilingo.platform.module.career.dto.request.JobRiskAssessmentRequest;
import com.amilingo.platform.module.career.dto.response.JobRiskAssessmentView;
import com.amilingo.platform.module.career.dto.response.JobRiskAssessmentView.RiskSignalView;
import com.amilingo.platform.module.career.enums.RiskLevel;
import com.amilingo.platform.module.career.enums.RiskSeverity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class JobRiskAssessmentService {

    private static final String DISCLAIMER =
            "结果仅用于整理需要核实的信息，不构成对企业信用、违法行为或是否应聘的最终判断。";

    public JobRiskAssessmentView assess(JobRiskAssessmentRequest request) {
        List<WeightedSignal> weightedSignals = new ArrayList<>();
        evaluateCriticalConduct(request, weightedSignals);
        evaluateRecruitingTransparency(request, weightedSignals);
        evaluateCompanyContext(request, weightedSignals);

        int score = Math.min(100, weightedSignals.stream().mapToInt(WeightedSignal::weight).sum());
        boolean critical = weightedSignals.stream()
                .anyMatch(signal -> signal.view().severity() == RiskSeverity.CRITICAL);
        RiskLevel level = critical || score >= 50
                ? RiskLevel.HIGH
                : score >= 25 ? RiskLevel.MEDIUM : RiskLevel.LOW;

        return new JobRiskAssessmentView(
                request.companyName().trim(),
                score,
                level,
                weightedSignals.stream().map(WeightedSignal::view).toList(),
                buildChecklist(request),
                DISCLAIMER
        );
    }

    private void evaluateCriticalConduct(JobRiskAssessmentRequest request, List<WeightedSignal> signals) {
        if (Boolean.TRUE.equals(request.chargesApplicantFees())) {
            add(signals, 40, "APPLICANT_FEE", RiskSeverity.CRITICAL,
                    "要求求职者付费", "正规招聘通常不会以培训费、押金、保证金等名义要求求职者先付款。");
        }
        if (Boolean.TRUE.equals(request.requestsSensitiveDataBeforeOffer())) {
            add(signals, 30, "EARLY_SENSITIVE_DATA", RiskSeverity.HIGH,
                    "过早索取敏感资料", "在正式录用前索取银行卡、验证码或证件原件等信息，需要立即核实用途和接收方身份。");
        }
    }

    private void evaluateRecruitingTransparency(JobRiskAssessmentRequest request, List<WeightedSignal> signals) {
        if (Boolean.FALSE.equals(request.verifiedBusinessAddress())) {
            add(signals, 12, "UNVERIFIED_ADDRESS", RiskSeverity.MEDIUM,
                    "办公地址尚未核实", "应通过企业登记信息、地图和面试通知交叉确认办公地点是否一致。");
        }
        if (Boolean.FALSE.equals(request.officialWebsite())) {
            add(signals, 8, "NO_OFFICIAL_WEBSITE", RiskSeverity.LOW,
                    "未找到企业官网", "没有官网并不等于不可靠，但需要通过企业登记和其他公开渠道补充核实。");
        }
        if (Boolean.TRUE.equals(request.usesOnlyPersonalMessaging())) {
            add(signals, 12, "PERSONAL_CONTACT_ONLY", RiskSeverity.MEDIUM,
                    "只通过私人账号联系", "如果招聘全程仅使用私人聊天账号，应进一步核对招聘人员身份和企业邮箱。");
        }
        if (Boolean.FALSE.equals(request.jobDescriptionClear())) {
            add(signals, 10, "UNCLEAR_JOB_DESCRIPTION", RiskSeverity.MEDIUM,
                    "岗位职责不清晰", "面试前应确认日常职责、汇报对象、工作地点、工时和绩效标准。");
        }
        if (Boolean.FALSE.equals(request.providesWrittenTerms())) {
            add(signals, 8, "NO_WRITTEN_TERMS", RiskSeverity.LOW,
                    "缺少书面条件", "薪资结构、试用期、社保或强积金、休假与入职时间应以书面信息确认。");
        }
        if (Boolean.TRUE.equals(request.salaryUnusuallyHigh())) {
            add(signals, 10, "UNUSUAL_SALARY", RiskSeverity.MEDIUM,
                    "薪资明显偏离市场", "高薪本身不是问题，但应确认薪资构成、考核条件以及是否存在收费或销售任务。");
        }
    }

    private void evaluateCompanyContext(JobRiskAssessmentRequest request, List<WeightedSignal> signals) {
        if (request.registrationDate() != null && request.registrationDate().isAfter(LocalDate.now().minusYears(1))) {
            add(signals, 6, "NEW_COMPANY", RiskSeverity.INFO,
                    "公司成立时间较短", "初创企业同样可能可靠，但经营历史较少，应额外核实团队、业务和资金安排。");
        }
        if (request.employeeCount() != null && request.employeeCount() <= 5) {
            add(signals, 4, "VERY_SMALL_TEAM", RiskSeverity.INFO,
                    "公开人员规模较小", "小团队不等于不可靠，需要进一步确认实际负责人、劳动关系和岗位稳定性。");
        }
        if (request.registeredCapitalAmount() != null
                && request.registeredCapitalAmount().compareTo(BigDecimal.ZERO) == 0) {
            String currency = request.registeredCapitalCurrency() == null
                    ? ""
                    : "（币种：" + request.registeredCapitalCurrency().trim() + "）";
            add(signals, 4, "ZERO_REGISTERED_CAPITAL", RiskSeverity.INFO,
                    "登记资本为零", "登记资本" + currency + "只能作为背景信息，不能单独证明企业是否可靠。");
        }
    }

    private List<String> buildChecklist(JobRiskAssessmentRequest request) {
        List<String> checklist = new ArrayList<>();
        checklist.add("核对企业登记名称、招聘主体和劳动合同签署主体是否一致");
        if (!Boolean.TRUE.equals(request.verifiedBusinessAddress())) {
            checklist.add("通过企业登记和地图核对办公地址，线下面试前确认具体楼层和联系人");
        }
        if (!Boolean.TRUE.equals(request.officialWebsite())) {
            checklist.add("寻找企业官网、官方邮箱或经认证的招聘主页，核实招聘人员身份");
        }
        if (!Boolean.TRUE.equals(request.jobDescriptionClear())) {
            checklist.add("询问日常职责、汇报关系、工作时间、薪资结构和绩效考核方式");
        }
        if (!Boolean.TRUE.equals(request.providesWrittenTerms())) {
            checklist.add("在接受 Offer 前取得包含薪酬、试用期、福利和入职时间的书面条款");
        }
        checklist.add("不要提供短信验证码、网银密码，也不要为获得面试或 Offer 支付费用");
        return List.copyOf(checklist);
    }

    private void add(List<WeightedSignal> signals, int weight, String code, RiskSeverity severity,
                     String title, String explanation) {
        signals.add(new WeightedSignal(weight, new RiskSignalView(code, severity, title, explanation)));
    }

    private record WeightedSignal(int weight, RiskSignalView view) {
    }
}
