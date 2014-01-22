package org.jenkinsci.plugins.ovirt;

import hudson.slaves.Cloud;

import hudson.model.Label;

import java.util.Collection;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.FormValidation;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * OVirtHypervisor is used to provide a different communication model for
 * Jenkins so that we can create new slaves by communicating with the ovirt
 * server and probing for the vms available there.
 *
 * @see <a href="http://javadoc.jenkins-ci.org/hudson/slaves/Cloud.html">
 */
public class OVirtHypervisor extends Cloud {
    private String ovirtURL;
    private String username;
    private String password;

    /**
     *
     * @param name Name of the OVirt Server
     * @param ovirtURL The ovirt server's API url
     * @param username The username of the user to login in the ovirt server
     * @param password The password of the user to login in the ovirt server
     */
    @DataBoundConstructor
    public OVirtHypervisor(final String name,
                           final String ovirtURL,
                           final String username,
                           final String password) {
        super(name);
        this.ovirtURL = ovirtURL;
        this.username = username;
        this.password = password;
    }

    public String getURL() {
        return ovirtURL;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Returns true if this cloud is capable of provisioning new nodes for the given label
     * @param label
     * @return false
     *
     * TODO: work on this
     */
    @Override
    public boolean canProvision(Label label) {
        return false;
    }

    @Override
    /**
     * Provisions new nodes from this cloud
     * TODO: work on this
     */
    public Collection<PlannedNode> provision (Label label, int excessWorkload) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<Cloud> {

        @Override
        public String getDisplayName() {
            return "ovirt engine";
        }

        /**
         * TODO: work on this
         * @param name
         * @return
         */
        public FormValidation doCheckName(@QueryParameter("name") final String name) {
            return FormValidation.ok();
        }

        /**
         * This method is called from the view. It is provided as a button and when pressed, this method is called.
         * It will return a FormValidation object that will indicate if the options provided in the field of the plugin are
         * valid or not.
         *
         * @param ovirtURL The ovirt server's API url
         * @param username The username of the user to login in the ovirt server
         * @param password The password of the user to login in the ovirt server
         *
         * @return FormValidation object that represent whether the test was successful or not
         *
         * TODO: work on this
         */
        public FormValidation doTestConnection(@QueryParameter("ovirtURL") final String ovirtURL,
                                               @QueryParameter("username") final String username,
                                               @QueryParameter("password") final String password) {
//            return FormValidation.error("Boo!");
            return FormValidation.ok("Passed!");
        }
    }
}
