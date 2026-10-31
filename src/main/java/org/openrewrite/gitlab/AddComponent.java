/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.MergeYaml.InsertMode;

import java.util.List;

@EqualsAndHashCode(callSuper = false)
@Value
public class AddComponent extends Recipe {
    @Option(displayName = "Component",
            description = "Name of the component to use add.",
            example = "$CI_SERVER_FQDN/components/opentofu/full-pipeline")
    String newComponent;

    @Option(displayName = "Version",
            description = "Version of the component to add.",
            example = "0.10.0")
    String version;

    @Option(displayName = "Inputs",
            description = "The set of inputs to provide",
            example = "opentofu_version: 1.6.1",
            required = false)
    @Nullable
    List<String> inputs;

    @Option(displayName = "Insert mode",
            description = "Where to insert the component in an existing `include` list. Default is `Last`.",
            valid = {"Before", "After", "Last"},
            required = false)
    @Nullable
    InsertMode insertMode;

    @Option(displayName = "Insert property",
            description = "The existing `include` entry to insert the component before or after, written as its first key and value. " +
                          "Only used when `insertMode` is `Before` or `After`.",
            example = "template: Gradle.gitlab-ci.yml",
            required = false)
    @Nullable
    String insertProperty;

    String displayName = "Add GitLab component";

    String description = "Add a GitLab component to an existing list, or add a new list where none was present.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        StringBuilder includeBlock = new StringBuilder()
                .append("include:\n")
                .append(" - component: ")
                .append(newComponent)
                .append("@")
                .append(version)
                .append("\n");

        if (inputs != null && !inputs.isEmpty()) {
            includeBlock.append("   inputs:\n");
            inputs.forEach(input -> includeBlock.append("     ").append(input).append("\n"));
        }

        return MergeKeyword.global(
                "include",
                //language=yml
                includeBlock.toString(),
                false,
                "component",
                insertMode,
                insertProperty)
                .getVisitor();
    }
}
