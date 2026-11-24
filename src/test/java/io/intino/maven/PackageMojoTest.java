package io.intino.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PackageMojoTest {
	@TempDir Path directory;

	@Test void reusesOutputUntilInputsChange() throws Exception {
		Path client = Files.createDirectories(directory.resolve("client"));
		Path source = client.resolve("App.js");
		Files.writeString(source, "version 1");
		Path output = Files.createDirectories(directory.resolve("output"));
		Files.writeString(output.resolve("bundle.js"), "bundle");
		Path stamp = directory.resolve("build/client.sha256");
		PackageMojo.writeFingerprint(client, stamp);
		assertFalse(PackageMojo.requiresBuild(client, output, stamp));
		Files.writeString(source, "version 1");
		assertFalse(PackageMojo.requiresBuild(client, output, stamp), "Rewriting identical content must not rebuild");
		Files.writeString(source, "version 2");
		assertTrue(PackageMojo.requiresBuild(client, output, stamp));
		PackageMojo.writeFingerprint(client, stamp);
		Files.delete(source);
		assertTrue(PackageMojo.requiresBuild(client, output, stamp), "Deleted sources must invalidate the build");
	}

	@Test void emptyOutputRequiresBuildEvenWithMatchingFingerprint() throws Exception {
		Path client = Files.createDirectories(directory.resolve("client"));
		Path output = Files.createDirectories(directory.resolve("output"));
		Path stamp = directory.resolve("build/client.sha256");
		PackageMojo.writeFingerprint(client, stamp);
		assertTrue(PackageMojo.requiresBuild(client, output, stamp));
	}
}
