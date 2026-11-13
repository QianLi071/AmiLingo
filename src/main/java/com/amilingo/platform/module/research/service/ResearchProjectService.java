package com.amilingo.platform.module.research.service;

import com.amilingo.platform.module.research.dto.ResearchProjectDTO;
import com.amilingo.platform.module.research.entity.ResearchProject;
import com.amilingo.platform.module.research.repository.ResearchProjectRepository;
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

    /** 查询某用户的所有科研项目列表 */
    public List<ResearchProjectDTO> listByUser(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /** 按 ID 查询单条，不存在返回 empty */
    public Optional<ResearchProjectDTO> findById(Long id) {
        return repo.findById(id).map(this::toDTO);
    }

    /** 新增科研项目 */
    @Transactional
    public ResearchProjectDTO create(ResearchProjectDTO dto) {
        ResearchProject entity = toEntity(dto);
        return toDTO(repo.save(entity));
    }

    /** 更新科研项目，不存在返回 empty */
    @Transactional
    public Optional<ResearchProjectDTO> update(Long id, ResearchProjectDTO dto) {
        return repo.findById(id).map(existing -> {
            existing.setProjectName(dto.getProjectName());
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
                .role(e.getRole())
                .supervisor(e.getSupervisor())
                .laboratory(e.getLaboratory())
                .startDate(e.getStartDate())
                .endDate(e.getEndDate())
                .status(e.getStatus())
                .outcome(e.getOutcome())
                .attachments(splitAttachments(e.getAttachments()))
                .build();
    }

    /** DTO → Entity：附件 List 合并为逗号分隔字符串 */
    private ResearchProject toEntity(ResearchProjectDTO dto) {
        return ResearchProject.builder()
                .userId(dto.getUserId())
                .projectName(dto.getProjectName())
                .role(dto.getRole())
                .supervisor(dto.getSupervisor())
                .laboratory(dto.getLaboratory())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .status(dto.getStatus())
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
}
