package com.amilingo.platform.module.paper.service;

import com.amilingo.platform.module.paper.entity.*;
import com.amilingo.platform.module.paper.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Paper 模块统一对象级权限判断，主框架接入 JWT 后以当前用户调用。 */
@Service @RequiredArgsConstructor
public class PaperAccessService {
    private final PaperRepository paperRepo;
    private final PaperCollaboratorRepository collaboratorRepo;

    public Paper requireRead(Long paperId,Long userId){
        Paper p=paperRepo.findById(paperId).orElseThrow(()->new IllegalArgumentException("论文不存在"));
        if(p.getUserId().equals(userId))return p;
        PaperCollaborator c=collaboratorRepo.findByPaperIdAndUserId(paperId,userId)
            .filter(x->"ACTIVE".equals(x.getStatus())).orElseThrow(()->new SecurityException("无权访问该论文"));
        return p;
    }
    public Paper requireEdit(Long paperId,Long userId){
        Paper p=requireRead(paperId,userId);
        if(p.getUserId().equals(userId))return p;
        String role=collaboratorRepo.findByPaperIdAndUserId(paperId,userId).orElseThrow().getRole();
        if(!"EDITOR".equals(role))throw new SecurityException("当前协作者只有只读权限");
        return p;
    }
    public Paper requireOwner(Long paperId,Long userId){
        Paper p=requireRead(paperId,userId);
        if(!p.getUserId().equals(userId))throw new SecurityException("只有论文所有者可以执行此操作");
        return p;
    }
}
