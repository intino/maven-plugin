package io.intino.maven;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebArtifactTest {
	@Test void recognisesTheLegioWebArtifactSyntax() {
		var matcher = Pattern.compile("WebArtifact\\(\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"([^\\\"]+)\\\"\\s*\\)\\s*([^\\s]+)")
			.matcher("WebArtifact(\"io.intino.alexandria\", \"ui-framework-elements\", \"7.4.25\") alexandria-ui-elements");
		assertTrue(matcher.find());
		assertEquals("io.intino.alexandria", matcher.group(1));
		assertEquals("alexandria-ui-elements", matcher.group(4));
	}

	@Test void convertsUiServiceNamesToClientDirectories() {
		assertEquals("inventario-elements", ImportMojo.kebabCase("InventarioElements"));
		assertEquals("ui-elements", ImportMojo.kebabCase("UIElements"));
	}
}
