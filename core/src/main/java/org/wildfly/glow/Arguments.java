package org.wildfly.glow;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.wildfly.channel.Channel;

public class Arguments implements GoOfflineArguments, ScanArguments {

    public static final String COMPACT_PROPERTY = "compact";
    private static final String MANUAL_LAYERS_PROPERTY = "org.wildfly.glow.manual.layers";

    private final Set<String> executionProfiles;
    private final Set<String> userEnabledAddOns;
    private final List<Path> binaries;
    private final Path provisioningXML;
    private final OutputFormat output;
    private final String executionContext;
    private final boolean suggest;
    private final String version;
    private final String configName;
    private final Set<String> layersForJndi;
    public static final String CLOUD_EXECUTION_CONTEXT = "cloud";
    public static final String BARE_METAL_EXECUTION_CONTEXT = "bare-metal";
    public static final String STANDALONE_XML = "standalone.xml";
    public static final String PREVIEW = "preview";
    private final Boolean compact;
    private final Set<String> manualLayers;
    private final boolean verbose;
    private final String variant;
    private final Set<Pattern> excludeArchivesFromScan;
    private final String configStability;
    private final String packageStability;
    private final String defaultConfigStability;
    private final boolean isCli;
    private final List<Channel> channels;
    private final Set<String> spaces;
    private final MetadataProvider metadataProvider;
    private final boolean disableForkEmbedded;

    protected Arguments(
            String executionContext,
            Set<String> executionProfiles,
            Set<String> userEnabledAddOns,
            List<Path> binaries,
            Path provisioningXML,
            OutputFormat output,
            boolean suggest,
            String version,
            String configName,
            Set<String> layersForJndi,
            boolean verbose,
            String variant,
            Set<Pattern> excludeArchivesFromScan,
            String configStability,
            String packageStability,
            String defaultConfigStability,
            boolean isCli,
            List<Channel> channels,
            Set<String> spaces,
            MetadataProvider metadataProvider,
            boolean disableForkEmbedded) {
        this.executionProfiles = executionProfiles;
        this.userEnabledAddOns = userEnabledAddOns;
        this.binaries = binaries;
        this.provisioningXML = provisioningXML;
        this.output = output;
        this.executionContext = executionContext == null ? BARE_METAL_EXECUTION_CONTEXT : executionContext;
        this.suggest = suggest;
        this.version = version;
        this.configName = configName == null ? STANDALONE_XML : configName;
        this.layersForJndi = layersForJndi;
        this.verbose = verbose;
        this.variant = variant;
        this.excludeArchivesFromScan = excludeArchivesFromScan;
        this.configStability = configStability;
        this.packageStability = packageStability;
        this.defaultConfigStability = defaultConfigStability;
        HiddenPropertiesAccessor hiddenPropertiesAccessor = new HiddenPropertiesAccessor();
        this.compact = Boolean.parseBoolean(hiddenPropertiesAccessor.getProperty(COMPACT_PROPERTY));
        String manualLayers = hiddenPropertiesAccessor.getProperty(MANUAL_LAYERS_PROPERTY);
        if (manualLayers == null) {
            this.manualLayers = Collections.emptySet();
        } else {
            this.manualLayers = new HashSet<>(Arrays.asList(manualLayers.split(",")));
        }
        this.isCli = isCli;
        this.channels = channels;
        this.spaces = spaces == null ? Collections.emptySet() : spaces;
        this.metadataProvider = metadataProvider;
        this.disableForkEmbedded = disableForkEmbedded;
    }

    /**
     * @return the configName
     */
    @Override
    public String getConfigName() {
        return configName;
    }

    /**
     * @return the executionProfiles
     */
    @Override
    public Set<String> getExecutionProfiles() {
        return executionProfiles;
    }

    /**
     * @return the userEnabledAddOns
     */
    @Override
    public Set<String> getUserEnabledAddOns() {
        return userEnabledAddOns;
    }

    /**
     * @return the binary
     */
    @Override
    public List<Path> getBinaries() {
        return binaries;
    }

    /**
     * @return the provisioningXML
     */
    @Override
    public Path getProvisioningXML() {
        return provisioningXML;
    }

    /**
     * @return the output
     */
    @Override
    public OutputFormat getOutput() {
        return output;
    }

    /**
     * @return the executionContext
     */
    @Override
    public String getExecutionContext() {
        return executionContext;
    }

    /**
     * @return the suggest
     */
    @Override
    public boolean isDisableForkEmbedded() {
        return disableForkEmbedded;
    }
    /**
     * @return the suggest
     */
    @Override
    public boolean isSuggest() {
        return suggest;
    }

    @Override
    public boolean isCloud() {
        return CLOUD_EXECUTION_CONTEXT.equals(executionContext);
    }

    @Override
    public String getVersion() {
        return version;
    }

    Set<String> getManualLayers() {
        return manualLayers;
    }

    public Set<String> getLayersForJndi() {
        return layersForJndi;
    }

    /**
     * Internal use only
     */
    public Boolean isCompact() {
        return compact;
    }

    public boolean isVerbose() {
        return verbose;
    }

    @Deprecated
    @Override
    public boolean isTechPreview() {
        return PREVIEW.equals(variant);
    }

    @Override
    public String getVariant() {
        return variant;
    }

    @Override
    public Set<Pattern> getExcludeArchivesFromScan() {
        return excludeArchivesFromScan;
    }

    @Override
    public String getConfigStability() {
        return configStability;
    }

    @Override
    public String getPackageStability() {
        return packageStability;
    }

    @Override
    public String getDefaultConfigStability() {
        return defaultConfigStability;
    }

    /**
     * @return the isCli
     */
    @Override
    public boolean isCli() {
        return isCli;
    }

    /**
     * @return the channel session
     */
    @Override
    public List<Channel> getChannels() {
        return channels;
    }

    /**
     * @return the set of spaces
     */
    @Override
    public Set<String> getSpaces() {
        return spaces;
    }

    /**
     * @return the metadata provider
     */
    @Override
    public MetadataProvider getMetadataProvider() {
        return metadataProvider;
    }

    static GoOfflineArguments.Builder goOfflineBuilder() {
        return new GoOfflineArguments.Builder();
    }

    public static ScanArguments.Builder scanBuilder() {
        return new ScanArguments.Builder();
    }

}
