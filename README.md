ovirt-slaves-plugin
===================

Jenkins plugin to control VM slaves managed by ovirt/RHEV.


## How does it work
`OVirtHypervisor` will be the link between Jenkins and the ovirt server to
connect existing slave vms on the server to Jenkins. It needs to override the
Cloud abstract class to achieve so.


## Building the plugin
```
mvn install
```

## Test the plugin
To test the plugin in an isolated environment, you just have to run this
command:
```
mvn hpi:run
```

To access this local instance of Jenkins, open http://localhost:8080/jenkins

## Usage
To configure the plugin, you will have to first add a hypervisor into Jenkins:
In `Jenkins/Manage Jenkins/Configure System/Cloud/Add a new cloud/ovirt engine`:

![Alt text](https://raw.githubusercontent.com/thescouser89/ovirt-slaves-plugin/master/doc_misc/pic/settings.png)

The _Engine API URL_ should point to the API endpoint of your ovirt/RHEV server.
The cluster name is used to specify which group of vms you want to see. Leave
blank if you want to see all the vms in the server.

Next we'll add a new node.

![Alt text](https://raw.githubusercontent.com/thescouser89/ovirt-slaves-plugin/master/doc_misc/pic/new_node.png)

The new node in Jenkins will link to an existing vm
in the hypervisor configured above.

![Alt text](https://raw.githubusercontent.com/thescouser89/ovirt-slaves-plugin/master/doc_misc/pic/configure_node.png)

Besides choosing the vm, you can also specify the snapshot you'd like the vm
revert to everytime it is launched. This will allow you to have a clean
environment everytime you relaunch the node.

