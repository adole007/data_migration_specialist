<<<<<<< HEAD
param(
  [string]$Source = ".",
  [int]$MaxSizeMb = 50,
  [string]$Output = "scan-report.xlsx",
  [string]$Config = ""
)

$cp = "out"
if (!(Test-Path $cp)) { throw "Build output not found. Run scripts/build.ps1 first." }

$cmd = @("java", "-cp", $cp, "com.oz.dms.DataMigrationScanner")
if ($Config) { $cmd += @("--config", $Config) } else { $cmd += @("--source", $Source, "--maxSizeMb", $MaxSizeMb, "--output", $Output) }

Write-Host "Running: $($cmd -join ' ')"
if ($cmd.Length -eq 0) { throw "No command to run" }
$exe = $cmd[0]
$argsList = @()
if ($cmd.Length -gt 1) { $argsList = $cmd[1..($cmd.Length-1)] }
& $exe @argsList
if ($LASTEXITCODE -ne 0) { throw "Run failed" }
=======
param(
  [string]$Source = ".",
  [int]$MaxSizeMb = 50,
  [string]$Output = "scan-report.xlsx",
  [string]$Config = ""
)

$cp = "out"
if (!(Test-Path $cp)) { throw "Build output not found. Run scripts/build.ps1 first." }

$cmd = @("java", "-cp", $cp, "com.oz.dms.DataMigrationScanner")
if ($Config) { $cmd += @("--config", $Config) } else { $cmd += @("--source", $Source, "--maxSizeMb", $MaxSizeMb, "--output", $Output) }

Write-Host "Running: $($cmd -join ' ')"
if ($cmd.Length -eq 0) { throw "No command to run" }
$exe = $cmd[0]
$argsList = @()
if ($cmd.Length -gt 1) { $argsList = $cmd[1..($cmd.Length-1)] }
& $exe @argsList
if ($LASTEXITCODE -ne 0) { throw "Run failed" }
>>>>>>> c27f44f (Initial commit from genesis_task)
