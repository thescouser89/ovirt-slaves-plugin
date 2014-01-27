package org.jenkinsci.plugins.ovirt;

import hudson.Plugin;
import hudson.slaves.Cloud;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PluginEntry is the entry point to provide plugin functionality.
 * One instance of this class will be created by Jenkins.
 *
 * @See <a href="http://javadoc.jenkins-ci.org/hudson/Plugin.html">
 */
public class PluginEntry extends Plugin {

    private static final java.util.logging.Logger LOGGER = Logger.getLogger(PluginEntry.class.getName());

    @Override
    public void start() throws Exception {
        LOGGER.log(Level.FINE, "Starting ovirt-slave plugin");
    }

    @Override
    public void stop() throws Exception {
        LOGGER.log(Level.FINE, "Stopping ovirt-slave plugin");
    }

    private Map<String, OVirtHypervisor> getDescHypervisor() {
        Map<String, OVirtHypervisor> descHypervisor = new HashMap<String, OVirtHypervisor>();
        for (Cloud cloud: Jenkins.getInstance().clouds) {
            if (cloud instanceof OVirtHypervisor) {
                OVirtHypervisor temp = (OVirtHypervisor) cloud;
                descHypervisor.put(temp.getHypervisorDescription(), temp);
            }
        }
        return descHypervisor;
    }
}
