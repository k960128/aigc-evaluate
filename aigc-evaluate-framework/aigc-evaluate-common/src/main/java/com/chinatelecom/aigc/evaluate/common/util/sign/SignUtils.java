package com.chinatelecom.aigc.evaluate.common.util.sign;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;

@RestController
@Slf4j
@Component
public class SignUtils {

    // 授权字符串字段数
    public static final int AUTHORIZATION_ITEM_NUM = 7;
    // HMAC SHA256 算法常量
    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    public static final List<String> DEFAULT_HEADERS = Arrays.asList(
            HttpHeaders.HOST,
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.CONTENT_LENGTH,
            "Content-Md5");

    /**
     * 生成签名
     * @param signPrefix  签名前缀
     * @param appKey  应用密钥
     * @param method  HTTP请求方法
     * @param path    HTTP请求uri
     * @param params  HTTP请求参数
     * @param headers HTTP请求headers
     * @return 生成的签名
     */
    public static String genSignature(String signPrefix,String appKey, HttpMethod method, String path,
                                      Map<String, String> params, Map<String, String> headers,String signedHeaders) {
        log.debug("signPrefix: {}", signPrefix);
        log.debug("appKey: {}", appKey);
        log.debug("path: {}", path);
        log.debug("params: {}", params);
        log.debug("headers: {}", headers);
        log.debug("signedHeaders: {}", signedHeaders);

        // 格式化基础签名信息
        String signingKey;
        try {
            // 生成签名密钥
            signingKey = generateHmacSHA256Hex(signPrefix, appKey);
        } catch (Exception e) {
            log.error("{signPrefix}/{X-APP-ID}/{region}/{timestamp}/{expirationPeriodInSeconds}签名错误", e);
            throw new RuntimeException(e);
        }
        log.debug("appKey:[{}] signingKey:[{}]",appKey,signingKey);
        // 获取规范化的请求字符串
        String canonicalRequest = getCanonicalRequest(method, path, params, headers,signedHeaders);
        log.debug("请求内容签名信息 canonicalRequest:\n{}",canonicalRequest);
        String signature;
        try {
            // 生成最终签名
            signature = generateHmacSHA256Hex(canonicalRequest, signingKey);
        } catch (Exception e) {
            log.error("canonicalRequest 签名错误", e);
            throw new RuntimeException(e);
        }
        return signature;
    }

    /**
     * 获取规范化的请求字符串
     *
     * @param method  HTTP请求方法
     * @param path    HTTP请求uri
     * @param params  HTTP请求参数
     * @param headers HTTP请求headers
     * @param signedHeaders 签名头
     * @return 规范化的请求字符串
     */
    private static String getCanonicalRequest(HttpMethod method, String path, Map<String, String> params,
                                              Map<String, String> headers, String signedHeaders) {
        // 构建规范化的请求字符串
        StringBuilder canonicalRequest = new StringBuilder();
        canonicalRequest.append(method).append("\n");
        String canonicalURI;
        try {
            // 规范化请求URI
            canonicalURI = URLEncoder.encode(path, "utf-8").replaceAll("%2F", "/");
        } catch (UnsupportedEncodingException e) {
            log.error("url encode error", e);
            throw new RuntimeException(e);
        }
        canonicalRequest.append(canonicalURI).append("\n");

        // 获取规范化查询字符串
        canonicalRequest.append(getCanonicalQueryString(params)).append("\n");

        // 获取规范化请求头
        String canonicalHeaders = getCanonicalHeaders(headers,signedHeaders);
        canonicalRequest.append(canonicalHeaders);
        return canonicalRequest.toString();
    }

    /**
     * 获取规范化的查询字符串
     *
     * @param paramMap 查询参数映射
     * @return 规范化的查询字符串
     */
    private static String getCanonicalQueryString(Map<String, String> paramMap) {
        List<String> params = new ArrayList<>();
        for (Map.Entry<String, String> param : paramMap.entrySet()) {
            String key = param.getKey();
            String value = param.getValue();
            try {
                params.add(URLEncoder.encode(key, "UTF-8") + "=" + (StrUtil.isNotBlank(value) ? URLEncoder.encode(value, "UTF-8") : ""));
            } catch (UnsupportedEncodingException e) {
                log.error("params encode error", e);
                throw new RuntimeException(e);
            }
        }
        // 按字母顺序排序查询参数
//        params.sort(String.CASE_INSENSITIVE_ORDER);
        Collections.sort(params);
        return StrUtil.join( "&",params);
    }

    /**
     * 获取规范化的请求头
     * @param headerMap 请求头映射
     * @param signedHeaders 签名头
     * @return 规范化的请求头字符串
     */
    private static String getCanonicalHeaders(Map<String, String> headerMap, String signedHeaders) {
        if (headerMap.isEmpty()) {
            return "";
        }
        // 将请求头键名转换为小写
        Map<String, String> lowerCaseHeaderMap = new HashMap<>();
        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
            lowerCaseHeaderMap.put(entry.getKey().toLowerCase(), entry.getValue());
        }

        List<String> headerList = new ArrayList<>();
        List<String> headerNames;
        if (StrUtil.isNotBlank(signedHeaders)) {
            headerNames = StrUtil.split(signedHeaders,";");
        } else {
            headerNames = DEFAULT_HEADERS;
        }
        for (String headerName : headerNames) {
            String lowerCaseHeaderName = headerName.trim().toLowerCase();
            String headerValue = lowerCaseHeaderMap.get(lowerCaseHeaderName);
            if (StrUtil.isNotBlank(headerValue)) {
                try {
                    headerList.add(lowerCaseHeaderName + ":" + StrUtil.trim(URLEncoder.encode(headerValue, "UTF-8")));
                } catch (UnsupportedEncodingException e) {
                    log.error("headers encode error", e);
                    throw new RuntimeException(e);
                }
            }
        }
        // 按字母顺序排序请求头
//        headerList.sort(String.CASE_INSENSITIVE_ORDER);
        Collections.sort(headerList);
        return StrUtil.join("\n",headerList );
    }

    /**
     * 生成一个 HmacSHA256 哈希值。
     *
     * @param data 要进行哈希处理的数据。
     * @param key  用于哈希处理的密钥。
     * @return 生成的 HmacSHA256 哈希值，以十六进制编码表示。
     * @throws Exception 如果哈希处理失败，则抛出异常。
     */
    private static String generateHmacSHA256Hex(String data, String key) throws Exception {
        SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA256_ALGORITHM);
        Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
        mac.init(signingKey);
        byte[] rawHmac = mac.doFinal(data.getBytes());
        return bytesToHex(rawHmac);
    }

    /**
     * 将字节数组转换为十六进制字符串。
     *
     * @param bytes 要转换的字节数组。
     * @return 字节数组的十六进制表示。
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

