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

/// An `image:` or `services:` value, split into its name (registry and path), the tag after the last `:` of the last
/// path segment, and the digest after `@`.
@Value
@With
public class ImageReference {
    String name;

    @Nullable
    String tag;

    @Nullable
    String digest;

    public static ImageReference parse(String image) {
        int lastSlash = image.lastIndexOf('/');
        String digest = null;
        String rest = image;
        int at = image.indexOf('@', lastSlash + 1);
        if (at >= 0) {
            digest = image.substring(at + 1);
            rest = image.substring(0, at);
        }
        int colon = rest.lastIndexOf(':');
        return colon > lastSlash ?
                new ImageReference(rest.substring(0, colon), rest.substring(colon + 1), digest) :
                new ImageReference(rest, null, digest);
    }

    /// The last path segment of the name, e.g. `eclipse-temurin` for `registry.example.com/library/eclipse-temurin`.
    public String getRepository() {
        return name.substring(name.lastIndexOf('/') + 1);
    }

    /// Whether a variable may hold part of the repository, tag or digest, so that they can't be read literally.
    public boolean hasVariable() {
        return getRepository().contains("$") ||
               tag != null && tag.contains("$") ||
               digest != null && digest.contains("$");
    }

    @Override
    public String toString() {
        return name + (tag == null ? "" : ":" + tag) + (digest == null ? "" : "@" + digest);
    }
}
