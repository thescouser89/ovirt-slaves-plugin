package org.jenkinsci.plugins.ovirt;

import hudson.slaves.Cloud;

import hudson.model.Label;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Label;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.FormValidation;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.fileupload.FileItem;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.ovirt.engine.sdk.Api;

import javax.servlet.ServletException;

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
        System.out.println(new Date());
    }

    public String getovirtURL() {
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
         * Validation for the cloud given name. The name must only have symbols dot (.) or underscore (_),
         * letters, and numbers.
         *
         * @param name the cloud name to verify
         * @return FormValidation object
         */
        public FormValidation doCheckName(@QueryParameter("name") final String name) {

            String regex = "[._a-z0-9]+";
            Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(name);
            if(m.matches()) {
                return FormValidation.ok();
            }
            return FormValidation.error("Cloud name allows only: " + regex);

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
            try {
                Api api = new Api(ovirtURL, username, password, "ovirt.trustore");
                api.shutdown();
                return FormValidation.ok("Test succeeded!");
            } catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }

        }

        /**
         * This file will only render the startUpload.jelly file when the user is creating a new cloud
         *
         * Code from the secret plugin
         *
         * @See <a href='https://github.com/jenkinsci/secret-plugin/blob/master/src/main/java/hudson/plugins/secret/SecretBuildWrapper.java'>
         *
         * @param req
         * @param rsp
         * @throws IOException
         * @throws ServletException
         */
        public void doStartUpload(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            rsp.setContentType("text/html");
            req.getView(OVirtHypervisor.class, "startUpload.jelly").forward(req, rsp);
        }

        /**
         * This code will be called when the user presses the 'Upload' button while configuring a new cloud
         *
         * Code from the secret plugin
         *
         * @See <a href='https://github.com/jenkinsci/secret-plugin/blob/master/src/main/java/hudson/plugins/secret/SecretBuildWrapper.java'>
         *
         * @param req
         * @param rsp
         * @throws IOException
         * @throws ServletException
         */
        public void doUpload(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            FileItem file = req.getFileItem("trustore.file");

            if (file == null) {
                throw new ServletException("No file uploaded");
            }

            saveFile(file.get(), "ovirt.trustore");
            confirmUpload(rsp);
        }

        private void confirmUpload(final StaplerResponse rsp) throws IOException {
            rsp.setContentType("text/html");
            rsp.getWriter().println("Uploaded trustore");
        }

        private void saveFile(final byte[] data, final String filename) throws IOException {
            OutputStream os = new FileOutputStream(new File(filename));
            try {
                os.write(data);
            } finally {
                os.close();
            }
        }
    }
}
