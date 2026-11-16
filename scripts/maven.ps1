$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
# Match the Java executable on PATH rather than an unrelated, stale JAVA_HOME.
$javaBin = Split-Path -Parent (Get-Command java -ErrorAction Stop).Source
if (Test-Path -LiteralPath (Join-Path $javaBin 'javac.exe')) {
    $env:JAVA_HOME = Split-Path -Parent $javaBin
}
$projectRoot = Split-Path -Parent $PSScriptRoot
$version = '3.9.9'
$toolRoot = Join-Path $projectRoot '.tools'
$maven = Join-Path $toolRoot "apache-maven-$version\bin\mvn.cmd"
if (-not (Test-Path -LiteralPath $maven)) {
    New-Item -ItemType Directory -Force -Path $toolRoot | Out-Null
    $archive = Join-Path $toolRoot "apache-maven-$version-bin.zip"
    $url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/apache-maven-$version-bin.zip"
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $archive
    $expected = (Invoke-WebRequest -UseBasicParsing -Uri "$url.sha512").Content.Trim().Split(' ')[0]
    $actual = (Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash
    if ($actual -ine $expected) { throw 'Maven download checksum mismatch.' }
    Expand-Archive -LiteralPath $archive -DestinationPath $toolRoot -Force
    Remove-Item -LiteralPath $archive
}
& $maven @args
exit $LASTEXITCODE
