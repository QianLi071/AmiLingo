package com.amilingo.platform.module.patent.service;

import com.amilingo.platform.module.patent.dto.PatentDTO;
import com.amilingo.platform.module.patent.entity.Patent;
import com.amilingo.platform.module.patent.repository.PatentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 专利台账业务逻辑层。
 * 负责 DTO ↔ Entity 转换，以及增删改查的核心逻辑。
 */
@Service
@RequiredArgsConstructor
public class PatentService {

    private final PatentRepository repo;

    /** 查询某用户的所有专利列表 */
    public List<PatentDTO> listByUser(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /** 按 ID 查询单条，不存在返回 empty */
    public Optional<PatentDTO> findById(Long id) {
        return repo.findById(id).map(this::toDTO);
    }

    /** 新增专利 */
    @Transactional
    public PatentDTO create(PatentDTO dto) {
        return toDTO(repo.save(toEntity(dto)));
    }

    /** 更新专利，不存在返回 empty */
    @Transactional
    public Optional<PatentDTO> update(Long id, PatentDTO dto) {
        return repo.findById(id).map(existing -> {
            existing.setName(dto.getName());
            existing.setType(dto.getType());
            existing.setApplicationNumber(dto.getApplicationNumber());
            existing.setGrantNumber(dto.getGrantNumber());
            existing.setInventorOrder(dto.getInventorOrder());
            existing.setStatus(dto.getStatus());
            existing.setApplicationDate(dto.getApplicationDate());
            existing.setGrantDate(dto.getGrantDate());
            existing.setAttachments(joinAttachments(dto.getAttachments()));
            return toDTO(repo.save(existing));
        });
    }

    /** 删除专利，返回是否存在 */
    @Transactional
    public boolean delete(Long id) {
        if (!repo.existsById(id)) return false;
        repo.deleteById(id);
        return true;
    }

    // ── 内部转换方法 ────────────────────────────────────────────────────────────

    private PatentDTO toDTO(Patent e) {
        return PatentDTO.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .name(e.getName())
                .type(e.getType())
                .applicationNumber(e.getApplicationNumber())
                .grantNumber(e.getGrantNumber())
                .inventorOrder(e.getInventorOrder())
                .status(e.getStatus())
                .applicationDate(e.getApplicationDate())
                .grantDate(e.getGrantDate())
                .attachments(splitAttachments(e.getAttachments()))
                .build();
    }

    private Patent toEntity(PatentDTO dto) {
        return Patent.builder()
                .userId(dto.getUserId())
                .name(dto.getName())
                .type(dto.getType())
                .applicationNumber(dto.getApplicationNumber())
                .grantNumber(dto.getGrantNumber())
                .inventorOrder(dto.getInventorOrder())
                .status(dto.getStatus())
                .applicationDate(dto.getApplicationDate())
                .grantDate(dto.getGrantDate())
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
