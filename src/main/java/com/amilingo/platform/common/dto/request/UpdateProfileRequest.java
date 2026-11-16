package com.amilingo.platform.common.dto.request;

/**
 * 修改当前用户资料（头像 / 昵称 / 介绍 / 用户名）。
 * 字段为 null 表示不修改（PATCH 语义）；传空串表示清空该字段（会做合法性校验）。
 */
public record UpdateProfileRequest(String username, String nickname, String avatarUrl, String bio) {
}
