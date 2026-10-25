package com.amilingo.platform.module.paper.service;

import com.amilingo.platform.module.paper.dto.PaperDTO;
import com.amilingo.platform.module.paper.entity.Paper;
import com.amilingo.platform.module.paper.repository.PaperRepository;
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

    /** 查询某用户的所有论文列表 */
    public List<PaperDTO> listByUser(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /** 按 ID 查询单条，不存在返回 empty */
    public Optional<PaperDTO> findById(Long id) {
        return repo.findById(id).map(this::toDTO);
    }

    /** 新增论文 */
    @Transactional
    public PaperDTO create(PaperDTO dto) {
        return toDTO(repo.save(toEntity(dto)));
    }

    /** 更新论文，不存在返回 empty */
    @Transactional
    public Optional<PaperDTO> update(Long id, PaperDTO dto) {
        return repo.findById(id).map(existing -> {
            existing.setTitle(dto.getTitle());
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
    public boolean delete(Long id) {
        if (!repo.existsById(id)) return false;
        repo.deleteById(id);
        return true;
    }

    // ── 内部转换方法 ────────────────────────────────────────────────────────────

    private PaperDTO toDTO(Paper e) {
        return PaperDTO.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .title(e.getTitle())
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
}
