Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

Get-Process | Where-Object { $_.ProcessName -eq 'MJDingTalk' } | Stop-Process -Force
Start-Sleep -Milliseconds 800

Start-Process -FilePath 'D:\desktop\MJ-DingTalk-release\MJDingTalk.exe' -WorkingDirectory 'D:\desktop\MJ-DingTalk-release'

function Snap($n) {
    $b = [System.Windows.Forms.SystemInformation]::VirtualScreen
    $bmp = New-Object System.Drawing.Bitmap $b.Width, $b.Height
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($b.X, $b.Y, 0, 0, $bmp.Size)
    $p = 'D:\desktop\MJ-DingTalk\poc\burst_{0}.png' -f $n
    $bmp.Save($p, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
    Write-Host ("frame {0} at {1}" -f $n, (Get-Date -Format 'HH:mm:ss.fff'))
}

for ($n = 1; $n -le 8; $n++) {
    Snap $n
    Start-Sleep -Milliseconds 700
}
Write-Host "done"
