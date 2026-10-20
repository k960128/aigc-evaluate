package com.kant.llm.eval.sample.controller;

import com.kant.llm.eval.sample.dao.entity.AgentSampleMutationTacticsDO;
import com.kant.llm.eval.sample.dao.mapper.AgentSampleMutationTacticsMapper;
import com.kant.llm.eval.sample.service.impl.AgentSampleMutationTacticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证变异战术树接口的层级关系与响应结构，不依赖真实数据库。
 */
@ExtendWith(MockitoExtension.class)
class SampleFactoryMutationTacticsTreeTest {

    @Mock
    private AgentSampleMutationTacticsMapper mapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AgentSampleMutationTacticsServiceImpl service = new AgentSampleMutationTacticsServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        mockMvc = MockMvcBuilders.standaloneSetup(new SampleFactoryV2Controller(service)).build();
    }

    @Test
    void returnsNestedTreeUsingTacticsIdsInsteadOfPrimaryKeys() throws Exception {
        AgentSampleMutationTacticsDO root = tactic(1000L, 10L, 0L);
        AgentSampleMutationTacticsDO child = tactic(2000L, 20L, 10L);
        AgentSampleMutationTacticsDO sibling = tactic(2001L, 21L, 10L);
        AgentSampleMutationTacticsDO grandchild = tactic(3000L, 30L, 20L);
        child.setExplain("战术原理");
        child.setExample("变异示例");
        // 子节点先于父节点出现时，也应按业务战术ID正确关联。
        when(mapper.selectList(any())).thenReturn(List.of(child, grandchild, root, sibling));

        mockMvc.perform(get("/sample-factory/mutation-tactics/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(1000))
                .andExpect(jsonPath("$.data[0].mutationTacticsId").value(10))
                .andExpect(jsonPath("$.data[0].children", hasSize(2)))
                .andExpect(jsonPath("$.data[0].children[0].mutationTacticsId").value(20))
                .andExpect(jsonPath("$.data[0].children[0].mutationTacticsParentId").value(10))
                .andExpect(jsonPath("$.data[0].children[0].mutationTacticsCode").value("TACTIC_20"))
                .andExpect(jsonPath("$.data[0].children[0].mutationTacticsName").value("战术20"))
                .andExpect(jsonPath("$.data[0].children[0].explain").value("战术原理"))
                .andExpect(jsonPath("$.data[0].children[0].example").value("变异示例"))
                .andExpect(jsonPath("$.data[0].children[0].status").value(1))
                .andExpect(jsonPath("$.data[0].children[0].children[0].mutationTacticsId").value(30))
                .andExpect(jsonPath("$.data[0].children[0].children[0].children", hasSize(0)))
                .andExpect(jsonPath("$.data[0].children[1].mutationTacticsId").value(21))
                .andExpect(jsonPath("$.data[0].children[1].children", hasSize(0)));

        verify(mapper).selectList(any());
    }

    @Test
    void returnsEmptyArrayWhenNoTacticsExist() throws Exception {
        when(mapper.selectList(any())).thenReturn(List.of());

        mockMvc.perform(get("/sample-factory/mutation-tactics/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void returnsMissingParentsAsRootsAndPreservesDisabledStatus() throws Exception {
        AgentSampleMutationTacticsDO root = tactic(1000L, 10L, 0L);
        AgentSampleMutationTacticsDO nullParent = tactic(2000L, 20L, null);
        AgentSampleMutationTacticsDO orphan = tactic(3000L, 30L, 999L);
        orphan.setStatus(0);
        when(mapper.selectList(any())).thenReturn(List.of(root, nullParent, orphan));

        mockMvc.perform(get("/sample-factory/mutation-tactics/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].mutationTacticsId").value(10))
                .andExpect(jsonPath("$.data[1].mutationTacticsId").value(20))
                .andExpect(jsonPath("$.data[2].mutationTacticsId").value(30))
                .andExpect(jsonPath("$.data[2].mutationTacticsParentId").value(999))
                .andExpect(jsonPath("$.data[2].status").value(0))
                .andExpect(jsonPath("$.data[2].children", hasSize(0)));
    }

    @Test
    void treatsSelfReferencingTacticAsRoot() throws Exception {
        when(mapper.selectList(any())).thenReturn(List.of(tactic(1000L, 10L, 10L)));

        mockMvc.perform(get("/sample-factory/mutation-tactics/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].mutationTacticsId").value(10))
                .andExpect(jsonPath("$.data[0].children", hasSize(0)));
    }

    private AgentSampleMutationTacticsDO tactic(Long id, Long tacticsId, Long parentId) {
        AgentSampleMutationTacticsDO tactic = new AgentSampleMutationTacticsDO();
        tactic.setId(id);
        tactic.setMutationTacticsId(tacticsId);
        tactic.setMutationTacticsParentId(parentId);
        tactic.setMutationTacticsCode("TACTIC_" + tacticsId);
        tactic.setMutationTacticsName("战术" + tacticsId);
        tactic.setStatus(1);
        return tactic;
    }
}
