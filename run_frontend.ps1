$nodeVersion = "v20.12.2"
$nodeZip = "node-$nodeVersion-win-x64.zip"
$nodeUrl = "https://nodejs.org/dist/$nodeVersion/$nodeZip"
$nodeDir = "node-$nodeVersion-win-x64"

if (!(Test-Path $nodeDir)) {
    Write-Host "Downloading Node.js..."
    Invoke-WebRequest -Uri $nodeUrl -OutFile $nodeZip
    Write-Host "Extracting Node.js..."
    Expand-Archive -Path $nodeZip -DestinationPath . -Force
}

$env:Path = "$PWD\$nodeDir;" + $env:Path

cd frontend
Write-Host "Installing NPM dependencies..."
npm install
Write-Host "Starting Vite frontend..."
npm run dev
