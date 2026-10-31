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
package org.openrewrite.gitlab.search;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.gitlab.ImageKeywords;
import org.openrewrite.gitlab.ImageReference;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

@EqualsAndHashCode(callSuper = false)
@Value
public class FindUnpinnedImages extends Recipe {
    String displayName = "Find unpinned images";

    String description = "Find `image` and `services` entries in `.gitlab-ci.yml` that use the `latest` tag " +
            "or no tag at all, so that they can be pinned to a specific version.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new FindSourceFiles(".gitlab-ci.yml"), new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ctx) {
                Yaml.Mapping.Entry e = super.visitMappingEntry(entry, ctx);
                if (ImageKeywords.isImage(getCursor())) {
                    return e.withValue(markUnpinned(e.getValue()));
                }
                if (ImageKeywords.isServices(getCursor()) && e.getValue() instanceof Yaml.Sequence) {
                    Yaml.Sequence services = (Yaml.Sequence) e.getValue();
                    return e.withValue(services.withEntries(ListUtils.map(services.getEntries(),
                            service -> service.withBlock(markUnpinned(service.getBlock())))));
                }
                return e;
            }
        });
    }

    private static Yaml.Block markUnpinned(Yaml.Block image) {
        if (image instanceof Yaml.Scalar) {
            return isUnpinned(((Yaml.Scalar) image).getValue()) ? SearchResult.found(image) : image;
        }
        if (image instanceof Yaml.Mapping) {
            Yaml.Mapping mapping = (Yaml.Mapping) image;
            return mapping.withEntries(ListUtils.map(mapping.getEntries(), entry -> "name".equals(entry.getKey().getValue()) ?
                    entry.withValue(markUnpinned(entry.getValue())) : entry));
        }
        return image;
    }

    private static boolean isUnpinned(String image) {
        ImageReference reference = ImageReference.parse(image);
        if (reference.getDigest() != null || reference.getRepository().isEmpty()) {
            return false;
        }
        if (reference.getTag() != null) {
            return "latest".equals(reference.getTag());
        }
        // A variable may hold the tag
        return !reference.getRepository().contains("$");
    }
}
