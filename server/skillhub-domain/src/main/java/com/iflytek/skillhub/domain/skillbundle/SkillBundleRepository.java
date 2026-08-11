package com.iflytek.skillhub.domain.skillbundle;

import java.util.List;
import java.util.Optional;

public interface SkillBundleRepository {
    Optional<SkillBundle> findById(Long id);
    Optional<SkillBundle> findByNamespaceIdAndSlug(Long namespaceId, String slug);
    List<SkillBundle> findByNamespaceId(Long namespaceId);
    boolean existsByNamespaceId(Long namespaceId);
    List<SkillBundle> findAllByOrderByUpdatedAtDesc();
    SkillBundle save(SkillBundle bundle);
    void incrementDownloadCount(Long id);
}
