package org.n3r;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertTrue;


public class FoobarTest {
    @Test
    public void testFileNameEqual() {
        assertTrue(new File("pom.xml").equals(new File("pom.xml")));
    }
}
