package com.amilingo.platform.module.research.controller;

import com.amilingo.platform.common.dto.Result;
import com.amilingo.platform.module.research.dto.ResearchProjectDTO;
import com.amilingo.platform.module.research.service.ResearchProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 科研项目台账 REST 接口。
 *
 * GET    /api/research?userId=1        查询用户所有科研项目
 * GET    /api/research/{id}            查询单条科研项目
 * POST   /api/research                 新增科研项目
 * PUT    /api/research/{id}            更新科研项目
 * DELETE /api/research/{id}            删除科研项目
 */
@RestController
@RequestMapping("/api/research")
@RequiredArgsConstructor
public class ResearchProjectController {

    private final ResearchProjectService service;

    /** 查询某用户的全部科研项目列表 */
    @GetMapping
    public Result<List<ResearchProjectDTO>> list(@RequestParam Long userId) {
        return Result.ok(service.listByUser(userId));
    }

    /** 按 ID 查询单条科研项目 */
    @GetMapping("/{id}")
    public Result<ResearchProjectDTO> getById(@PathVariable Long id) {
        return service.findById(id)
                .<Result<ResearchProjectDTO>>map(Result::ok)
                .orElse(Result.notFound("科研项目不存在，id=" + id));
    }

    /** 新增科研项目 */
    @PostMapping
    public Result<ResearchProjectDTO> create(@RequestBody ResearchProjectDTO dto) {
        return Result.ok(service.create(dto));
    }

    /** 更新科研项目 */
    @PutMapping("/{id}")
    public Result<ResearchProjectDTO> update(@PathVariable Long id,
                                             @RequestBody ResearchProjectDTO dto) {
        return service.update(id, dto)
                .<Result<ResearchProjectDTO>>map(Result::ok)
                .orElse(Result.notFound("科研项目不存在，id=" + id));
    }

    /** 删除科研项目 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return service.delete(id)
                ? Result.ok()
                : Result.notFound("科研项目不存在，id=" + id);
    }
}
