package io.intino.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/** Builds generated code after accessors, but before the Maven compiler runs. */
@Mojo(name = "compile", defaultPhase = LifecyclePhase.PROCESS_SOURCES, threadSafe = true)
public final class CompileMojo extends KonosMojo {
	@Override public void execute() throws MojoExecutionException { execute("Build"); }
}
