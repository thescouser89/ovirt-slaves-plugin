ovirt-slaves-plugin
===================

Jenkins plugin to control VM slaves managed by ovirt/RHEV.


## How does it works
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
