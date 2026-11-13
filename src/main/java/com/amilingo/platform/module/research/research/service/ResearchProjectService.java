package com.amilingo.platform.module.research.service;

import com.amilingo.platform.module.research.dto.ResearchProjectDTO;
import com.amilingo.platform.module.research.entity.ResearchProject;
import com.amilingo.platform.module.research.entity.ProjectMember;
import com.amilingo.platform.module.research.repository.ResearchProjectRepository;
import com.amilingo.platform.module.research.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 科研项目台账业务逻辑层。
 * 负责 DTO ↔ Entity 转换，以及增删改查的核心逻辑。
 */
@Service
@RequiredArgsConstructor
public class ResearchProjectService {

    private final ResearchProjectRepository repo;
    private final ProjectMemberRepository memberRepo;

    /** 查询某用户的所有科研项目列表 */
    public List<ResearchProjectDTO> listByUser(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /** 按 ID 查询单条，不存在返回 empty */
    public Optional<ResearchProjectDTO> findById(Long id) {
        return repo.findById(id).map(this::toDTO);
    }

    public List<ResearchProjectDTO> listOpportunities() {
        return repo.findByApplicationStatusOrderByCreatedAtDesc("APPROVED")
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /** 新增科研项目 */
    @Transactional
    public ResearchProjectDTO create(ResearchProjectDTO dto) {
        validate(dto);
        ResearchProject saved = repo.save(toEntity(dto));
        memberRepo.save(ProjectMember.builder().projectId(saved.getId()).userId(saved.getUserId())
                .role("OWNER").status("ACTIVE").build());
        return toDTO(saved);
    }

    /** 更新科研项目，不存在返回 empty */
    @Transactional
    public Optional<ResearchProjectDTO> update(Long id, ResearchProjectDTO dto) {
        validate(dto);
        return repo.findById(id).map(existing -> {
            if (dto.getUserId() == null || !existing.getUserId().equals(dto.getUserId())) {
                throw new SecurityException("无权修改该科研项目");
            }
            existing.setProjectName(dto.getProjectName());
            existing.setDescription(dto.getDescription());
            existing.setResearchField(dto.getResearchField());
            existing.setRole(dto.getRole());
            existing.setSupervisor(dto.getSupervisor());
            existing.setLaboratory(dto.getLaboratory());
            existing.setStartDate(dto.getStartDate());
            existing.setEndDate(dto.getEndDate());
            existing.setStatus(dto.getStatus());
            existing.setOutcome(dto.getOutcome());
            existing.setAttachments(joinAttachments(dto.getAttachments()));
            return toDTO(repo.save(existing));
        });
    }

    /** 删除科研项目，返回是否存在 */
    @Transactional
    public boolean delete(Long id) {
        if (!repo.existsById(id)) return false;
        repo.deleteById(id);
        return true;
    }

    // ── 内部转换方法 ────────────────────────────────────────────────────────────

    /** Entity → DTO：附件字符串拆分为 List */
    private ResearchProjectDTO toDTO(ResearchProject e) {
        return ResearchProjectDTO.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .projectName(e.getProjectName())
                .description(e.getDescription())
                .researchField(e.getResearchField())
                .role(e.getRole())
                .supervisor(e.getSupervisor())
                .laboratory(e.getLaboratory())
                .startDate(e.getStartDate())
                .endDate(e.getEndDate())
                .status(e.getStatus())
                .applicationStatus(e.getApplicationStatus())
                .approvalOpinion(e.getApprovalOpinion())
                .approvedBy(e.getApprovedBy())
                .progress(e.getProgress())
                .outcome(e.getOutcome())
                .attachments(splitAttachments(e.getAttachments()))
                .build();
    }

    /** DTO → Entity：附件 List 合并为逗号分隔字符串 */
    private ResearchProject toEntity(ResearchProjectDTO dto) {
        return ResearchProject.builder()
                .userId(dto.getUserId())
                .projectName(dto.getProjectName())
                .description(dto.getDescription())
                .researchField(dto.getResearchField())
                .role(dto.getRole())
                .supervisor(dto.getSupervisor())
                .laboratory(dto.getLaboratory())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .status(dto.getStatus())
                .applicationStatus(dto.getApplicationStatus())
                .progress(dto.getProgress())
                .outcome(dto.getOutcome())
                .attachments(joinAttachments(dto.getAttachments()))
                .build();
    }

    private List<String> splitAttachments(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        return Arrays.asList(raw.split(","));
    }

    private String joinAttachments(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return String.join(",", list);
    }

    private void validate(ResearchProjectDTO dto) {
        if (dto.getUserId() == null) throw new IllegalArgumentException("userId 不能为空");
        if (dto.getProjectName() == null || dto.getProjectName().isBlank()) {
            throw new IllegalArgumentException("项目名称不能为空");
        }
        if (dto.getStartDate() != null && dto.getEndDate() != null && dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        if (dto.getProgress() != null && (dto.getProgress() < 0 || dto.getProgress() > 100)) {
            throw new IllegalArgumentException("项目进度必须在 0 到 100 之间");
        }
    }
}
