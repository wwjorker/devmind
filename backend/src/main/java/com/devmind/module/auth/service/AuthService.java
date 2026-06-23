package com.devmind.module.auth.service;

import com.devmind.common.api.ResultCode;
import com.devmind.common.exception.BizException;
import com.devmind.common.security.JwtTokenProvider;
import com.devmind.module.auth.dto.LoginRequest;
import com.devmind.module.auth.dto.RegisterRequest;
import com.devmind.module.auth.vo.LoginResponse;
import com.devmind.module.auth.vo.UserProfileResponse;
import com.devmind.module.user.entity.UserAccount;
import com.devmind.module.user.service.UserAccountService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private static final Integer USER_STATUS_ENABLED = 1;
    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserAccountService userAccountService,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        if (userAccountService.existsByUsername(request.getUsername())) {
            throw new BizException(ResultCode.CONFLICT, "username already exists");
        }

        UserAccount userAccount = new UserAccount();
        userAccount.setUsername(request.getUsername());
        userAccount.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userAccount.setNickname(resolveNickname(request));
        userAccount.setEmail(request.getEmail());
        userAccount.setStatus(USER_STATUS_ENABLED);
        userAccountService.save(userAccount);

        return toProfile(userAccount);
    }

    public LoginResponse login(LoginRequest request) {
        UserAccount userAccount = userAccountService.findByUsername(request.getUsername())
                .orElseThrow(() -> new BizException(ResultCode.UNAUTHORIZED, "invalid username or password"));

        if (!USER_STATUS_ENABLED.equals(userAccount.getStatus())) {
            throw new BizException(ResultCode.FORBIDDEN, "account is disabled");
        }
        if (!passwordEncoder.matches(request.getPassword(), userAccount.getPasswordHash())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "invalid username or password");
        }

        String token = jwtTokenProvider.createToken(userAccount.getId(), userAccount.getUsername());
        return new LoginResponse(token, "Bearer", jwtTokenProvider.getExpireSeconds());
    }

    public UserProfileResponse getCurrentUser(Long userId) {
        UserAccount userAccount = userAccountService.findById(userId)
                .orElseThrow(() -> new BizException(ResultCode.UNAUTHORIZED, "user not found"));
        return toProfile(userAccount);
    }

    private String resolveNickname(RegisterRequest request) {
        if (StringUtils.hasText(request.getNickname())) {
            return request.getNickname();
        }
        return request.getUsername();
    }

    private UserProfileResponse toProfile(UserAccount userAccount) {
        return new UserProfileResponse(
                userAccount.getId(),
                userAccount.getUsername(),
                userAccount.getNickname(),
                userAccount.getEmail()
        );
    }
}
