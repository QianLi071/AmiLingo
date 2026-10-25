package com.amilingo.platform.module.paper.controller;

import com.amilingo.platform.common.dto.Result;
import com.amilingo.platform.module.paper.dto.PaperDTO;
import com.amilingo.platform.module.paper.service.PaperService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 论文台账 REST 接口。
 *
 * GET    /api/paper?userId=1           查询用户所有论文
 * GET    /api/paper/{id}               查询单条论文
 * POST   /api/paper                    新增论文
 * PUT    /api/paper/{id}               更新论文
 * DELETE /api/paper/{id}               删除论文
 */
@RestController
@RequestMapping("/api/paper")
@RequiredArgsConstructor
public class PaperController {

    private final PaperService service;

    /** 查询某用户的全部论文列表 */
    @GetMapping
    public Result<List<PaperDTO>> list(@RequestParam Long userId) {
        return Result.ok(service.listByUser(userId));
    }

    /** 按 ID 查询单条论文 */
    @GetMapping("/{id}")
    public Result<PaperDTO> getById(@PathVariable Long id) {
        return service.findById(id)
                .<Result<PaperDTO>>map(Result::ok)
                .orElse(Result.notFound("论文不存在，id=" + id));
    }

    /** 新增论文 */
    @PostMapping
    public Result<PaperDTO> create(@RequestBody PaperDTO dto) {
        return Result.ok(service.create(dto));
    }

    /** 更新论文 */
    @PutMapping("/{id}")
    public Result<PaperDTO> update(@PathVariable Long id,
                                   @RequestBody PaperDTO dto) {
        return service.update(id, dto)
                .<Result<PaperDTO>>map(Result::ok)
                .orElse(Result.notFound("论文不存在，id=" + id));
    }

    /** 删除论文 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return service.delete(id)
                ? Result.ok()
                : Result.notFound("论文不存在，id=" + id);
    }
}
