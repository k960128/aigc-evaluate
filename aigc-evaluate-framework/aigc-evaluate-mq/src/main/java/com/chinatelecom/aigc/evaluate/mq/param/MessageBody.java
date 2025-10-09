package com.chinatelecom.aigc.evaluate.mq.param;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 消息
 *
 * @author AIGC
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
public class MessageBody {
    /**
     * 消息id
     */
    private String msgId;

    private String userId;
    /**
     * 消息体
     */
    private String body;
}