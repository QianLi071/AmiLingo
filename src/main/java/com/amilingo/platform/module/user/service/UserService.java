package com.amilingo.platform.module.user.service;

import com.amilingo.platform.common.dto.request.RegisterRequest;
import com.amilingo.platform.common.dto.request.UpdateProfileRequest;
import com.amilingo.platform.common.exceptions.*;
import com.amilingo.platform.common.util.Snowflake;
import com.amilingo.platform.common.util.StringUtil;
import com.amilingo.platform.component.abstracts.IUserService;
import com.amilingo.platform.component.caching.UserCache;
import com.amilingo.platform.component.services.EmailValidationService;
import com.amilingo.platform.module.user.entity.user.User;
import com.amilingo.platform.module.user.repository.UserRepository;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class UserService implements IUserService {
    /** 用户名：中英文/数字/下划线，2-32 位 */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_\\u4e00-\\u9fa5]{2,32}$");

    private final UserRepository userRepository;
    private final UserCache userCacheEngine;
    private final PasswordEncoder passwordEncoder;
    private final EmailValidationService emailValidationService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public UserService(UserRepository userRepository, UserCache userCacheEngine, PasswordEncoder passwordEncoder,
                       EmailValidationService emailValidationService) {
        this.userRepository = userRepository;
        this.userCacheEngine = userCacheEngine;
        this.passwordEncoder = passwordEncoder;
        this.emailValidationService = emailValidationService;
    }

    @Override
    @Transactional
    public void saveUser(@NonNull User user) {
        userRepository.save(user);
        bestEffortCache(() -> userCacheEngine.cache(user));
    }

    @Transactional
    public void changePassword(@NonNull String password, @NonNull Long userId){
        User user = getUserById(userId);
        if (user == null){
            throw new UserNotFoundException("用户不存在");
        }
        user.setPasswordHash(passwordEncoder.encode(password));
        saveUser(user);
    }

    @Override
    public User getUserById(@NonNull Long userId) {
        try {
            return userCacheEngine.getCachedById(userId);
        } catch (Exception e) {
            log.debug("缓存不可用，从数据库加载用户: {}", userId);
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                bestEffortCache(() -> userCacheEngine.cache(user));
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
                bestEffortCache(() -> userCacheEngine.setUserEmailKey(user));
                return user;
            }
        }
        return null;
    }

    @Override
    public User getUserByPhone(String phone) {
        if (!StringUtil.isValidChinaMobile(phone)) {
            return null;
        }
        Optional<User> userOpt = userRepository.findUserByPhone(phone);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            bestEffortCache(() -> userCacheEngine.cache(user));
            return user;
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
                bestEffortCache(() -> userCacheEngine.cache(user));
                return user;
            }
            throw new PasswordIncorrectException("密码错误");
        }
        throw new EmailNotFoundException("");
    }

    @Override
    public User loginViaEmailValidation(String email, String code) {
        return null;
    }

    @Override
    public User findOrCreateByPhone(String phone) {
        User existing = getUserByPhone(phone);
        if (existing != null) {
            return existing;
        }
        // 首次手机号登录自动注册：
        //  - 用户名随机生成（用户可通过资料修改接口更改）；
        //  - 密码为随机占位哈希（无对应明文，密码登录永远无法通过），用户可绑定邮箱后自行设置密码
        User user = User
                .builder()
                .id(Snowflake.nextId())
                .createdAt(LocalDateTime.now())
                .phone(phone)
                .username(generateUsername())
                .passwordHash(passwordEncoder.encode(randomSentinelPassword()))
                .build();
        try {
            // 故意不加 @Transactional：save 单语句独立提交，
            // 便于捕获唯一约束冲突（并发首登）后回查获胜方
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("手机号并发注册冲突，回查已有用户: {}", StringUtil.maskPhone(phone));
            User winner = getUserByPhone(phone);
            if (winner != null) {
                return winner;
            }
            throw new ApiException(HttpStatus.CONFLICT, "手机号注册冲突，请重试");
        }
        bestEffortCache(() -> userCacheEngine.cache(user));
        log.info("手机号首次登录自动注册: userId={}", user.getId());
        return user;
    }

    @Override
    @Transactional
    public User bindEmail(Long userId, String email) {
        User user = requireExistingUser(userId);
        String normalized;
        try {
            normalized = emailValidationService.normalize(email);
        } catch (BadRequestException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "邮箱格式不正确");
        }
        if (normalized.equals(user.getEmail())) {
            // 幂等：邮箱未变化
            return user;
        }
        User existing = getUserByEmail(normalized);
        if (existing != null && !existing.getId().equals(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "该邮箱已被其他账号绑定");
        }
        String oldEmail = user.getEmail();
        user.setEmail(normalized);
        saveUser(user);
        // 换绑后必须清理旧"邮箱 → userId"缓存映射，否则旧邮箱仍可命中该用户（安全风险）
        if (oldEmail != null && !oldEmail.equals(normalized)) {
            bestEffortCache(() -> userCacheEngine.deleteUserEmailKey(oldEmail));
        }
        bestEffortCache(() -> userCacheEngine.setUserEmailKey(user));
        log.info("用户绑定/换绑邮箱: userId={}", userId);
        return user;
    }

    @Override
    @Transactional
    public User bindPhone(Long userId, String phone) {
        User user = requireExistingUser(userId);
        if (!StringUtil.isValidChinaMobile(phone)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "手机号格式不正确");
        }
        if (phone.equals(user.getPhone())) {
            // 幂等：手机号未变化
            return user;
        }
        User existing = getUserByPhone(phone);
        if (existing != null && !existing.getId().equals(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "该手机号已被其他账号绑定");
        }
        user.setPhone(phone);
        saveUser(user);
        log.info("用户绑定/换绑手机号: userId={}", userId);
        return user;
    }

    @Override
    @Transactional
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        User user = requireExistingUser(userId);
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (request.username() != null) {
            applyUsername(user, request.username());
        }
        if (request.nickname() != null) {
            String nickname = request.nickname().trim();
            if (nickname.length() > 32) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "昵称最长 32 个字符");
            }
            user.setNickname(nickname);
        }
        if (request.avatarUrl() != null) {
            String avatarUrl = request.avatarUrl().trim();
            if (!isLegalAvatarUrl(avatarUrl)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "头像地址不合法（仅支持 http(s) 绝对地址或以 / 开头的站内路径）");
            }
            user.setAvatarUrl(avatarUrl);
        }
        if (request.bio() != null) {
            if (request.bio().length() > 500) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "个人介绍最长 500 个字符");
            }
            user.setBio(request.bio());
        }
        saveUser(user);
        log.info("用户资料已更新: userId={}", userId);
        return user;
    }

    private void applyUsername(User user, String rawUsername) {
        String username = rawUsername.trim();
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户名只能包含中英文、数字、下划线，长度 2-32");
        }
        if (username.equals(user.getUsername())) {
            return;
        }
        Optional<User> existing = userRepository.findUserByUsername(username);
        if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "用户名已被占用");
        }
        user.setUsername(username);
    }

    /**
     * 头像地址白名单校验：仅允许 http(s) 绝对地址或站内相对路径，
     * 阻止 javascript: / data: 等危险协议与含空白的 URL。
     */
    private boolean isLegalAvatarUrl(String url) {
        return url.length() <= 512
                && !url.contains(" ")
                && (url.startsWith("https://") || url.startsWith("http://") || url.startsWith("/"));
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
        // 数据库必须真实落库；缓存仅尽力，Redis 不可用时降级
        try {
            userRepository.save(user);
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        bestEffortCache(() -> userCacheEngine.setUserEmailKey(user));
        return user;
    }

    private User requireExistingUser(Long userId) {
        User user = getUserById(userId);
        if (user == null) {
            throw new UserNotFoundException("用户不存在");
        }
        return user;
    }

    /** 随机用户名：amilingo_ + 8 位十六进制，冲突由唯一约束兜底（冲突时接口返回 409 可重试） */
    private String generateUsername() {
        return "amilingo_" + String.format("%08x", secureRandom.nextLong() & 0xFFFFFFFFL);
    }

    /** 随机占位密码（256-bit 随机字符串的 BCrypt 哈希）：无明文对应，密码登录永远无法通过 */
    private String randomSentinelPassword() {
        return UUID.randomUUID() + UUID.randomUUID().toString();
    }

    /** 缓存尽力而为：Redis 异常只记录日志，不影响真实数据库操作。 */
    private void bestEffortCache(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("Redis 缓存不可用，已降级直连数据库: {}", e.getMessage());
        }
    }
}
