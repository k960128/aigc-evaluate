package com.kant.llm.agent.chat.req;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSendReq implements Serializable {
    private String userId;
    private String conversationId;
    private String content;
}
