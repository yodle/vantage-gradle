1.6.1 / 2017-07-17
=================
vantageGenerate task does not actually remove unresolvable configurations from the project configurations 

1.6.0 / 2017-07-11
=================
vantageGenerate task no longer attempts to resolve unresolvable dependencies.  See https://discuss.gradle.org/t/3-4-rc-1-3-3-resolving-configurations-may-be-disallowed-and-throw-illegalstateexception/21470

1.5.0 / 2016-10-11
=================
vantageCheck and vantageGenerate no longer require versions to be set.  (vantagePublish still requires it)

1.4.1 / 2016-09-12
=================
The vantageCheck task now appends a random string to the version to ensure that it doesn't conflict with an existing version

1.3.1 / 2016-07-06
=================
Fixed vantageCheck strict mode so that strictMode=false wouldn't swallow the exception thrown due to the project's dependencies having fatal issues

1.3.0 / 2016-06-24
=================
Adding vantageCheck task to allow for optionally gating builds if dependencies of the current project have issues above a configurable threshold
