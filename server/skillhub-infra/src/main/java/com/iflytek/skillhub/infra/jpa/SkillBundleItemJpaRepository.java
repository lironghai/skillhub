package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skillbundle.SkillBundleItem;
import com.iflytek.skillhub.domain.skillbundle.SkillBundleItemRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SkillBundleItemJpaRepository extends JpaRepository<SkillBundleItem, Long>, SkillBundleItemRepository {
    List<SkillBundleItem> findByBundleIdOrderBySortOrderAscIdAsc(Long bundleId);
    List<SkillBundleItem> findByBundleIdIn(List<Long> bundleIds);

    @Transactional
    void deleteByBundleId(Long bundleId);
}
