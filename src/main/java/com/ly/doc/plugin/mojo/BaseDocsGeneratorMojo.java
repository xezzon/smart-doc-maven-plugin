package com.ly.doc.plugin.mojo;

import com.ly.doc.helper.JavaProjectBuilderHelper;
import com.ly.doc.model.*;
import com.ly.doc.plugin.constant.MojoConstants;
import com.ly.doc.plugin.util.ArtifactFilterUtil;
import com.ly.doc.plugin.util.ClassLoaderUtil;
import com.ly.doc.plugin.util.MojoUtils;
import com.power.common.constants.Charset;
import com.power.common.util.CollectionUtil;
import com.power.common.util.DateTimeUtil;
import com.power.common.util.RegexUtil;
import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.library.SortedClassLibraryBuilder;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static com.ly.doc.plugin.constant.GlobalConstants.TARGET_OUT_PATH;

/**
 * @author xezzon
 */
public abstract class BaseDocsGeneratorMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;
    @Component(hint = "default")
    private DependencyGraphBuilder dependencyGraphBuilder;
    @Component
    private RepositorySystem repositorySystem;
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;
    @Parameter(property = "scope")
    private String scope;
    @Parameter(required = false)
    private Set<String> excludes;
    @Parameter(required = false)
    private Set<String> includes;
    @Parameter(property = "smartdoc.skip", defaultValue = "false")
    private boolean skip;
    @Parameter(defaultValue = "${mojoExecution}")
    private MojoExecution mojoEx;

    @Parameter(defaultValue = "${project.build.outputDirectory}/smart-doc", required = true)
    protected File outputDirectory;
    @Parameter(property = "increment", defaultValue = "false")
    private Boolean increment;
    @Parameter()
    private String rpcConsumerConfig;
    @Parameter(defaultValue = "${tornaToken}")
    private String tornaToken;
    @Parameter()
    private BodyAdvice requestBodyAdvice;
    @Parameter()
    private BodyAdvice responseBodyAdvice;
    @Parameter()
    private List<ApiDataDictionary> apiDataDictionaries;
    @Parameter()
    private List<ApiErrorCodeDictionary> apiErrorCodes;
    @Parameter()
    private List<ApiConstant> apiConstants;

    private final ApiConfig apiConfig = new ApiConfig();

    protected abstract void executeMojo(ApiConfig apiConfig, JavaProjectBuilder javaProjectBuilder);

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }
        this.getLog().info("------------------------------------------------------------------------");
        this.getLog().info("Smart-doc Start preparing sources at: " + DateTimeUtil.nowStrTime());
        this.buildApiConfig();
        JavaProjectBuilder javaProjectBuilder = this.buildJavaProjectBuilder(project.getBuild().getSourceDirectory());

        String goal = mojoEx.getGoal();
        getLog().info("Smart-doc Starting Create API Documentation at: " + DateTimeUtil.nowStrTime());
        if (!MojoConstants.TORNA_RPC_MOJO.equals(goal) && !MojoConstants.TORNA_REST_MOJO.equals(goal)) {
            getLog().info("API documentation is output to => " + apiConfig.getOutPath().replace("\\", "/"));
        }
        try {
            this.executeMojo(this.apiConfig, javaProjectBuilder);
        } catch (Exception e) {
            getLog().error(e);
            if (apiConfig.isStrict()) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }

    private void buildApiConfig() {
        apiConfig.setOutPath(this.outputDirectory.getAbsolutePath());
        apiConfig.setProjectName(project.getName());
        apiConfig.setBaseDir(project.getBasedir().getAbsolutePath());
        apiConfig.setCodePath(project.getBuild().getSourceDirectory());
        apiConfig.setClassLoader(ClassLoaderUtil.getRuntimeClassLoader(project));
        apiConfig.setIncrement(this.increment);
        apiConfig.setRpcConsumerConfig(this.rpcConsumerConfig);
        apiConfig.setAppToken(this.tornaToken);
        apiConfig.setRequestBodyAdvice(this.requestBodyAdvice);
        apiConfig.setResponseBodyAdvice(this.responseBodyAdvice);
        apiConfig.setDataDictionaries(this.apiDataDictionaries);
        apiConfig.setErrorCodeDictionaries(this.apiErrorCodes);
        apiConfig.setApiConstants(this.apiConstants);
    }

    /**
     * Classloading
     */
    private JavaProjectBuilder buildJavaProjectBuilder(String codePath) throws MojoExecutionException {
        SortedClassLibraryBuilder classLibraryBuilder = new SortedClassLibraryBuilder();
        classLibraryBuilder.setErrorHander(e -> getLog().error("Parse error", e));
        JavaProjectBuilder javaDocBuilder = JavaProjectBuilderHelper.create(classLibraryBuilder);
        javaDocBuilder.setEncoding(Charset.DEFAULT_CHARSET);
        javaDocBuilder.setErrorHandler(e -> getLog().warn(e.getMessage()));
        //addSourceTree
        javaDocBuilder.addSourceTree(new File(codePath));
        javaDocBuilder.addClassLoader(ClassLoaderUtil.getRuntimeClassLoader(project));
        loadSourcesDependencies(javaDocBuilder);
        javaDocBuilder.setEncoding(project.getModel().getModelEncoding());
        return javaDocBuilder;
    }

    /**
     * load sources
     */
    private void loadSourcesDependencies(JavaProjectBuilder javaDocBuilder) throws MojoExecutionException {
        try {
            List<String> currentProjectModules = getCurrentProjectArtifacts(this.project);
            ArtifactFilter artifactFilter = this.createResolvingArtifactFilter();
            ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(this.session.getProjectBuildingRequest());
            buildingRequest.setProject(this.project);
            DependencyNode rootNode = this.dependencyGraphBuilder.buildDependencyGraph(buildingRequest,
                artifactFilter);
            List<DependencyNode> dependencyNodes = rootNode.getChildren();
            List<Artifact> artifactList = this.getArtifacts(dependencyNodes);
            List<String> projectArtifacts = project.getArtifacts().stream()
                .map(moduleName -> moduleName.getGroupId() + ":" + moduleName.getArtifactId())
                .collect(Collectors.toList());
            artifactList.forEach(artifact -> {
                if (ArtifactFilterUtil.ignoreSpringBootArtifactById(artifact)) {
                    return;
                }
                String artifactName = artifact.getGroupId() + ":" + artifact.getArtifactId();
                if (currentProjectModules.contains(artifactName)) {
                    projectArtifacts.add(artifactName);
                    return;
                }
                if (RegexUtil.isMatches(excludes, artifactName)) {
                    return;
                }
                Artifact sourcesArtifact = repositorySystem.createArtifactWithClassifier(artifact.getGroupId(),
                    artifact.getArtifactId(), artifact.getVersion(), artifact.getType(), "sources");
                if (RegexUtil.isMatches(includes, artifactName)) {
                    projectArtifacts.add(artifactName);
                    this.loadSourcesDependency(javaDocBuilder, sourcesArtifact);
                    return;
                }
                if (CollectionUtil.isEmpty(includes)) {
                    projectArtifacts.add(artifactName);
                    this.loadSourcesDependency(javaDocBuilder, sourcesArtifact);
                }
            });

        } catch (DependencyGraphBuilderException e) {
            throw new MojoExecutionException("Can't build project dependency graph", e);
        }
    }

    /**
     * reference https://github.com/jboz/living-documentation
     *
     * @param javaDocBuilder  JavaProjectBuilder
     * @param sourcesArtifact Artifact
     */
    private void loadSourcesDependency(JavaProjectBuilder javaDocBuilder, Artifact sourcesArtifact) {
        String artifactName = sourcesArtifact.getGroupId() + ":" + sourcesArtifact.getArtifactId();
        getLog().debug("smart-doc loaded artifact:" + artifactName);
        // create request
        ArtifactResolutionRequest request = new ArtifactResolutionRequest();
        request.setArtifact(sourcesArtifact);
        //request.setResolveTransitively(true);
        request.setRemoteRepositories(project.getRemoteArtifactRepositories());
        // resolve dependencies
        ArtifactResolutionResult result = repositorySystem.resolve(request);
        // load java source file into javadoc builder
        result.getArtifacts().forEach(artifact -> {
            JarFile jarFile;
            String sourceURL;
            try {
                sourceURL = artifact.getFile().toURI().toURL().toString();
                if (getLog().isDebugEnabled()) {
                    getLog().debug("smart-doc loaded jar source:" + sourceURL);
                }
                jarFile = new JarFile(artifact.getFile());
            } catch (IOException e) {
                getLog().warn("Unable to load jar source " + artifact + " : " + e.getMessage());
                return;
            }

            for (Enumeration<?> entries = jarFile.entries(); entries.hasMoreElements(); ) {
                JarEntry entry = (JarEntry) entries.nextElement();
                String name = entry.getName();
                try {
                    if (name.endsWith(".java") && !name.endsWith("/package-info.java")) {
                        String uri = "jar:" + sourceURL + "!/" + name;
                        if (getLog().isDebugEnabled()) {
                            getLog().debug(uri);
                        }
                        javaDocBuilder.addSource(new URL(uri));
                    }
                } catch (Throwable e) {
                    getLog().warn("syntax error in jar :" + sourceURL);
                    getLog().warn(e.getMessage());
                }
            }
        });
    }

    /**
     * copy from maven-dependency-plugin tree TreeMojo.java
     *
     * @return ArtifactFilter
     */
    private ArtifactFilter createResolvingArtifactFilter() {
        ScopeArtifactFilter filter;
        if (this.scope != null) {
            this.getLog().debug("+ Resolving dependency tree for scope '" + this.scope + "'");
            filter = new ScopeArtifactFilter(this.scope);
        } else {
            filter = null;
        }
        return filter;
    }

    private List<Artifact> getArtifacts(List<DependencyNode> dependencyNodes) {
        List<Artifact> artifacts = new ArrayList<>();
        if (CollectionUtil.isEmpty(dependencyNodes)) {
            return artifacts;
        }
        for (DependencyNode dependencyNode : dependencyNodes) {
            if (ArtifactFilterUtil.ignoreArtifact(dependencyNode.getArtifact())) {
                continue;
            }
            artifacts.add(dependencyNode.getArtifact());
            if (dependencyNode.getChildren().size() > 0) {
                artifacts.addAll(getArtifacts(dependencyNode.getChildren()));
            }
        }
        return artifacts;
    }

    private List<String> getCurrentProjectArtifacts(MavenProject project) {
        if (!project.hasParent()) {
            return new ArrayList<>(0);
        }
        List<String> finalArtifactsName = new ArrayList<>();
        MavenProject mavenProject = project.getParent();
        if (Objects.nonNull(mavenProject)) {
            File file = mavenProject.getBasedir();
            if (!Objects.isNull(file)) {
                String groupId = mavenProject.getGroupId();
                List<String> moduleList = mavenProject.getModules();
                moduleList.forEach(str -> finalArtifactsName.add(groupId + ":" + str));
            }
        }
        return finalArtifactsName;
    }
}
