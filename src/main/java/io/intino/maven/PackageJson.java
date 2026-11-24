package io.intino.maven;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class PackageJson {
	private PackageJson() { }

	static void addLocalDependency(Path packageFile, String name, String localPath) throws IOException {
		JsonObject root = JsonParser.parseString(Files.readString(packageFile)).getAsJsonObject();
		JsonObject dependencies = root.has("dependencies") ? root.getAsJsonObject("dependencies") : new JsonObject();
		dependencies.addProperty(name, "file:" + localPath);
		root.add("dependencies", dependencies);
		Files.writeString(packageFile, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n");
	}
}
