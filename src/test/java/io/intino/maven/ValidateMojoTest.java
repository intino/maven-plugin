package io.intino.maven;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ValidateMojoTest {
	@Test void skipsPomProjectsWithoutServerFiles() throws Exception {
		var mojo = new ValidateMojo();
		var packaging = ValidateMojo.class.getDeclaredField("packaging");
		packaging.setAccessible(true);
		packaging.set(mojo, "pom");
		assertDoesNotThrow(mojo::execute);
	}
}
