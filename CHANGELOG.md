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
