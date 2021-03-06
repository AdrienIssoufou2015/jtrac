/*
 * Copyright 2002-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.jtrac.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;

/**
 * base class for our mojos
 */
public abstract class AntPropsMojo extends AbstractMojo {

	//======================== MOJO ===============================
    /**
     * @parameter expression="${project}"
     */
    protected MavenProject project;

    /**
     * @parameter expression="${localRepository}"
     */
    private ArtifactRepository localRepository;

    /**
     * @component role="org.apache.maven.artifact.resolver.ArtifactResolver"
     */
    private ArtifactResolver artifactResolver;

    /**
     * @parameter expression="${project.remoteArtifactRepositories}"
     */
    private List remoteArtifactRepositories;

    /**
     * @component role="org.apache.maven.artifact.factory.ArtifactFactory"
     */
    private ArtifactFactory artifactFactory;

    /**
     * @component role="org.apache.maven.project.MavenProjectBuilder"
     */
    private MavenProjectBuilder mavenProjectBuilder;
    
    //======================== CONFIG ================================
    
    /**
     * Things that will be added to the "test.jars" master classpath
     * @parameter
     */
    private List testPaths; 
    
    /**
     * Things that are not related to build / test and run but other
     * stuff like checkstyle, code coverage etc.
     * @parameter
     */    
    private List extraPaths;
    
    //======================== PRIVATE ===============================
    /**
     * properties initialized at the top e.g. "m2.repo"
     */
	protected Map buildProperties = new LinkedHashMap();
	
	/**
	 * this collects paths resolved from the input "testPaths" parameter
	 */
	protected Map testClassPaths = new LinkedHashMap();
	
	/**
	 * this collects paths resolved from the input "extraPaths" parameter
	 */
	protected Map extraClassPaths = new LinkedHashMap();
	
	/**
	 * this collects the list of files that go into WEB-INF/lib
	 */
	protected Set runtimeFiles; 
	
	/** 
	 * hack to hold paths flagged as filesets and that should be output as
	 * (relativePath1,relativePath2) not classpaths like (absolutePath1:absolutePath2)
	 * ideally we should have extended the Value Objects instead of using Maps above
	 */
	protected Set filesets = new HashSet(); 
										     
    //========================== MAIN ================================
    
	public void execute() throws MojoExecutionException {
		if (testPaths == null) {
			testPaths = new ArrayList();
		}
		if (extraPaths == null) {
			extraPaths = new ArrayList();
		}
		String repoBaseDir = localRepository.getBasedir().replace('\\','/');					
		try {
			buildProperties.put("m2.repo", repoBaseDir);
			//========================================================
			Collection runtimeArtifacts = project.getArtifacts();
			runtimeFiles = getRelativePaths(getFiles(runtimeArtifacts), repoBaseDir);
			//========================================================
			Set testArtifacts = project.getDependencyArtifacts();
			testArtifacts.addAll(project.getTestArtifacts());
			Collection testFiles = getFiles(testArtifacts);
			testClassPaths.put("m2.repo", getRelativePaths(testFiles, repoBaseDir));			
			//========================================================
			Properties props = loadProperties();
			for (Iterator i = testPaths.iterator(); i.hasNext(); ) {
				TestPath testPath = (TestPath) i.next();
				String baseDirProperty = testPath.getBaseDirProperty();
				String baseDirPath = props.getProperty(baseDirProperty);
				if (baseDirPath == null) {
					getLog().warn("baseDirProperty + '" + baseDirProperty + "' does not exist in build.properties");
					break;
				}
				File baseDir = new File(baseDirPath);
				if (!baseDir.exists() || !baseDir.isDirectory()) {
					getLog().warn("path + '" + baseDirPath + "' is not valid directory");
					break;
				}
				buildProperties.put(baseDirProperty, baseDirPath);
				Set filePaths = new TreeSet();
				for (Iterator j = testPath.getPaths().iterator(); j.hasNext(); ) {
					String path = (String) j.next();
					File file = new File(baseDirPath + "/" + path);
					if (!file.exists()) {
						getLog().warn("additional test path: '" + file.getPath() + "' does not exist");
						continue;
					}
					if (file.isDirectory()) {
						File[] files = file.listFiles();
						for (int x = 0; x < files.length; x++) {
							filePaths.add(getRelativePath(files[x], baseDir.getPath()));
						}
					} else {
						filePaths.add(getRelativePath(file, baseDir.getPath()));
					}					
				}
				testClassPaths.put(baseDirProperty, filePaths);
			}			
			//========================================================
			for (Iterator i = extraPaths.iterator(); i.hasNext(); ) {
				ExtraPath ep = (ExtraPath) i.next();
				Set paths = new TreeSet();
				Collection files = new ArrayList();
				for (Iterator j = ep.getDependencies().iterator(); j.hasNext(); ) {
					Dependency d = (Dependency) j.next();
					if (d.isResolve()) {
						files.addAll(getFiles(d.getGroupId(), d.getArtifactId(), d.getVersion()));
					} else {
						Artifact a = getArtifact(d.getGroupId(), d.getArtifactId(), d.getVersion());
						files.add(resolveArtifact(a));
					}
				}
				paths.addAll(getRelativePaths(files, repoBaseDir));
				extraClassPaths.put(ep.getName(), paths);
				if (ep.isFileset()) {
					filesets.add(ep.getName());
				}
			}			
			//========================================================
			generate();
		} catch (Exception e) {
			e.printStackTrace();
			throw new MojoExecutionException(e.getLocalizedMessage());
		}
	}
	
	//========================== HELPER METHODS ======================
	
	/**
	 * instantiate a single artifact using Maven, on the fly
	 */
	private Artifact getArtifact(String groupId, String artifactId, String version) {
	    return artifactFactory.createArtifact(groupId, artifactId, version, "", "jar");      
	}

	/**
	 * resolve dependencies for the given artifact details using Maven, on the fly
	 */
	private Collection resolveDependencies(String groupId, String artifactId, String version) throws Exception {
	    Artifact pomArtifact = getArtifact(groupId, artifactId, version); 
	    MavenProject pomProject = mavenProjectBuilder.buildFromRepository(pomArtifact, remoteArtifactRepositories, localRepository);    
	    Collection artifacts = pomProject.createArtifacts(artifactFactory, Artifact.SCOPE_TEST, new ScopeArtifactFilter(Artifact.SCOPE_TEST));
	    Iterator i = artifacts.iterator();
	    while(i.hasNext()) {
	    	Artifact a = (Artifact) i.next();
	        resolveArtifact(a);
	    }
	    artifacts.add(pomArtifact);
	    return artifacts;      
	}

	/**
	 * resolve single artifact to file, and resolve fully from repository if required
	 */
	private File resolveArtifact(Artifact artifact) throws Exception {
	    File f = artifact.getFile();
	    if (f != null) {
	    	return f;
	    }
	    getLog().info("resolving artifact: " + artifact);
	    artifactResolver.resolve(artifact, remoteArtifactRepositories, localRepository);
	    return artifact.getFile();    
	}	
	
	/**
	 * convert a collection of maven artifacts into a collection of files
	 */
	private Collection getFiles(Collection artifacts) throws Exception {
	    Collection files = new ArrayList();
	    Iterator i = artifacts.iterator();
	    while(i.hasNext()) {
	    	Artifact a = (Artifact) i.next();
	        files.add(resolveArtifact(a));
	    }
	    return files;
	}
	
	/**
	 * convenience combination of resolving and getting a bunch of files
	 */
	private Collection getFiles(String groupId, String artifactId, String version) throws Exception {
	    return getFiles(resolveDependencies(groupId, artifactId, version));
	}	
	
	/**
	 * function to return relative path given base path and the target file
	 */
	private String getRelativePath(File file, String basePath) {
	    String p = basePath.replace('\\','/');
	    int len = p.length() + 1;
	    if (p.endsWith("/")) {
	    	len--;
	    }
	    return file.getPath().substring(len).replace('\\','/');
	}
	
	/**
	 * add path entries for the given bunch of files
	 */
	private Set getRelativePaths(Collection files, String basePath) {
		Set paths = new TreeSet();
		Iterator i = files.iterator();
		while (i.hasNext()) {
			File f = (File) i.next();
			String path = getRelativePath(f, basePath);
			paths.add(path);
		}
		return paths;
	}
	
	/**
	 * load properties from file
	 */
	private Properties loadProperties() throws Exception {
		File file = new File("build.properties");
		Properties props = new Properties();
		if (!file.exists()) {
			getLog().warn("build.properties does not exist");
			return props;
		}
		InputStream is = null;
		try {
			is = new FileInputStream("build.properties");			
			props.load(is);
		} finally {
			is.close();
		}
		return props;
	}	
	
	protected abstract void generate() throws Exception;
	
	protected void generateBuildXml() {
		String projectName = project.getArtifactId();
		String projectNameTitleCase = Character.toUpperCase(projectName.charAt(0)) + projectName.substring(1);
		String buildSource = FileUtils.readFile(getClass(), "build.xml").toString();		
		String buildTarget = buildSource.replace("@@project.name@@", projectName);
		buildTarget = buildTarget.replace("@@project.name.titleCase@@", projectNameTitleCase);
		FileUtils.writeFile(buildTarget, "build.xml", false);
		getLog().info("created 'build.xml'");		
	}
	
}
