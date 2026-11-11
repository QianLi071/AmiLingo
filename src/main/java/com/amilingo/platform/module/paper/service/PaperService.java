package com.amilingo.platform.module.paper.service;

import com.amilingo.platform.module.paper.dto.PaperDTO;
import com.amilingo.platform.module.paper.entity.Paper;
import com.amilingo.platform.module.paper.repository.PaperRepository;
import com.amilingo.platform.module.paper.repository.PaperCollaboratorRepository;
import com.amilingo.platform.module.paper.entity.PaperCollaborator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 论文台账业务逻辑层。
 * 负责 DTO ↔ Entity 转换，以及增删改查的核心逻辑。
 */
@Service
@RequiredArgsConstructor
public class PaperService {

    private final PaperRepository repo;
    private final PaperCollaboratorRepository collaboratorRepo;
    private final PaperAccessService access;

    /** 查询某用户的所有论文列表 */
    public List<PaperDTO> listByUser(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /** 按 ID 查询单条，不存在返回 empty */
    public Optional<PaperDTO> findById(Long id, Long actorUserId) {
        return Optional.of(toDTO(access.requireRead(id, actorUserId)));
    }

    /** 新增论文 */
    @Transactional
    public PaperDTO create(PaperDTO dto) {
        validate(dto);
        Paper saved = repo.save(toEntity(dto));
        collaboratorRepo.save(PaperCollaborator.builder().paperId(saved.getId()).userId(saved.getUserId())
                .role("OWNER").status("ACTIVE").invitedBy(saved.getUserId()).build());
        return toDTO(saved);
    }

    /** 更新论文，不存在返回 empty */
    @Transactional
    public Optional<PaperDTO> update(Long id, PaperDTO dto) {
        validate(dto);
        return Optional.of(access.requireEdit(id, dto.getUserId())).map(existing -> {
            existing.setTitle(dto.getTitle());
            existing.setTopicBackground(dto.getTopicBackground());
            existing.setResearchDirection(dto.getResearchDirection());
            existing.setResearchObjective(dto.getResearchObjective());
            existing.setResearchQuestion(dto.getResearchQuestion());
            existing.setKeywords(dto.getKeywords());
            existing.setTopicSource(dto.getTopicSource());
            existing.setSupervisor(dto.getSupervisor());
            existing.setExpectedCompletionDate(dto.getExpectedCompletionDate());
            existing.setAuthorOrder(dto.getAuthorOrder());
            existing.setJournal(dto.getJournal());
            existing.setLevel(dto.getLevel());
            existing.setStatus(dto.getStatus());
            existing.setSubmissionDate(dto.getSubmissionDate());
            existing.setAcceptanceDate(dto.getAcceptanceDate());
            existing.setPublicationDate(dto.getPublicationDate());
            existing.setDoi(dto.getDoi());
            existing.setAttachments(joinAttachments(dto.getAttachments()));
            return toDTO(repo.save(existing));
        });
    }

    /** 删除论文，返回是否存在 */
    @Transactional
    public boolean delete(Long id, Long actorUserId) {
        if (!repo.existsById(id)) return false;
        access.requireOwner(id, actorUserId);
        repo.deleteById(id);
        return true;
    }

    // ── 内部转换方法 ────────────────────────────────────────────────────────────

    private PaperDTO toDTO(Paper e) {
        return PaperDTO.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .title(e.getTitle())
                .topicBackground(e.getTopicBackground())
                .researchDirection(e.getResearchDirection())
                .researchObjective(e.getResearchObjective())
                .researchQuestion(e.getResearchQuestion())
                .keywords(e.getKeywords())
                .topicSource(e.getTopicSource())
                .supervisor(e.getSupervisor())
                .expectedCompletionDate(e.getExpectedCompletionDate())
                .topicStatus(e.getTopicStatus())
                .authorOrder(e.getAuthorOrder())
                .journal(e.getJournal())
                .level(e.getLevel())
                .status(e.getStatus())
                .submissionDate(e.getSubmissionDate())
                .acceptanceDate(e.getAcceptanceDate())
                .publicationDate(e.getPublicationDate())
                .doi(e.getDoi())
                .attachments(splitAttachments(e.getAttachments()))
                .build();
    }

    private Paper toEntity(PaperDTO dto) {
        return Paper.builder()
                .userId(dto.getUserId())
                .title(dto.getTitle())
                .topicBackground(dto.getTopicBackground())
                .researchDirection(dto.getResearchDirection())
                .researchObjective(dto.getResearchObjective())
                .researchQuestion(dto.getResearchQuestion())
                .keywords(dto.getKeywords())
                .topicSource(dto.getTopicSource())
                .supervisor(dto.getSupervisor())
                .expectedCompletionDate(dto.getExpectedCompletionDate())
                .topicStatus(dto.getTopicStatus())
                .authorOrder(dto.getAuthorOrder())
                .journal(dto.getJournal())
                .level(dto.getLevel())
                .status(dto.getStatus())
                .submissionDate(dto.getSubmissionDate())
                .acceptanceDate(dto.getAcceptanceDate())
                .publicationDate(dto.getPublicationDate())
                .doi(dto.getDoi())
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

    private void validate(PaperDTO dto) {
        if (dto.getUserId() == null) throw new IllegalArgumentException("userId 不能为空");
        if (dto.getTitle() == null || dto.getTitle().isBlank()) throw new IllegalArgumentException("论文标题不能为空");
        if (dto.getAuthorOrder() != null && dto.getAuthorOrder() < 1) throw new IllegalArgumentException("作者顺序必须大于 0");
        if (dto.getSubmissionDate() != null && dto.getAcceptanceDate() != null && dto.getAcceptanceDate().isBefore(dto.getSubmissionDate())) throw new IllegalArgumentException("录用日期不能早于投稿日期");
        if (dto.getAcceptanceDate() != null && dto.getPublicationDate() != null && dto.getPublicationDate().isBefore(dto.getAcceptanceDate())) throw new IllegalArgumentException("见刊日期不能早于录用日期");
    }
}
