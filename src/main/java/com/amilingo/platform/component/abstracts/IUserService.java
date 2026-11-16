package com.amilingo.platform.component.abstracts;

import com.amilingo.platform.common.dto.request.RegisterRequest;
import com.amilingo.platform.common.dto.request.UpdateProfileRequest;
import com.amilingo.platform.module.user.entity.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

public interface IUserService {
    @Transactional
    void saveUser(User user);
    User getUserById(Long id);
    User getUserByEmail(String email);
    /** 按手机号查询用户，未绑定返回 null */
    User getUserByPhone(String phone);
    Page<User> getAllUsers(Pageable page);
    User loginViaEmailPwd(String email, String password);
    User loginViaEmailValidation(String email, String code);

    /**
     * 手机号验证码登录：手机号已存在则返回该用户，不存在则自动注册
     * （用户名随机生成，密码为随机占位哈希不可用于密码登录）。
     */
    User findOrCreateByPhone(String phone);

    /** 绑定/换绑邮箱（须已通过邮箱验证码校验）。冲突时抛 409。 */
    User bindEmail(Long userId, String email);

    /** 绑定/换绑手机号（须已通过短信验证码校验）。冲突时抛 409。 */
    User bindPhone(Long userId, String phone);

    /** 修改用户资料（昵称/头像/介绍/用户名，PATCH 语义：null 字段不修改） */
    User updateProfile(Long userId, UpdateProfileRequest request);

    User register(RegisterRequest payload, String password);
}
