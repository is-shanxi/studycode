package com.certpractice.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.certpractice.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/**
 * Cross-checks the OpenAPI 3.1 document against the Spring MVC route table so
 * the contract cannot silently drift from the implementation. The check is
 * bidirectional: every implemented route is documented and every documented
 * route exists. It also validates operation metadata, path parameters and all
 * internal $ref targets.
 */
class OpenApiContractTest extends IntegrationTestSupport {

    private static final String API_PREFIX = "/api/v1";
    private static final Pattern PATH_PARAMETER = Pattern.compile("\\{([^}]+)}");
    private static final List<String> HTTP_METHODS = List.of(
            "get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final List<String> SPEC_CANDIDATES = List.of(
            "../../specs/001-cert-practice-platform/contracts/openapi.yaml",
            "specs/001-cert-practice-platform/contracts/openapi.yaml");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void openApiDocumentCoversEveryImplementedRoute() throws Exception {
        Map<String, Object> spec = loadSpec();
        assertEquals("3.1.0", spec.get("openapi"));

        Map<String, Object> paths = asMap(spec.get("paths"));
        assertFalse(paths.isEmpty(), "openapi.yaml must declare paths");

        Set<String> implemented = implementedOperations();
        Set<String> documented = documentedOperations(paths);

        Set<String> missing = new TreeSet<>(implemented);
        missing.removeAll(documented);
        Set<String> stale = new TreeSet<>(documented);
        stale.removeAll(implemented);

        assertTrue(missing.isEmpty(), "Routes missing from openapi.yaml: " + missing);
        assertTrue(stale.isEmpty(), "openapi.yaml documents routes that do not exist: " + stale);

        validateOperations(spec, paths);
        validateRefs(spec, spec);
        validateSecurity(spec, paths);
    }

    private Set<String> implementedOperations() {
        Set<String> operations = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, ?> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith(API_PREFIX)) {
                    continue;
                }
                String relative = pattern.substring(API_PREFIX.length());
                if (relative.isEmpty()) {
                    relative = "/";
                }
                for (RequestMethod method : methods) {
                    operations.add(method.name() + " " + relative);
                }
            }
        }
        assertFalse(operations.isEmpty(), "No Spring MVC routes were discovered");
        return operations;
    }

    private Set<String> documentedOperations(final Map<String, Object> paths) {
        Set<String> operations = new TreeSet<>();
        paths.forEach((path, value) -> {
            Map<String, Object> pathItem = asMap(value);
            for (String method : HTTP_METHODS) {
                if (pathItem.containsKey(method)) {
                    operations.add(method.toUpperCase() + " " + path);
                }
            }
        });
        return operations;
    }

    private void validateOperations(
            final Map<String, Object> spec,
            final Map<String, Object> paths) {
        Set<String> definedTags = definedTags(spec);
        Set<String> operationIds = new HashSet<>();
        int operationCount = 0;

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            Map<String, Object> pathItem = asMap(pathEntry.getValue());
            List<Map<String, Object>> sharedParameters = parameters(pathItem.get("parameters"));
            for (String method : HTTP_METHODS) {
                if (!pathItem.containsKey(method)) {
                    continue;
                }
                operationCount++;
                Map<String, Object> operation = asMap(pathItem.get(method));
                String label = method.toUpperCase() + " " + pathEntry.getKey();

                String operationId = operation.get("operationId") instanceof String value ? value : null;
                assertNotNull(operationId, label + " is missing operationId");
                assertTrue(!operationId.isBlank(), label + " has a blank operationId");
                assertTrue(operationIds.add(operationId), "Duplicate operationId: " + operationId);

                assertTrue(operation.get("tags") instanceof List<?> tags && !tags.isEmpty(),
                        label + " must declare at least one tag");
                for (Object tag : (List<?>) operation.get("tags")) {
                    assertTrue(definedTags.contains(String.valueOf(tag)),
                            label + " uses an undefined tag: " + tag);
                }

                assertTrue(operation.get("responses") instanceof Map<?, ?> responses
                                && !responses.isEmpty(),
                        label + " must declare responses");
                assertTrue(operation.containsKey("security") || spec.containsKey("security"),
                        label + " must inherit or declare security");

                Set<String> undeclared = templateParameters(pathEntry.getKey());
                List<Map<String, Object>> operationParameters = parameters(
                        operation.get("parameters"));
                List<Map<String, Object>> allParameters = new ArrayList<>(sharedParameters);
                allParameters.addAll(operationParameters);
                for (Map<String, Object> parameter : allParameters) {
                    Map<String, Object> resolved = resolveParameter(spec, parameter);
                    if ("path".equals(resolved.get("in"))) {
                        undeclared.remove(String.valueOf(resolved.get("name")));
                    }
                }
                assertTrue(undeclared.isEmpty(),
                        label + " does not declare path parameters: " + undeclared);
            }
        }
        assertTrue(operationCount > 10, "The OpenAPI document looks incomplete");
    }

    private void validateRefs(final Map<String, Object> spec, final Object node) {
        if (node instanceof Map<?, ?> map) {
            Object ref = map.get("$ref");
            if (ref instanceof String reference) {
                assertNotNull(resolveRef(spec, reference), "Unresolved $ref: " + reference);
            }
            for (Object value : map.values()) {
                validateRefs(spec, value);
            }
        } else if (node instanceof List<?> list) {
            for (Object value : list) {
                validateRefs(spec, value);
            }
        }
    }

    private void validateSecurity(
            final Map<String, Object> spec,
            final Map<String, Object> paths) {
        Map<String, Object> schemes = asMap(asMap(spec.get("components")).get("securitySchemes"));
        assertFalse(schemes.isEmpty(), "securitySchemes must be defined");
        for (Object requirement : securityRequirements(spec.get("security"))) {
            for (String scheme : asMap(requirement).keySet()) {
                assertTrue(schemes.containsKey(scheme), "Unknown security scheme: " + scheme);
            }
        }
        paths.forEach((path, value) -> {
            Map<String, Object> pathItem = asMap(value);
            for (String method : HTTP_METHODS) {
                if (!pathItem.containsKey(method)) {
                    continue;
                }
                Map<String, Object> operation = asMap(pathItem.get(method));
                if (!operation.containsKey("security")) {
                    continue;
                }
                for (Object requirement : securityRequirements(operation.get("security"))) {
                    for (String scheme : asMap(requirement).keySet()) {
                        assertTrue(schemes.containsKey(scheme),
                                path + " uses unknown security scheme: " + scheme);
                    }
                }
            }
        });
    }

    private List<?> securityRequirements(final Object security) {
        return security instanceof List<?> list ? list : List.of();
    }

    private Set<String> definedTags(final Map<String, Object> spec) {
        Set<String> tags = new HashSet<>();
        if (spec.get("tags") instanceof List<?> list) {
            for (Object tag : list) {
                Object name = asMap(tag).get("name");
                if (name != null) {
                    tags.add(String.valueOf(name));
                }
            }
        }
        return tags;
    }

    private Set<String> templateParameters(final String path) {
        Set<String> names = new HashSet<>();
        Matcher matcher = PATH_PARAMETER.matcher(path);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private List<Map<String, Object>> parameters(final Object value) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                parameters.add(asMap(item));
            }
        }
        return parameters;
    }

    private Map<String, Object> resolveParameter(
            final Map<String, Object> spec,
            final Map<String, Object> parameter) {
        Object ref = parameter.get("$ref");
        return ref instanceof String reference ? asMap(resolveRef(spec, reference)) : parameter;
    }

    private Object resolveRef(final Map<String, Object> spec, final String reference) {
        if (!reference.startsWith("#/")) {
            fail("Only local $ref values are supported: " + reference);
        }
        Object current = spec;
        for (String token : reference.substring(2).split("/")) {
            String key = token.replace("~1", "/").replace("~0", "~");
            current = asMap(current).get(key);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Map<String, Object> loadSpec() throws Exception {
        for (String candidate : SPEC_CANDIDATES) {
            Path path = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.exists(path)) {
                try (InputStream input = Files.newInputStream(path)) {
                    return asMap(new Yaml().load(input));
                }
            }
        }
        fail("openapi.yaml was not found from the Maven working directory");
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(final Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
