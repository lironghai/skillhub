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
        String skillPath = selectSkillPath(snapshot.files());
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
        List<SkillHubMcpReferenceFile> references = snapshot.files().stream()
                .map(SkillFile::getFilePath)
                .filter(this::isReferencePath)
                .sorted(Comparator.naturalOrder())
                .map(path -> new SkillHubMcpReferenceFile(path, readFileContent(activeVersionId, path)))
                .toList();
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

    private String selectSkillPath(List<SkillFile> files) {
        return files.stream()
                .map(SkillFile::getFilePath)
                .filter("SKILL.md"::equals)
                .findFirst()
                .orElse(null);
    }

    private String readFileContent(Long versionId, String path) {
        try (InputStream inputStream = skillQueryService.getFileContentByVersionId(versionId, path)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
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
