package com.amilingo.platform.module.patent.controller;

import com.amilingo.platform.common.dto.Result;
import com.amilingo.platform.module.patent.dto.PatentDTO;
import com.amilingo.platform.module.patent.service.PatentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 专利台账 REST 接口。
 *
 * GET    /api/patent?userId=1          查询用户所有专利
 * GET    /api/patent/{id}              查询单条专利
 * POST   /api/patent                   新增专利
 * PUT    /api/patent/{id}              更新专利
 * DELETE /api/patent/{id}              删除专利
 */
@RestController
@RequestMapping("/api/patent")
@RequiredArgsConstructor
public class PatentController {

    private final PatentService service;

    /** 查询某用户的全部专利列表 */
    @GetMapping
    public Result<List<PatentDTO>> list(@RequestParam Long userId) {
        return Result.ok(service.listByUser(userId));
    }

    /** 按 ID 查询单条专利 */
    @GetMapping("/{id}")
    public Result<PatentDTO> getById(@PathVariable Long id) {
        return service.findById(id)
                .<Result<PatentDTO>>map(Result::ok)
                .orElse(Result.notFound("专利不存在，id=" + id));
    }

    /** 新增专利 */
    @PostMapping
    public Result<PatentDTO> create(@RequestBody PatentDTO dto) {
        return Result.ok(service.create(dto));
    }

    /** 更新专利 */
    @PutMapping("/{id}")
    public Result<PatentDTO> update(@PathVariable Long id,
                                    @RequestBody PatentDTO dto) {
        return service.update(id, dto)
                .<Result<PatentDTO>>map(Result::ok)
                .orElse(Result.notFound("专利不存在，id=" + id));
    }

    /** 删除专利 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return service.delete(id)
                ? Result.ok()
                : Result.notFound("专利不存在，id=" + id);
    }
}
