package com.kant.llm.eval.newpipeline.service;

import com.kant.llm.eval.task.controller.EvalTaskController;
import com.kant.llm.eval.task.dto.resp.RetryEvalResultVO;
import com.kant.llm.eval.task.service.EvalResultRetryService;
import com.kant.llm.eval.task.service.EvalTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EvalResultRetryControllerTest {
    private final EvalResultRetryService retries = mock(EvalResultRetryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new EvalTaskController(mock(EvalTaskService.class), retries)).build();
    }

    @Test
    void acceptsValidRequestAndReturnsExecutionVersion() throws Exception {
        when(retries.retry(any())).thenReturn(new RetryEvalResultVO(1L, 2, "SAFETY_EVALUATE", "request-1", "ACCEPTED"));

        mvc.perform(post("/eval-task/result/retry").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultDetailId\":1,\"mode\":\"AUTO\",\"requestId\":\"request-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.executionVersion").value(2))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
    }

    @Test
    void rejectsMissingRequestId() throws Exception {
        mvc.perform(post("/eval-task/result/retry").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultDetailId\":1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(retries);
    }

    @Test
    void rejectsUnsupportedMode() throws Exception {
        mvc.perform(post("/eval-task/result/retry").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultDetailId\":1,\"requestId\":\"one\",\"mode\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(retries);
    }
}
