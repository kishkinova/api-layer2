scripts
=======

The `scripts` directory contains useful scripts that can be used by developer or are used by the build on Jenkins.

`classify_changes.py` - Repository changes classifier
  - It used by Jenkins build to skip some stages in case of a specific changes 

`post_actions.py` - Changes the label in Zowe pull request

`apiml_cm.sh` - APIML Certificate Management


Testing
-------

`apiml_cm.sh` is tested by Mocha script using Nixt package. 

To run the tests go to the repository root and issue:

    npm run test-script
