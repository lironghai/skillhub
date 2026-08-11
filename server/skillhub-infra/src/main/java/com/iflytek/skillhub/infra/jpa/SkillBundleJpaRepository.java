package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.skillbundle.SkillBundle;
import com.iflytek.skillhub.domain.skillbundle.SkillBundleRepository;
import com.iflytek.skillhub.domain.skillbundle.SkillBundleStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SkillBundleJpaRepository extends JpaRepository<SkillBundle, Long>, SkillBundleRepository {

    Optional<SkillBundle> findByNamespaceIdAndSlug(Long namespaceId, String slug);

    java.util.List<SkillBundle> findByNamespaceId(Long namespaceId);

    boolean existsByNamespaceId(Long namespaceId);

    Page<SkillBundle> findByStatus(SkillBundleStatus status, Pageable pageable);

    java.util.List<SkillBundle> findAllByOrderByUpdatedAtDesc();

    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update SkillBundle b set b.downloadCount = b.downloadCount + 1 where b.id = :id")
    void incrementDownloadCount(Long id);
}
