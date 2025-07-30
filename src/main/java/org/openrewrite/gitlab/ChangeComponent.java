/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.openrewrite.yaml.ChangeValue;

import java.util.List;

import static java.util.Collections.singletonList;

@EqualsAndHashCode(callSuper = false)
@Value
public class ChangeComponent extends Recipe {

    @Option(displayName = "Old Component",
            description = "The name of the component to match.",
            example = "$CI_SERVER_FQDN/components/opentofu/full-pipeline")
    String oldComponent;

    @Option(displayName = "Old Component version",
            description = "Version of the existing component to use match.",
            example = "0.10.0")
    String oldComponentVersion;

    @Option(displayName = "New Component",
            description = "Name of the new component to use instead.",
            example = "$CI_SERVER_FQDN/components/opentofu/full-pipeline",
            required = false)
    @Nullable
    String newComponent;

    @Option(displayName = "New Component version",
            description = "Version of the new component to use instead.",
            example = "0.10.0")
    String newComponentVersion;

    @Override
    public String getDisplayName() {
        return "Change GitLab Component";
    }

    @Override
    public String getDescription() {
        return "Change a GitLab Component in use.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return singletonList(
                new ChangeValue(
                        String.format("$.include[?(@.component =~ '%s@%s')].component", oldComponent, oldComponentVersion),
                        (newComponent == null ? oldComponent : newComponent) + "@" + newComponentVersion,
                        ".gitlab-ci.yml")
        );
    }
}
