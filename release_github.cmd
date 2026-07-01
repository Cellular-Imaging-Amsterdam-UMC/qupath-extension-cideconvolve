@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

set "TAG=%~1"
if not defined TAG (
    if exist "%SCRIPT_DIR%\version.txt" (
        set /p TAG=<"%SCRIPT_DIR%\version.txt"
    )
)
if not defined TAG (
    echo ERROR: No tag provided and version.txt is missing or empty.
    echo Usage: %~nx0 [v0.2.1]
    exit /b 1
)

set "VERSION=%TAG%"
if /i "%VERSION:~0,1%"=="v" set "VERSION=%VERSION:~1%"
if not defined VERSION (
    echo ERROR: Could not derive version from tag %TAG%.
    exit /b 1
)

set "JAR_NAME=qupath-extension-cideconvolve-%VERSION%.jar"
set "JAR_PATH=%SCRIPT_DIR%\build\libs\%JAR_NAME%"

pushd "%SCRIPT_DIR%" >nul
if errorlevel 1 (
    echo ERROR: Failed to change directory to %SCRIPT_DIR%.
    exit /b 1
)

where git >nul 2>nul
if errorlevel 1 (
    echo ERROR: git was not found in PATH.
    goto :fail
)

where gh >nul 2>nul
if errorlevel 1 (
    if exist "%ProgramFiles%\GitHub CLI\gh.exe" (
        set "PATH=%ProgramFiles%\GitHub CLI;%PATH%"
    )
)

where gh >nul 2>nul
if errorlevel 1 (
    echo GitHub CLI ^(gh^) was not found in PATH.
    echo Attempting install with winget...
    where winget >nul 2>nul
    if errorlevel 1 (
        echo ERROR: winget is not available. Install GitHub CLI from:
        echo   https://cli.github.com/
        goto :fail
    )
    winget install --id GitHub.cli -e --accept-package-agreements --accept-source-agreements
    if errorlevel 1 (
        echo ERROR: GitHub CLI install failed.
        goto :fail
    )
)

where gh >nul 2>nul
if errorlevel 1 (
    if exist "%ProgramFiles%\GitHub CLI\gh.exe" (
        set "PATH=%ProgramFiles%\GitHub CLI;%PATH%"
    )
)

where gh >nul 2>nul
if errorlevel 1 (
    echo ERROR: GitHub CLI was installed, but gh.exe is not available in this shell.
    echo Open a new terminal and rerun: %~nx0 %TAG%
    goto :fail
)

gh auth status >nul 2>nul
if errorlevel 1 (
    echo ERROR: GitHub CLI is not authenticated.
    echo Run:
    echo   gh auth login
    echo Then rerun:
    echo   %~nx0 %TAG%
    goto :fail
)

git rev-parse --is-inside-work-tree >nul 2>nul
if errorlevel 1 (
    echo ERROR: This directory is not a git repository.
    goto :fail
)

for /f "usebackq delims=" %%A in (`git remote get-url origin`) do set "ORIGIN_URL=%%A"
echo Origin: %ORIGIN_URL%
echo %ORIGIN_URL% | findstr /i /c:"Cellular-Imaging-Amsterdam-UMC/qupath-extension-cideconvolve" >nul
if errorlevel 1 (
    echo ERROR: origin does not point to Cellular-Imaging-Amsterdam-UMC/qupath-extension-cideconvolve.
    goto :fail
)

git rev-parse --abbrev-ref --symbolic-full-name @{u} >nul 2>nul
if errorlevel 1 (
    echo ERROR: Current branch has no upstream branch configured.
    echo Set it with: git push -u origin HEAD
    goto :fail
)

git update-index -q --refresh
set "DIRTY="
for /f "usebackq delims=" %%A in (`git status --porcelain`) do set "DIRTY=1"
if defined DIRTY (
    echo ERROR: Working tree has uncommitted changes. Commit or stash them before releasing.
    git status --short
    goto :fail
)

echo Fetching latest remote refs...
git fetch --prune
if errorlevel 1 goto :fail

set "AHEAD="
set "BEHIND="
for /f "tokens=1,2" %%A in ('git rev-list --left-right --count HEAD...@{u}') do (
    set "AHEAD=%%A"
    set "BEHIND=%%B"
)
if not "%AHEAD%"=="0" (
    echo ERROR: Local branch is %AHEAD% commit^(s^) ahead of upstream. Push first.
    goto :fail
)
if not "%BEHIND%"=="0" (
    echo ERROR: Local branch is %BEHIND% commit^(s^) behind upstream. Pull or rebase first.
    goto :fail
)

git rev-parse -q --verify "refs/tags/%TAG%" >nul 2>nul
if not errorlevel 1 (
    echo ERROR: Local tag %TAG% already exists.
    goto :fail
)

git ls-remote --exit-code --tags origin "refs/tags/%TAG%" >nul 2>nul
if not errorlevel 1 (
    echo ERROR: Remote tag %TAG% already exists.
    goto :fail
)

gh release view "%TAG%" >nul 2>nul
if not errorlevel 1 (
    echo ERROR: GitHub release %TAG% already exists.
    goto :fail
)

echo Building release jar %JAR_NAME%...
call "%SCRIPT_DIR%\gradlew.bat" clean test shadowJar -PreleaseVersion=%VERSION%
if errorlevel 1 goto :fail

if not exist "%JAR_PATH%" (
    echo ERROR: Expected jar not found:
    echo   %JAR_PATH%
    goto :fail
)

echo Creating annotated tag %TAG%...
git tag -a "%TAG%" -m "Release %TAG%"
if errorlevel 1 goto :fail

echo Pushing tag %TAG%...
git push origin "%TAG%"
if errorlevel 1 goto :fail_after_tag

echo Creating GitHub release %TAG% and uploading %JAR_NAME%...
gh release create "%TAG%" "%JAR_PATH%" --verify-tag --title "%TAG%" --generate-notes
if errorlevel 1 goto :fail_after_push

popd >nul
echo Done. GitHub release %TAG% created with asset:
echo   %JAR_NAME%
endlocal & exit /b 0

:fail_after_push
echo ERROR: Release creation failed. The tag was pushed to origin.
goto :fail

:fail_after_tag
echo ERROR: Push failed. The local tag %TAG% was created but not pushed.
goto :fail

:fail
set "EXITCODE=%ERRORLEVEL%"
if "%EXITCODE%"=="0" set "EXITCODE=1"
popd >nul
endlocal & exit /b %EXITCODE%
