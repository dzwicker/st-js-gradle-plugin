package com.github.dzwicker.stjs.gradle;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stjs.generator.GenerationDirectory;
import org.stjs.generator.Generator;
import org.stjs.generator.GeneratorConfiguration;
import org.stjs.generator.GeneratorConfigurationBuilder;
import org.stjs.generator.JavascriptFileGenerationException;
import org.stjs.generator.MultipleFileGenerationException;

import groovy.lang.Closure;

public class GenerateStJsTask extends ConventionTask implements PatternFilterable {

	private static final Logger logger = LoggerFactory.getLogger(GenerateStJsTask.class);

	private static final Object PACKAGE_INFO_JAVA = "package-info.java";

	private final PatternFilterable patternSet = new PatternSet();

	/**
	 * The list of packages that can be referenced from the classes that will be processed by the generator
	 *
	 * @parameter
	 */
	protected List<String> allowedPackages;

	/**
	 * Sets the granularity in milliseconds of the last modification date for testing whether a source needs
	 * recompilation.
	 * <p/>
	 * default-value="0"
	 */
	private int staleMillis;

	/**
	 * If true the check, if (!array.hasOwnProperty(index)) continue; is added in each "for" array iteration
	 * <p/>
	 * default-value="true"
	 */
	private boolean generateArrayHasOwnProperty = true;

	/**
	 * If true, it generates for each JavaScript the corresponding source map back to the corresponding Java file. It
	 * also copies the Java source file in the same folder as the generated Javascript file.
	 * <p/>
	 * default-value="false"
	 */
	private boolean generateSourceMap;

	/**
	 * If true, it packs all the generated Javascript file (using the correct dependency order) into a single file named
	 * <artifactName>.js
	 * <p/>
	 * default-value="false"
	 */
	protected boolean pack;

	/**
	 * The source directories containing the sources to be compiled.
	 */
	private SourceDirectorySet compileSourceRoots;

	private File generatedSourcesDirectory;

	private String encoding = "UTF-8";

	private FileCollection classpath;
	private boolean war;
	private SourceSetOutput output;

	public GenerateStJsTask() {
		dependsOn(JavaPlugin.CLASSES_TASK_NAME);
		setGroup("generate");
	}

	@TaskAction
	protected void generate() {
		final GenerationDirectory genDir = getGeneratedSourcesDirectory();

		long t1 = System.currentTimeMillis();
		logger.info("Generating JavaScript files to " + genDir.getAbsolutePath());

		final ClassLoader builtProjectClassLoader = getBuiltProjectClassLoader();

		GeneratorConfigurationBuilder configBuilder = new GeneratorConfigurationBuilder();
		configBuilder.generateArrayHasOwnProperty(generateArrayHasOwnProperty);
		configBuilder.generateSourceMap(generateSourceMap);
		configBuilder.sourceEncoding(encoding);

		// configBuilder.allowedPackage("org.stjs.javascript");
		configBuilder.allowedPackage("org.junit");
		// configBuilder.allowedPackage("org.stjs.testing");

		if (allowedPackages != null) {
			configBuilder.allowedPackages(allowedPackages);
		}

		// scan all the packages
		Collection<String> packages = accumulatePackages();
		configBuilder.allowedPackages(packages);

		final GeneratorConfiguration configuration = configBuilder.build();
		final Generator generator = new Generator();
		generator.init(builtProjectClassLoader, encoding);

		final int[] generatedFiles = {0};
		final boolean[] hasFailures = new boolean[1];
		final File sourceDir = compileSourceRoots.getSrcDirs().iterator().next();
		// scan the modified sources
		FileTree src = compileSourceRoots.getAsFileTree();
		src = src.matching(patternSet);
		src.visit(new FileVisitor() {

			@Override
			public void visitDir(FileVisitDetails dirDetails) {
				// ignore
			}

			@Override
			public void visitFile(FileVisitDetails fileDetails) {
				if (fileDetails.getName().equals(PACKAGE_INFO_JAVA)) {
					if (logger.isDebugEnabled()) {
						logger.debug("Skipping " + fileDetails);
					}
					return;
				}

				File absoluteTarget = new File(
						generatedSourcesDirectory,
						fileDetails.getRelativePath().getPathString().replaceFirst("\\.java", ".js")
				);
				if (logger.isDebugEnabled()) {
					logger.debug("Generating " + absoluteTarget);
				}

				if (!absoluteTarget.getParentFile().exists() && !absoluteTarget.getParentFile().mkdirs()) {
					logger.error("Cannot create output directory:" + absoluteTarget.getParentFile());
					return;
				}
				String className = getClassNameForSource(fileDetails.getRelativePath().getPathString());
				if (logger.isDebugEnabled()) {
					logger.debug("Class: " + className);
				}

				try {
					generator.generateJavascript(
							builtProjectClassLoader,
							className,
							sourceDir,
							genDir,
							output.getClassesDir(),
							configuration
					);
					++generatedFiles[0];
				} catch (MultipleFileGenerationException e) {
					for (JavascriptFileGenerationException jse : e.getExceptions()) {
						logger.error("{}@{},{} has error '{}'",
								jse.getSourcePosition().getFile(),
								jse.getSourcePosition().getLine(),
								jse.getSourcePosition(),
								jse.getMessage()
						);
						logger.error("");
						logger.error("");
					}
					hasFailures[0] = true;
					// continue with the next file
				} catch (JavascriptFileGenerationException jse) {
					logger.error("{}@{},{} has error '{}'",
							jse.getSourcePosition().getFile(),
							jse.getSourcePosition().getLine(),
							jse.getSourcePosition(),
							jse.getMessage()
					);
					logger.error("");
					logger.error("");
					hasFailures[0] = true;
					// continue with the next file
				} catch (Exception e) {
					// TODO - maybe should filter more here
					logger.error("{}@{},{} has error '{}'",
							fileDetails.getPath(),
							1,
							1,
							e.toString()
					);
					logger.error("");
					logger.error("");
					hasFailures[0] = true;
					throw new RuntimeException("Error generating javascript:" + e, e);
				}
			}
		});

		generator.close();
		long t2 = System.currentTimeMillis();
		logger.info("Generated " + generatedFiles[0] + " JavaScript files in " + (t2 - t1) + " ms");
		if (generatedFiles[0] > 0) {
			filesGenerated(generator, genDir);
		}

		if (hasFailures[0]) {
			throw new RuntimeException("Errors generating JavaScript");
		}
	}

	protected void filesGenerated(final Generator generator, final GenerationDirectory genDir) {
		// copy the javascript support
		try {
			generator.copyJavascriptSupport(getGeneratedSourcesDirectory().getAbsolutePath());
		} catch (Exception ex) {
			throw new RuntimeException("Error when copying support files:" + ex.getMessage(), ex);
		}
		//TODO pack not supported
		//packFiles(generator, genDir);

	}

	/**
	 * {@inheritDoc}
	 */
	public GenerateStJsTask include(String... includes) {
		patternSet.include(includes);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public GenerateStJsTask include(Iterable<String> includes) {
		patternSet.include(includes);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public GenerateStJsTask include(Spec<FileTreeElement> includeSpec) {
		patternSet.include(includeSpec);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public GenerateStJsTask include(Closure includeSpec) {
		patternSet.include(includeSpec);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public GenerateStJsTask exclude(String... excludes) {
		patternSet.exclude(excludes);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public GenerateStJsTask exclude(Iterable<String> excludes) {
		patternSet.exclude(excludes);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public GenerateStJsTask exclude(Spec<FileTreeElement> excludeSpec) {
		patternSet.exclude(excludeSpec);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public GenerateStJsTask exclude(Closure excludeSpec) {
		patternSet.exclude(excludeSpec);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public Set<String> getIncludes() {
		return patternSet.getIncludes();
	}

	/**
	 * {@inheritDoc}
	 */
	public GenerateStJsTask setIncludes(Iterable<String> includes) {
		patternSet.setIncludes(includes);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	public Set<String> getExcludes() {
		return patternSet.getExcludes();
	}

	/**
	 * {@inheritDoc}
	 */
	public GenerateStJsTask setExcludes(Iterable<String> excludes) {
		patternSet.setExcludes(excludes);
		return this;
	}

	private String getClassNameForSource(String sourcePath) {
		// remove ending .java and replace / by .
		return sourcePath.substring(0, sourcePath.length() - 5).replace(File.separatorChar, '.');
	}

//    //TODO howto handle stale in gradle???
//    /**
//     * @return the list of Java source files to processed (those which are older than the corresponding Javascript
//     *         file). The returned files are relative to the given source directory.
//     */
//    @SuppressWarnings("unchecked")
//    private List<File> accumulateSources(GenerationDirectory genDir, File sourceDir, SourceMapping jsMapping, SourceMapping stjsMapping,
//                                         int stale) throws MojoExecutionException {
//        final List<File> result = new ArrayList<>();
//        if (sourceDir == null || !sourceDir.exists()) {
//            return result;
//        }
//        SourceInclusionScanner jsScanner = getSourceInclusionScanner(stale);
//        jsScanner.addSourceMapping(jsMapping);
//
//        SourceInclusionScanner stjsScanner = getSourceInclusionScanner(stale);
//        stjsScanner.addSourceMapping(stjsMapping);
//
//        final Set<File> staleFiles = new LinkedHashSet<>();
//
//        for (File f : sourceDir.listFiles()) {
//            if (!f.isDirectory()) {
//                continue;
//            }
//
//            try {
//                staleFiles.addAll(jsScanner.getIncludedSources(f.getParentFile(), genDir.getAbsolutePath()));
//                staleFiles.addAll(stjsScanner.getIncludedSources(f.getParentFile(), getBuildOutputDirectory()));
//            } catch (InclusionScanException e) {
//                throw new MojoExecutionException("Error scanning source root: \'" + sourceDir.getPath() + "\' "
//                    + "for stale files to recompile.", e);
//            }
//        }
//
//        // Trim root path from file paths
//        for (File file : staleFiles) {
//            String filePath = file.getPath();
//            String basePath = sourceDir.getAbsoluteFile().toString();
//            result.add(new File(filePath.substring(basePath.length() + 1)));
//        }
//        return result;
//    }
//
//    protected SourceInclusionScanner getSourceInclusionScanner(int staleMillis) {
//        SourceInclusionScanner scanner;
//
//        if (includes.isEmpty() && excludes.isEmpty()) {
//            scanner = new StaleClassSourceScanner(staleMillis, getBuildOutputDirectory());
//        } else {
//            if (includes.isEmpty()) {
//                includes.add("**/*.java");
//            }
//            scanner = new StaleClassSourceScanner(staleMillis, includes, excludes, getBuildOutputDirectory());
//        }
//
//        return scanner;
//    }

	private Collection<String> accumulatePackages() {
		final Collection<String> result = new HashSet<>();

		compileSourceRoots.getAsFileTree().visit(new FileVisitor() {

			@Override
			public void visitDir(FileVisitDetails dirDetails) {
				final String packageName =
						dirDetails.getRelativePath().getPathString().replace(File.separatorChar, '.');
				if (logger.isDebugEnabled()) {
					logger.debug("Packages: " + packageName);
				}
				result.add(packageName);
			}

			@Override
			public void visitFile(FileVisitDetails fileDetails) {
				// ignore
			}
		});
		return result;
	}

	private ClassLoader getBuiltProjectClassLoader() {
		final List<URL> urls = new ArrayList<>();
		classpath.getAsFileTree().visit(new FileVisitor() {

			@Override
			public void visitDir(FileVisitDetails dirDetails) {
				//ignore
			}

			@Override
			public void visitFile(FileVisitDetails fileDetails) {
				if (logger.isDebugEnabled()) {
					logger.debug("Classpath: " + fileDetails.getFile());
				}
				try {
					urls.add(fileDetails.getFile().toURI().toURL());
				} catch (MalformedURLException e) {
					throw new RuntimeException("Error trying to set the Hibernate Tools classpath", e);
				}
			}
		});
		try {
			urls.add(output.getClassesDir().toURI().toURL());
			urls.add(output.getResourcesDir().toURI().toURL());
		} catch (MalformedURLException e) {
			throw new RuntimeException("Error trying to set the Hibernate Tools classpath", e);
		}
		return new URLClassLoader(
				urls.toArray(new URL[] {}),
				Thread.currentThread().getContextClassLoader()
		);
	}

	public GenerationDirectory getGeneratedSourcesDirectory() {
		final File classpath = null;
		final File relativeToClasspath = new File("/");
		return new GenerationDirectory(generatedSourcesDirectory, classpath, relativeToClasspath);
	}

	public List<String> getAllowedPackages() {
		return allowedPackages;
	}

	public void setAllowedPackages(List<String> allowedPackages) {
		this.allowedPackages = allowedPackages;
	}

	public int getStaleMillis() {
		return staleMillis;
	}

	public void setStaleMillis(int staleMillis) {
		this.staleMillis = staleMillis;
	}

	public boolean isGenerateArrayHasOwnProperty() {
		return generateArrayHasOwnProperty;
	}

	public void setGenerateArrayHasOwnProperty(boolean generateArrayHasOwnProperty) {
		this.generateArrayHasOwnProperty = generateArrayHasOwnProperty;
	}

	public boolean isGenerateSourceMap() {
		return generateSourceMap;
	}

	public void setGenerateSourceMap(boolean generateSourceMap) {
		this.generateSourceMap = generateSourceMap;
	}

	public boolean isPack() {
		return pack;
	}

	public void setPack(boolean pack) {
		this.pack = pack;
	}

	public SourceDirectorySet getCompileSourceRoots() {
		return compileSourceRoots;
	}

	public void setCompileSourceRoots(SourceDirectorySet compileSourceRoots) {
		this.compileSourceRoots = compileSourceRoots;
	}

	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	/**
	 * Returns the classpath
	 */
	@InputFiles
	public FileCollection getClasspath() {
		return classpath;
	}

	/**
	 * Set the classpath
	 */
	public void setClasspath(final FileCollection classpath) {
		this.classpath = classpath;
	}

	/**
	 * Set the classpath
	 */
	public void classpath(final FileCollection classpath) {
		this.classpath = classpath;
	}

	public boolean isWar() {
		return war;
	}

	public void setWar(boolean war) {
		this.war = war;
	}

	public void setGeneratedSourcesDirectory(File generatedSourcesDirectory) {
		this.generatedSourcesDirectory = generatedSourcesDirectory;
	}

	public void setOutput(SourceSetOutput output) {
		this.output = output;
	}

	public SourceSetOutput getOutput() {
		return output;
	}
}
