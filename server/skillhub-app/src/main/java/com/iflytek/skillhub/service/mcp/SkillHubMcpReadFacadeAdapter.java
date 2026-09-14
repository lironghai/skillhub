package com.iflytek.skillhub.service.mcp;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillFile;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.skill.service.SkillSlugResolutionService;
import com.iflytek.skillhub.mcp.SkillHubMcpListSkillsResponse;
import com.iflytek.skillhub.mcp.SkillHubMcpReadFacade;
import com.iflytek.skillhub.mcp.SkillHubMcpReferenceFile;
import com.iflytek.skillhub.mcp.SkillHubMcpSkillDetailResponse;
import com.iflytek.skillhub.mcp.SkillHubMcpSkillRefResponse;
import com.iflytek.skillhub.mcp.SkillHubMcpSkillSummary;
import com.iflytek.skillhub.service.SkillSearchAppService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class SkillHubMcpReadFacadeAdapter implements SkillHubMcpReadFacade {

    static final int MAX_REFERENCE_FILES = 32;
    static final int MAX_REFERENCE_BYTES = 1024 * 1024;
    static final int MAX_SKILL_CONTENT_BYTES = 1024 * 1024;

    private final SkillSearchAppService skillSearchAppService;
    private final SkillQueryService skillQueryService;
    private final SkillRepository skillRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillSlugResolutionService skillSlugResolutionService;

    public SkillHubMcpReadFacadeAdapter(
            SkillSearchAppService skillSearchAppService,
            SkillQueryService skillQueryService,
            SkillRepository skillRepository,
            NamespaceRepository namespaceRepository,
            SkillSlugResolutionService skillSlugResolutionService) {
        this.skillSearchAppService = skillSearchAppService;
        this.skillQueryService = skillQueryService;
        this.skillRepository = skillRepository;
        this.namespaceRepository = namespaceRepository;
        this.skillSlugResolutionService = skillSlugResolutionService;
    }

    @Override
    public SkillHubMcpListSkillsResponse listSkills(String query, String namespace, String sort, int page, int size) {
        ViewerContext viewerContext = currentViewerContext();
        SkillSearchAppService.SearchResponse response = skillSearchAppService.search(
                query,
                namespace,
                normalizeSort(sort),
                page,
                size,
                List.of(),
                viewerContext.userId(),
                viewerContext.userNsRoles());
        return new SkillHubMcpListSkillsResponse(
                response.items().stream().map(this::toSummary).toList(),
                response.total(),
                response.page(),
                response.size());
    }

    @Override
    public SkillHubMcpSkillDetailResponse getSkillDetail(Long skillId, String namespace, String slug) {
        ViewerContext viewerContext = currentViewerContext();
        SkillLocator locator = resolveLocator(skillId, namespace, slug, viewerContext);
        SkillQueryService.SkillDetailDTO detail = skillQueryService.getSkillDetail(
                locator.namespaceSlug(),
                locator.skillSlug(),
                viewerContext.userId(),
                viewerContext.userNsRoles());
        Long activeVersionId = selectReferenceVersionId(detail);
        if (activeVersionId == null) {
            return new SkillHubMcpSkillDetailResponse(
                    detail.id(),
                    locator.namespaceSlug(),
                    detail.slug(),
                    null,
                    null,
                    null,
                    null,
                    detail.resolutionMode());
        }
        SkillQueryService.ReviewSkillSnapshotDTO snapshot = skillQueryService.getReviewSkillSnapshot(activeVersionId);
        SkillFile skillFile = selectSkillFile(snapshot.files());
        String skillPath = skillFile == null ? null : skillFile.getFilePath();
        if (skillFile != null && skillFile.getFileSize() > MAX_SKILL_CONTENT_BYTES) {
            throw new IllegalArgumentException("SKILL.md exceeds " + MAX_SKILL_CONTENT_BYTES + " bytes");
        }
        String skillContent = skillPath == null ? null : readFileContent(activeVersionId, skillPath);
        return new SkillHubMcpSkillDetailResponse(
                detail.id(),
                locator.namespaceSlug(),
                detail.slug(),
                activeVersionId,
                selectReferenceVersion(detail),
                skillPath,
                skillContent,
                detail.resolutionMode());
    }

    @Override
    public SkillHubMcpSkillRefResponse getSkillRef(Long skillId, String namespace, String slug) {
        ViewerContext viewerContext = currentViewerContext();
        SkillLocator locator = resolveLocator(skillId, namespace, slug, viewerContext);
        SkillQueryService.SkillDetailDTO detail = skillQueryService.getSkillDetail(
                locator.namespaceSlug(),
                locator.skillSlug(),
                viewerContext.userId(),
                viewerContext.userNsRoles());
        Long activeVersionId = selectReferenceVersionId(detail);
        if (activeVersionId == null) {
            return new SkillHubMcpSkillRefResponse(
                    detail.id(),
                    locator.namespaceSlug(),
                    detail.slug(),
                    null,
                    null,
                    List.of(),
                    detail.resolutionMode());
        }
        SkillQueryService.ReviewSkillSnapshotDTO snapshot = skillQueryService.getReviewSkillSnapshot(activeVersionId);
        List<SkillFile> referenceFiles = snapshot.files().stream()
                .filter(file -> isReferencePath(file.getFilePath()))
                .sorted(Comparator.comparing(SkillFile::getFilePath))
                .toList();
        if (referenceFiles.size() > MAX_REFERENCE_FILES) {
            throw new IllegalArgumentException("Skill references exceed " + MAX_REFERENCE_FILES + " files");
        }
        long declaredBytes = referenceFiles.stream().mapToLong(SkillFile::getFileSize).sum();
        if (declaredBytes > MAX_REFERENCE_BYTES) {
            throw new IllegalArgumentException("Skill references exceed " + MAX_REFERENCE_BYTES + " bytes");
        }
        int remainingBytes = MAX_REFERENCE_BYTES;
        List<SkillHubMcpReferenceFile> references = new ArrayList<>(referenceFiles.size());
        for (SkillFile file : referenceFiles) {
            byte[] content = readFileBytes(
                    activeVersionId,
                    file.getFilePath(),
                    remainingBytes,
                    "Skill references exceed " + MAX_REFERENCE_BYTES + " bytes");
            remainingBytes -= content.length;
            references.add(new SkillHubMcpReferenceFile(
                    file.getFilePath(),
                    new String(content, StandardCharsets.UTF_8)));
        }
        return new SkillHubMcpSkillRefResponse(
                detail.id(),
                locator.namespaceSlug(),
                detail.slug(),
                activeVersionId,
                selectReferenceVersion(detail),
                references,
                detail.resolutionMode());
    }

    private SkillHubMcpSkillSummary toSummary(com.iflytek.skillhub.dto.SkillSummaryResponse response) {
        return new SkillHubMcpSkillSummary(
                response.id(),
                response.namespace(),
                response.slug(),
                response.displayName(),
                response.summary(),
                response.visibility(),
                response.status(),
                toVersionString(response.headlineVersion()),
                toVersionString(response.publishedVersion()),
                response.updatedAt(),
                response.downloadCount(),
                response.ratingAvg(),
                response.ratingCount(),
                response.resolutionMode());
    }

    private String toVersionString(com.iflytek.skillhub.dto.SkillLifecycleVersionResponse response) {
        return response == null ? null : response.version();
    }

    private String normalizeSort(String sort) {
        return sort == null || sort.isBlank() ? "newest" : sort.trim();
    }

    private ViewerContext currentViewerContext() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return new ViewerContext(null, Map.of());
        }
        Object userId = servletRequestAttributes.getRequest().getAttribute("userId");
        Object roles = servletRequestAttributes.getRequest().getAttribute("userNsRoles");
        Map<Long, NamespaceRole> userNsRoles = roles instanceof Map<?, ?> map
                ? map.entrySet().stream()
                .filter(entry -> entry.getKey() instanceof Long && entry.getValue() instanceof NamespaceRole)
                .collect(Collectors.toMap(
                        entry -> (Long) entry.getKey(),
                        entry -> (NamespaceRole) entry.getValue(),
                        (left, right) -> left))
                : Map.of();
        return new ViewerContext(userId instanceof String ? (String) userId : null, userNsRoles);
    }

    private SkillLocator resolveLocator(Long skillId, String namespace, String slug, ViewerContext viewerContext) {
        if (skillId != null) {
            Skill skill = skillRepository.findById(skillId)
                    .orElseThrow(() -> new DomainBadRequestException("error.skill.notFound", skillId));
            Namespace actualNamespace = namespaceRepository.findById(skill.getNamespaceId())
                    .orElseThrow(() -> new DomainBadRequestException("error.namespace.notFound", skill.getNamespaceId()));
            if (!isBlank(namespace) && !Objects.equals(actualNamespace.getSlug(), namespace)) {
                throw new DomainBadRequestException("error.skill.lookup.mismatch.namespace", namespace);
            }
            if (!isBlank(slug) && !Objects.equals(skill.getSlug(), slug)) {
                throw new DomainBadRequestException("error.skill.lookup.mismatch.slug", slug);
            }
            return new SkillLocator(actualNamespace.getSlug(), skill.getSlug());
        }

        Namespace namespaceEntity = namespaceRepository.findBySlug(namespace)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", namespace));
        Skill skill = skillSlugResolutionService.resolve(
                namespaceEntity.getId(),
                slug,
                viewerContext.userId(),
                SkillSlugResolutionService.Preference.CURRENT_USER);
        return new SkillLocator(namespaceEntity.getSlug(), skill.getSlug());
    }

    private Long selectReferenceVersionId(SkillQueryService.SkillDetailDTO detail) {
        if (detail.headlineVersion() != null) {
            return detail.headlineVersion().id();
        }
        if (detail.publishedVersion() != null) {
            return detail.publishedVersion().id();
        }
        if (detail.ownerPreviewVersion() != null) {
            return detail.ownerPreviewVersion().id();
        }
        return null;
    }

    private String selectReferenceVersion(SkillQueryService.SkillDetailDTO detail) {
        if (detail.headlineVersion() != null) {
            return detail.headlineVersion().version();
        }
        if (detail.publishedVersion() != null) {
            return detail.publishedVersion().version();
        }
        if (detail.ownerPreviewVersion() != null) {
            return detail.ownerPreviewVersion().version();
        }
        return null;
    }

    private SkillFile selectSkillFile(List<SkillFile> files) {
        return files.stream()
                .filter(file -> "SKILL.md".equals(file.getFilePath()))
                .findFirst()
                .orElse(null);
    }

    private String readFileContent(Long versionId, String path) {
        byte[] content = readFileBytes(
                versionId,
                path,
                MAX_SKILL_CONTENT_BYTES,
                "SKILL.md exceeds " + MAX_SKILL_CONTENT_BYTES + " bytes");
        return new String(content, StandardCharsets.UTF_8);
    }

    private byte[] readFileBytes(Long versionId, String path, int maxBytes, String limitMessage) {
        try (InputStream inputStream = skillQueryService.getFileContentByVersionId(versionId, path)) {
            byte[] content = inputStream.readNBytes(maxBytes + 1);
            if (content.length > maxBytes) {
                throw new IllegalArgumentException(limitMessage);
            }
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read skill MCP content", e);
        }
    }

    private boolean isReferencePath(String path) {
        return path != null && path.startsWith("references/") && path.length() > "references/".length();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ViewerContext(String userId, Map<Long, NamespaceRole> userNsRoles) {}

    private record SkillLocator(String namespaceSlug, String skillSlug) {}
}
