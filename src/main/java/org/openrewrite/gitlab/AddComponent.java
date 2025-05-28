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
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.yaml.MergeYaml;

import java.util.Collections;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
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

    @Override
    public String getDisplayName() {
        return "Add GitLab component";
    }

    @Override
    public String getDescription() {
        return "Add a GitLab component to an existing list, or add a new list where none was present.";
    }

    @Override
    public List<Recipe> getRecipeList() {
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

        return Collections.singletonList(
                new MergeYaml(
                        "$",
                        //language=yml
                        includeBlock.toString(),
                        false,
                        "component",
                        ".gitlab-ci.yml",
                        null,
                        null,
                        null)
        );
    }
}
