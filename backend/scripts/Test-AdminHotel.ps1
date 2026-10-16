param(
    [string]$BaseUrl = 'http://localhost:8080',
    [Parameter(Mandatory = $true)][string]$Email
)

$ErrorActionPreference = 'Stop'
$credential = Get-Credential -UserName $Email -Message 'Login de diagnostico; la clave y el JWT no se imprimen'
if (-not $credential) { throw 'Diagnostico cancelado.' }
$login = $null
$headers = $null
$body = $null
try {
    $body = @{ email = $Email; password = $credential.GetNetworkCredential().Password } | ConvertTo-Json
    try {
        $login = Invoke-RestMethod -Method Post -Uri "$($BaseUrl.TrimEnd('/'))/api/v1/auth/login" `
            -ContentType 'application/json' -Body $body -TimeoutSec 10
    } catch {
        if ($_.Exception.Response) { Write-Output "Login HTTP $([int]$_.Exception.Response.StatusCode)" }
        else { Write-Output 'No se pudo conectar al login.' }
        return
    }
    $body = $null
    $payload = $login.token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    $payload = $payload.PadRight($payload.Length + ((4 - $payload.Length % 4) % 4), '=')
    $claims = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json
    # Decoding is diagnostic only; Spring Security validates the signature.
    [pscustomobject]@{ LoginHTTP = 200; UserId = $claims.userId; Role = $claims.role; ExpiresAtUnix = $claims.exp } | Format-List
    $headers = @{ Authorization = "Bearer $($login.token)" }
    try {
        $result = Invoke-WebRequest -UseBasicParsing -Method Get `
            -Uri "$($BaseUrl.TrimEnd('/'))/api/v1/admin/hotel" -Headers $headers -TimeoutSec 10
        $hotel = $result.Content | ConvertFrom-Json
        [pscustomobject]@{ HotelHTTP = [int]$result.StatusCode; HotelId = $hotel.id } | Format-List
    } catch {
        if ($_.Exception.Response) { [pscustomobject]@{ HotelHTTP = [int]$_.Exception.Response.StatusCode } | Format-List }
        else { Write-Output 'No se pudo conectar a Mi hotel.' }
    }
} finally {
    $body = $null
    $headers = $null
    $login = $null
    $credential = $null
    $payload = $null
    $claims = $null
}
