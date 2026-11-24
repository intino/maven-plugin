package io.intino.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/** Generates server code with Konos before the Maven compiler runs. */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PROCESS_SOURCES, threadSafe = true)
public final class CompileMojo extends KonosMojo {
	@Override public void execute() throws MojoExecutionException { execute("Build"); }
}
