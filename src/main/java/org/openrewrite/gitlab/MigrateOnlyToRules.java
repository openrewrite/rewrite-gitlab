/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.gitlab;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateOnlyToRules extends Recipe {

    String displayName = "Migrate `only` to `rules`";

    String description = "Replace the deprecated `only` keyword with equivalent `rules` " +
            "in `.gitlab-ci.yml` job definitions. Handles simple ref list forms; " +
            "complex object forms with `refs`, `variables`, or `changes` sub-keys are left unchanged.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Documents visitDocuments(Yaml.Documents documents, ExecutionContext ctx) {
                if (!documents.getSourcePath().endsWith(".gitlab-ci.yml")) {
                    return documents;
                }
                return super.visitDocuments(documents, ctx);
            }

            @Override
            public Yaml.Mapping visitMapping(Yaml.Mapping mapping, ExecutionContext ctx) {
                Yaml.Mapping m = super.visitMapping(mapping, ctx);

                int onlyIdx = -1;
                boolean hasRules = false;
                boolean hasExcept = false;

                for (int i = 0; i < m.getEntries().size(); i++) {
                    String key = m.getEntries().get(i).getKey().getValue();
                    if ("only".equals(key)) {
                        onlyIdx = i;
                    } else if ("rules".equals(key)) {
                        hasRules = true;
                    } else if ("except".equals(key)) {
                        hasExcept = true;
                    }
                }

                if (onlyIdx < 0 || hasRules || hasExcept) {
                    return m;
                }

                Yaml.Mapping.Entry onlyEntry = m.getEntries().get(onlyIdx);

                if (!(onlyEntry.getValue() instanceof Yaml.Sequence)) {
                    return m;
                }

                Yaml.Sequence seq = (Yaml.Sequence) onlyEntry.getValue();
                List<String> refs = new ArrayList<>();
                for (Yaml.Sequence.Entry seqEntry : seq.getEntries()) {
                    if (!(seqEntry.getBlock() instanceof Yaml.Scalar)) {
                        return m;
                    }
                    refs.add(((Yaml.Scalar) seqEntry.getBlock()).getValue());
                }

                if (refs.isEmpty()) {
                    return m;
                }

                String entryPrefix = onlyEntry.getPrefix();
                String baseIndent = entryPrefix.contains("\n") ?
                        entryPrefix.substring(entryPrefix.lastIndexOf('\n') + 1) : "  ";
                String seqIndent = baseIndent + "  ";

                StringBuilder sb = new StringBuilder("rules:");
                for (String ref : refs) {
                    sb.append("\n").append(seqIndent).append("- if: ").append(refToCondition(ref));
                }

                Yaml.Mapping.Entry rulesEntry = parseRulesEntry(sb.toString(), onlyEntry.getPrefix());
                if (rulesEntry == null) {
                    return m;
                }

                List<Yaml.Mapping.Entry> newEntries = new ArrayList<>(m.getEntries());
                newEntries.set(onlyIdx, rulesEntry);
                return m.withEntries(newEntries);
            }
        };
    }

    static Yaml.Mapping.Entry parseRulesEntry(String yamlString, String prefix) {
        return YamlParser.builder().build()
                .parse(yamlString)
                .map(Yaml.Documents.class::cast)
                .map(docs -> (Yaml.Mapping) docs.getDocuments().get(0).getBlock())
                .map(mapping -> mapping.getEntries().get(0).withPrefix(prefix))
                .findFirst()
                .orElse(null);
    }

    static String refToCondition(String ref) {
        switch (ref) {
            case "branches":
                return "$CI_COMMIT_BRANCH";
            case "tags":
                return "$CI_COMMIT_TAG";
            case "merge_requests":
                return "$CI_PIPELINE_SOURCE == 'merge_request_event'";
            case "schedules":
                return "$CI_PIPELINE_SOURCE == 'schedule'";
            case "web":
                return "$CI_PIPELINE_SOURCE == 'web'";
            case "api":
                return "$CI_PIPELINE_SOURCE == 'api'";
            case "pipelines":
                return "$CI_PIPELINE_SOURCE == 'pipeline'";
            case "triggers":
                return "$CI_PIPELINE_SOURCE == 'trigger'";
            case "pushes":
                return "$CI_PIPELINE_SOURCE == 'push'";
            default:
                if (ref.startsWith("/") && ref.endsWith("/")) {
                    return "$CI_COMMIT_BRANCH =~ " + ref;
                }
                return "$CI_COMMIT_BRANCH == '" + ref + "'";
        }
    }
}
