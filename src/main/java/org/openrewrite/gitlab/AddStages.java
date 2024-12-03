/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.yaml.MergeYaml;

import java.util.Collections;
import java.util.List;

@Value
@EqualsAndHashCode(callSuper = false)
public class AddStages extends Recipe {
    @Option(displayName = "Stages",
            description = "Stages to add.",
            example = "build,test,deploy",
            required = true)
    List<String> stages;

    @Option(
            displayName = "Accept theirs",
            description = "When the set of stages would conflict, prefer the original value.",
            required = false)
    Boolean acceptTheirs;

    @Override
    public String getDisplayName() {
        return "Add GitLab stages";
    }

    @Override
    public String getDescription() {
        return "Add or Update the set of stages defined in `.gitlab-ci.yml`.";
    }

    @Override
    public List<Recipe> getRecipeList() {
        return Collections.singletonList(
                new MergeYaml(
                        "$",
                        //language=yml
                        "stages:\n" +
                                "  - " + String.join("\n  - ", stages),
                        acceptTheirs,
                        "stages",
                        ".gitlab-ci.yml")
        );
    }
}