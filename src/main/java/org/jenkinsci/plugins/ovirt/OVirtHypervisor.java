package org.jenkinsci.plugins.ovirt;

import hudson.slaves.Cloud;

import hudson.model.Label;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.FormValidation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;
import org.apache.commons.fileupload.FileItem;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.ovirt.engine.sdk.Api;
import org.ovirt.engine.sdk.decorators.Cluster;
import org.ovirt.engine.sdk.decorators.VM;

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
    private String clusterName;
    private String username;
    private String password;
    private String truststoreLocation;

    private transient Api api;
    private transient Cluster cluster;
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
                           final String clusterName,
                           final String username,
                           final String password,
                           final String truststoreLocation) {
        super(name);
        this.ovirtURL = ovirtURL.trim();
        this.clusterName = clusterName.trim();
        this.username = username.trim();
        this.password = password.trim();
        this.truststoreLocation = truststoreLocation.trim();
    }

    /**
     * Go through all the clouds Objects known to Jenkins and find the
     * OVirtHypervisor object belonging to this hypervisor description.
     *
     * If it is not found, a RuntimeException will be thrown
     *
     * @return the hypervisor object found.
     * @throws RuntimeException
     */
    public static OVirtHypervisor find(final String hypervisorDescription)
                                                      throws RuntimeException {
        if (hypervisorDescription == null) {
            return null;
        }

        for (Cloud cloud: Jenkins.getInstance().clouds) {
            if (cloud instanceof OVirtHypervisor) {
                OVirtHypervisor temp = (OVirtHypervisor) cloud;

                if (temp.getHypervisorDescription()
                        .equals(hypervisorDescription)) {
                    return temp;
                }
            }
        }

        // if nothing found, we are here
        throw new RuntimeException("Could not find our ovirt instance");
    }

    /**
     * Returns a map with as key the hypervisor description,
     * and as value the hypervisor object itself.
     *
     * @return Map
     */
    public static Map<String, OVirtHypervisor> getAll() {
        Map<String, OVirtHypervisor> descHypervisor =
                                        new HashMap<String, OVirtHypervisor>();

        for (Cloud cloud: Jenkins.getInstance().clouds) {
            if (cloud instanceof OVirtHypervisor) {
                OVirtHypervisor temp = (OVirtHypervisor) cloud;
                descHypervisor.put(temp.getHypervisorDescription(), temp);
            }
        }
        return descHypervisor;
    }

    /**
     * The hypervisorDescription string representation
     *
     * @return String
     */
    public String getHypervisorDescription() {
        return this.name + " " + ovirtURL;
    }

    public String getovirtURL() {
        return ovirtURL;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getTruststoreLocation() {
        return truststoreLocation;
    }

    /**
     * Returns true if this cloud is capable of provisioning new nodes for the
     * given label. Right now we can't create a new node from this plugin
     *
     * @param label the label used
     * @return false
     */
    @Override
    public boolean canProvision(Label label) {
        return false;
    }

    /**
     * Provisions new nodes from this cloud. This plugin does not support
     * this feature.
     */
    @Override
    public Collection<PlannedNode> provision (Label label, int excessWorkload) {
        throw new UnsupportedOperationException("Not supported yet");
    }

    /**
     * Determines if the cluster was specified in this object. If clusterName
     * is just an empty string, then return False
     *
     * @return true if clusterName is specified
     */
    private boolean isClusterSpecified() {
        return !clusterName.trim().equals("");
    }

    private boolean isTruststoreSpecified() {
        return !truststoreLocation.trim().equals("");
    }
    /**
     * Return a list of vm names for this particular ovirt server
     *
     * @return a list of vm names
     */
    public List<String> getVMNames() {
        List<String> vmNames = new ArrayList<String>();

        for(VM vm: getVMs()) {
            vmNames.add(vm.getName());
        }
        return vmNames;
    }

    /**
     * Get the api object. Will create a new Api object if it has not been
     * initialized yet.
     *
     * @return Api object for this hypervisor
     *         null if creation of Api object throws an exception
     */
    public Api getAPI() {
        try {
            if(api == null) {
                if (isTruststoreSpecified()) {
                    api = new Api(ovirtURL,
                                  username,
                                  password,
                                  truststoreLocation);
                } else {
                    api = new Api(ovirtURL, username, password);
                }
            }
            return api;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get the VM object of a vm from the vm name string.
     *
     * @param vm: vm name in the ovirt server
     * @return the VM object
     */
    public VM getVM(String vm) {
        for(VM vmi: getVMs()) {
            if (vmi.getName().equals(vm)) {
                return vmi;
            }
        }
        return null;
    }

    /**
     * Get a list of VM objects; those VM objects represents all the vms in
     * the ovirt server belonging to a cluster, if the cluster value is
     * specified.
     *
     * @return list of VM objects
     */
    public List<VM> getVMs() {
        try {
            List<VM> vms = getAPI().getVMs().list();
            List<VM> vmsInCluster = new ArrayList<VM>();
            // if clusterName specified, search for vms in that cluster
            if (isClusterSpecified()) {
                for (VM vm: vms) {
                    if (vm.getCluster()
                           .getHref()
                           .equals(getCluster().getHref())) {
                        vmsInCluster.add(vm);
                    }
                }
                return vmsInCluster;
            } else {
                return vms;
            }
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Get the cluster object corresponding to the clusterName if clusterName
     * is specified. The cluster object will then be memoized.
     * @return null if clusterName is empty
     *         cluster object corresponding to clusterName
     * @throws Exception
     */
    public Cluster getCluster() throws Exception {
        if (cluster == null && isClusterSpecified()) {
            cluster = getAPI().getClusters().get(clusterName);
        }
        return cluster;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<Cloud> {

        @Override
        public String getDisplayName() {
            return "ovirt engine";
        }

        /**
         * Validation for the cloud given name. The name must only have symbols
         * dot (.) or underscore (_), letters, and numbers.
         *
         * @param name the cloud name to verify
         * @return FormValidation object
         */
        public FormValidation doCheckName(@QueryParameter("name")
                                          final String name) {

            String regex = "[._a-z0-9]+";
            Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
                               .matcher(name);

            if(m.matches()) {
                return FormValidation.ok();
            }
            return FormValidation.error("Cloud name allows only: " + regex);
        }

        /**
         * This method is called from the view. It is provided as a button and
         * when pressed, this method is called. It will return a FormValidation
         * object that will indicate if the options provided in the field of
         * the plugin are valid or not.
         *
         * @param ovirtURL The ovirt server's API url
         * @param username The username of the user to login in the ovirt server
         * @param password The password of the user to login in the ovirt server
         *
         * @return FormValidation object that represent whether the test was
         *         successful or not
         */
        public FormValidation
        doTestConnection(@QueryParameter("ovirtURL") final String ovirtURL,
                         @QueryParameter("username") final String username,
                         @QueryParameter("password") final String password) {
            try {
                new Api(ovirtURL, username, password, "ovirt.trustore").shutdown();
                return FormValidation.ok("Test succeeded!");
            } catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
        }

        /**
         * This file will only render the startUpload.jelly file when the user
         * is creating a new cloud
         *
         * Code from the secret plugin
         *
         * @link <a href='https://github.com/jenkinsci/secret-plugin/blob/master/src/main/java/hudson/plugins/secret/SecretBuildWrapper.java'>
         *
         * @param req request
         * @param rsp response
         * @throws IOException
         * @throws ServletException
         */
        public void doStartUpload(StaplerRequest req, StaplerResponse rsp)
                                        throws IOException, ServletException {
            rsp.setContentType("text/html");
            req.getView(OVirtHypervisor.class, "startUpload.jelly")
               .forward(req, rsp);
        }

        /**
         * This code will be called when the user presses the 'Upload' button
         * while configuring a new cloud
         *
         * Code from the secret plugin
         *
         * @link <a href='https://github.com/jenkinsci/secret-plugin/blob/master/src/main/java/hudson/plugins/secret/SecretBuildWrapper.java'>
         *
         * @param req request
         * @param rsp response
         * @throws IOException
         * @throws ServletException
         *
         * TODO: where to save the file?
         */
        public void doUpload(StaplerRequest req, StaplerResponse rsp)
                                        throws IOException, ServletException {
            FileItem file = req.getFileItem("trustore.file");

            if (file == null) {
                throw new ServletException("No file uploaded");
            }

            saveFile(file.get(), "ovirt.trustore");
            confirmUpload(rsp);
        }

        /**
         * Tell req that we were able to upload the trustore
         *
         * @param rsp the response object
         * @throws IOException
         */
        private void confirmUpload(final StaplerResponse rsp)
                                                            throws IOException {
            rsp.setContentType("text/html");
            rsp.getWriter().println("Uploaded trustore");
        }

        /**
         * Helper method to save data into a file named 'filename'
         * @param data: the data to be written to filename
         * @param filename: the filename to write to
         * @throws IOException
         */
        private void saveFile(final byte[] data, final String filename)
                                                            throws IOException {
            OutputStream os = new FileOutputStream(new File(filename));
            try {
                os.write(data);
            } finally {
                os.close();
            }
        }
    }
}
