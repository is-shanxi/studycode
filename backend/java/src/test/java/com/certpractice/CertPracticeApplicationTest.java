package com.certpractice;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

class CertPracticeApplicationTest {

    @Test
    void exposesMainMethod() throws NoSuchMethodException {
        Method main = CertPracticeApplication.class.getMethod("main", String[].class);

        assertThat(Modifier.isStatic(main.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(main.getModifiers())).isTrue();
    }
}
