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
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.yaml.MergeYaml;

import java.util.Collections;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddTemplate extends Recipe {

    @Option(displayName = "Template",
            description = "Name of the template to use instead.",
            example = "OpenTofu/Base.gitlab-ci.yml")
    String newTemplate;

    @Override
    public String getDisplayName() {
        return "Add GitLab template";
    }

    @Override
    public String getDescription() {
        return "Add a GitLab template to an existing list, or add a new list where none was present.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return Collections.singletonList(
                new MergeYaml(
                        "$",
                        "include:\n - template: " + newTemplate,
                        false,
                        "template",
                        ".gitlab-ci.yml"
                )
        );
    }
}
