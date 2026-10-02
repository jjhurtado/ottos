package org.jobits.ottos.identity;

import org.jobits.ottos.ApiTestSupport;

import org.jobits.ottos.identity.domain.Permission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps code and catalog in sync: every permission an endpoint checks must exist in the permissions table
 * (added by a migration), and ADMIN must hold all of them.
 */
class PermissionCatalogTest extends ApiTestSupport {

    private static final Pattern PERMISSION = Pattern.compile("'([a-z][a-z0-9-]*:[a-z0-9:-]+)'");

    @Test
    void everyPermissionCheckedInCodeExistsInTheCatalog() throws Exception {
        Set<String> checked = permissionsCheckedByControllers();
        Set<String> catalog = permissions.findAll().stream().map(Permission::getCode).collect(Collectors.toSet());

        assertThat(checked).isNotEmpty();
        assertThat(catalog).containsAll(checked);
    }

    @Test
    void adminHoldsEveryPermission() {
        Set<String> catalog = permissions.findAll().stream().map(Permission::getCode).collect(Collectors.toSet());
        Set<String> admin = roles.findAllByOrderByCodeAsc().stream()
                .filter(r -> r.getCode().equals("ADMIN"))
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        assertThat(admin).isEqualTo(catalog);
    }

    private static Set<String> permissionsCheckedByControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> found = new TreeSet<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("org.jobits.ottos")) {
            Class<?> controller = Class.forName(definition.getBeanClassName());
            collect(controller.getAnnotation(PreAuthorize.class), found);
            for (Method method : controller.getDeclaredMethods()) {
                collect(method.getAnnotation(PreAuthorize.class), found);
            }
        }
        return found;
    }

    private static void collect(PreAuthorize annotation, Set<String> found) {
        if (annotation == null) {
            return;
        }
        Matcher matcher = PERMISSION.matcher(annotation.value());
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
    }
}
