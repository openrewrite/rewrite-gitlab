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

import lombok.Value;
import lombok.With;
import org.jspecify.annotations.Nullable;

/// A `component:` include value, split into its path and the version after the last `@`.
@Value
@With
public class ComponentReference {
    String path;

    @Nullable
    String version;

    public static ComponentReference parse(String component) {
        int at = component.lastIndexOf('@');
        return at < 0 ?
                new ComponentReference(component, null) :
                new ComponentReference(component.substring(0, at), component.substring(at + 1));
    }

    @Override
    public String toString() {
        return version == null ? path : path + "@" + version;
    }
}
