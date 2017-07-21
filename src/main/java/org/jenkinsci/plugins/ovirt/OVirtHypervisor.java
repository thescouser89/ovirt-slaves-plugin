package org.jenkinsci.plugins.ovirt;

import hudson.slaves.Cloud;

import hudson.model.Label;

import java.io.IOException;
import java.util.*;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.FormValidation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.ovirt.engine.sdk4.Connection;
import org.ovirt.engine.sdk4.ConnectionBuilder;
import org.ovirt.engine.sdk4.Error;
import org.ovirt.engine.sdk4.services.ClustersService;
import org.ovirt.engine.sdk4.services.VmsService;
import org.ovirt.engine.sdk4.types.Cluster;
import org.ovirt.engine.sdk4.types.Vm;

/**
 * OVirtHypervisor is used to provide a different communication model for
 * Jenkins so that we can create new slaves by communicating with the ovirt
 * server and probing for the vms available there.
 *
 * @see <a href="http://javadoc.jenkins-ci.org/hudson/slaves/Cloud.html"></a>
 */
public class OVirtHypervisor extends Cloud {
    private String ovirtURL;
    private String clusterName;
    private String username;
    private String password;

    private transient Connection conn;
    private transient Cluster cluster;

    /**
     * @param name     Name of the OVirt Server
     * @param ovirtURL The ovirt server's API url
     * @param username The username of the user to login in the ovirt server
     * @param password The password of the user to login in the ovirt server
     */
    @DataBoundConstructor
    public OVirtHypervisor(final String name,
                           final String ovirtURL,
                           final String clusterName,
                           final String username,
                           final String password) {
        super(name);
        this.ovirtURL = ovirtURL.trim();
        this.clusterName = clusterName.trim();
        this.username = username.trim();
        this.password = password.trim();
    }

    /**
     * Go through all the clouds Objects known to Jenkins and find the
     * OVirtHypervisor object belonging to this hypervisor description.
     * <p>
     * If it is not found, a RuntimeException will be thrown
     *
     * @param hypervisorDescription description
     * @return the hypervisor object found.
     * @throws RuntimeException Could not find our ovirt instance
     */
    public static OVirtHypervisor find(final String hypervisorDescription)
            throws RuntimeException {
        if (hypervisorDescription == null) {
            return null;
        }

        for (Cloud cloud : Jenkins.getInstance().clouds) {
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

        for (Cloud cloud : Jenkins.getInstance().clouds) {
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
    public Collection<PlannedNode> provision(Label label, int excessWorkload) {
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

    /**
     * Return a list of vm names for this particular ovirt server
     *
     * @return a list of vm names
     */
    public List<String> getVMNames() {
        List<String> vmNames = new ArrayList<String>();

        for (Vm vm : getVMs()) {
            vmNames.add(vm.name());
        }
        return vmNames;
    }

    /**
     * Get the api object. Will create a new Api object if it has not been
     * initialized yet.
     *
     * @return Api object for this hypervisor
     * null if creation of Api object throws an exception
     */
    public Connection getConnection() {
        try {
            if (conn == null) {
                ConnectionBuilder myBuilder = ConnectionBuilder.connection()
                        .url(ovirtURL)
                        .user(username)
                        .password(password)
                        .insecure(true);
                conn = myBuilder.build();
            }
            return conn;
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
    public Vm getVM(String vm) {
        for (Vm vmi : getVMs()) {
            if (vmi.name().equals(vm)) {
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
    public List<Vm> getVMs() {
        try {

            VmsService vmsService = getConnection().systemService().vmsService();

            List<Vm> vms = vmsService.list().send().vms();
            List<Vm> vmsInCluster = new ArrayList<Vm>();
            // if clusterName specified, search for vms in that cluster
            if (isClusterSpecified()) {
                for (Vm vm : vms) {
                    if (vm.cluster().href()
                            .equals(getCluster().href())) {
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
     *
     * @return null if clusterName is empty
     * cluster object corresponding to clusterName
     * @throws Exception some issue with the ovirt server communication
     */
    public Cluster getCluster() throws Exception {
        if (cluster == null && isClusterSpecified()) {
            ClustersService clustersService = getConnection().systemService().clustersService();
            List<Cluster> clusters = clustersService.list().send().clusters();
            for (Cluster temp: clusters) {
                if (temp.name().equals(clusterName)) {
                    cluster = temp;
                    break;
                }
            }
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

            if (m.matches()) {
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
         * @return FormValidation object that represent whether the test was
         * successful or not
         */
        public FormValidation
        doTestConnection(@QueryParameter("ovirtURL") final String ovirtURL,
                         @QueryParameter("username") final String username,
                         @QueryParameter("password") final String password) {
            try {
                ConnectionBuilder.connection()
                        .url(ovirtURL)
                        .user(username)
                        .password(password)
                        .insecure(true)
                        .build()
                        .authenticate();
                return FormValidation.ok("Test succeeded!");
            } catch (Error e) {
                return FormValidation.error(e.getMessage());
            }
        }
    }
}
