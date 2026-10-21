<#
Examples, run from the project root (execution policy applies to this process only):
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\examples\ollama-chat.ps1 -Prompt 'Explain a Java record.'
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\examples\ollama-chat.ps1 -Model 'gemma4:cloud' -Prompt 'Explain a Java record.'
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\examples\ollama-chat.ps1 -Cloud -Prompt 'Explain a Java record.'

Local requests use the installed Ollama server. Cloud model names ending in
:cloud also work through that server after running ollama signin.
-Cloud calls https://ollama.com directly and requires OLLAMA_API_KEY in the
current process environment. Obtain a key at https://ollama.com/settings/keys.
Direct cloud requests use API model names, such as gemma4:31b.
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateNotNullOrEmpty()]
    [string]$Prompt = 'Say hello in one sentence.',

    [string]$Model,

    [switch]$Cloud
)

$ErrorActionPreference = 'Stop'
$headers = @{}
if ($Cloud) {
    if ([string]::IsNullOrWhiteSpace($env:OLLAMA_API_KEY)) {
        throw 'Set OLLAMA_API_KEY in this terminal before calling the direct cloud API.'
    }
    $endpoint = 'https://ollama.com/api/chat'
    $headers.Authorization = 'Bearer ' + $env:OLLAMA_API_KEY
    if ([string]::IsNullOrWhiteSpace($Model)) { $Model = 'gemma4:31b' }
} else {
    $endpoint = 'http://localhost:11434/api/chat'
    if ([string]::IsNullOrWhiteSpace($Model)) { $Model = 'qwen3.5:0.8b' }
}

$request = @{
    model = $Model
    messages = @(@{ role = 'user'; content = $Prompt })
    stream = $false
    think = $false
}
if (-not $Cloud -and $Model -notmatch ':cloud$') {
    # Keep memory use modest on this computer's 4 GB GPU.
    $request.options = @{ num_ctx = 2048; num_predict = 512 }
    $request.keep_alive = '2m'
}

$json = $request | ConvertTo-Json -Depth 6 -Compress
$response = Invoke-RestMethod -Uri $endpoint -Method Post -Headers $headers `
    -ContentType 'application/json; charset=utf-8' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($json)) -TimeoutSec 240
if (-not $response.done -or [string]::IsNullOrWhiteSpace($response.message.content)) {
    throw 'Ollama did not return a completed text response.'
}
$response.message.content
