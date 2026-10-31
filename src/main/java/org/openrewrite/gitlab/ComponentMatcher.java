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

import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.Validated;
import org.openrewrite.semver.Semver;
import org.openrewrite.semver.VersionComparator;
import org.openrewrite.yaml.JsonPathMatcher;
import org.openrewrite.yaml.tree.Yaml;

import java.util.regex.Pattern;

/// Matches `component:` includes by literal path and an optional version pattern. A `null` path matches any component.
///
/// A version pattern matches a version that is equal to it, such as `1`, `0.10.0`, `~latest` or `main`, or one
/// that satisfies it as a semver selector, such as `1.x` or `~1`. A `null` pattern matches any version.
public class ComponentMatcher {
    // Paths used to be regular expressions, where `$CI_SERVER_FQDN` had to be written as `\$CI_SERVER_FQDN`
    private static final Pattern LEGACY_REGEX_ESCAPE = Pattern.compile("\\\\(\\W)");

    private final JsonPathMatcher componentInclude = new JsonPathMatcher("$.include[*].component");

    private final @Nullable String path;

    private final @Nullable String versionPattern;

    private final @Nullable VersionComparator versionComparator;

    public ComponentMatcher(@Nullable String path, @Nullable String versionPattern) {
        this.path = path == null ? null : LEGACY_REGEX_ESCAPE.matcher(path).replaceAll("$1");
        this.versionPattern = versionPattern;
        Validated<VersionComparator> semver = versionPattern == null ? null : Semver.validate(versionPattern, null);
        this.versionComparator = semver != null && semver.isValid() ? semver.getValue() : null;
    }

    public @Nullable String getPath() {
        return path;
    }

    /// Returns the include's component reference when the cursor points at a matching `component:` entry.
    public @Nullable ComponentReference find(Cursor cursor) {
        if (!(cursor.getValue() instanceof Yaml.Mapping.Entry) || !componentInclude.matches(cursor)) {
            return null;
        }
        Yaml.Block value = ((Yaml.Mapping.Entry) cursor.getValue()).getValue();
        if (!(value instanceof Yaml.Scalar)) {
            return null;
        }
        ComponentReference reference = ComponentReference.parse(((Yaml.Scalar) value).getValue());
        return matches(reference) ? reference : null;
    }

    public boolean matches(ComponentReference reference) {
        return (path == null || path.equals(reference.getPath())) && matchesVersion(reference.getVersion());
    }

    private boolean matchesVersion(@Nullable String version) {
        if (versionPattern == null) {
            return true;
        }
        if (version == null) {
            return false;
        }
        return versionPattern.equals(version) ||
               versionComparator != null && versionComparator.isValid(null, version);
    }
}
