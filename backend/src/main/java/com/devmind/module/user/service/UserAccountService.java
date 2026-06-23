package com.devmind.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devmind.module.user.entity.UserAccount;
import com.devmind.module.user.mapper.UserAccountMapper;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserAccountService {

    private final UserAccountMapper userAccountMapper;

    public UserAccountService(UserAccountMapper userAccountMapper) {
        this.userAccountMapper = userAccountMapper;
    }

    public Optional<UserAccount> findByUsername(String username) {
        LambdaQueryWrapper<UserAccount> queryWrapper = new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username);
        return Optional.ofNullable(userAccountMapper.selectOne(queryWrapper));
    }

    public Optional<UserAccount> findById(Long id) {
        return Optional.ofNullable(userAccountMapper.selectById(id));
    }

    public boolean existsByUsername(String username) {
        LambdaQueryWrapper<UserAccount> queryWrapper = new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username);
        return userAccountMapper.exists(queryWrapper);
    }

    public void save(UserAccount userAccount) {
        userAccountMapper.insert(userAccount);
    }
}
