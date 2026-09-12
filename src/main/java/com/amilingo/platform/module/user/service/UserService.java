package com.amilingo.platform.module.user.service;

import com.amilingo.platform.common.dto.request.RegisterRequest;
import com.amilingo.platform.common.exceptions.CacheException;
import com.amilingo.platform.common.exceptions.CacheMissedException;
import com.amilingo.platform.common.exceptions.EmailNotFoundException;
import com.amilingo.platform.common.exceptions.PasswordIncorrectException;
import com.amilingo.platform.common.util.Snowflake;
import com.amilingo.platform.component.abstracts.IUserService;
import com.amilingo.platform.component.caching.UserCache;
import com.amilingo.platform.module.user.entity.user.User;
import com.amilingo.platform.module.user.repository.UserRepository;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class UserService implements IUserService {
    private final UserRepository userRepository;
    private final UserCache userCacheEngine;
    private final PasswordEncoder passwordEncoder;
    @Autowired
    public UserService(UserRepository userRepository, UserCache userCacheEngine, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userCacheEngine = userCacheEngine;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void saveUser(@NonNull User user) {
        try {
            userCacheEngine.cache(user);
        } catch (CacheException e){
            User savedUser = userRepository.save(user);
            log.info("User saved to database: {}", savedUser.getId());
        }
    }
    @Override
    public User getUserById(@NonNull Long userId) {
        try {
            return userCacheEngine.getCachedById(userId);
        } catch (CacheMissedException e) {
            log.debug("User not in cache, fetching from database: {}", userId);
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                userCacheEngine.cache(user);
                return user;
            }
        }
        return null;
    }

    @Override
    public User getUserByEmail(@NonNull String email) {
        try {
            return userCacheEngine.getUserByEmail(email);
        } catch (Exception e) {
            Optional<User> userOpt = userRepository.findUserByEmail(email);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                userCacheEngine.setUserEmailKey(user);
                return user;
            }
        }
        return null;
    }

    @Override
    public Page<User> getAllUsers(Pageable page) {
        return this.userRepository.findAll(page);
    }

    @Override
    public User loginViaEmailPwd(String email, String password) throws EmailNotFoundException, PasswordIncorrectException {
        User user = getUserByEmail(email);
        if (user != null){
            if (passwordEncoder.matches(password, user.getPasswordHash())){
                userCacheEngine.cache(user);
                return user;
            }
            throw new PasswordIncorrectException("");
        }
        throw new EmailNotFoundException("");
    }

    @Override
    public User loginViaEmailValidation(String email, String code) {
        return null;
    }

    @Override
    public @Nullable User register(RegisterRequest request, String password) throws IllegalArgumentException{
        String hashedPassword = (passwordEncoder.encode(password));
        User user = User
                .builder()
                .id(Snowflake.nextId())
                .createdAt(LocalDateTime.now())
                .email(request.getEmail())
                .username(request.getName())
                .passwordHash(hashedPassword)
                .build();
        try {
            userRepository.save(user);
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        return user;
    }
}
