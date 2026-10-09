package com.amilingo.platform.component.abstracts;

import com.amilingo.platform.common.dto.request.RegisterRequest;
import com.amilingo.platform.module.user.entity.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

public interface IUserService {
    @Transactional
    void saveUser(User user);
    User getUserById(Long id);
    User getUserByEmail(String email);
    Page<User> getAllUsers(Pageable page);
    User loginViaEmailPwd(String email, String password);
    User loginViaEmailValidation(String email, String code);

    User register(RegisterRequest payload, String password);
}
