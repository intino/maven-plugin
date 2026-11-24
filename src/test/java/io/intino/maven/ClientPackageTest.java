package io.intino.maven;

import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.DeploymentRepository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import static org.junit.jupiter.api.Assertions.*;

class ClientPackageTest {
	@TempDir Path directory;
	@Test void packagesOnlyClientSourcesWithMavenMetadata() throws Exception {
		Path client = Files.createDirectories(directory.resolve("client"));
		Files.writeString(client.resolve("artifact.legio"), "Artifact(groupId=\"com.example\", version=\"2.1.0\") client\nWebImports");
		for (String folder : new String[]{"src", "gen", "res", "node_modules"}) {
			Files.createDirectories(client.resolve(folder)); Files.writeString(client.resolve(folder + "/file.txt"), folder);
		}
		Files.writeString(client.resolve("package.json"), "{}"); Files.writeString(client.resolve("webpack.config.js"), "configuration");
		var distribution = new DistributionManagement(); var repository = new DeploymentRepository();
		repository.setId("example"); repository.setUrl("https://example.org/maven"); distribution.setRepository(repository);
		Path pom = ClientPackage.create(client, directory.resolve("build/client"), distribution);
		try (var reader = Files.newBufferedReader(pom)) {
			var model = new MavenXpp3Reader().read(reader);
			assertEquals("com.example", model.getGroupId()); assertEquals("2.1.0", model.getVersion());
			assertEquals("example", model.getDistributionManagement().getRepository().getId());
		}
		for (String suffix : new String[]{"", "-sources"}) {
			try (var jar = new JarFile(pom.getParent().resolve("target/client-2.1.0" + suffix + ".jar").toFile())) {
				assertNotNull(jar.getManifest());
				for (String entry : new String[]{"src/file.txt", "gen/file.txt", "res/file.txt", "package.json", "META-INF/maven/com.example/client/pom.xml", "META-INF/maven/com.example/client/pom.properties"}) assertNotNull(jar.getEntry(entry), entry);
				assertNull(jar.getEntry("node_modules/file.txt")); assertNull(jar.getEntry("artifact.legio")); assertNull(jar.getEntry("webpack.config.js"));
			}
		}
	}
}
