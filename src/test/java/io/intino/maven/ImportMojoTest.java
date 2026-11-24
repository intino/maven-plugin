package io.intino.maven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.apache.maven.plugin.MojoExecutionException;

class ImportMojoTest {
	@Test void skipsAggregatorWithoutConverterConfiguration() throws Exception {
		var mojo = new ImportMojo();
		var packaging = ImportMojo.class.getDeclaredField("packaging");
		packaging.setAccessible(true);
		packaging.set(mojo, "pom");
		assertDoesNotThrow(mojo::execute);
	}

	@Test void stillRequiresConverterConfigurationForServer() {
		assertThrows(MojoExecutionException.class, new ImportMojo()::execute);
	}
}
