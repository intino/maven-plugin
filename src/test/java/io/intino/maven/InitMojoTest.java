package io.intino.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InitMojoTest {
	@Test void readsKonosVersionWithoutConfusingOtherDsls() throws Exception {
		assertEquals("14.2.2", InitMojo.konosVersion("Dsl(name=\"Other\", version=\"1.0\")\nDsl(name=\"Konos\", version=\"14.2.2\") > Builder(generationPackage=\"box\")"));
		assertEquals("14.3.5", InitMojo.konosVersion("Dsl(\n version = \"14.3.5\", name = \"Konos\")"));
	}

	@Test void requiresAnExplicitKonosVersion() {
		assertThrows(MojoExecutionException.class, () -> InitMojo.konosVersion("Dsl(name=\"Other\", version=\"1.0\")"));
	}
}
