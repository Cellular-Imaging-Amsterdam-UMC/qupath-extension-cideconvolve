# QuPath CIDeconvolve Extension

Run CIDeconvolve deconvolution from QuPath and open the result back in QuPath.

CIDeconvolve is a Python/PyTorch deconvolution tool for microscopy images. This
QuPath extension provides a graphical dialog, settings management, ROI
processing, project import, and a Groovy scripting API around the Python
`ci-deconvolve` package.

The full CIDeconvolve Python project is maintained at
[Cellular-Imaging-Amsterdam-UMC/cideconvolve](https://github.com/Cellular-Imaging-Amsterdam-UMC/cideconvolve).
This repository contains the QuPath extension that calls that Python package.

## What It Does

- Runs `ci-deconvolve` on the active QuPath image.
- Supports whole-image deconvolution.
- Supports ROI deconvolution from selected annotations.
- Supports batch ROI deconvolution from all annotations with a chosen class.
- Preserves full Z and T extent for ROI exports while cropping only in XY.
- Writes OME-Zarr or OME-TIFF output.
- Opens compatible output images in QuPath after processing.
- Adds multi-ROI outputs to the current QuPath project when possible.
- Logs image metadata sources, fallback values, Python output, and generated
  files.

## Requirements

- QuPath 0.7.0
- Python 3.12
- PyTorch installed in that Python environment
- `ci-deconvolve>=3.1.0`

The extension does not include Python, PyTorch, or CIDeconvolve. You install
those separately, then tell QuPath which Python executable to use.

## Install Python Package

Create or activate a Python 3.12 environment, then install CIDeconvolve from
PyPI:

```powershell
python -m pip install "ci-deconvolve>=3.1.0"
```

Install PyTorch using the command recommended for your system at:

```text
https://pytorch.org/get-started/locally/
```

You can verify the environment from a terminal:

```powershell
python -m ci_deconvolve.cli --validate-env
```

The output should include:

```text
CIDECONVOLVE_OK
ci_deconvolve=3.1.0
```

or a newer version.

## Install The QuPath Extension

1. Download the extension jar from the
   [latest GitHub release], download `qupath-extension-cideconvolve-x.x.x.jar`.
2. Copy the downloaded jar into your QuPath extensions folder.
3. Restart QuPath.
4. Open `Edit > Preferences > CIDeconvolve`.
5. Set `Python executable` to the `python.exe` from your Python 3.12
   environment.
6. Optional: choose whether outputs go next to the QuPath project or next to the
   original image file.
7. Run `Extensions > CIDeconvolve > Validate Python environment`.

If validation succeeds, the extension is ready to use.

## Run From The Dialog

Open an image in QuPath, then choose:

```text
Extensions > CIDeconvolve > Run deconvolution...
```

Important controls:

- `Output format`: choose OME-Zarr or OME-TIFF.
- `Create max-Z`: write a max-Z projection instead of full 3D output.
- `Region`: choose full image, selected annotations, or annotations by class.
- `Settings`: save/load reusable parameter sets in the project settings folder.
- `Overrule image metadata`: use dialog values instead of metadata found in the
  image.
- `Advanced`: stabilization, convergence, 2D widefield, QC, and temporary file
  options.
- `SNR mode`: `none`, `auto`, or `manual`. `SNR value` is used only for
  manual mode; `Acuity` ranges from -100 (smoother) to +100 (sharper).

For class-based ROI processing, assign a QuPath class to annotations and select
that class in the dialog.

## Output Files

By default, outputs are written to a `cidecon` folder in the QuPath project
folder. If no project is available, the extension falls back to the source image
folder or the user home folder.

Typical output names:

```text
ImageName_decon.ome.zarr
ImageName_decon.ome.tiff
ImageName_Class_001_decon.ome.zarr
```

The extension also writes useful side files when enabled:

- `ci_deconvolve_manifest.json`: run manifest.
- `*.cideconvolve.provenance.json`: ROI/source provenance for exported ROIs.
- MIP/source MIP QC files for OME-TIFF runs when QC MIPs are enabled.

## Metadata

The log reports which parameters came from image metadata and which values came
from user settings or fallbacks. Pixel size and Z spacing are written to output
metadata. For OME-Zarr output, the extension also reads the NGFF/CIDeconvolve
JSON metadata and applies the calibration when opening the image in QuPath.

OME-Zarr output targets NGFF v0.4 on Zarr v2 for QuPath 0.7 and OMERO-style
compatibility.

## Groovy Scripting

Open:

```text
Extensions > CIDeconvolve > Open example Groovy script
```

The example script includes comments for the available options. For automatic
output import/opening and script log output, use `runBlockingAndImport()` and
print `result.getLogs()`:

```groovy
import qupath.ext.cideconvolve.CIDeconvolve

def outputDir = buildFilePath(PROJECT_BASE_DIR, 'cidecon')
mkdirs(outputDir)

def result = CIDeconvolve.builder(getQuPath())
        .outputDirectory(outputDir)
        .omeZarr()
        .fullImage()
        .iterations(40)
        .runBlockingAndImport()

println result.getStatus()
result.getLogs().each { println it }
```

Use `.selectedObjects()` for selected annotation ROIs, or
`.annotationClass('Class name')` for all annotations with a class.

## Cancellation

The log window Cancel button requests cancellation, terminates the active Python
process tree, and removes the temporary PID file after the process exits. The
Python CLI also checks for cancellation between major processing steps.

## Troubleshooting

If validation fails, check:

- The configured Python executable is the Python from your intended environment.
- `python -m ci_deconvolve.cli --validate-env` works in a terminal.
- PyTorch is installed and can import.
- `ci-deconvolve` is version `3.1.0` or newer.

If an OME-Zarr output does not open automatically, inspect the log. QuPath 0.7
requires compatible NGFF/Zarr metadata; older Python package versions may write
layouts that QuPath cannot open directly.

Developer/build notes are in [docs/DEVELOPER.md](docs/DEVELOPER.md).

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).

