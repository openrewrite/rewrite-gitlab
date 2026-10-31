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

import org.openrewrite.Cursor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.HashSet;
import java.util.Set;

import static java.util.Arrays.asList;

/// Recognizes the `image:` and `services:` keywords of `.gitlab-ci.yml` at the global level, in `default` and in jobs.
public final class ImageKeywords {
    private static final Set<String> NON_JOB_KEYWORDS = new HashSet<>(asList(
            "include", "stages", "variables", "workflow", "spec"));

    private ImageKeywords() {
    }

    public static boolean isImage(Cursor entry) {
        return isKeyword(entry, "image");
    }

    public static boolean isServices(Cursor entry) {
        return isKeyword(entry, "services");
    }

    private static boolean isKeyword(Cursor entry, String keyword) {
        return entry.getValue() instanceof Yaml.Mapping.Entry &&
               keyword.equals(((Yaml.Mapping.Entry) entry.getValue()).getKey().getValue()) &&
               atGlobalOrJobLevel(entry);
    }

    private static boolean atGlobalOrJobLevel(Cursor entry) {
        Cursor mapping = entry.getParentTreeCursor();
        Cursor parent = mapping.getParentTreeCursor();
        if (parent.getValue() instanceof Yaml.Document) {
            return true;
        }
        return parent.getValue() instanceof Yaml.Mapping.Entry &&
               !NON_JOB_KEYWORDS.contains(((Yaml.Mapping.Entry) parent.getValue()).getKey().getValue()) &&
               parent.getParentTreeCursor().getParentTreeCursor().getValue() instanceof Yaml.Document;
    }
}
