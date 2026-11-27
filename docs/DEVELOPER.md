# Developer Notes

This file contains developer-focused notes for building and maintaining the
QuPath CIDeconvolve extension.

## Project

The extension runs `ci-deconvolve>=3.1.0` from an existing Python 3.12
environment and opens generated OME output back in QuPath.

## Requirements

- QuPath 0.7.0
- Eclipse Temurin JDK 25 for the current local build setup
- Python 3.12 with `ci-deconvolve>=3.1.0` and PyTorch installed

The extension does not bundle Python, PyTorch, or CIDeconvolve.

## Build

The Gradle build uses Java 25. It reads the extension version from `version.txt`
unless `-PreleaseVersion` supplies a version explicitly.

```powershell
.\gradlew.bat clean test shadowJar
```

The extension jar is written under `build/libs`.

For the current local QuPath standalone install, copy the shadow jar to:

```text
C:\rahoebe\qupath\QuPath7-StandAlone-Full\QuPathExtensions\extensions
```

## Local Python Development

The local Python CLI source lives outside this repository at:

```text
C:\rahoebe\Python\cideconvolve\ci_deconvolve_cli
```

For local development, install it editable into the Python environment used by
QuPath:

```powershell
C:\rahoebe\qupath\QuPath7-StandAlone\PythonEnvs\cellpose3_spotiflow\python.exe -m pip install -e C:\rahoebe\Python\cideconvolve\ci_deconvolve_cli
```

Validate what QuPath will see:

```powershell
C:\rahoebe\qupath\QuPath7-StandAlone\PythonEnvs\cellpose3_spotiflow\python.exe -m ci_deconvolve.cli --validate-env
```

Expected output starts with `CIDECONVOLVE_OK` and reports
`ci_deconvolve=3.1.0` or newer.

## Behavior Notes

- Full image runs use the local image path directly when it is an OME-TIFF or
  supported OME-Zarr.
- Other image server types are exported to temporary OME-TIFF before running.
- ROI runs crop in XY and preserve the full Z and T extent.
- Multi-ROI outputs are added to the current QuPath project when possible.
- OME-Zarr output targets NGFF v0.4 on Zarr v2 with OMERO channel display
  metadata for QuPath 0.7 compatibility.
- The Java extension reads OME-Zarr pixel calibration from JSON metadata and
  applies it to the QuPath image server after opening.

## Script API

Groovy scripts can use:

```groovy
import qupath.ext.cideconvolve.CIDeconvolve

def result = CIDeconvolve.builder(getQuPath())
        .outputDirectory(buildFilePath(PROJECT_BASE_DIR, 'cidecon'))
        .omeZarr()
        .annotationClass('Tumor')
        .iterations(40)
        .runBlocking()

println result.getStatus()
result.getLogs().each { println it }
```

Use `runBlocking()` when a script needs returned logs/status. Use `run()` for
GUI-style asynchronous convenience.

## Releasing

Update `version.txt`, run `clean test shadowJar`, and publish the resulting
`qupath-ci-deconvolve-<version>.jar` on the matching GitHub release.
Replace the previous CI Deconvolve JAR in QuPath Standalone Full and run its
diagnostics to verify the bundled extension and Python environment.
