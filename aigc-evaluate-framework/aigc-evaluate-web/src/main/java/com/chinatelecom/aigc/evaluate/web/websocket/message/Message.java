package com.chinatelecom.aigc.evaluate.web.websocket.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private String type;     // 消息类型，比如 report, progress, complete
    private Object data;  // 消息内容
}
