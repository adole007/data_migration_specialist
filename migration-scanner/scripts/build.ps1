<<<<<<< HEAD
param(
  [string]$JavaSourceVersion = "17",
  [string]$JavaTargetVersion = "17"
)

Write-Host "Compiling Java sources..."
$src = Get-ChildItem -Recurse -Filter *.java | ForEach-Object FullName
New-Item -ItemType Directory -Force out | Out-Null
javac -encoding UTF-8 -source $JavaSourceVersion -target $JavaTargetVersion -d out $src
if ($LASTEXITCODE -ne 0) { throw "javac failed" }
Write-Host "Build complete: out/"
=======
param(
  [string]$JavaSourceVersion = "17",
  [string]$JavaTargetVersion = "17"
)

Write-Host "Compiling Java sources..."
$src = Get-ChildItem -Recurse -Filter *.java | ForEach-Object FullName
New-Item -ItemType Directory -Force out | Out-Null
javac -encoding UTF-8 -source $JavaSourceVersion -target $JavaTargetVersion -d out $src
if ($LASTEXITCODE -ne 0) { throw "javac failed" }
Write-Host "Build complete: out/"
>>>>>>> c27f44f (Initial commit from genesis_task)
