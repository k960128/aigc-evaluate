package com.chinatelecom.aigc.evaluate.web.interceptor;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.chinatelecom.aigc.evaluate.common.exception.enums.GlobalErrorCodeConstants;
import com.chinatelecom.aigc.evaluate.common.pojo.PermissionResponseDTO;
import com.chinatelecom.aigc.evaluate.common.util.http.HttpUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static com.chinatelecom.aigc.evaluate.common.exception.util.ServiceExceptionUtil.exception;

/**
 * @author : biols
 * create at:  2024/8/9 17:47
 * @description:
 */
@Slf4j
@Component
public class RequestInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_URL = "/admin-api/system/auth/get-permission-info";

    @Value("${authorization.ip:127.0.0.1}")
    private String authorizationIp;

    @Value("${authorization.port:8888}")
    private String authorizationPort;

    @Value("${authorization.open:false}")
    private Boolean openAuthorization;


    private static final Cache<String, PermissionResponseDTO> authorizeCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();


    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws IOException {

        if (!openAuthorization) {
            return true;
        }
        String requestURI = request.getRequestURI();
        String endpoint = requestURI.replace("/aigc/evaluate", "");
        String token = request.getHeader("Authorization");

        if ("/v2/api-docs".equals(endpoint)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "This endpoint is disabled");
            return false;
        }

        if (StringUtils.isEmpty(token)) {
            throw exception(GlobalErrorCodeConstants.UNAUTHORIZED);
        }

        PermissionResponseDTO permissionResponse = getAuthorizationFromCacheOrService(token);

        if (permissionResponse == null || permissionResponse.getCode() == null) {
            throw exception(GlobalErrorCodeConstants.UNAUTHORIZED);
        }

        if (permissionResponse.getCode().equals(401)) {
            throw exception(GlobalErrorCodeConstants.UNAUTHORIZED);
        }

        PermissionResponseDTO.DetailData data = permissionResponse.getData();
        if (data == null) {
            throw exception(GlobalErrorCodeConstants.FORBIDDEN);
        }

        if (isSuperAdmin(data) || hasPermission(data, endpoint)) {
            return true;
        } else {
            throw exception(GlobalErrorCodeConstants.FORBIDDEN);
        }
    }

    private PermissionResponseDTO getAuthorizationFromCacheOrService(String token) throws IOException {
        PermissionResponseDTO cachedResponse = authorizeCache.getIfPresent(token);

        if (cachedResponse != null) {
            return cachedResponse;
        }

        HashMap<String, String> headers = new HashMap<>();
        headers.put("Authorization", token);
        headers.put("Tenant-id", String.valueOf(1));

        String responseData = HttpUtils.doGet(String.format("%s:%s%s", authorizationIp, authorizationPort, AUTHORIZATION_URL), headers);
        PermissionResponseDTO response = JSON.parseObject(responseData, PermissionResponseDTO.class);
        if (response != null && response.getCode() != null && response.getCode() == 0) {
            authorizeCache.put(token, response);
        }
        return response;
    }

    private boolean isSuperAdmin(PermissionResponseDTO.DetailData data) {
        return !CollectionUtil.isEmpty(data.getRoles()) && data.getRoles().contains("super_admin");
    }

    private boolean hasPermission(PermissionResponseDTO.DetailData data, String endpoint) {
        return !CollectionUtil.isEmpty(data.getPermissions()) && data.getPermissions().contains(endpoint);
    }
}

