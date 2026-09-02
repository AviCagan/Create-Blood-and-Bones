# Create: Blood & Bones - fresh dev-client launch on Windows.
#
# Usage (from PowerShell, inside the repo folder):
#   .\run-client.ps1               # pull latest, rebuild, launch client
#   .\run-client.ps1 -CleanWorld   # same, but also delete the dev world first
#   .\run-client.ps1 -Tests        # run the headless game tests instead of the client
#
# Needs: Git and a Java 21 JDK on PATH (https://adoptium.net, "Temurin 21").
# First run downloads Minecraft assets and decompiles the game; expect 5-10 minutes.

param(
    [switch]$CleanWorld,
    [switch]$Tests,
    [string]$Branch = "claude/chat-session-ipebci"
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "== Fetching latest $Branch ==" -ForegroundColor Cyan
git fetch origin $Branch
git checkout $Branch
git reset --hard "origin/$Branch"   # discards local edits in this folder

Write-Host "== Stopping stale Gradle daemons ==" -ForegroundColor Cyan
.\gradlew.bat --stop

if ($CleanWorld) {
    Write-Host "== Deleting dev worlds ==" -ForegroundColor Yellow
    if (Test-Path run\saves) { Remove-Item -Recurse -Force run\saves }
}

if ($Tests) {
    Write-Host "== Running headless game tests ==" -ForegroundColor Cyan
    .\gradlew.bat runGameTestServer
} else {
    Write-Host "== Building and launching the client ==" -ForegroundColor Cyan
    .\gradlew.bat runClient
}
