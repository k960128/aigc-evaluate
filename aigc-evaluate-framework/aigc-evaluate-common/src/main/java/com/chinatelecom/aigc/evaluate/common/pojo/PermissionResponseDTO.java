package com.chinatelecom.aigc.evaluate.common.pojo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author : biols
 * create at:  2024/8/11 22:36
 * @description:
 */
@Data
public class PermissionResponseDTO implements Serializable {

    private Integer code;

    private String msg;

    private DetailData data;


    @Data
    public static class DetailData {

        private List<String> roles;

        private List<String> permissions;
    }
}

