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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.yaml.MergeYaml;

import java.util.List;

import static java.util.Collections.singletonList;

@EqualsAndHashCode(callSuper = false)
@Value
public class AddDefaultKeyword extends Recipe {

    @Option(displayName = "Keyword",
            description = "The keyword to add under the `default` section.",
            example = "image")
    String keyword;

    @Option(displayName = "Value",
            description = "The value for the keyword.",
            example = "ruby:3.0")
    String value;

    @Option(displayName = "Accept theirs",
            description = "When the keyword already exists, prefer the original value.",
            required = false)
    @Nullable
    Boolean acceptTheirs;

    String displayName = "Add default keyword";

    String description = "Add or update a keyword in the `default` section of `.gitlab-ci.yml`.";

    @Override
    public List<Recipe> getRecipeList() {
        return singletonList(
                new MergeYaml(
                        "$",
                        //language=yml
                        "default:\n  " + keyword + ": " + value,
                        acceptTheirs,
                        null,
                        ".gitlab-ci.yml",
                        null,
                        null,
                        null)
        );
    }
}
