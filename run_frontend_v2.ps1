$nodeVersion = "v22.13.1"
$nodeZip = "node-$nodeVersion-win-x64.zip"
$nodeUrl = "https://nodejs.org/dist/$nodeVersion/$nodeZip"
$nodeDir = "node-$nodeVersion-win-x64"

if (!(Test-Path $nodeDir)) {
    Write-Host "Downloading Node.js $nodeVersion..."
    Invoke-WebRequest -Uri $nodeUrl -OutFile $nodeZip
    Write-Host "Extracting Node.js $nodeVersion..."
    Expand-Archive -Path $nodeZip -DestinationPath . -Force
}

$env:Path = "$PWD\$nodeDir;" + $env:Path

cd frontend
Write-Host "Cleaning up old node_modules..."
Remove-Item -Recurse -Force node_modules -ErrorAction SilentlyContinue
Remove-Item -Force package-lock.json -ErrorAction SilentlyContinue

Write-Host "Installing NPM dependencies..."
npm install
Write-Host "Starting Vite frontend..."
npm run dev
