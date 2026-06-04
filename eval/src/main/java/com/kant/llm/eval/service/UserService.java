package com.kant.llm.eval.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kant.llm.eval.dao.entity.UserDO;
import com.kant.llm.eval.dto.req.LoginRequest;

public interface UserService extends IService<UserDO> {

    SaTokenInfo login(LoginRequest request);
}
