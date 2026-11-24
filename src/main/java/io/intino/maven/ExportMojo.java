package io.intino.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/** Produces Konos accessors before generated sources are compiled. */
@Mojo(name = "export", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public final class ExportMojo extends KonosMojo {
	@Override public void execute() throws MojoExecutionException { execute("Export"); }
}
