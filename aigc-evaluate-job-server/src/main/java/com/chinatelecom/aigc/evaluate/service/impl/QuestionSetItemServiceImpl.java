package com.chinatelecom.aigc.evaluate.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.chinatelecom.aigc.evaluate.common.enums.QuestionCategoryEnum;
import com.chinatelecom.aigc.evaluate.common.enums.QuestionDifficultyEnum;
import com.chinatelecom.aigc.evaluate.common.pojo.PageResult;
import com.chinatelecom.aigc.evaluate.domain.QuestionDO;
import com.chinatelecom.aigc.evaluate.domain.QuestionSetInfoDO;
import com.chinatelecom.aigc.evaluate.domain.QuestionSetItemDO;
import com.chinatelecom.aigc.evaluate.domain.QuestionTagInfoDO;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSetItemPageReq;
import com.chinatelecom.aigc.evaluate.dto.req.QuestionSetPageByReq;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionSetItemResp;
import com.chinatelecom.aigc.evaluate.dto.resp.QuestionSetItemStatisticsResp;
import com.chinatelecom.aigc.evaluate.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.chinatelecom.aigc.evaluate.mapper.QuestionMapper;
import com.chinatelecom.aigc.evaluate.mapper.QuestionSetInfoMapper;
import com.chinatelecom.aigc.evaluate.mapper.QuestionSetItemMapper;
import com.chinatelecom.aigc.evaluate.mapper.QuestionTagInfoMapper;
import com.chinatelecom.aigc.evaluate.service.QuestionSetItemService;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QuestionSetItemServiceImpl implements QuestionSetItemService {

    private final QuestionSetInfoMapper questionSetInfoMapper;
    private final QuestionMapper questionMapper;
    private final QuestionSetItemMapper questionSetItemMapper;

    private final QuestionTagInfoMapper questionTagInfoMapper;

    public QuestionSetItemServiceImpl(QuestionSetInfoMapper questionSetInfoMapper,
                                      QuestionMapper questionMapper,
                                      QuestionSetItemMapper questionSetItemMapper,
                                      QuestionTagInfoMapper questionTagInfoMapper) {
        this.questionSetInfoMapper = questionSetInfoMapper;
        this.questionMapper = questionMapper;
        this.questionSetItemMapper = questionSetItemMapper;
        this.questionTagInfoMapper = questionTagInfoMapper;
    }

    @Override
    public List<QuestionSetItemResp> list(List<Long> ids, Boolean distinct) {
        List<QuestionSetItemResp> respList = new ArrayList<>();
        // 先查出所有题集
        List<QuestionSetInfoDO> questionSetInfoDOList = questionSetInfoMapper.selectList(new LambdaQueryWrapperX<QuestionSetInfoDO>()
                .in(QuestionSetInfoDO::getId, ids));
        if (CollectionUtil.isNotEmpty(questionSetInfoDOList)) {
            Map<Long, List<QuestionSetInfoDO>> questionSetByIdMap = questionSetInfoDOList.stream().collect(Collectors.groupingBy(QuestionSetInfoDO::getId));
            // 查询映射习题
            List<QuestionSetItemDO> questionSetItemDOList = questionSetItemMapper.selectListByQuestionSetIds(questionSetInfoDOList.stream().map(QuestionSetInfoDO::getId).collect(Collectors.toList()));
            // 查出题目
            Map<String, List<QuestionDO>> questionByQuestionIdMap =
                    questionMapper.selectList().stream().collect(Collectors.groupingBy(QuestionDO::getQuestionId));
            // 组装数据
            questionSetItemDOList.forEach(itemDO -> {
                QuestionSetItemResp questionSetItemResp = new QuestionSetItemResp();
                questionSetItemResp.setId(itemDO.getId());
                questionSetItemResp.setQuestionSetId(itemDO.getQuestionSetId());
                questionSetItemResp.setQuestionSetName(questionSetByIdMap.get(itemDO.getQuestionSetId()).get(0).getQuestionSetName());
                questionSetItemResp.setQuestionId(itemDO.getQuestionId());
                questionSetItemResp.setQuestionVersion(itemDO.getQuestionVersion());
                questionSetItemResp.setTitle(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getTitle());
                questionSetItemResp.setCategory(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getCategory());
                questionSetItemResp.setTags(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getTags());
                questionSetItemResp.setDifficulty(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getDifficulty());
                questionSetItemResp.setAttackMethod(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getAttackMethod());
                respList.add(questionSetItemResp);
            });
        }

        if (distinct && CollectionUtil.isNotEmpty(respList)) {
            // resultList去重
            // 使用 Stream API 和 Collectors.toMap 进行去重
            Map<String, QuestionSetItemResp> uniqueMap = respList.stream()
                    .collect(Collectors.toMap(QuestionSetItemResp::getQuestionId,
                            item -> item, (existing, replacement) -> existing));
            // 如果你需要将结果转换回 List，可以这样做
            return new ArrayList<>(uniqueMap.values());
        }
        return respList;
    }

    @Override
    public PageResult<QuestionSetItemResp> getQuestionSetItemPage(QuestionSetItemPageReq req) {
        // 先查出所有题集
        List<QuestionSetInfoDO> questionSetInfoDOList = questionSetInfoMapper.selectList(new LambdaQueryWrapperX<QuestionSetInfoDO>()
                .likeIfPresent(QuestionSetInfoDO::getQuestionSetName, req.getQuestionSetName()));
        if (CollectionUtil.isNotEmpty(questionSetInfoDOList)) {
            // 获取对应的题目
            List<QuestionDO> questionDOList = questionMapper.selectList(new LambdaQueryWrapperX<QuestionDO>()
                    .likeIfPresent(QuestionDO::getTitle, req.getQuestionTitle()));

            PageResult<QuestionSetItemDO> questionSetItemDOPageResult = questionSetItemMapper.selectPage(req, new LambdaQueryWrapperX<QuestionSetItemDO>()
                    .inIfPresent(QuestionSetItemDO::getQuestionSetId, questionSetInfoDOList.stream().map(QuestionSetInfoDO::getId).collect(Collectors.toList()))
                    .inIfPresent(QuestionSetItemDO::getQuestionId, questionDOList.stream().map(QuestionDO::getQuestionId).collect(Collectors.toList())));

            // 将题集转换成Map
            Map<Long, List<QuestionSetInfoDO>> questionSetByIdMap =
                    questionSetInfoDOList.stream().collect(Collectors.groupingBy(QuestionSetInfoDO::getId));
            // 将题目转换成Map
            Map<String, List<QuestionDO>> questionByQuestionIdMap =
                    questionMapper.selectList().stream().collect(Collectors.groupingBy(QuestionDO::getQuestionId));

            List<QuestionSetItemResp> respList = new ArrayList<>();
            questionSetItemDOPageResult.getList().forEach(itemDO -> {
                QuestionSetItemResp questionSetItemResp = new QuestionSetItemResp();
                questionSetItemResp.setId(itemDO.getId());
                questionSetItemResp.setQuestionSetId(itemDO.getQuestionSetId());
                questionSetItemResp.setQuestionSetName(questionSetByIdMap.get(itemDO.getQuestionSetId()).get(0).getQuestionSetName());
                questionSetItemResp.setQuestionId(itemDO.getQuestionId());
                questionSetItemResp.setQuestionVersion(itemDO.getQuestionVersion());
                questionSetItemResp.setTitle(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getTitle());
                questionSetItemResp.setCategory(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getCategory());
                questionSetItemResp.setTags(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getTags());
                questionSetItemResp.setDifficulty(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getDifficulty());
                questionSetItemResp.setAttackMethod(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getAttackMethod());
                respList.add(questionSetItemResp);
            });

            PageResult<QuestionSetItemResp> pageResult = new PageResult<>();
            pageResult.setList(respList);
            pageResult.setTotal(questionSetItemDOPageResult.getTotal());

            return pageResult;
        }
        return null;
    }

    @Override
    public PageResult<QuestionSetItemResp> getQuestionByIdPage(QuestionSetPageByReq req) {
//        QuestionSetInfoDO questionSetInfoDO = questionSetInfoMapper.selectById(req.getQuestionSetId());
//        if (ObjectUtil.isNotNull(questionSetInfoDO)) {
//            // 获取对应的题目
//            List<QuestionDO> questionDOList = questionMapper.selectList(new LambdaQueryWrapperX<QuestionDO>()
//                    .likeIfPresent(QuestionDO::getTitle, req.getQuestionTitle())
//                    .eqIfPresent(QuestionDO::getCategory, req.getQuestionCategory()));
//            if (CollectionUtil.isEmpty(questionDOList)) {
//                return null;
//            }
//            PageResult<QuestionSetItemDO> questionSetItemDOPageResult = questionSetItemMapper.selectPage(req, new LambdaQueryWrapperX<QuestionSetItemDO>()
//                    .eq(QuestionSetItemDO::getQuestionSetId, questionSetInfoDO.getId())
//                    .inIfPresent(QuestionSetItemDO::getQuestionId, questionDOList.stream().map(QuestionDO::getQuestionId).collect(Collectors.toList()))
//            );
//
//            // 将题目转换成Map
//            Map<String, List<QuestionDO>> questionByQuestionIdMap =
//                    questionMapper.selectList().stream().collect(Collectors.groupingBy(QuestionDO::getQuestionId));
//            List<QuestionSetItemResp> respList = new ArrayList<>();
//            questionSetItemDOPageResult.getList().forEach(itemDO -> {
//                QuestionSetItemResp questionSetItemResp = new QuestionSetItemResp();
//                questionSetItemResp.setId(itemDO.getId());
//                questionSetItemResp.setQuestionSetId(itemDO.getQuestionSetId());
//                questionSetItemResp.setQuestionSetName(questionSetInfoDO.getQuestionSetName());
//                questionSetItemResp.setQuestionId(itemDO.getQuestionId());
//                questionSetItemResp.setQuestionVersion(itemDO.getQuestionVersion());
//                questionSetItemResp.setTitle(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getTitle());
//                questionSetItemResp.setCategory(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getCategory());
//                questionSetItemResp.setTags(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getTags());
//                questionSetItemResp.setDifficulty(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getDifficulty());
//                questionSetItemResp.setAttackMethod(questionByQuestionIdMap.get(itemDO.getQuestionId()).get(0).getAttackMethod());
//                respList.add(questionSetItemResp);
//            });
//
//            PageResult<QuestionSetItemResp> pageResult = new PageResult<>();
//            pageResult.setList(respList);
//            pageResult.setTotal(questionSetItemDOPageResult.getTotal());
//
//            return pageResult;
//        }
        return null;
    }

    @Override
    public Integer getQuestionSetItemCountBySetId(Long setId) {
        if (ObjectUtil.isNotNull(setId)) {
            // Integer取值范围
            long count = questionSetItemMapper.selectCount(new LambdaQueryWrapperX<QuestionSetItemDO>()
                    .eq(QuestionSetItemDO::getQuestionSetId, setId)).intValue();
            // 有可能为出现溢出问题，后期题库超过亿级，考虑优化此段代码
            return (int) count;
        }
        return 0;
    }

    @Override
    public List<String> getQuestionIdBySetId(Long setId) {
        if (ObjectUtil.isNotNull(setId)) {
            return questionSetItemMapper.selectList(new LambdaQueryWrapperX<QuestionSetItemDO>()
                            .eq(QuestionSetItemDO::getQuestionSetId, setId))
                    .stream()
                    .map(QuestionSetItemDO::getQuestionId)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public List<String> getQuestionIdBySetIdGroupCategory(Long setId, String category) {
        if (ObjectUtil.isNotNull(setId)) {
            List<String> questionIds = questionSetItemMapper.selectList(new LambdaQueryWrapperX<QuestionSetItemDO>()
                            .eq(QuestionSetItemDO::getQuestionSetId, setId))
                    .stream()
                    .map(QuestionSetItemDO::getQuestionId)
                    .collect(Collectors.toList());

            if (CollectionUtil.isNotEmpty(questionIds)) {
                return questionMapper.selectList(new LambdaQueryWrapperX<QuestionDO>()
                                .in(QuestionDO::getQuestionId, questionIds)
                                .eq(QuestionDO::getCategory, category))
                        .stream()
                        .map(QuestionDO::getQuestionId)
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    @Override
    public Map<Long, Integer> getQuestionSetItemCountBySetIds(List<Long> setIds) {
        if (CollectionUtil.isNotEmpty(setIds)) {
            //根据SetIds，获取每个题库对应的题目数量，并转换成Map，SetId当作Key
            return questionSetItemMapper.selectList(new LambdaQueryWrapperX<QuestionSetItemDO>()
                            .in(QuestionSetItemDO::getQuestionSetId, setIds))
                    .stream()
                    .collect(Collectors.groupingBy(QuestionSetItemDO::getQuestionSetId, Collectors.collectingAndThen(Collectors.toList(), List::size)));
        }
        return Collections.emptyMap();
    }


    @Override
    public List<QuestionSetItemStatisticsResp> getQuestionSetStatistics(List<Long> ids, Boolean distinct) {
        List<QuestionSetItemStatisticsResp> result = new ArrayList<>();

        // 先查出所有题集
        List<QuestionSetInfoDO> questionSetInfoDOList = questionSetInfoMapper.selectList(new LambdaQueryWrapperX<QuestionSetInfoDO>()
                .in(QuestionSetInfoDO::getId, ids));

        if (CollectionUtil.isNotEmpty(questionSetInfoDOList)) {
            // 查询映射习题
            List<QuestionSetItemDO> questionSetItemDOList = questionSetItemMapper.selectListByQuestionSetIds(questionSetInfoDOList.stream().map(QuestionSetInfoDO::getId).collect(Collectors.toList()));

            // 查出题目
            Map<String, List<QuestionDO>> questionByQuestionIdMap =
                    questionMapper.selectList().stream().collect(Collectors.groupingBy(QuestionDO::getQuestionId));

            // 初始化总计
            Map<QuestionDifficultyEnum, Long> totalCount = new HashMap<>();
            for (QuestionDifficultyEnum difficulty : QuestionDifficultyEnum.values()) {
                totalCount.put(difficulty, 0L);
            }

            // 分类统计
            Map<QuestionCategoryEnum, Map<QuestionDifficultyEnum, Long>> categoryCountMap = new HashMap<>();

            // 统计每个问题的难度和类别
            questionSetItemDOList.forEach(itemDO -> {
                // 获取 questionId 对应的列表，如果不存在则跳过
                List<QuestionDO> questionDOList = questionByQuestionIdMap.get(itemDO.getQuestionId());
                if (questionDOList != null && !questionDOList.isEmpty()) {
                    // 获取问题的 category 和 difficulty
                    String categoryDesc = questionDOList.get(0).getCategory();
                    String difficultyDesc = questionDOList.get(0).getDifficulty();

                    // 使用枚举获取 code
                    QuestionCategoryEnum category = QuestionCategoryEnum.valueOf(categoryDesc);
                    QuestionDifficultyEnum difficulty = QuestionDifficultyEnum.valueOf(difficultyDesc);

                    // 更新全局总计
                    totalCount.put(difficulty, totalCount.getOrDefault(difficulty, 0L) + 1);

                    // 分类统计
                    categoryCountMap.computeIfAbsent(category, k -> new HashMap<>());
                    Map<QuestionDifficultyEnum, Long> difficultyMap = categoryCountMap.get(category);

                    // 更新该类别的统计数据
                    difficultyMap.put(difficulty, difficultyMap.getOrDefault(difficulty, 0L) + 1);
                }
            });

            // 1. 添加总统计数据
            QuestionSetItemStatisticsResp totalStatistics = new QuestionSetItemStatisticsResp();
            totalStatistics.setName("total");
            totalStatistics.setTotal(questionSetItemDOList.size());  // 总题数
            totalStatistics.setHard(totalCount.get(QuestionDifficultyEnum.HARD));
            totalStatistics.setMedium(totalCount.get(QuestionDifficultyEnum.MEDIUM));
            totalStatistics.setSimple(totalCount.get(QuestionDifficultyEnum.SIMPLE));
            result.add(totalStatistics);

            // 2. 添加每个类别的统计数据
            categoryCountMap.forEach((category, difficultyMap) -> {
                QuestionSetItemStatisticsResp categoryStatistics = new QuestionSetItemStatisticsResp();
                categoryStatistics.setName(category.name());
                long categoryTotal = difficultyMap.values().stream().mapToLong(Long::longValue).sum();
                categoryStatistics.setTotal(categoryTotal);
                categoryStatistics.setHard(difficultyMap.getOrDefault(QuestionDifficultyEnum.HARD, 0L));
                categoryStatistics.setMedium(difficultyMap.getOrDefault(QuestionDifficultyEnum.MEDIUM, 0L));
                categoryStatistics.setSimple(difficultyMap.getOrDefault(QuestionDifficultyEnum.SIMPLE, 0L));

                result.add(categoryStatistics);
            });
        }

        return result;
    }

    @Override
    public PageResult<QuestionSetItemResp> searchBySetIdPage(QuestionSetPageByReq req) {
        PageResult<QuestionSetItemResp> questionSetItemRespPageResult = new PageResult<>();
        QuestionSetInfoDO questionSetInfoDO = questionSetInfoMapper.selectById(req.getQuestionSetId());
        if (ObjectUtil.isNotNull(questionSetInfoDO)) {
            questionSetItemRespPageResult = questionSetItemMapper.selectJoinPage(req, QuestionSetItemResp.class, new MPJLambdaWrapper<>(QuestionSetItemDO.class)
                    .selectAll()
                    .eq(QuestionSetItemDO::getQuestionSetId, req.getQuestionSetId())
                    .leftJoin(QuestionDO.class, QuestionDO::getQuestionId, QuestionSetItemDO::getQuestionId)
                    .selectAll(QuestionDO.class)
                    .eqIfExists(QuestionDO::getCategory, req.getCategory())
                    .eqIfExists(QuestionDO::getDifficulty, req.getDifficulty())
                    .likeIfExists(QuestionDO::getTitle, req.getTitle())
                    .likeIfExists(QuestionDO::getDataSource, req.getDataSource())
                    .orderByDesc(QuestionSetItemDO::getId));

            questionSetItemRespPageResult.getList().forEach(each-> each.setQuestionSetName(questionSetInfoDO.getQuestionSetName()));
        }
        return questionSetItemRespPageResult;
    }

    @Override
    public List<QuestionSetItemStatisticsResp> getQuestionSetStatistic(Long id, Boolean distinct) {
        List<QuestionSetItemStatisticsResp> result = new ArrayList<>();

        // 先查出所有题集
        List<QuestionSetInfoDO> questionSetInfoDOList = questionSetInfoMapper.selectList(new LambdaQueryWrapperX<QuestionSetInfoDO>()
                .eq(QuestionSetInfoDO::getId, id));

        if (CollectionUtil.isNotEmpty(questionSetInfoDOList)) {
            // 查询映射习题
            List<QuestionSetItemDO> questionSetItemDOList = questionSetItemMapper.selectListByQuestionSetIds(questionSetInfoDOList.stream().map(QuestionSetInfoDO::getId).collect(Collectors.toList()));

            // 查出题目
            Map<String, List<QuestionDO>> questionByQuestionIdMap =
                    questionMapper.selectList().stream().collect(Collectors.groupingBy(QuestionDO::getQuestionId));

            // 初始化总计
            Map<QuestionDifficultyEnum, Long> totalCount = new HashMap<>();
            for (QuestionDifficultyEnum difficulty : QuestionDifficultyEnum.values()) {
                totalCount.put(difficulty, 0L);
            }

            // 分类统计
            Map<QuestionCategoryEnum, Map<QuestionDifficultyEnum, Long>> categoryCountMap = new HashMap<>();

            // 统计每个问题的难度和类别
            questionSetItemDOList.forEach(itemDO -> {
                // 获取 questionId 对应的列表，如果不存在则跳过
                List<QuestionDO> questionDOList = questionByQuestionIdMap.get(itemDO.getQuestionId());
                if (questionDOList != null && !questionDOList.isEmpty()) {
                    // 获取问题的 category 和 difficulty
                    String categoryDesc = questionDOList.get(0).getCategory();
                    String difficultyDesc = questionDOList.get(0).getDifficulty();

                    // 使用枚举获取 code
                    QuestionCategoryEnum category = QuestionCategoryEnum.valueOf(categoryDesc);
                    QuestionDifficultyEnum difficulty = QuestionDifficultyEnum.valueOf(difficultyDesc);

                    // 更新全局总计
                    totalCount.put(difficulty, totalCount.getOrDefault(difficulty, 0L) + 1);

                    // 分类统计
                    categoryCountMap.computeIfAbsent(category, k -> new HashMap<>());
                    Map<QuestionDifficultyEnum, Long> difficultyMap = categoryCountMap.get(category);

                    // 更新该类别的统计数据
                    difficultyMap.put(difficulty, difficultyMap.getOrDefault(difficulty, 0L) + 1);
                }
            });
            // 1. 添加每个类别的统计数据
            categoryCountMap.forEach((category, difficultyMap) -> {
                QuestionSetItemStatisticsResp categoryStatistics = new QuestionSetItemStatisticsResp();
                categoryStatistics.setName(category.name());
                long categoryTotal = difficultyMap.values().stream().mapToLong(Long::longValue).sum();
                categoryStatistics.setTotal(categoryTotal);
                categoryStatistics.setHard(difficultyMap.getOrDefault(QuestionDifficultyEnum.HARD, 0L));
                categoryStatistics.setMedium(difficultyMap.getOrDefault(QuestionDifficultyEnum.MEDIUM, 0L));
                categoryStatistics.setSimple(difficultyMap.getOrDefault(QuestionDifficultyEnum.SIMPLE, 0L));

                result.add(categoryStatistics);
            });
        }

        return result;
    }
}
