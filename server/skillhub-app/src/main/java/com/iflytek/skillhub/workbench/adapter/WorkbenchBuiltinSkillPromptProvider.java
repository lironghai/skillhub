package com.iflytek.skillhub.workbench.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;

@Component
public class WorkbenchBuiltinSkillPromptProvider {

    static final String BASE_SYSTEM_PROMPT = "你是 SkillHub 工作台助手，帮助用户创建、更新和检查技能文件。";
    static final String SKILL_CREATOR_PATTERN =
            "classpath*:workbench/builtin-skills/skill-creator/**/*.md";
    static final int MAX_BUILTIN_SKILL_PROMPT_CHARS = 80_000;

    private static final Logger log = LoggerFactory.getLogger(WorkbenchBuiltinSkillPromptProvider.class);

    private final ResourcePatternResolver resourcePatternResolver;
    private volatile String cachedSystemPrompt;

    public WorkbenchBuiltinSkillPromptProvider() {
        this(new PathMatchingResourcePatternResolver());
    }

    WorkbenchBuiltinSkillPromptProvider(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    public String systemPrompt() {
        String cached = cachedSystemPrompt;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (cachedSystemPrompt == null) {
                cachedSystemPrompt = buildSystemPrompt();
            }
            return cachedSystemPrompt;
        }
    }

    private String buildSystemPrompt() {
        String builtinSkill = loadSkillCreatorMarkdown();
        if (builtinSkill.isBlank()) {
            return BASE_SYSTEM_PROMPT;
        }
        return BASE_SYSTEM_PROMPT
                + "\n\n你内置了 skill-creator 技能。用户请求创建、更新、检查或打包 SkillHub 技能时，"
                + "优先遵循下面的 skill-creator 资料。SkillHub 技能产物仍必须保持中立的 "
                + "<skill>/SKILL.md 包结构，不要假设 Codex、Claude Code 或其他工作台专属适配。"
                + "\n\n<skill-creator>\n"
                + builtinSkill
                + "\n</skill-creator>";
    }

    private String loadSkillCreatorMarkdown() {
        Resource[] resources;
        try {
            resources = resourcePatternResolver.getResources(SKILL_CREATOR_PATTERN);
        } catch (IOException ex) {
            log.warn("Failed to resolve workbench built-in skill resources: {}", ex.getMessage());
            return "";
        }
        if (resources.length == 0) {
            log.info("No workbench built-in skill resources found for {}", SKILL_CREATOR_PATTERN);
            return "";
        }

        StringBuilder prompt = new StringBuilder();
        for (Resource resource : Arrays.stream(resources)
                .filter(Resource::isReadable)
                .sorted(Comparator.comparing(WorkbenchBuiltinSkillPromptProvider::sortKey))
                .toList()) {
            String path = displayPath(resource);
            String content;
            try {
                content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                log.warn("Failed to read workbench built-in skill resource {}: {}", path, ex.getMessage());
                continue;
            }
            if (content.isBlank()) {
                continue;
            }
            prompt.append("\n\n### ").append(path).append("\n\n").append(content.strip());
            if (prompt.length() > MAX_BUILTIN_SKILL_PROMPT_CHARS) {
                prompt.setLength(MAX_BUILTIN_SKILL_PROMPT_CHARS);
                prompt.append("\n\n[skill-creator 内容已按长度上限截断]");
                break;
            }
        }
        return prompt.toString().strip();
    }

    private static String sortKey(Resource resource) {
        String path = displayPath(resource);
        if (path.endsWith("/SKILL.md")) {
            return "00-" + path;
        }
        if (path.contains("/references/")) {
            return "10-" + path;
        }
        if (path.contains("/agents/")) {
            return "20-" + path;
        }
        return "90-" + path;
    }

    private static String displayPath(Resource resource) {
        try {
            String url = resource.getURL().toString().replace('\\', '/');
            int index = url.indexOf("workbench/builtin-skills/");
            return index >= 0 ? url.substring(index) : resource.getFilename();
        } catch (IOException ex) {
            return resource.getFilename();
        }
    }
}
