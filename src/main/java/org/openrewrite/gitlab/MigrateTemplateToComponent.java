/*
 * Copyright 2026 the original author or authors.
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
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.format.AutoFormatVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class MigrateTemplateToComponent extends Recipe {

    @Option(displayName = "Old template",
            description = "The name of the template to replace.",
            example = "Terraform/Base.latest.gitlab-ci.yml")
    String oldTemplate;

    @Option(displayName = "New component",
            description = "Name of the component to use instead.",
            example = "$CI_SERVER_FQDN/components/opentofu/job-templates")
    String newComponent;

    @Option(displayName = "Version",
            description = "Version of the component to add.",
            example = "~latest")
    String version;

    @Option(displayName = "Inputs",
            description = "The set of inputs to provide to the component.",
            example = "opentofu_version: 1.6.0",
            required = false)
    @Nullable
    List<String> inputs;

    @Override
    public String getDisplayName() {
        return "Migrate GitLab template to component";
    }

    @Override
    public String getDescription() {
        return "Replace a GitLab `template:` include with a `component:` include, " +
                "as recommended by GitLab's CI/CD Catalog migration guides.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        JsonPathMatcher matcher = new JsonPathMatcher(
                "$.include[?(@.template =~ '" + oldTemplate + "(?:@.+)?')]");
        String componentYaml = buildComponentYaml();
        return Preconditions.check(
                new FindSourceFiles(".gitlab-ci.yml"),
                new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Sequence.@Nullable Entry visitSequenceEntry(Yaml.Sequence.Entry entry, ExecutionContext ctx) {
                        Yaml.Sequence.Entry e = super.visitSequenceEntry(entry, ctx);
                        if (!(e.getBlock() instanceof Yaml.Mapping) || !matcher.matches(new Cursor(getCursor(), e.getBlock()))) {
                            return e;
                        }
                        Yaml.Mapping original = (Yaml.Mapping) e.getBlock();
                        Yaml.Mapping replacement = parseComponentMapping(componentYaml);
                        if (replacement == null || original.getEntries().isEmpty()) {
                            return e;
                        }
                        String firstEntryPrefix = original.getEntries().get(0).getPrefix();
                        replacement = replacement.withEntries(ListUtils.mapFirst(replacement.getEntries(),
                                first -> first.withPrefix(firstEntryPrefix)));
                        doAfterVisit(new AutoFormatVisitor<>(null));
                        return e.withBlock(replacement);
                    }
                }
        );
    }

    private String buildComponentYaml() {
        StringBuilder sb = new StringBuilder("component: ")
                .append(newComponent)
                .append('@')
                .append(version)
                .append('\n');
        if (inputs != null && !inputs.isEmpty()) {
            sb.append("inputs:\n");
            for (String input : inputs) {
                sb.append("  ").append(input).append('\n');
            }
        }
        return sb.toString();
    }

    private static Yaml.@Nullable Mapping parseComponentMapping(String yamlString) {
        return YamlParser.builder().build()
                .parse(yamlString)
                .map(Yaml.Documents.class::cast)
                .map(docs -> (Yaml.Mapping) docs.getDocuments().get(0).getBlock())
                .findFirst()
                .orElse(null);
    }
}
