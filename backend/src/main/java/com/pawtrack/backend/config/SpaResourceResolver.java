package com.pawtrack.backend.config;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;
import java.io.IOException;
import java.util.Set;

/** Missing client routes resolve to the shell; API/media/docs and asset misses never do. */
final class SpaResourceResolver extends PathResourceResolver {
    private static final Set<String> RESERVED = Set.of("api", "uploads", "actuator", "v3", "swagger-ui", "error");

    @Override
    protected Resource getResource(String path, Resource location) throws IOException {
        Resource resource = super.getResource(path, location);
        if (resource != null) return resource;
        String firstSegment = path.split("/", 2)[0];
        if (RESERVED.contains(firstSegment) || path.contains(".")) return null;
        return super.getResource("index.html", location);
    }
}
