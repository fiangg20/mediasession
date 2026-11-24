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

function Save-Thumb($props, $dir, $name) {
    $result = @{ file = ''; accent = 0 }
    $net = $null; $img = $null; $flat = $null; $out = $null
    $fg = $null; $og = $null; $brush = $null; $path = $null
    try {
        $ref = $props.Thumbnail
        if ($null -eq $ref) { return $result }
        $ras = Await ($ref.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
        if ($null -eq $ras) { return $result }
        $net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($ras)
        $img = [System.Drawing.Image]::FromStream($net)
        $size = 256
        $flat = New-Object System.Drawing.Bitmap($size, $size)
        $fg = [System.Drawing.Graphics]::FromImage($flat)
        $fg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $fg.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $scale = [Math]::Max($size / $img.Width, $size / $img.Height)
        $w = [single]($img.Width * $scale)
        $h = [single]($img.Height * $scale)
        $fg.DrawImage($img, [single](($size - $w) / 2), [single](($size - $h) / 2), $w, $h)

        $out = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $og = [System.Drawing.Graphics]::FromImage($out)
        $og.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $d = [single]56
        $s = [single]$size
        $path = New-Object System.Drawing.Drawing2D.GraphicsPath
        $path.AddArc([single]0, [single]0, $d, $d, [single]180, [single]90)
        $path.AddArc([single]($s - $d), [single]0, $d, $d, [single]270, [single]90)
        $path.AddArc([single]($s - $d), [single]($s - $d), $d, $d, [single]0, [single]90)
        $path.AddArc([single]0, [single]($s - $d), $d, $d, [single]90, [single]90)
        $path.CloseFigure()
        $brush = New-Object System.Drawing.TextureBrush($flat)
        $og.FillPath($brush, $path)

        $r = 0; $g = 0; $b = 0
        for ($x = 0; $x -lt 16; $x++) {
            for ($y = 0; $y -lt 16; $y++) {
                $p = $flat.GetPixel($x * 16 + 8, $y * 16 + 8)
                $r += $p.R; $g += $p.G; $b += $p.B
            }
        }
        $result.accent = (([int]($r / 256)) -shl 16) -bor (([int]($g / 256)) -shl 8) -bor ([int]($b / 256))

        $file = Join-Path $dir $name
        $out.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
        $result.file = $file
    }
    catch { }
    finally {
        foreach ($o in @($brush, $path, $og, $fg, $out, $flat, $img, $net)) {
            if ($null -ne $o) { $o.Dispose() }
        }
    }
    return $result
}

if (-not (Test-Path -LiteralPath $OutDir)) {
    New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
}
Get-ChildItem -LiteralPath $OutDir -Filter 'art_*.png' | Remove-Item -Force

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
                if ($thumbFile -eq '' -and $thumbTries -lt 8 -and $null -ne $props) {
                    $thumbTries++
                    $counter++
                    $t = Save-Thumb $props $OutDir ('art_' + $counter + '.png')
                    $thumbFile = [string]$t.file
                    $thumbAccent = [int]$t.accent
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
