# Developer Notes

This file contains developer-focused notes for building and maintaining the
QuPath CIDeconvolve extension.

## Project

The extension runs `ci-deconvolve>=0.2.0` from an existing Python 3.12
environment and opens generated OME output back in QuPath.

## Requirements

- QuPath 0.7.0
- Eclipse Temurin JDK 25 for the current local build setup
- Python 3.12 with `ci-deconvolve>=0.2.0` and PyTorch installed

The extension does not bundle Python, PyTorch, or CIDeconvolve.

## Build

The project is pinned to the locally installed Temurin 25 JDK in
`gradle.properties`.

```powershell
.\gradlew.bat clean test shadowJar
```

The extension jar is written under `build/libs`.

For the current local QuPath standalone install, copy the shadow jar to:

```text
C:\rahoebe\QuPath7-StandAlone\QuPathExtensions\extensions
```

## Local Python Development

The local Python CLI source lives outside this repository at:

```text
C:\rahoebe\Python\cideconvolve\ci_deconvolve_cli
```

For local development, install it editable into the Python environment used by
QuPath:

```powershell
C:\rahoebe\QuPath7-StandAlone\PythonEnvs\cellpose3_spotiflow\python.exe -m pip install -e C:\rahoebe\Python\cideconvolve\ci_deconvolve_cli
```

Validate what QuPath will see:

```powershell
C:\rahoebe\QuPath7-StandAlone\PythonEnvs\cellpose3_spotiflow\python.exe -m ci_deconvolve.cli --validate-env
```

Expected output starts with `CIDECONVOLVE_OK` and reports
`ci_deconvolve=0.2.0` or newer.

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

Maintainers can create a GitHub Release with the installable QuPath jar by
running:

```cmd
release_github.cmd
```

The script reads `version.txt`, builds
`qupath-extension-cideconvolve-0.2.1.jar`, creates the matching Git tag, and
uploads the jar as a GitHub Release asset. Pass a tag explicitly to override the
file:

```cmd
release_github.cmd v0.2.1
```