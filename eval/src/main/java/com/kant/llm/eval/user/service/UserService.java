package com.kant.llm.eval.user.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.user.dao.entity.UserDO;
import com.kant.llm.eval.user.dto.req.LoginRequest;

public interface UserService extends IService<UserDO> {

    SaTokenInfo login(LoginRequest request);
}
