package org.jfrog.bamboo.configuration;

import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityDefaultsHelper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfrog.bamboo.configuration.util.TaskConfigurationValidations;
import org.jfrog.bamboo.context.AbstractBuildContext;
import org.jfrog.bamboo.context.NpmBuildContext;

import java.util.Map;
import java.util.Set;

/**
 * Configuration for {@link org.jfrog.bamboo.task.ArtifactoryNpmTask}
 */
public class ArtifactoryNpmConfiguration extends AbstractArtifactoryConfiguration {
    public static final String CFG_NPM_COMMAND_INSTALL = "install";
    private static final String CFG_NPM_COMMAND_PUBLISH = "publish";
    private static final String KEY = "artifactoryNpmBuilder";
    private static final Set<String> FIELDS_TO_COPY = NpmBuildContext.getFieldsToCopy();
    private static final Map<String, String> CFG_NPM_COMMAND_OPTIONS = ImmutableMap.of(CFG_NPM_COMMAND_INSTALL, "install", CFG_NPM_COMMAND_PUBLISH, "pack and publish");

    public ArtifactoryNpmConfiguration() {
        super(NpmBuildContext.PREFIX, CapabilityDefaultsHelper.CAPABILITY_BUILDER_PREFIX + ".npm");
    }

    @Override
    public void populateContextForCreate(@NotNull Map<String, Object> context) {
        super.populateContextForCreate(context);
        populateNpmCommandsContext(context);
        context.put("artifactoryNpmTask", this);
        context.put("builderType", this);
        context.put("builder", this);
        context.put("adminConfig", administrationConfiguration);
        context.put("baseUrl", administrationConfiguration.getBaseUrl());
        Plan plan = (Plan) context.get("plan");
        context.put("build", plan);
        context.put("dummyList", Lists.newArrayList());
        context.put("serverConfigManager", serverConfigManager);
        context.put("selectedResolutionServerId", -1);
        context.put("selectedResolutionRepoKey", "");
        context.put("selectedPublishingServerId", -1);
        context.put("selectedPublishingRepoKey", "");
    }

    @Override
    public void populateContextForEdit(@NotNull Map<String, Object> context, @NotNull TaskDefinition taskDefinition) {
        super.populateContextForEdit(context, taskDefinition);
        populateNpmCommandsContext(context);
        populateContextWithConfiguration(context, taskDefinition, FIELDS_TO_COPY);

        context.put("selectedPublishingServerId", context.get(NpmBuildContext.NPM_DEPLOYER_SERVER_ID));
        context.put("selectedResolutionServerId", context.get(NpmBuildContext.NPM_RESOLVER_SERVER_ID));

        String selectedResolutionRepoKey = context.get(NpmBuildContext.NPM_RESOLUTION_REPO) != null ?
                context.get(NpmBuildContext.NPM_RESOLUTION_REPO).toString() : NpmBuildContext.NO_RESOLUTION_REPO_KEY_CONFIGURED;
        context.put("selectedResolutionRepoKey", selectedResolutionRepoKey);

        String selectedPublishingRepoKey = context.get(NpmBuildContext.NPM_PUBLISHING_REPO) != null ?
                context.get(NpmBuildContext.NPM_PUBLISHING_REPO).toString() : NpmBuildContext.NO_PUBLISHING_REPO_KEY_CONFIGURED;
        context.put("selectedPublishingRepoKey", selectedPublishingRepoKey);

        context.put("serverConfigManager", serverConfigManager);
        String envVarsExcludePatterns = (String) context.get(AbstractBuildContext.ENV_VARS_EXCLUDE_PATTERNS);
        if (envVarsExcludePatterns == null) {
            context.put(AbstractBuildContext.ENV_VARS_EXCLUDE_PATTERNS, AbstractBuildContext.ENV_VARS_TO_EXCLUDE);
        }
    }

    private void populateNpmCommandsContext(@NotNull Map<String, Object> context) {
        context.put("npmCommandOptions", CFG_NPM_COMMAND_OPTIONS);
        context.put(NpmBuildContext.COMMAND_CHOICE, CFG_NPM_COMMAND_INSTALL);
    }

    @Override
    protected void resetResolverConfigIfNeeded(AbstractBuildContext buildContext) {
        long serverId = buildContext.getResolutionArtifactoryServerId();
        if (serverId == -1) {
            buildContext.resetResolverContextToDefault();
        }
    }

    @NotNull
    @Override
    public Map<String, String> generateTaskConfigMap(@NotNull ActionParametersMap params,
                                                     @Nullable TaskDefinition previousTaskDefinition) {
        Map<String, String> taskConfigMap = super.generateTaskConfigMap(params, previousTaskDefinition);
        taskConfiguratorHelper.populateTaskConfigMapWithActionParameters(taskConfigMap, params, FIELDS_TO_COPY);
        NpmBuildContext buildContext = new NpmBuildContext(taskConfigMap);
        resetDeployerConfigIfNeeded(buildContext);
        resetResolverConfigIfNeeded(buildContext);
        taskConfigMap.putAll(super.getSshFileContent(params, previousTaskDefinition));
        decryptFields(taskConfigMap);
        return taskConfigMap;
    }

    @Override
    protected String getKey() {
        return KEY;
    }

    @Override
    public boolean taskProducesTestResults(@NotNull TaskDefinition taskDefinition) {
        return false;
    }

    @Override
    public void validate(@NotNull ActionParametersMap params, @NotNull ErrorCollection errorCollection) {
        String commandChoiceKey = NpmBuildContext.COMMAND_CHOICE;
        if (CFG_NPM_COMMAND_PUBLISH.equals(params.getString(commandChoiceKey))) {
            // Validate publish server.
            String deploymentServerKey = NpmBuildContext.PREFIX + NpmBuildContext.SERVER_ID_PARAM;
            String deploymentRepoKey = NpmBuildContext.PREFIX + NpmBuildContext.PUBLISHING_REPO_PARAM;
            TaskConfigurationValidations.validateArtifactoryServerAndRepo(deploymentServerKey, deploymentRepoKey, serverConfigManager, params, errorCollection);
        }

        // Validate Executable.
        String executableKey = NpmBuildContext.PREFIX + AbstractBuildContext.EXECUTABLE;
        if (StringUtils.isBlank(params.getString(executableKey))) {
            errorCollection.addError(executableKey, "Please specify an Executable.");
        }
    }
}
