package com.iflytek.skillhub.domain.skillbundle;

import java.util.List;

public interface SkillBundleLabelRepository {
    List<SkillBundleLabel> findByBundleId(Long bundleId);
    List<SkillBundleLabel> findByBundleIdIn(List<Long> bundleIds);
    void deleteByBundleId(Long bundleId);
    void flush();
    <S extends SkillBundleLabel> List<S> saveAll(Iterable<S> labels);
}
