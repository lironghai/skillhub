package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skillbundle.SkillBundleLabel;
import com.iflytek.skillhub.domain.skillbundle.SkillBundleLabelRepository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SkillBundleLabelJpaRepository extends JpaRepository<SkillBundleLabel, Long>, SkillBundleLabelRepository {
    List<SkillBundleLabel> findByBundleId(Long bundleId);
    List<SkillBundleLabel> findByBundleIdIn(List<Long> bundleIds);

    @Transactional
    void deleteByBundleId(Long bundleId);
}
