package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.label.LabelType;
import com.iflytek.skillhub.domain.label.SkillLabel;
import com.iflytek.skillhub.domain.label.SkillLabelService;
import com.iflytek.skillhub.dto.SkillLabelDto;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Projects filter-visible recommended labels onto skill summary responses.
 */
@Service
public class SkillSummaryLabelProjectionService {

    private final SkillLabelService skillLabelService;
    private final LabelDefinitionService labelDefinitionService;
    private final LabelLocalizationService labelLocalizationService;

    public SkillSummaryLabelProjectionService(
            SkillLabelService skillLabelService,
            LabelDefinitionService labelDefinitionService,
            LabelLocalizationService labelLocalizationService) {
        this.skillLabelService = skillLabelService;
        this.labelDefinitionService = labelDefinitionService;
        this.labelLocalizationService = labelLocalizationService;
    }

    public Map<Long, List<SkillLabelDto>> projectBySkillIds(List<Long> skillIds) {
        List<SkillLabel> skillLabels = skillLabelService.listSkillLabelsBySkillIds(skillIds);
        if (skillLabels == null || skillLabels.isEmpty()) {
            return Map.of();
        }

        List<Long> labelIds = skillLabels.stream()
                .map(SkillLabel::getLabelId)
                .distinct()
                .toList();
        Map<Long, LabelDefinition> definitionsById = labelDefinitionService.listByIds(labelIds).stream()
                .collect(Collectors.toMap(LabelDefinition::getId, definition -> definition));
        Map<Long, List<LabelTranslation>> translationsByLabelId =
                labelDefinitionService.listTranslationsByLabelIds(labelIds);

        return skillLabels.stream()
                .filter(skillLabel -> isSummaryVisible(definitionsById.get(skillLabel.getLabelId())))
                .collect(Collectors.groupingBy(
                        SkillLabel::getSkillId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                labels -> labels.stream()
                                        .sorted(Comparator
                                                .comparingInt((SkillLabel skillLabel) -> definitionsById
                                                        .get(skillLabel.getLabelId())
                                                        .getSortOrder())
                                                .thenComparing(skillLabel -> definitionsById
                                                        .get(skillLabel.getLabelId())
                                                        .getId()))
                                        .map(skillLabel -> toLabelDto(
                                                definitionsById.get(skillLabel.getLabelId()),
                                                translationsByLabelId))
                                        .toList()
                        )
                ));
    }

    private boolean isSummaryVisible(LabelDefinition definition) {
        return definition != null
                && definition.getType() == LabelType.RECOMMENDED
                && definition.isVisibleInFilter();
    }

    private SkillLabelDto toLabelDto(
            LabelDefinition definition,
            Map<Long, List<LabelTranslation>> translationsByLabelId) {
        return new SkillLabelDto(
                definition.getSlug(),
                definition.getType().name(),
                labelLocalizationService.resolveDisplayName(
                        definition.getSlug(),
                        translationsByLabelId.getOrDefault(definition.getId(), List.of()))
        );
    }
}
