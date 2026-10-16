package com.amilingo.platform.module.interest.service;

import com.amilingo.platform.module.interest.entity.InterestTag;
import com.amilingo.platform.module.interest.entity.UserInterest;
import com.amilingo.platform.module.interest.repository.InterestTagRepository;
import com.amilingo.platform.module.interest.repository.UserInterestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 兴趣标签与用户兴趣服务。 */
@Service
public class InterestService {

    private final InterestTagRepository interestTagRepository;
    private final UserInterestRepository userInterestRepository;

    public InterestService(InterestTagRepository interestTagRepository,
                           UserInterestRepository userInterestRepository) {
        this.interestTagRepository = interestTagRepository;
        this.userInterestRepository = userInterestRepository;
    }

    @Transactional(readOnly = true)
    public List<InterestTag> listTags(String category) {
        if (category == null || category.isBlank()) {
            return interestTagRepository.findAll();
        }
        return interestTagRepository.findByCategory(category);
    }

    @Transactional
    public InterestTag saveTag(InterestTag tag) {
        return interestTagRepository.save(tag);
    }

    @Transactional(readOnly = true)
    public List<UserInterest> myInterests(Long userId) {
        return userInterestRepository.findByUserId(userId);
    }

    @Transactional
    public List<UserInterest> setInterests(Long userId, List<InterestInput> interests) {
        userInterestRepository.deleteAll(userInterestRepository.findByUserId(userId));

        List<UserInterest> result = new ArrayList<>();
        if (interests != null) {
            Set<Long> seenTagIds = new LinkedHashSet<>();
            for (InterestInput input : interests) {
                if (input == null || input.tagId() == null || !seenTagIds.add(input.tagId())) {
                    continue;
                }
                UserInterest userInterest = UserInterest.builder()
                        .userId(userId)
                        .tagId(input.tagId())
                        .level(input.level() != null ? input.level() : 50)
                        .build();
                result.add(userInterestRepository.save(userInterest));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<InterestTag> recommend(Long userId) {
        Set<Long> followedTagIds = new LinkedHashSet<>();
        for (UserInterest userInterest : userInterestRepository.findByUserId(userId)) {
            followedTagIds.add(userInterest.getTagId());
        }
        List<InterestTag> recommendations = new ArrayList<>();
        for (InterestTag tag : interestTagRepository.findAll()) {
            if (!followedTagIds.contains(tag.getId())) {
                recommendations.add(tag);
                if (recommendations.size() >= 10) {
                    break;
                }
            }
        }
        return recommendations;
    }

    /** 兴趣设置入参。 */
    public record InterestInput(Long tagId, Integer level) {
    }
}
