# Generates CHARGEVERSE's four built-in chimes as 16-bit PCM WAV (22050 Hz mono).
# Original synthesized tones — no third-party assets, zero licensing concerns.
param()

$ErrorActionPreference = "Stop"
$outDir = "G:\CHARGEVERSE\app\src\main\assets\sounds"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$rate = 22050

function Write-Wav {
    param([string]$Path, [double[]]$Samples)
    $n = $Samples.Count
    $dataLen = $n * 2
    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter($ms)
    # RIFF header
    $bw.Write([System.Text.Encoding]::ASCII.GetBytes("RIFF"))
    $bw.Write([int](36 + $dataLen))
    $bw.Write([System.Text.Encoding]::ASCII.GetBytes("WAVE"))
    $bw.Write([System.Text.Encoding]::ASCII.GetBytes("fmt "))
    $bw.Write([int]16)
    $bw.Write([int16]1)      # PCM
    $bw.Write([int16]1)      # mono
    $bw.Write([int]$rate)
    $bw.Write([int]($rate * 2))
    $bw.Write([int16]2)
    $bw.Write([int16]16)
    $bw.Write([System.Text.Encoding]::ASCII.GetBytes("data"))
    $bw.Write([int]$dataLen)
    foreach ($s in $Samples) {
        $v = [int][math]::Round([math]::Max(-1, [math]::Min(1, $s)) * 32767)
        $bw.Write([int16]$v)
    }
    $bw.Flush()
    [System.IO.File]::WriteAllBytes($Path, $ms.ToArray())
    $bw.Close()
}

function Tone {
    param([double]$Freq, [double]$Seconds, [double]$Amp = 0.6, [double]$Attack = 0.01, [double]$Release = 0.35)
    $n = [int]($rate * $Seconds)
    $out = New-Object double[] $n
    for ($i = 0; $i -lt $n; $i++) {
        $t = $i / $rate
        $env = 1.0
        if ($t -lt $Attack) { $env = $t / $Attack }
        elseif ($t -gt ($Seconds - $Release)) {
            $rel = ($Seconds - $t) / $Release
            if ($rel -lt 0) { $rel = 0 }
            $env = $rel
        }
        # Soft sine + slight 2nd harmonic for warmth
        $out[$i] = $Amp * $env * (
            [math]::Sin(2 * [math]::PI * $Freq * $t) * 0.8 +
            [math]::Sin(2 * [math]::PI * $Freq * 2 * $t) * 0.2
        )
    }
    return $out
}

function Concat {
    param([double[][]]$Parts)
    $total = 0
    foreach ($p in $Parts) { $total += $p.Count }
    $out = New-Object double[] $total
    $o = 0
    foreach ($p in $Parts) {
        [Array]::Copy($p, 0, $out, $o, $p.Count)
        $o += $p.Count
    }
    return $out
}

# 1) connect.wav — rising two-tone A4 -> E5 (plugged in: energizing)
$connect = Concat @((Tone 440 0.28 0.55 0.01 0.12), (Tone 659 0.5 0.6 0.01 0.4))
Write-Wav (Join-Path $outDir "connect.wav") $connect

# 2) disconnect.wav — falling two-tone E5 -> A4 (unplugged: settling)
$disconnect = Concat @((Tone 659 0.28 0.55 0.01 0.12), (Tone 440 0.5 0.6 0.01 0.4))
Write-Wav (Join-Path $outDir "disconnect.wav") $disconnect

# 3) full.wav — bright triad arpeggio C5 E5 G5 C6 (battery full)
$full = Concat @(
    (Tone 523 0.22 0.5 0.01 0.1),
    (Tone 659 0.22 0.5 0.01 0.1),
    (Tone 784 0.22 0.5 0.01 0.1),
    (Tone 1047 0.6 0.6 0.01 0.5)
)
Write-Wav (Join-Path $outDir "full.wav") $full

# 4) low.wav — double soft beep A4, gap, G4 (battery low: gentle warning)
$beep1 = Tone 440 0.22 0.45 0.01 0.12
$gap = New-Object double[] ([int]($rate * 0.15))
$beep2 = Tone 392 0.45 0.5 0.01 0.35
$low = Concat @($beep1, $gap, $beep2)
Write-Wav (Join-Path $outDir "low.wav") $low

Get-ChildItem $outDir | Select-Object Name, Length
