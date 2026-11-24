package io.intino.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

/** Generates only the Web UI sibling modules declared as {@code Service ... as UI}. */
@Mojo(name = "compile-ui", threadSafe = true)
public final class CompileClientMojo extends KonosMojo {

	@Override public void execute() throws MojoExecutionException {
		execute("OnlyElements");
	}
}
