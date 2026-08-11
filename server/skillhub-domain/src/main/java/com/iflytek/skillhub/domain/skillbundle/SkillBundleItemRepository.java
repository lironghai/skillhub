package com.iflytek.skillhub.domain.skillbundle;

import java.util.List;

public interface SkillBundleItemRepository {
    List<SkillBundleItem> findByBundleIdOrderBySortOrderAscIdAsc(Long bundleId);
    List<SkillBundleItem> findByBundleIdIn(List<Long> bundleIds);
    void deleteByBundleId(Long bundleId);
    void flush();
    <S extends SkillBundleItem> List<S> saveAll(Iterable<S> items);
}
