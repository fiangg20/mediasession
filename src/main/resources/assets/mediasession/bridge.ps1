param([string]$OutDir)

$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

Add-Type -AssemblyName System.Runtime.WindowsRuntime
Add-Type -AssemblyName System.Drawing

$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]

$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($op, [Type]$type) {
    try {
        $task = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
        if ($task.Wait(5000)) { return $task.Result }
    }
    catch { }
    return $null
}

function Emit($obj) {
    $json = $obj | ConvertTo-Json -Compress -Depth 4
    [Console]::Out.WriteLine($json)
    [Console]::Out.Flush()
}

function Log($message) {
    try {
        Add-Content -LiteralPath (Join-Path $OutDir 'bridge.log') -Value ((Get-Date -Format 'HH:mm:ss') + ' ' + $message)
    }
    catch { }
}

function Read-Bytes($ref) {
    $ras = Await ($ref.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
    if ($null -eq $ras) { return $null }
    $net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($ras)
    $ms = New-Object System.IO.MemoryStream
    $net.CopyTo($ms)
    $net.Dispose()
    return , $ms.ToArray()
}

function Load-Image($bytes) {
    try {
        $gdi = New-Object System.IO.MemoryStream(, $bytes)
        return [System.Drawing.Image]::FromStream($gdi)
    }
    catch { Log ('gdi decode failed: ' + $_.Exception.Message) }
    try {
        Add-Type -AssemblyName PresentationCore
        $in = New-Object System.IO.MemoryStream(, $bytes)
        $decoder = [System.Windows.Media.Imaging.BitmapDecoder]::Create($in, [System.Windows.Media.Imaging.BitmapCreateOptions]::PreservePixelFormat, [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad)
        $encoder = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
        $encoder.Frames.Add($decoder.Frames[0])
        $png = New-Object System.IO.MemoryStream
        $encoder.Save($png)
        $png.Position = 0
        return [System.Drawing.Image]::FromStream($png)
    }
    catch { Log ('wic decode failed: ' + $_.Exception.Message) }
    return $null
}

function Save-Thumb($props, $dir, $name) {
    $result = @{ file = ''; accent = 0; final = $false }
    $img = $null; $flat = $null; $gfx = $null
    try {
        $ref = $props.Thumbnail
        if ($null -eq $ref) { return $result }
        $bytes = Read-Bytes $ref
        if ($null -eq $bytes -or $bytes.Length -eq 0) {
            Log 'thumbnail stream empty'
            return $result
        }
        Log ('thumbnail bytes: ' + $bytes.Length)
        $img = Load-Image $bytes
        if ($null -eq $img) {
            $result.final = $true
            return $result
        }
        $size = 256
        $flat = New-Object System.Drawing.Bitmap($size, $size)
        $gfx = [System.Drawing.Graphics]::FromImage($flat)
        $gfx.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $gfx.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $scale = [Math]::Max($size / $img.Width, $size / $img.Height)
        $w = [single]($img.Width * $scale)
        $h = [single]($img.Height * $scale)
        $gfx.DrawImage($img, [single](($size - $w) / 2), [single](($size - $h) / 2), $w, $h)

        $sumR = 0; $sumG = 0; $sumB = 0
        for ($ix = 0; $ix -lt 16; $ix++) {
            for ($iy = 0; $iy -lt 16; $iy++) {
                $px = $flat.GetPixel($ix * 16 + 8, $iy * 16 + 8)
                $sumR += $px.R; $sumG += $px.G; $sumB += $px.B
            }
        }
        $result.accent = (([int]($sumR / 256)) -shl 16) -bor (([int]($sumG / 256)) -shl 8) -bor ([int]($sumB / 256))

        $file = Join-Path $dir $name
        $flat.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
        $result.file = $file
        $result.final = $true
        Log ('thumbnail saved: ' + $file)
    }
    catch { Log ('thumbnail failed: ' + $_.Exception.Message) }
    finally {
        foreach ($o in @($gfx, $flat, $img)) {
            if ($null -ne $o) { $o.Dispose() }
        }
    }
    return $result
}

if (-not (Test-Path -LiteralPath $OutDir)) {
    New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
}
Get-ChildItem -LiteralPath $OutDir -Filter 'art_*.png' | Remove-Item -Force
Set-Content -LiteralPath (Join-Path $OutDir 'bridge.log') -Value ''

$manager = $null
while ($null -eq $manager) {
    $manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
    if ($null -eq $manager) { Start-Sleep -Seconds 2 }
}

function Pick-Session {
    try {
        if ($selected -ne '') {
            foreach ($x in $manager.GetSessions()) {
                if ($x.SourceAppUserModelId -eq $selected) { return $x }
            }
        }
        return $manager.GetCurrentSession()
    }
    catch { return $null }
}

$selected = ''
$thumbKey = ''
$thumbFile = ''
$thumbAccent = 0
$thumbTries = 0
$counter = 0
$tick = 0
$force = $true
$pending = ''
$utf8 = New-Object System.Text.UTF8Encoding($false)
$buffer = New-Object byte[] 4096
$stdin = [Console]::OpenStandardInput()
$read = $stdin.ReadAsync($buffer, 0, $buffer.Length)

while ($true) {
    if ($read.IsCompleted) {
        $n = 0
        try { $n = $read.Result } catch { $n = 0 }
        if ($n -le 0) { break }
        $pending += $utf8.GetString($buffer, 0, $n)
        $read = $stdin.ReadAsync($buffer, 0, $buffer.Length)
        while ($true) {
            $idx = $pending.IndexOf("`n")
            if ($idx -lt 0) { break }
            $line = $pending.Substring(0, $idx).Trim()
            $pending = $pending.Substring($idx + 1)
            if ($line -eq '') { continue }
            $parts = $line.Split("`t")
            $session = Pick-Session
            try {
                switch ($parts[0]) {
                    'toggle' {
                        if ($null -ne $session) { $null = Await ($session.TryTogglePlayPauseAsync()) ([bool]) }
                    }
                    'next' {
                        if ($null -ne $session) { $null = Await ($session.TrySkipNextAsync()) ([bool]) }
                    }
                    'prev' {
                        if ($null -ne $session) { $null = Await ($session.TrySkipPreviousAsync()) ([bool]) }
                    }
                    'seek' {
                        if ($null -ne $session) {
                            $tl = $session.GetTimelineProperties()
                            $target = $tl.StartTime.TotalSeconds + [double]$parts[1]
                            $null = Await ($session.TryChangePlaybackPositionAsync([long]($target * 10000000))) ([bool])
                        }
                    }
                    'select' {
                        $selected = [string]$parts[1]
                    }
                }
            }
            catch { }
            $force = $true
        }
    }

    $tick++
    if ($force -or ($tick % 4 -eq 0)) {
        $force = $false
        try {
            $ids = @()
            foreach ($x in $manager.GetSessions()) { $ids += [string]$x.SourceAppUserModelId }
            $session = Pick-Session
            if ($null -eq $session) {
                Emit ([ordered]@{ active = $false; sessions = @($ids) })
            }
            else {
                $info = $session.GetPlaybackInfo()
                $status = [string]$info.PlaybackStatus
                $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                $tl = $session.GetTimelineProperties()
                $ctl = $info.Controls

                $title = ''; $artist = ''; $album = ''
                if ($null -ne $props) {
                    $title = [string]$props.Title
                    $artist = [string]$props.Artist
                    $album = [string]$props.AlbumTitle
                }
                $sid = [string]$session.SourceAppUserModelId

                $key = $sid + '|' + $title + '|' + $artist + '|' + $album
                if ($key -ne $thumbKey) {
                    $thumbKey = $key
                    $thumbTries = 0
                    $thumbFile = ''
                    $thumbAccent = 0
                }
                if ($thumbFile -eq '' -and $thumbTries -lt 40 -and $null -ne $props) {
                    $thumbTries++
                    $counter++
                    $t = Save-Thumb $props $OutDir ('art_' + $counter + '.png')
                    $thumbFile = [string]$t.file
                    $thumbAccent = [int]$t.accent
                    if ($t.final -and $thumbFile -eq '') { $thumbTries = 99 }
                }

                $start = $tl.StartTime.TotalSeconds
                $dur = [Math]::Max(0, $tl.EndTime.TotalSeconds - $start)
                $pos = $tl.Position.TotalSeconds - $start
                if ($status -eq 'Playing') {
                    $elapsed = ([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalSeconds
                    $rate = 1.0
                    if ($null -ne $info.PlaybackRate) { $rate = [double]$info.PlaybackRate }
                    if ($elapsed -gt 0 -and $elapsed -lt 86400) { $pos += $elapsed * $rate }
                }
                if ($dur -gt 0) { $pos = [Math]::Min($pos, $dur) }
                $pos = [Math]::Max($pos, 0)

                Emit ([ordered]@{
                    active    = $true
                    id        = $sid
                    status    = $status
                    title     = $title
                    artist    = $artist
                    album     = $album
                    position  = [Math]::Round($pos, 2)
                    duration  = [Math]::Round($dur, 2)
                    canToggle = [bool]($ctl.IsPlayPauseToggleEnabled -or $ctl.IsPlayEnabled -or $ctl.IsPauseEnabled)
                    canNext   = [bool]$ctl.IsNextEnabled
                    canPrev   = [bool]$ctl.IsPreviousEnabled
                    canSeek   = [bool]$ctl.IsPlaybackPositionEnabled
                    art       = $thumbFile
                    accent    = $thumbAccent
                    sessions  = @($ids)
                })
            }
        }
        catch { }
    }
    Start-Sleep -Milliseconds 100
}
