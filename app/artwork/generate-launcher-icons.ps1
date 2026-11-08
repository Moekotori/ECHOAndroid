param(
    [string]$Source = (Join-Path $PSScriptRoot '../../scripts/assets/echo-app-icon.png')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$resourceRoot = Join-Path $PSScriptRoot '../src/main/res'
$sourceImage = [System.Drawing.Bitmap]::new((Resolve-Path -LiteralPath $Source).Path)

function Write-LauncherIcon([int]$Size, [bool]$Adaptive, [string]$Path) {
    $bitmap = [System.Drawing.Bitmap]::new($Size, $Size)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $attributes = [System.Drawing.Imaging.ImageAttributes]::new()
    try {
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $attributes.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
        if ($Adaptive) {
            # Android's nominal visible viewport is the middle 72dp of a 108dp layer.
            # Extend the original background's edge pixels into the motion inset.
            $edge = [int]($Size * 2 / 3)
            $inset = [int](($Size - $edge) / 2)
            $destStarts = @(0, $inset, ($inset + $edge))
            $destLengths = @($inset, $edge, $inset)
            $sourceX = @(0, 0, ($sourceImage.Width - 1))
            $sourceY = @(0, 0, ($sourceImage.Height - 1))
            $sourceWidths = @(1, $sourceImage.Width, 1)
            $sourceHeights = @(1, $sourceImage.Height, 1)
            for ($row = 0; $row -lt 3; $row++) {
                for ($column = 0; $column -lt 3; $column++) {
                    $dest = [System.Drawing.Rectangle]::new(
                        $destStarts[$column], $destStarts[$row],
                        $destLengths[$column], $destLengths[$row])
                    $graphics.DrawImage($sourceImage, $dest,
                        $sourceX[$column], $sourceY[$row],
                        $sourceWidths[$column], $sourceHeights[$row],
                        [System.Drawing.GraphicsUnit]::Pixel, $attributes)
                }
            }
        } else {
            $graphics.DrawImage($sourceImage, [System.Drawing.Rectangle]::new(0, 0, $Size, $Size),
                0, 0, $sourceImage.Width, $sourceImage.Height,
                [System.Drawing.GraphicsUnit]::Pixel, $attributes)
        }
        $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $attributes.Dispose()
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

try {
    if ($sourceImage.Width -ne $sourceImage.Height) {
        throw 'The selected launcher artwork must be square.'
    }
    foreach ($density in @(
        @{ Name = 'mdpi'; Scale = 1 },
        @{ Name = 'hdpi'; Scale = 1.5 },
        @{ Name = 'xhdpi'; Scale = 2 },
        @{ Name = 'xxhdpi'; Scale = 3 },
        @{ Name = 'xxxhdpi'; Scale = 4 }
    )) {
        $directory = Join-Path $resourceRoot ('mipmap-' + $density.Name)
        Write-LauncherIcon ([int](48 * $density.Scale)) $false (Join-Path $directory 'ic_launcher.png')
        Write-LauncherIcon ([int](108 * $density.Scale)) $true (Join-Path $directory 'ic_launcher_foreground.png')
    }
} finally {
    $sourceImage.Dispose()
}
