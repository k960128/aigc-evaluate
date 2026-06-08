package com.kant.llm.eval.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kant.llm.eval.common.errorcode.BaseErrorCode;
import com.kant.llm.eval.common.exception.ServiceException;
import com.kant.llm.eval.dao.entity.UserDO;
import com.kant.llm.eval.dao.mapper.UserMapper;
import com.kant.llm.eval.dto.req.LoginRequest;
import com.kant.llm.eval.service.UserService;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    @Override
    public SaTokenInfo login(LoginRequest request) {
        UserDO userDO = getOne(new LambdaQueryWrapper<UserDO>()
                .eq(UserDO::getUsername, request.getUsername())
                .last("limit 1"));
        if (userDO == null || !Objects.equals(userDO.getPassword(), request.getPassword())) {
            throw new ServiceException("用户名或密码错误", BaseErrorCode.PASSWORD_VERIFY_ERROR);
        }

        StpUtil.login(userDO.getId());
        return StpUtil.getTokenInfo();
    }
}
